# Google account and Drive backup

Depositly 1.2 Build 13 uses Google's Android AuthorizationClient for account selection and consent. It requests `openid`, profile/email and the limited `drive.appdata` scope. OAuth tokens stay in native memory and Google's token cache; they are not sent to the WebView, stored in Depositly preferences, or included in exports. There is no backend server and no embedded client secret.

## Google Cloud setup

Use the existing **depositly** project (project number **688859064258**).

1. [Enable Google Drive API](https://console.cloud.google.com/apis/library/drive.googleapis.com?project=depositly).
2. In [Google Auth Platform → Clients](https://console.cloud.google.com/auth/clients?project=depositly), check the supplied Android client `688859064258-7kftaf2f1sa8iap35hq982j8melh51ld.apps.googleusercontent.com`. Its package must be `app.depositly.mobile` and its SHA-1 certificate must be `3B:CE:15:E7:58:AC:CF:A1:DF:E1:21:CE:B7:CD:79:50:1B:D5:58:A0`.
3. In [Data Access](https://console.cloud.google.com/auth/scopes?project=depositly), add `https://www.googleapis.com/auth/drive.appdata` and the basic identity scopes above if they are not already listed.
4. The screenshots show the app in **Testing**. Add the Google accounts you will use under [Audience → Test users](https://console.cloud.google.com/auth/audience?project=depositly). Testing authorization can expire after seven days when Drive scope is requested; sign in again when prompted. To allow accounts outside that list, finish the consent-screen requirements and publish the OAuth app to Production.

The supplied web client `688859064258-h97snetju0b3rts3jltgd210dobn75f1.apps.googleusercontent.com` and its secret are not needed for this Android-only authorization flow. Do not place either downloaded credential file in the repository. Android authorization identifies the app using the registered package and signing certificate, so the Android client ID is documented here rather than passed as a web audience or redirect URL.

These Cloud Console settings could not be inspected from this workspace; there is no connected, authenticated Cloud Console browser session.

## Using the feature

Open **Settings → Account & backup → Connect Google account**, select the same account used on the website, and allow Drive access. The latest registry applies automatically. Normal edits synchronize quietly without backup review dialogs. JSON/CSV file imports keep their explicit review screen.

The shared registry contains Self and Others investments, receipt history and snapshots, currency, number format, theme, default deposit view, and reminder time/enabled preferences. Notification permission remains controlled by Android; automatic sync never opens a notification-permission prompt. Credentials and generated reminder plans are excluded.

Interest type is part of each deposit's financial terms and is synchronized with its cycle and tenure. The updated website and phone app support compound deposits with reader version 6; older clients reject the backup until updated. Simple-only backups remain compatible with reader version 5. Corrected legacy 100-day payout terms are saved back to the registry using the original snapshot as the merge baseline, preserving concurrent edits and existing receipts.

Optional `bankAccountId` links connect deposits to saved receiving accounts without changing their institution or financial terms. New receipt snapshots can retain the receiving bank, holder and account number. Any live link or retained receiving-account history requires reader version 7 in both the app and website. The registry derives this requirement from its live records so a concurrent display-preference edit cannot downgrade it. Partial exports may retain an unresolved ID; the interface asks the user to select a saved account rather than guessing a connection.

Local edits commit atomically to account-scoped storage with field revisions and deletion markers. The app pulls on opening/resuming and every 15 seconds while active. Persisted JobScheduler jobs retry on a network connection and request periodic refreshes (15-minute minimum). Android may delay background execution, especially under battery restrictions or after force-stop. Website sync runs while its page is open. Offline changes remain queued until the next successful connection.

Drive stores immutable full versions named **Depositly-UTC-date-time-ID.json** in the hidden appDataFolder. Parent-file metadata identifies concurrent heads; both clients merge independent fields and resolve competing writes deterministically by revision timestamp/counter/device identity. Deletions have tombstones. Versions are kept without automatic cleanup and remain subject to Google account storage limits. The Android and web OAuth clients must belong to the same Cloud project.

**Backup versions** lists dated copies from either platform. Restoring one publishes a new revision to all connected devices; earlier files remain available. A legacy backup is adopted automatically on upgrade. Pending edits from an older linked Android installation migrate into that account's local queue. Unassigned phone data can seed the first connected account if Drive has no registry; switching to another account never copies the previous account's records. Each verified Google subject has a separate local vault.

**Sign out** removes the active account link while preserving that account's cached records and Drive versions. **Remove Google access** additionally revokes authorization. File import/export remains available without signing in. Use updated clients together; older versions cannot participate in field-level synchronization.

## Validation

Automated tests cover quiet UI application, account fences, deferred updates while a form is open, receipt/preference preservation, offline retries, edits during upload, concurrent heads, deletion tombstones, explicit restoration, authenticated version endpoints, CSRF, and Java/JavaScript protocol fixtures. Release builds verify package/version, ZIP alignment and the original signing certificate.

No Android phone was connected during implementation. Real Google authorization and the website-to-phone round trip require the configured deployment and an authorized device; mocked tests do not verify Cloud Console configuration or Android background scheduling.

References: [Android authorization](https://developer.android.com/identity/authorization), [Drive application data](https://developers.google.com/workspace/drive/api/guides/appdata), [OAuth testing limits](https://developers.google.com/identity/protocols/oauth2#expiration).
