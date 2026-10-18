#!/usr/bin/env bash
#
# Publishes VersionFrau to Maven Central.
#
#   scripts/publish.sh                # set up signing, verify locally, upload to Central (release manually in the Portal)
#   scripts/publish.sh --release      # same, but release automatically after validation
#   scripts/publish.sh --local-only   # set up signing and verify with publishToMavenLocal, no upload
#   scripts/publish.sh --key <KEY_ID> # use a specific GPG key when more than one secret key exists
#
# What it does:
#   1. picks the GPG secret key and uploads its PUBLIC key to keyserver.ubuntu.com (Central verifies signatures there)
#   2. exports the private key in the single-line form Gradle expects (no header/footer, no =XXXX checksum)
#   3. writes signingInMemoryKey / signingInMemoryKeyId / signingInMemoryKeyPassword into ~/.gradle/gradle.properties
#      (backup kept as gradle.properties.bak), and asks for the Central token if it is missing
#   4. runs publishToMavenLocal and checks every artifact has a .asc signature
#   5. uploads with publishToMavenCentral (or publishAndReleaseToMavenCentral with --release)
#
# Environment overrides: GPG_PASSPHRASE, MAVEN_CENTRAL_USERNAME, MAVEN_CENTRAL_PASSWORD.

set -euo pipefail

project_root_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
gradle_user_properties_file="${GRADLE_USER_HOME:-$HOME/.gradle}/gradle.properties"
keyserver_url="hkps://keyserver.ubuntu.com"

publish_mode="central"
requested_key_id=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --release) publish_mode="central-release" ;;
        --local-only) publish_mode="local" ;;
        --key) requested_key_id="${2:?--key needs a key id}"; shift ;;
        -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
        *) echo "Unknown option: $1" >&2; exit 1 ;;
    esac
    shift
done

print_step() { printf '\n\033[1m==> %s\033[0m\n' "$1"; }
fail() { printf '\033[31mError:\033[0m %s\n' "$1" >&2; exit 1; }

# Escapes a value for a .properties file (backslash is the only character that breaks these values).
escape_properties_value() { local raw_value="$1"; printf '%s' "${raw_value//\\/\\\\}"; }

read_gradle_property() {
    local property_name="$1"
    [[ -f "$gradle_user_properties_file" ]] || return 0
    grep -E "^${property_name}[[:space:]]*=" "$gradle_user_properties_file" | tail -n 1 | cut -d= -f2- || true
}

command -v gpg >/dev/null || fail "gpg is not installed (brew install gnupg)."

# --- 1. Select the key -----------------------------------------------------------------------------------
print_step "Selecting GPG key"
secret_key_ids="$(gpg --list-secret-keys --with-colons | awk -F: '$1 == "sec" { print $5 }')"
[[ -n "$secret_key_ids" ]] || fail "No GPG secret key found. Create one with: gpg --full-generate-key"

if [[ -n "$requested_key_id" ]]; then
    selected_long_key_id="$(gpg --list-secret-keys --with-colons "$requested_key_id" | awk -F: '$1 == "sec" { print $5; exit }')"
    [[ -n "$selected_long_key_id" ]] || fail "No secret key matches '$requested_key_id'."
elif [[ "$(printf '%s\n' "$secret_key_ids" | wc -l | tr -d ' ')" -gt 1 ]]; then
    gpg --list-secret-keys --keyid-format=long
    fail "Several secret keys exist; choose one with --key <KEY_ID>."
else
    selected_long_key_id="$secret_key_ids"
fi

selected_fingerprint="$(gpg --list-secret-keys --with-colons "$selected_long_key_id" | awk -F: '$1 == "fpr" { print $10; exit }')"
selected_short_key_id="${selected_long_key_id: -8}"
selected_user_id="$(gpg --list-secret-keys --with-colons "$selected_long_key_id" | awk -F: '$1 == "uid" { print $10; exit }')"
echo "Using key $selected_long_key_id ($selected_user_id)"

# --- 2. Publish the public key ---------------------------------------------------------------------------
print_step "Uploading public key to $keyserver_url"
gpg --keyserver "$keyserver_url" --send-keys "$selected_fingerprint" \
    || echo "Warning: upload failed; Central will reject the deployment until the public key is on a keyserver."

# --- 3. Export the private key in Gradle's single-line form ----------------------------------------------
gpg_passphrase="${GPG_PASSPHRASE:-}"
if [[ -z "$gpg_passphrase" ]]; then
    read -r -s -p "GPG passphrase for $selected_short_key_id: " gpg_passphrase; echo
fi

print_step "Exporting private key"
# Loopback pinentry reuses the passphrase entered above; a wrong passphrase makes the export fail here.
armored_private_key="$(
    printf '%s' "$gpg_passphrase" \
        | gpg --batch --yes --pinentry-mode loopback --passphrase-fd 0 \
              --armor --export-secret-keys "$selected_fingerprint"
)" || fail "Exporting the private key failed (wrong passphrase?)."
single_line_private_key="$(
    printf '%s\n' "$armored_private_key" \
        | grep -v -E '^-----(BEGIN|END) PGP' \
        | grep -v -E '^=[A-Za-z0-9+/]{4}$' \
        | grep -v -E '^(Version|Comment):' \
        | tr -d '[:space:]'
)"
[[ -n "$single_line_private_key" ]] || fail "Exporting the private key produced no data (wrong passphrase?)."

# --- 4. Collect Central credentials ----------------------------------------------------------------------
central_username="${MAVEN_CENTRAL_USERNAME:-$(read_gradle_property mavenCentralUsername)}"
central_password="${MAVEN_CENTRAL_PASSWORD:-$(read_gradle_property mavenCentralPassword)}"
if [[ "$publish_mode" != "local" ]]; then
    if [[ -z "$central_username" ]]; then
        read -r -p "Central Portal token username: " central_username
    fi
    if [[ -z "$central_password" ]]; then
        read -r -s -p "Central Portal token password: " central_password; echo
    fi
fi

# --- 5. Write ~/.gradle/gradle.properties ----------------------------------------------------------------
print_step "Updating $gradle_user_properties_file"
mkdir -p "$(dirname "$gradle_user_properties_file")"
touch "$gradle_user_properties_file"
cp "$gradle_user_properties_file" "$gradle_user_properties_file.bak"

managed_property_pattern='^(signingInMemoryKey|signingInMemoryKeyId|signingInMemoryKeyPassword|mavenCentralUsername|mavenCentralPassword)[[:space:]]*='
updated_properties_file="$(mktemp)"
trap 'rm -f "$updated_properties_file"' EXIT
grep -v -E "$managed_property_pattern" "$gradle_user_properties_file" > "$updated_properties_file" || true
{
    [[ -s "$updated_properties_file" && -n "$(tail -c 1 "$updated_properties_file")" ]] && echo
    if [[ -n "$central_username" ]]; then
        echo "mavenCentralUsername=$(escape_properties_value "$central_username")"
        echo "mavenCentralPassword=$(escape_properties_value "$central_password")"
    fi
    echo "signingInMemoryKeyId=$selected_short_key_id"
    echo "signingInMemoryKeyPassword=$(escape_properties_value "$gpg_passphrase")"
    echo "signingInMemoryKey=$single_line_private_key"
} >> "$updated_properties_file"
mv "$updated_properties_file" "$gradle_user_properties_file"
chmod 600 "$gradle_user_properties_file"
echo "Done (previous version saved as gradle.properties.bak)."

if grep -q -E '^signing\.' "$gradle_user_properties_file"; then
    echo "Note: old 'signing.*' entries are still in the file; they are not used and can be removed."
fi

# --- 6. Verify locally -----------------------------------------------------------------------------------
cd "$project_root_directory"
project_version="$(./gradlew -q properties --property version | awk -F': ' '/^version:/ { print $2 }')"
project_group="$(./gradlew -q properties --property group | awk -F': ' '/^group:/ { print $2 }')"
local_artifact_directory="$HOME/.m2/repository/${project_group//.//}/versionfrau/$project_version"

print_step "Verifying signing with publishToMavenLocal ($project_group:versionfrau:$project_version)"
rm -rf "$local_artifact_directory"
./gradlew publishToMavenLocal --quiet || fail "publishToMavenLocal failed (wrong passphrase gives 'checksum mismatch')."

unsigned_artifact_count=0
for artifact_file in "$local_artifact_directory"/*.{jar,pom,module}; do
    [[ -f "$artifact_file" ]] || continue
    if [[ ! -f "$artifact_file.asc" ]]; then
        echo "Missing signature: $artifact_file"
        unsigned_artifact_count=$((unsigned_artifact_count + 1))
    fi
done
[[ "$unsigned_artifact_count" -eq 0 ]] || fail "$unsigned_artifact_count artifact(s) are not signed."
ls "$local_artifact_directory"
echo "All artifacts are signed."

# --- 7. Upload -------------------------------------------------------------------------------------------
case "$publish_mode" in
    local)
        echo; echo "Local verification only; nothing uploaded."
        ;;
    central)
        print_step "Uploading $project_version to Maven Central"
        ./gradlew publishToMavenCentral --no-configuration-cache
        echo; echo "Uploaded. Release it at https://central.sonatype.com/publishing/deployments"
        ;;
    central-release)
        print_step "Uploading and releasing $project_version on Maven Central"
        ./gradlew publishAndReleaseToMavenCentral --no-configuration-cache
        echo; echo "Released. It appears on Maven Central within ~30 minutes (search can take longer)."
        ;;
esac
