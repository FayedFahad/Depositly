Google account and private Google Drive backup are now available in Settings → Account & cloud backup.

- Automatic and manual backups include investments, receipt history, theme, currency, number format and reminder preferences.
- Signing in with the same account on a fresh install restores the latest complete backup. Existing phone data is reviewed before restoring or uploading.
- Offline changes remain on the phone and retry when the app is opened. Complete snapshot uploads preserve previous backups if the network fails.
- File-based JSON/CSV import and export still work offline.

Google Play services is required. The Google Cloud project must have Drive API enabled, the Android OAuth client registered with the release signing certificate, and test-user access configured while the OAuth app is in Testing. See [Google Drive setup](https://github.com/FayedFahad/Depositly/blob/main/docs/google-drive.md).

Automated app and native Drive tests pass. Live Google sign-in and restoration between Android phones still need device validation.
