# gcp-backend (Cloud Run)

Node.js and Express backend running on Cloud Run for the UnityNodes update companion app.

## Responsibilities

- Scrape the UnityNodes Apache index for the latest APK
- Store the latest APK metadata in Firestore
- Maintain a heartbeat using a checkedAt timestamp
- Register Android FCM device tokens
- Send push notifications when a new APK is detected
- Expose a public endpoint the Android app can poll

## Environment variables

- API_KEY
  Shared secret used by the Android app and Cloud Scheduler

- UNITYNODES_INDEX_URL
  APK index URL, for example https://releases.unitynodes.io/android/

- PORT
  HTTP port, default 8080

## Endpoints

- GET /health
  Basic health check endpoint

- GET /latest.json
  Public endpoint returning the most recently stored APK metadata
  updatedAt changes only when the APK changes
  checkedAt updates on every scrape run

- POST /registerToken
  Registers or updates an Android FCM device token
  Requires Authorization: Bearer API_KEY

- POST /scrape
  Scrapes the APK index, updates Firestore if needed, and sends push notifications
  Requires Authorization: Bearer API_KEY

## Firestore semantics

- updatedAt
  Timestamp of the last APK metadata change

- checkedAt
  Timestamp of the last scrape run
  Used as a backend heartbeat

## Scheduler

- Cloud Scheduler calls the scrape endpoint on a fixed schedule, typically hourly
- Uses the same API_KEY as the Android app
- Keeps checkedAt fresh and triggers notifications automatically

## Security notes

- latest.json is intentionally public for Android app polling
- scrape and registerToken are protected with a Bearer API key
- Future hardening option is Cloud Scheduler OIDC with Cloud Run IAM
