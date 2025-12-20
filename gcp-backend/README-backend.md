# gcp-backend (Cloud Run)

## Env vars
- API_KEY: shared secret used by Android app + Cloud Scheduler
- UNITYNODES_INDEX_URL: https://releases.unitynodes.io/android/
- PORT: 8080

## Endpoints
- GET /health
- GET /latest.json
- POST /registerToken (Authorization: Bearer API_KEY)
- POST /scrape (Authorization: Bearer API_KEY)
