#!/usr/bin/env bash
# Looks for leaked secrets in the working tree AND in the full git history.
# Run it from the repository root before every push:   ./scripts/scan-secrets.sh
#
# Best option is gitleaks (https://github.com/gitleaks/gitleaks); it is used automatically if installed.
# The fallback below is a basic pattern search and WILL miss things: treat a clean result as "no obvious leaks".
set -uo pipefail

if command -v gitleaks >/dev/null 2>&1; then
  exec gitleaks detect --source . --redact --verbose
fi

echo "gitleaks not found: using the basic fallback scan (install gitleaks for proper coverage)."

git rev-parse --git-dir >/dev/null 2>&1 || { echo "Not a git repository."; exit 2; }

PATTERN='(AKIA[0-9A-Z]{16}|-----BEGIN [A-Z ]*PRIVATE KEY-----|gh[pousr]_[A-Za-z0-9]{36,}|xox[baprs]-[A-Za-z0-9-]{10,}|sk-[A-Za-z0-9]{32,}|(password|passwd|secret|api[_-]?key|token)[[:space:]]*[:=][[:space:]]*["'"'"']?[A-Za-z0-9/+_=.-]{16,})'
found=0

echo "== Files that should never be tracked =="
if git ls-files | grep -E '(^|/)\.env($|\.)|\.pem$|\.key$|\.p12$|\.jks$|secrets/' | grep -v '\.env\.example$'; then
  found=1
fi

echo "== Current files =="
if git grep -InE "$PATTERN" -- . ':!.env.example' ':!scripts/scan-secrets.sh' ':!SECURITY.md' ':!README.md'; then
  found=1
fi

echo "== Entire history (every commit, every branch) =="
if git log --all -p --no-color -- . ':!.env.example' ':!scripts/scan-secrets.sh' | grep -nEi "$PATTERN" | head -50; then
  found=1
fi

if [ "$found" -eq 1 ]; then
  echo
  echo "POSSIBLE SECRETS FOUND. If any are real: rotate them first (assume they are compromised),"
  echo "then remove them from history (git filter-repo) if you want to. Deleting a file in a new commit is not enough."
  exit 1
fi
echo "Nothing found."
