# Keystore

Real `.jks` files must never be committed. CI reads the keystore from GitHub Actions secrets.

## Generate one-time

```bash
keytool -genkeypair -v \
  -keystore release.jks \
  -alias dingclock \
  -keyalg RSA -keysize 4096 \
  -validity 36500 \
  -dname "CN=DingClock,O=Private,C=CN"
# pick a strong store password and the same or different key password
```

Back up the file + passwords to:
1. A password manager (1Password / Bitwarden)
2. An offline USB stick

**If you lose the keystore, you cannot publish upgrade APKs to the same install** — users will have to uninstall first.

## Upload to GitHub Actions secrets

Repository → Settings → Secrets and variables → Actions → New repository secret:

| Secret name | Value |
|---|---|
| `KEYSTORE_BASE64` | `base64 -i release.jks \| pbcopy` (macOS) or `base64 -w0 release.jks \| xclip -selection clipboard` (Linux) |
| `KEYSTORE_PASSWORD` | the store password |
| `KEY_ALIAS` | `dingclock` |
| `KEY_PASSWORD` | the key password (same as store password is fine) |

Once these are set, `Build APK` and `Release` workflows will produce signed APKs automatically.

## Local dev (optional)

Set the same environment variables and `KEYSTORE_PATH=/absolute/path/to/release.jks` before running `./gradlew :app:assembleRelease`. Unsigned builds are also produced if the secrets are absent.
