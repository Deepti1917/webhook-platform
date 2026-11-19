#!/usr/bin/env bash
# Prints fresh random values for every secret in .env.example.
# Usage:  ./scripts/generate-secrets.sh            (review the output, paste it into .env)
# The values are printed once to your terminal and are not stored anywhere else.
set -euo pipefail

command -v openssl >/dev/null || { echo "openssl is required" >&2; exit 1; }

# URL-safe, no characters that need quoting in .env or in a JDBC URL.
rand() { openssl rand -base64 48 | tr -d '\n=+/' | cut -c1-"$1"; }

echo "DB_OWNER_PASSWORD=$(rand 32)"
echo "APP_DB_PASSWORD=$(rand 32)"
echo "ADMIN_API_KEY=adm_$(rand 48)"
echo "SECRET_ENCRYPTION_KEY=$(openssl rand -base64 32)"
