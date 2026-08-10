#!/usr/bin/env bash
set -Eeuo pipefail

# One-time, idempotent activation of the approved WAAD V50 dictionary.
# Credentials are read interactively and are never stored in this repository.
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ARCHIVE="${SCRIPT_DIR}/WAAD_V50_DICTIONARY_RELEASE.json.gz"
EXPECTED_SHA256="db8a70887fc16e8cd8bfadc1637f4e65d5977b84aa65f6a5c4a3507acfa40e3d"
: "${WAAD_BASE_URL:?Set WAAD_BASE_URL to the production HTTPS origin, for example https://waad.example.com}"
BASE_URL="${WAAD_BASE_URL}"
DB_SERVICE="${WAAD_DB_SERVICE:-db}"
DB_USER="${WAAD_DB_USER:-postgres}"
DB_NAME="${WAAD_DB_NAME:-tba_waad_system}"

for name in gzip sha256sum curl docker python3; do
  command -v "${name}" >/dev/null 2>&1 || { echo "Missing command: ${name}" >&2; exit 1; }
done
[[ -f "${ARCHIVE}" ]] || { echo "Archive not found: ${ARCHIVE}" >&2; exit 1; }

active_count="$(docker compose exec -T "${DB_SERVICE}" psql -U "${DB_USER}" -d "${DB_NAME}" -Atqc \
  "SELECT COUNT(*) FROM medical_dictionary_releases WHERE status = 'ACTIVE';")"
if [[ "${active_count}" == "1" ]]; then
  echo "One dictionary release is already ACTIVE; nothing changed."
  docker compose exec -T "${DB_SERVICE}" psql -U "${DB_USER}" -d "${DB_NAME}" -c \
    "SELECT version,status,category_count,concept_count,alias_count,exception_count,activated_at FROM medical_dictionary_releases WHERE status='ACTIVE';"
  exit 0
fi
[[ "${active_count}" == "0" ]] || { echo "Unsafe state: ${active_count} ACTIVE releases." >&2; exit 1; }

temporary_dir="$(mktemp -d)"
cookie_jar="${temporary_dir}/cookies.txt"
seed_file="${temporary_dir}/WAAD_V50_DICTIONARY_RELEASE.json"
login_file="${temporary_dir}/login.json"
cleanup() { rm -rf -- "${temporary_dir}"; }
trap cleanup EXIT
umask 077

gzip -dc -- "${ARCHIVE}" > "${seed_file}"
actual_sha256="$(sha256sum "${seed_file}" | awk '{print $1}')"
[[ "${actual_sha256}" == "${EXPECTED_SHA256}" ]] || {
  echo "SHA-256 mismatch. Expected ${EXPECTED_SHA256}, got ${actual_sha256}." >&2
  exit 1
}

read -r -p "SUPER_ADMIN username/email: " admin_identifier
read -r -s -p "SUPER_ADMIN password: " admin_password
echo
ADMIN_IDENTIFIER="${admin_identifier}" ADMIN_PASSWORD="${admin_password}" python3 -c \
  'import json,os; print(json.dumps({"identifier":os.environ["ADMIN_IDENTIFIER"],"password":os.environ["ADMIN_PASSWORD"],"rememberMe":False}))' \
  > "${login_file}"
unset admin_password ADMIN_PASSWORD

curl --fail-with-body --silent --show-error --cookie-jar "${cookie_jar}" \
  --header 'Content-Type: application/json' --data-binary "@${login_file}" \
  "${BASE_URL%/}/api/v1/auth/session/login" >/dev/null
rm -f -- "${login_file}"

echo "Uploading and activating V50; this may take several minutes..."
curl --fail-with-body --silent --show-error --cookie "${cookie_jar}" \
  --form "file=@${seed_file};type=application/json" \
  "${BASE_URL%/}/api/v1/medical-dictionary/releases/v50/import-and-activate" >/dev/null

docker compose exec -T "${DB_SERVICE}" psql -U "${DB_USER}" -d "${DB_NAME}" -v ON_ERROR_STOP=1 -c "
DO \$\$ DECLARE n integer; BEGIN
 SELECT COUNT(*) INTO n FROM medical_dictionary_releases
 WHERE status='ACTIVE' AND version='V50' AND category_count=46
   AND concept_count=20399 AND alias_count=97977 AND exception_count=600;
 IF n <> 1 THEN RAISE EXCEPTION 'V50 verification failed: % matching releases', n; END IF;
END \$\$;"
docker compose exec -T "${DB_SERVICE}" psql -U "${DB_USER}" -d "${DB_NAME}" -c \
  "SELECT version,status,category_count,concept_count,alias_count,exception_count,activated_at FROM medical_dictionary_releases WHERE status='ACTIVE';"
echo "V50 dictionary activated successfully."
