# DingClock

Android manual-assist app for DingTalk login, built for vivo phones.

The app only keeps three capabilities:

1. Save the DingTalk password locally with Android Keystore encryption.
2. Guide the user to required system settings, primarily Accessibility.
3. Manually launch DingTalk and auto-fill the remembered-account password flow when DingTalk shows the login screen.

## Build

Builds run on GitHub Actions. Signed APKs are published from the `Release` workflow, and CI artifacts are uploaded by the `Build APK` workflow.

## Development setup (optional)

If you want a local dev environment:

```bash
# Install Android SDK + JDK 17 + Gradle 8.7
gradle wrapper --gradle-version 8.7
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

This repository does not check in `gradlew`; both CI workflows generate the wrapper on demand before building.

## Signing

Keystore is provided to CI via repository secrets. See [keystore/README.md](keystore/README.md).
