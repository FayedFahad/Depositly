# Development

This project preserves the uploaded Depositly 1.0.5 interface and WebView wrapper. It is an Apktool-source project rather than a Gradle rewrite. The financial/interchange modules and packaged interface are retained from that version.

Use `app/assets/index.html` for frontend changes. Assets such as bank logos and the currency font are embedded so the app works offline. `docs/assets/islami-bank-emblem.png` preserves the user-supplied bank artwork. Treat bank branding and font license notices separately from the application's ownership.

Edit native payout-reminder behavior in `native/src/app/depositly/mobile/reminders/`. D8 produces a helper DEX during the build. The application targets Android 34 with a minimum of Android 26, whose runtime [supports multiple DEX files natively](https://developer.android.com/build/multidex). Generated reminder smali does not belong in source control.

Builds stage a clean copy of the app in `build/`; they do not edit tracked source or depend on previous build caches. Apktool is downloaded from its official release and checked against the pinned digest in `tools/apktool-source.json`. Changing its version requires updating both the URL and digest.

Run `scripts/test.ps1` before pushing. The active checks cover whole-unit display, currencies and commas, dates, Self/Others scope, import/export, reminders and the 1.0.5 general-renewal behavior. Native scheduling policy tests execute the real pure-Java policy. Browser smoke and original compatibility fixtures are retained separately while their historical runtime paths are being modernized; they are not release gates.

Every signed build verifies package name, version, ZIP alignment and the installed app's certificate. Notification permission prompts, background delivery and reboot restoration still require tests on an Android device.

