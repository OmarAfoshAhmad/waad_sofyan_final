# Project workspace reference

This repository is the approved working copy for the current WAAD/TBA work.

## Approved local project path

`C:\tmp\wt-main-release`

Do not switch to another extracted/copy folder for this project unless the user explicitly asks for that.

## Local review database

Use the review PostgreSQL database that the existing backend review script is configured to use:

- Database host: `127.0.0.1`
- Database name: `waad_review1_local_context_20260905_host`
- Database user: `postgres`
- Database password: `postgres` in the local review environment only

## Correct backend script

Run the backend for local review using:

```powershell
cd C:\tmp\wt-main-release
.\scripts\run-backend-review.ps1
```

This avoids accidentally using another database or another project copy.

## Correct frontend command

Run the frontend locally using:

```powershell
cd C:\tmp\wt-main-release\frontend
npm run start -- --host 127.0.0.1 --port 5173
```

Use this browser URL for local testing:

`http://localhost:5173/`

`localhost` is preferred over `127.0.0.1` for this app because the backend CORS setup accepted `localhost:5173` in the review environment.

## Search normalization standard

Frontend search normalization must use:

`frontend/src/utils/searchText.js` → `normalizeArabicSearchText`

Backend search normalization must use:

`backend/src/main/java/com/waad/tba/common/search/SearchTextNormalizer.java`

Database-backed search must use:

`waad_search_normalize(text)`

Avoid adding screen-local normalization functions unless there is a documented reason.
