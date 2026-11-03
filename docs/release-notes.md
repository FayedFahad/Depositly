Depositly 1.3.1 Build 4

- Optimized Home, Payments, Calendar and deposit details to query payment schedules by date and visible rows, preserving exact counts, amounts and historical receipts. Long deposit schedules show 50 entries at a time with Show more.
- Payout reminders prepare their complete future plan in cancellable batches so startup and saved edits can remain responsive. Renewing reminders continue beyond the next year.
- Moved local backup preparation and document-provider reads/writes off Android's UI thread. Transfers retain the initiating account, discard stale results and show progress feedback; import review and the existing import size limit remain in place.

- Added custom bank ordering through the Bank order column in Bank Information CSV. Numbered banks appear first in the icon row and both Cards/Grid layouts; unnumbered banks follow alphabetically. Accounts from one bank share a rank, and conflicting or invalid ranks are rejected before import.
- Importing or merging the edited bank CSV saves the custom order without changing unrelated records. The order survives restarts and full backups, synchronizes with the website, and remains unchanged by deposit-only or older bank CSV imports. Clearing all ranks resets alphabetical order.

- Added a bank icon filter row above Cards and Grid in Bank Information. Only banks with saved accounts appear; selecting an icon shows that bank's accounts in either layout, and All banks clears the selection.
- Bank filtering stays selected across navigation and layout changes without changing saved records or backups. It resets when switching accounts or reopening the app, and clears when the selected bank has no remaining accounts. The icon row scrolls horizontally on narrow screens and keeps bank logos readable in both themes.

- Added editable current balances to bank accounts, with an Update action in account cards and details. Existing accounts without an entered balance count as zero.
- Home now adds bank balances to active Self deposits. The existing balance card keeps its layout and uses a single horizontal breakdown: Fixed deposits on the left, Savings certificates in the middle and Bank on the right.
- Bank balances survive local saves, CSV backups and Google sync with the website. Balance edits retain account details, deposit links and receipt history. Bank balances do not accrue forecast interest or change automatically when a payment is recorded.
- Bank Information CSV now includes balance and currency fields while still reading older files. Currency checks protect retained deposits and bank balances during merges and separate-category replacements.

- Rebuilt the signed APK from the current native Android source, bundled interface, styles and resources. Updated the app version and APK filename to 1.3.1 Build 4.

- Removed the Savings Certificate bank-icon background tint in light and dark modes. Bank logo tiles now match FDRs.

- Backups now use two separate CSV files: Deposits and Bank Information. Both use readable field headers instead of generic paths and values.
- Deposits CSV includes all deposit terms, receiving-account IDs, payment receipts with historical receiving-account identities, currency, display preferences and reminder settings. Deposits repeat across rows when they have multiple receipts.
- Bank Information CSV includes bank/account IDs, bank name, account title, account number, branch, routing number, account type and bank view. Text identifiers preserve leading zeros.
- Each import reviews and restores only its own category. Replacing deposits keeps saved bank accounts; replacing banks keeps deposits, receipts and other settings. Banks still linked to deposits are protected from removal during replacement.
- JSON export remains removed. Older investment-only CSV, Build 16 combined CSV and legacy JSON data remain readable for migration.

- Tenure and Payout Cycle now work independently. Either field can use a preset or Custom without changing the other field or enabling renewal.
- Saving, reopening, CSV backups and sync preserve explicitly chosen terms, including 100-day tenures with shorter payouts and 100-day payouts with longer tenures.
- Used the supplied logo image for the Android launcher and offline in-app header.
- Deposits remembers the last selected Card or Grid view across navigation and restarts. Removed the separate Default deposit view setting.
- Aligned Bank grid headers, account details and actions across neighboring cards, including wrapped account types and branch names. Improved spacing and copy-button placement, with a single-column fallback for older WebViews.
- Imported the supplied Build 11 visual redesign, including the emerald light/dark theme, revised controls, and simplified Home, Calendar and Settings layouts.
- Bundled the packaged styles into the phone's loaded document so the redesign renders offline with remote requests still blocked.
- Kept the existing interest calculations, account links and recorded receipt history.
- Added optional interest-receiving bank account links for FDRs and savings certificates. Any saved bank can be selected independently of the deposit institution; existing deposits remain unlinked until chosen.
- Deposit and payment details show receiving accounts. New receipts retain immutable account identities; Bank Information lists related deposits and protects accounts still in use from deletion.
- The two CSV files together and Google backups preserve links, full bank details and receiving-account history. Linked cloud data uses reader version 7.
- Removed the Date received field. New receipts use scheduled payout dates internally; existing history remains intact, and payment lists and filters follow scheduled dates.
- Removed the old startup logo/tagline flash and shortened the bottom-tab label to Bank. Bank Information page and menu titles remain unchanged.
- Replaced the bottom Forecast tab with Bank. The five tabs are Home, Deposits, Calendar, Payments and Bank.
- Moved the forecast period selector and net/gross/TDS summary card to Home above Coming up next. Changing the period updates only that section and keeps the scroll position.
- Redesigned Bank Information with clearer bank groups, prominent account numbers, holder/type labels, and compact Cards/Grid controls. Copy, share, edit, delete and saved view selection are preserved.
- Bank selection leaves Investment Name for manual input. Bank Information titles use a capital I, and the long portfolio calculation note has been removed.
- Fixed 100-day FDR schedules: the full term pays on its exact 100th day, including each renewal. Existing receipts keep their recorded dates and amounts; payments from the old split schedule reduce the remaining term interest due.
- Month cycles calculate interest using months / 12; day cycles use exact days / 360. Calendar renewal dates stay anchored to the original opening day, including month ends.
- Added Simple interest and Compound interest to the deposit form. Compound FDRs add net interest to principal only at renewal maturity; stopping renewal returns the accumulated principal and final interest. Without renewal, principal and interest are paid at maturity.
- Savings certificates always return principal with their final interest at maturity and do not renew.
- Compound balances appear in current portfolio totals; the Home forecast uses a simple-interest estimate on that balance.
- Compound cloud backups require reader version 6, and Deposits CSV backups preserve their full interest and renewal terms. The updated website uses matching calculation and backup rules.
- Account and routing numbers have copy icons on saved bank information and in its form. Android copies their exact text, including leading zeros.
- Accounts from the same bank are grouped under one bank heading and logo.
- Bank information offers Cards and Grid views. The selected layout is saved across navigation, restarts and Bank Information CSV backups; older deposit-only imports keep it. Retired Compact preferences fall back to Cards.
- Bank information is available from the hamburger menu, with an offline bank dropdown, bank logos, and account details that can be added, edited or deleted.
- Share an account as text with its title, bank, branch, account number and routing number. Account Type is optional free text.
- Bank Information CSV and Google backups include bank information. Importing older investment-only CSV files preserves saved accounts.
- On Home, press Back twice within two seconds to exit. Back first closes an open menu or form and returns other pages to Home.
- Saved edits synchronize automatically between the app and website using the same Google account.
- Routine updates apply quietly without backup review prompts.
- Backup versions lists dated copies that can be restored to the shared registry.
- Offline edits retry when connectivity returns. Android controls background execution timing.
- Both CSV exports and restores remain available offline.

Install this update over the existing app to keep your records. Both clients must use the updated sync protocol. Google Play services and the existing Google Drive authorization are required.

Automated website and Android checks pass. Live synchronization with an authorized phone still needs device validation.
