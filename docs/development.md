# Development

The canonical interface and financial/interchange engine is `app/assets/index.html`. Bank logos and the currency font are embedded for offline use. The wrapper retains the original `vault` SharedPreferences name and `data` key, so signed app updates preserve existing records.

The active Android build is `native/build.gradle`, using Gradle 8.11.1, Android Gradle Plugin 8.9.2, Java 17, compile/target SDK 34, build-tools 35.0.0, and minimum Android 26. `scripts/gradle.ps1` downloads Gradle from its official distribution and verifies the pinned SHA-256 in `tools/gradle-source.json`. Runtime dependencies are pinned in `native/gradle.lockfile`; update them deliberately with `-WriteLocks`. Google Play services authorization is version 22.0.0. Gradle packages native resources, dependency manifests and DEX files. Historical `app/smali/`, `scripts/build-native.ps1` and Apktool metadata are reference material, not the active build.

Edit the wrapper in `native/src/app/depositly/mobile/MainActivity.java`, Google authorization and Drive transport in `native/src/app/depositly/mobile/cloud/`, and payout reminders in `native/src/app/depositly/mobile/reminders/`. The wrapper prevents remote navigation and subresource requests; native bridges are exposed only to the packaged interface. Google authorization uses Google's native account/consent UI.

Run `scripts/test.ps1` for financial, display, import/export, reminder, renewal, cloud restore and CI-version regression checks. Run `scripts/gradle.ps1 -Tasks @('testReleaseUnitTest')` for native Drive transport tests. Both are release gates in GitHub Actions. Use `scripts/build.ps1` for a signed APK, or add `-Unsigned` for a build without signing credentials. Every signed build verifies package/version, ZIP alignment and the original signing certificate.

The Gradle cache defaults to `build/gradle-home`. Set `DEPOSITLY_GRADLE_CACHE` to a directory on another drive if local disk space is limited. Dependencies require internet access on the first build. Bootstrap can install the SDK platform/build-tools with `scripts/bootstrap.ps1 -InstallSdk`.

See [Google Drive setup](google-drive.md) for the supplied OAuth client, signing fingerprint, scope and test-user configuration. Actual Google sign-in, account switching, permissions, offline retry and restoration across phones require Android device testing. Notification delivery also depends on Android battery/alarm rules.
