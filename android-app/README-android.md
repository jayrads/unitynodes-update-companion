# android-app — UnityNodes Update Companion

This is the Android companion app that checks for new UnityNodes APK releases,
verifies their integrity + identity, and prompts the user to install them.

----------------------------------------------------------------

## Two important rules

1) Do NOT commit:
   android-app/app/google-services.json

2) Do NOT commit secrets:
   Copy Config.example.kt -> Config.kt locally.
   Config.kt is gitignored by design.

----------------------------------------------------------------

## Firebase (optional, required for push notifications)

If you want update notifications via FCM:

1) Place google-services.json here:
   android-app/app/google-services.json

2) The build automatically applies the Google Services plugin ONLY if that file exists.

Without Firebase:
- the app still builds and runs
- push notifications are simply disabled

----------------------------------------------------------------

## Configuration

Create your local config file:

    android-app/app/src/main/java/com/jayrads/unityupdater/Config.kt

By copying:

    Config.example.kt -> Config.kt

Fill in:
- BACKEND_BASE_URL
- API_KEY
- PINNED_CERT_SHA256 (lowercase hex, NO colons)

----------------------------------------------------------------

## How to get the pinned certificate fingerprint

    apksigner verify --print-certs Unity-<version>.apk
