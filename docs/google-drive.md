# Google account and Drive backup

Depositly 1.1.0 uses Google's Android AuthorizationClient for account selection and consent. It requests `openid`, profile/email and the limited `drive.appdata` scope. OAuth tokens stay in native memory and Google's token cache; they are not sent to the WebView, stored in Depositly preferences, or included in exports. There is no backend server and no embedded client secret.

## Google Cloud setup

Use the existing **depositly** project (project number **688859064258**).

1. [Enable Google Drive API](https://console.cloud.google.com/apis/library/drive.googleapis.com?project=depositly).
2. In [Google Auth Platform → Clients](https://console.cloud.google.com/auth/clients?project=depositly), check the supplied Android client `688859064258-7kftaf2f1sa8iap35hq982j8melh51ld.apps.googleusercontent.com`. Its package must be `app.depositly.mobile` and its SHA-1 certificate must be `3B:CE:15:E7:58:AC:CF:A1:DF:E1:21:CE:B7:CD:79:50:1B:D5:58:A0`.
3. In [Data Access](https://console.cloud.google.com/auth/scopes?project=depositly), add `https://www.googleapis.com/auth/drive.appdata` and the basic identity scopes above if they are not already listed.
4. The screenshots show the app in **Testing**. Add the Google accounts you will use under [Audience → Test users](https://console.cloud.google.com/auth/audience?project=depositly). Testing authorization can expire after seven days when Drive scope is requested; sign in again when prompted. To allow accounts outside that list, finish the consent-screen requirements and publish the OAuth app to Production.

The supplied web client `688859064258-h97snetju0b3rts3jltgd210dobn75f1.apps.googleusercontent.com` and its secret are not needed for this Android-only authorization flow. Do not place either downloaded credential file in the repository. Android authorization identifies the app using the registered package and signing certificate, so the Android client ID is documented here rather than passed as a web audience or redirect URL.

These Cloud Console settings could not be inspected from this workspace; there is no connected, authenticated Cloud Console browser session.

## Using the feature

Open **Settings → Account & cloud backup → Connect Google account**, select your account, and allow Google Drive backup access. With a fresh installation, the latest complete backup restores automatically. If this phone already contains records, choose Restore, Merge, or Keep this phone's data before automatic uploads begin. A new account without a backup also asks before copying existing phone records into that account.

A full backup contains all investments (Self and Others), receipt history and snapshots, currency, number format, theme, and reminder time/enabled preference. Notification permission remains controlled by Android; restoring an enabled preference can request permission. OAuth account credentials and generated alarm plans are not part of the backup.

Automatic backup saves changes while the app is open, with a short debounce. A pending snapshot is stored privately on the phone and retried with backoff after network failure, including on the next app opening. **Back up now** also works when automatic backup is off. Wait for **Backup saved to Google Drive** before uninstalling or changing phones. A pending backup has not reached Google yet.

Drive storage is the hidden `appDataFolder`, accessible only to Depositly. The files do not appear in My Drive. Uploads create complete immutable snapshots and retain the ten newest; failed uploads do not delete or overwrite previous backups. The newest snapshot is restored. This is a backup feature, not live multi-device synchronization: changing data on two phones can produce different backups, and the last completed upload becomes the restore source.

**Sign out** removes the local account link and pending cloud snapshot while keeping local vault data and existing Drive backups. **Remove Google access** also revokes Google's authorization; download a file backup first if you need to preserve a cloud copy. Google also allows users to remove application data through Drive settings. Device-local JSON/CSV import and export remain available without signing in.

## Validation

Automated checks cover fresh-install restoration, existing-data and new-account decisions, malformed/unsupported backup rejection, failed local saves, receipt and preference round trips, automatic/manual backup, account isolation, immutable uploads and retention after successful uploads. Signed builds verify package/version, ZIP alignment and the original signing certificate.

No Android device was connected during implementation. Test on two Android installations with Google Play services before relying on this feature: back up a sample investment and receipt on one phone, then sign in with the same test account on the other. Confirm receipt history, theme, currency, comma system and reminders. Also test canceled consent, revoked access, airplane mode plus reopening, and a phone that already has different records.

References: [Android authorization and account information](https://developer.android.com/identity/authorization), [Drive application data](https://developers.google.com/workspace/drive/api/guides/appdata), [OAuth testing limits](https://developers.google.com/identity/protocols/oauth2#expiration).
