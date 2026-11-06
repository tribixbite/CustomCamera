#!/data/data/com.termux/files/usr/bin/bash
#
# Generate CustomCamera's release signing key, store it in the repository
# secrets CI reads, and print the certificate's SHA-256 fingerprint (what Play
# Console / Android developer verification asks for).
#
# One key signs every channel — GitHub release APKs, CI builds, and F-Droid (which
# republishes our APK after reproducing it, see fdroid/README.md) — so users can
# move between channels without uninstalling. Same arrangement as Embeddy.
#
# Passwords are never taken on the command line or written by this script:
# keytool prompts for them, and `gh secret set` prompts for its values, so they
# stay out of shell history, the process table and any session transcript.
#
# Usage:
#   scripts/generate-release-key.sh [output.jks]          # create key + set secrets
#   scripts/generate-release-key.sh --fingerprint [jks]   # only print the SHA-256
#
# Default output is ~/git/customcamera-release.jks — OUTSIDE the repo so it
# cannot be committed by accident (and *.jks is gitignored as a second guard).

set -euo pipefail

REPO="tribixbite/CustomCamera"
DEFAULT_KEYSTORE="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/customcamera-release.jks"
ALIAS="customcamera"
VALIDITY_DAYS=10000   # ~27 years; Play requires expiry after 2033-10-22
KEYALG="RSA"
KEYSIZE=4096

die() { printf '\n\033[31merror:\033[0m %s\n' "$1" >&2; exit 1; }
info() { printf '\033[36m==>\033[0m %s\n' "$1"; }

command -v keytool >/dev/null 2>&1 || die "keytool not found. Install a JDK (pkg install openjdk-21)."

# Prints the SHA-256 of the key's certificate. keytool asks for the store password.
fingerprint() {
  keytool -list -v -keystore "$1" -alias "$ALIAS" | awk '/SHA256:/ { print $2; exit }'
}

if [ "${1:-}" = "--fingerprint" ]; then
  KEYSTORE="${2:-$DEFAULT_KEYSTORE}"
  [ -f "$KEYSTORE" ] || die "$KEYSTORE not found."
  info "SHA-256 certificate fingerprint of $KEYSTORE:"
  fingerprint "$KEYSTORE"
  exit 0
fi

KEYSTORE="${1:-$DEFAULT_KEYSTORE}"

# Refuse to clobber: replacing a key in use means installed copies can never update.
[ -e "$KEYSTORE" ] && die "$KEYSTORE already exists. Refusing to overwrite — this file is irreplaceable.
       Run with --fingerprint to read its SHA-256 instead."

cat <<'EOF'

  keytool asks for a keystore password (twice), then name/organisation fields
  (cosmetic for Android — a name and country code are enough), then confirms.
  A PKCS12 keystore uses the store password for the key as well.

  Put the password in a password manager now. Losing this file or its password
  means no update can ever be installed over a copy signed with it.

EOF

info "Generating $KEYSIZE-bit $KEYALG key '$ALIAS', valid $VALIDITY_DAYS days"
keytool -genkeypair -v \
  -keystore "$KEYSTORE" -storetype PKCS12 \
  -alias "$ALIAS" \
  -keyalg "$KEYALG" -keysize "$KEYSIZE" \
  -validity "$VALIDITY_DAYS"
chmod 600 "$KEYSTORE"
info "Keystore written to $KEYSTORE (mode 600)"

BACKUP=~/storage/shared/Documents/customcamera-release.jks
if [ -d ~/storage/shared/Documents ] && [ ! -e "$BACKUP" ]; then
  cp "$KEYSTORE" "$BACKUP"
  info "Backup copied to $BACKUP — also keep one off this phone"
fi

info "Reading the certificate fingerprint (enter the keystore password once more)"
SHA256=$(fingerprint "$KEYSTORE")

if command -v gh >/dev/null 2>&1; then
  info "Storing GitHub secrets for $REPO (each password prompt reads from the terminal)"
  base64 -w0 "$KEYSTORE" | gh secret set CC_KEYSTORE_B64 --repo "$REPO"
  printf '%s' "$ALIAS" | gh secret set CC_KEY_ALIAS --repo "$REPO"
  echo "Paste the keystore password:"
  gh secret set CC_KEYSTORE_PASSWORD --repo "$REPO"
  echo "Paste the same password again for the key (PKCS12 uses one password):"
  gh secret set CC_KEY_PASSWORD --repo "$REPO"
  gh secret list --repo "$REPO"
else
  info "gh not found — set CC_KEYSTORE_B64, CC_KEY_ALIAS, CC_KEYSTORE_PASSWORD, CC_KEY_PASSWORD by hand"
fi

cat <<EOF

  ── SHA-256 certificate fingerprint ──────────────────────────────────────────

  $SHA256

  ── Local signed builds ──────────────────────────────────────────────────────

  Create keystore.properties (gitignored) in the repo root:

    storeFile=$KEYSTORE
    storePassword=…
    keyAlias=$ALIAS
    keyPassword=…

  The GitHub secret is not a backup: a secret cannot be read back out.

EOF
