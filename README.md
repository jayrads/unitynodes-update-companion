# unitynodes-update-companion

A small “companion updater” for UnityNodes Android APKs.

This project has two components:

## 1) gcp-backend/ (Cloud Run)
- Scrapes https://releases.unitynodes.io/android/ (Apache directory listing)
- Determines the latest APK by timestamp
- Computes SHA-256 of the APK by streaming download (no full buffering)
- Stores latest metadata in Firestore (`meta/latest`)
- Stores device FCM tokens in Firestore (`devices/{token}`)
- Sends an FCM push when a new APK appears
- Prunes invalid/unregistered tokens after sends

## 2) android-app/ (Android app, Kotlin + Jetpack Compose)
- Displays:
  - Installed version
  - Latest version (from backend `/latest.json`)
  - Notifications status (Allowed/Blocked)
- Helps enable notifications (Android 13+ runtime permission + settings deep-link)
- Receives FCM pushes and shows “Update available”
- Downloads latest APK via DownloadManager to app-scoped storage
- On download completion verifies:
  1) SHA-256 matches `latest.json` (fail-closed)
  2) APK signing certificate fingerprint matches a pinned SHA-256 fingerprint (fail-closed)
- If checks pass, offers “Tap to install” (user must confirm install; no silent install)

## Security model
- **Integrity**: SHA-256 file hash must match backend-published value
- **Identity**: APK signing certificate SHA-256 must match pinned value in the app

## Limitations
- This does NOT silently install APKs. The user must confirm installation.
- The device must allow “Install unknown apps” for this companion app.

## Quickstart

### Backend
See `gcp-backend/README-backend.md`

### Android
See `android-app/README-android.md`
