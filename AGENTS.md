# Working files

Use `Z:\WORK DIRECTORY` for drafts, previews, temporary files, downloaded tools and build caches. Keep the repository focused on app source, build/release configuration, tests and required signing files. Local build scripts use `Z:\WORK DIRECTORY\Depositly`; CI uses its runner temporary directory.

# App release versions

Every requested update to the phone app must have a new visible version and a higher Android version code. Increment `Build N` for each completed app update (for example, `1.2 Build 2` → `1.2 Build 3`), unless the user requests another version scheme. Do not change only the internal version code.

Run `scripts/bump-version.ps1` before building an updated app. It updates the app configuration, version labels, documentation, and APK filename together. When the previous published APK has a higher internal code than the local build, pass its `build-info.json` using `-PreviousBuildInfoPath`.

Keep the new version consistent in `app/apktool.yml`, the Settings screen, release notes, and the delivered APK. Run the release checks and build a signed APK. Report the new visible version to the user. A rebuild of unchanged source for verification does not itself require another version; unsigned builds remain available for that purpose.
