# Depositly

Personal FDR and National Savings Certificate tracker by **Greenstone Solutions LLC**. Current application version: **1.1.0**.

Connect Google in **Settings → Account & backup** to synchronize saved edits automatically with the website and other devices using the same account. Dated versions remain available for explicit restore in **Backup versions**. See [Google Cloud setup and backup behavior](docs/google-drive.md) before testing sign-in.

The repository contains the current app source and an automatic signed APK build. Push app changes to **main**: GitHub Actions runs the tests, builds the app with its original signing certificate, and places the APK in the **Assets** section of the **latest** release. A failed build leaves the previous APK available.

## Files

| Path | Purpose |
| --- | --- |
| `app/assets/index.html` | Canonical app interface, calculations, bank catalog and import/export logic |
| `app/assets/OFL-NotoSansBengali.txt` | Embedded currency font license |
| `app/AndroidManifest.xml` | Android permissions and components |
| `app/apktool.yml` | Application version and Android SDK configuration |
| `app/res/` | Android app and notification icons |
| `native/src/app/depositly/mobile/MainActivity.java` | Android wrapper and local file import/export |
| `native/src/` | Google account/Drive backup and payout notifications |
| `scripts/` | Tool preparation, testing, building and release publishing |
| `tests/` | Financial, date, ownership, notification and renewal checks |
| `tools/gradle-source.json` | Pinned Gradle download and SHA-256 checksum |
| `docs/` | GitHub setup and development notes |
| `private/` | Local signing/authentication files; excluded from Git |
| `build/` | Generated compiler files; excluded from Git |
| `dist/` | Built APK and build metadata; excluded from Git |

The HTML is the source of truth. The Android wrapper, Google authorization library and notifications are built together with Gradle. The build does not require an older APK. Historical smali and Apktool metadata are retained as references and are not compiled.

## Updating the app

1. Edit the relevant files, commit, and push to `main`.
2. Wait for **Actions → Build and publish APK** to finish.
3. Open **Releases → latest → Assets** and download the APK.

CI automatically increases Android's internal version code, so a new successful build can update an installed app even when the visible version remains 1.1.0. To change the visible version, update `versionName` in `app/apktool.yml` and the version labels in the interface. Keep the configured version code increasing too. The tests check that the interface reports the configured version.

Pull requests run tests and build an unsigned APK. Signing credentials are used only for main-branch release builds. A build can also be started with **Actions → Run workflow** on `main`.

## Local build

Requires Windows, JDK 17, Node.js 18+, Android SDK platform 34 and build-tools 34.0.0 / 35.0.0. Set `ANDROID_SDK_ROOT` if the SDK is not at `C:/Android/Sdk`.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/bootstrap.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build.ps1
```

The signed result is `dist/Depositly-1.1.0.apk`. Local signing files belong at `private/signing/depositly.p12` and `private/signing/password.txt`. A fresh clone can run `scripts/build.ps1 -Unsigned` without them.

See [GitHub signing and release setup](docs/github.md) and [development notes](docs/development.md).

The preserved financial conventions include a 360-day interest year, calendar-month payout dates, custom day/month terms, maturity renewal, TDS, instrument-only CSV, full JSON backups, Self-only totals and separate Others calendar payments. Notification timing follows Android alarm and permission rules; device testing is needed for delivery behavior.

