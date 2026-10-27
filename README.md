# Depositly

Personal FDR and National Savings Certificate tracker by **Greenstone Solutions LLC**. Current application version: **1.2 Build 15**.

Connect Google in **Settings → Account & backup** to synchronize saved edits automatically with the website and other devices using the same account. Dated versions remain available for explicit restore in **Backup versions**. See [Google Cloud setup and backup behavior](docs/google-drive.md) before testing sign-in.

Open the **Bank** bottom tab to save bank accounts, edit their details, or share an account as text on the Bank Information page. The hamburger menu also keeps this shortcut. Account and routing numbers retain leading zeros. Full JSON and Google backups include saved accounts; investment CSV and selected-deposit exports exclude their full details. Press Back twice within two seconds on Home to exit the Android app.

Bank accounts are grouped by institution, with prominent account numbers, holder names, optional account-type labels and bank logos. Choose **Cards** or **Grid**; the app remembers the selected bank layout. Tap the copy icon beside an account or routing number to copy its exact value, including in the edit form. Both layouts offer sharing, while Grid's options button opens editing and deletion. A previously selected Compact view opens as Cards.

Deposits offer **Simple interest** and **Compound interest**. Calendar-month cycles calculate interest using months / 12; day cycles use the exact days / 360. A 100-day FDR pays on its 100th day and repeats that day count when renewing. Simple interest is paid separately; compound FDRs add net interest to principal at each renewal maturity. When renewal is off or stopped, final interest and principal are due at maturity. Savings certificates return principal at maturity and do not renew.

Each FDR or savings certificate can optionally link to any saved interest-receiving account, independently of its deposit institution. Choose it when adding or editing a deposit. Account details list their linked deposits, and an account must be unlinked or reassigned before deletion. Existing deposits stay unlinked until you choose an account.

New payment receipts retain the receiving bank, holder and account number as recorded, so later relinking or bank edits do not rewrite history. Marking a payment received needs only the amount and optional note; payment lists and filters use scheduled dates. Existing internal receipt dates remain preserved.

Linked deposits and recorded receiving-account identities require backup reader version 7, supported by the updated phone app and website. Full JSON and Google backups preserve these details. Deposit-only JSON and CSV keep account ID references without exporting full bank details; older clients reject linked exports until updated. Unlinked compound deposits retain reader version 6 compatibility, and legacy simple-interest backups remain readable. Existing receipts retain their original dates and amounts when affected 100-day schedules are repaired.

Home shows the forecast period selector and estimated net interest, gross interest and TDS above **Coming up next**. Choose 1 month, 3 months, 6 months or 1 year; changing the period keeps your place on Home. The forecast uses current invested balances and rates. Selecting a bank leaves Investment Name for manual input.

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
| `dist/` | Built APK and build metadata; excluded from Git |

The HTML is the source of truth. The Android wrapper, Google authorization library and notifications are built together with Gradle. The build does not require an older APK or decompiled smali. `app/apktool.yml` is retained as the version configuration used by the build and release scripts.

The Build 11 APK's recovered interface is integrated with its `app/assets/redesign.css` theme. `AppDocument` includes the packaged styles inside the loaded HTML, retaining stylesheet order and offline rendering while the WebView continues to block remote subresources. Financial calculations and backup contracts are unchanged.

Local drafts, test fixtures, downloaded Gradle tools, build caches and intermediate APKs use `Z:\WORK DIRECTORY\Depositly`. Set `DEPOSITLY_WORK_DIRECTORY` to override the parent directory. CI uses its runner temporary directory; other machines fall back to their system temporary directory. Final APKs and release metadata are written to `dist/`.

## Updating the app

1. Edit the relevant files, commit, and push to `main`.
2. Wait for **Actions → Build and publish APK** to finish.
3. Open **Releases → latest → Assets** and download the APK.

Every app update must increase the visible **Build** number and Android's internal version code. Run `scripts/bump-version.ps1` before building an update; it updates the configuration, Settings labels, documentation and APK filename together. Signed builds and CI reject a visible version that is not newer than the previous available build metadata. If the published Android code is higher than the local one, pass its `build-info.json` with `-PreviousBuildInfoPath`. CI can increase the internal code further to keep published updates installable. Use an unsigned build when rebuilding unchanged source for verification.

Pull requests run tests and build an unsigned APK. Signing credentials are used only for main-branch release builds. A build can also be started with **Actions → Run workflow** on `main`.

## Local build

Requires Windows, JDK 17, Node.js 18+, Android SDK platform 34 and build-tools 34.0.0 / 35.0.0. Set `ANDROID_SDK_ROOT` if the SDK is not at `C:/Android/Sdk`.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/bootstrap.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build.ps1
```

The signed result is `dist/Depositly-1.2-build-15.apk`. Local signing files belong at `private/signing/depositly.p12` and `private/signing/password.txt`. A fresh clone can run `scripts/build.ps1 -Unsigned` without them.

See [GitHub signing and release setup](docs/github.md) and [development notes](docs/development.md).

The financial conventions include a 360-day interest year for day cycles, month fractions for calendar cycles, custom day/month terms, net-interest capitalization at compound renewal maturity, TDS, instrument-only CSV, full JSON backups, Self-only totals and separate Others calendar payments. Notification timing follows Android alarm and permission rules; device testing is needed for delivery behavior.

