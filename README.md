# unitynodes-update-companion

A secure, Play-Store-independent update system for the **UnityNodes** Android app.

This project provides a small “companion updater” that detects new UnityNodes APK
releases, verifies them locally on-device, and guides the user through installation.

The goal is **transparent, auditable updates** without relying on Google Play.

---

## Project structure

This repository has two components:

### 1) gcp-backend/ (Cloud Run)

A lightweight backend responsible for detecting new APK releases and notifying devices.

Responsibilities:

- Scrapes https://releases.unitynodes.io/android/ (Apache directory listing)
- Determines the latest APK by timestamp
- Streams the APK to compute SHA-256 (no full buffering)
- Publishes latest metadata via `/latest.json`
- Stores metadata in Firestore (`meta/latest`)
- Stores device FCM tokens in Firestore (`devices/{token}`)
- Sends an FCM push when a new APK appears
- Prunes invalid / unregistered tokens after sends

The backend **does not distribute APKs** — it only provides metadata.

---

### 2) android-app/ (Android app — Kotlin + Jetpack Compose)

A companion app that securely updates UnityNodes without Google Play.

Features:

- Detects whether the UnityNodes app is installed
- Displays the installed UnityNodes version
- Fetches latest release metadata from the backend
- Shows notification permission status
- Helps enable notifications (Android 13+ runtime permission + settings deep-links)
- Receives FCM “Update available” pushes (optional)
- Downloads the APK using Android DownloadManager
- Verifies the APK locally using WorkManager
- Guides the user through installation (no silent installs)

---

## APK verification flow (Android app)

All verification happens **on-device** before installation is allowed.

### 1) Integrity verification (required)

- The APK’s SHA-256 is computed locally
- It must match the SHA-256 published by the backend
- Any mismatch blocks installation (fail-closed)

### 2) Publisher identity verification

The APK’s **signing certificate** is verified using one of two models:

#### Default: TOFU (Trust On First Use)

- On first successful install, the APK’s signing certificate is trusted
- The signer fingerprint is stored locally
- Future updates must be signed by the same certificate
- If the signer changes, the UI clearly warns the user before install

This mirrors SSH-style trust semantics.

#### Optional: Pinned certificate

- The app can be configured with a pinned signing certificate SHA-256
- If set, only APKs signed by that certificate are accepted
- Any mismatch blocks installation

---

## Security model summary

- **Integrity**: SHA-256 hash verification (fail-closed)
- **Identity**: APK signing certificate verification (TOFU or pinned)
- **Installation**: User-confirmed only (no silent installs)

The backend is treated as **informational**, not authoritative.

---

## Limitations

- APKs are never installed silently
- The user must confirm installation via the system installer
- The device must allow “Install unknown apps” for the companion app
- This does not replace Play Store licensing or update infrastructure

---

## Quickstart

### Backend

See:

gcp-backend/README-backend.md

### Android app

See:

android-app/README-android.md
