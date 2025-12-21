# android-app — UnityNodes Update Companion

This is the Android companion app for **UnityNodes**.

It checks for new UnityNodes APK releases, securely downloads them, verifies
their integrity and publisher identity, and guides the user through installation.

The app is designed to work **without Google Play**, using a hardened update
pipeline suitable for sideloaded production apps.

---

## What this app does

- Detects whether the UnityNodes app is installed
- Displays the installed version (versionName)
- Checks a backend for the latest available APK
- Downloads updates via Android DownloadManager
- Verifies APK integrity using SHA-256
- Verifies publisher identity using the APK signing certificate
- Uses **TOFU (Trust On First Use)** for signer trust
- Guides the user through installation
- Optionally sends push notifications when updates are available

All verification happens **locally on-device**.

---

## Two important rules

1. **Do NOT commit Firebase config**

   android-app/app/google-services.json

2. **Do NOT commit secrets**

   Copy `Config.example.kt` to `Config.kt` locally.
   `Config.kt` is gitignored by design.

---

## Configuration

Create your local config file at:

android-app/app/src/main/java/com/jayrads/unityupdater/Config.kt

By copying:

Config.example.kt → Config.kt

Fill in:

- BACKEND_BASE_URL — your Cloud Run / backend URL
- API_KEY — API key used by the backend
- PINNED_CERT_SHA256 — optional pinned signing cert fingerprint (lowercase hex)

If `PINNED_CERT_SHA256` is left empty, the app relies entirely on TOFU
(see below).

---

## APK verification model

### 1. Integrity verification (required)

Every downloaded APK is verified against the SHA-256 hash provided by the backend.

If the hash does not match, installation is blocked.

---

### 2. Publisher identity verification

The app verifies the APK’s **signing certificate**.

There are two supported models:

#### Option A: TOFU (Trust On First Use) — default

- On the first successful install, the APK’s signing certificate is trusted
- The trusted signer fingerprint is stored locally
- Future updates must be signed by the same certificate
- If the signer changes, the UI warns the user before install

This is similar to how SSH handles host keys.

#### Option B: Pinned certificate (optional)

If you set `PINNED_CERT_SHA256`:

- The APK must be signed by that certificate
- TOFU is bypassed
- Any mismatch blocks installation

---

## How to get a signing certificate fingerprint (optional)

If you want to pin the certificate:

apksigner verify --print-certs Unity-<version>.apk

Copy the SHA-256 value and format it as:

- lowercase
- no colons

Example:

0123abcd4567ef...

---

## Firebase (optional — only for push notifications)

Firebase is **optional** and only used for update notifications.

To enable FCM:

1. Place `google-services.json` at:

   android-app/app/google-services.json

2. Build the app normally

The build automatically applies the Google Services plugin **only if**
`google-services.json` exists.

Without Firebase:

- the app builds and runs normally
- update notifications are disabled
- manual refresh still works

---

## Development notes

- Update checks and verification use WorkManager
- Downloads use Android DownloadManager
- UI is built with Jetpack Compose
- No Play Services required unless Firebase is enabled
- No background services run unless a download is active

---

## Security philosophy

This app assumes:

- The backend may be reachable but not trusted for APK delivery
- The APK file must be verified independently
- The user should always know what is installed and why it is trusted

The goal is **transparent, auditable updates** without relying on Google Play.
