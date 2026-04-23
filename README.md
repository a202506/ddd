# DingClock

Android auto-attendance app for DingTalk (钉钉极速打卡), built for vivo phones.

Automates the daily flow: probe network → open DingTalk → auto-login if kicked out → tap 极速打卡 → verify success. Driven by AccessibilityService, scheduled by AlarmManager. No root / ADB required.

See the approved plan at `/Users/ying/.claude/plans/12-12-vivo-app-accessibilityservice-git-buzzing-mountain.md`.

## Build

Builds run on GitHub Actions. Download signed APK from the [Releases page](../../releases).

## Development setup (optional)

If you want a local dev environment:

```bash
# Install Android SDK + JDK 17 + Gradle 8.7
gradle wrapper --gradle-version 8.7
./gradlew :app:assembleDebug
```

## Signing

Keystore is provided to CI via repository secrets. See [keystore/README.md](keystore/README.md).
