# WAAD V50 production dictionary activation

The approved V50 release is committed as gzip because its uncompressed size
exceeds GitHub's regular 100 MB file limit.

Run from the production repository after `git pull`:

```bash
chmod +x ops/medical-dictionary/import-v50-production.sh
WAAD_BASE_URL=https://your-production-domain.example \
  ./ops/medical-dictionary/import-v50-production.sh
```

The script verifies the source SHA-256, logs in interactively as `SUPER_ADMIN`,
imports through the application API, verifies all release counts, and removes
the temporary seed, cookie, and credentials on exit. It performs no write when
one release is already active and refuses an ambiguous active-release state.
