# Rider Special — Android starter project

This is a free-build starter for a single Android app with Rider and Admin demo panels.

## Build APK
GitHub Actions builds a debug APK automatically on push or when manually started:
1. Create a GitHub repository.
2. Upload the contents of this folder to the repository root.
3. Open **Actions** → **Build Rider Special APK** → run workflow (or push to main).
4. Open the completed run and download the `Rider-Special-APK` artifact.

## Important limitations
- This is a local demo starter, not a production backend.
- OTP, cloud sync, payment verification, real-time chat, remote announcements, and durable subscription enforcement are placeholders and need a secure backend.
- Auto-Tap is simulation-only and does not control third-party apps.
- Do not store payment secrets or private keys in the Android app.
- The debug APK is for testing. A production release needs signing and security review.

No `google-services.json` or Google Services Gradle plugin is required.
