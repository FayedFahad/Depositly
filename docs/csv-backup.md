# Separate CSV backups

In Settings, open **Import, export & backup**. Save both files to back up all records:

- **Export Deposits (CSV)** saves `depositly-deposits-YYYY-MM-DD.csv`.
- **Export Bank Information (CSV)** saves `depositly-bank-information-YYYY-MM-DD.csv`.

Both are UTF-8 CSV files with named field headers. There is no JSON download or generic field-path export.

## Deposits

Deposit columns include Deposit ID, Deposit type, Investment name, Owner's name, Ownership, Bank ID, Bank name, Deposit / certificate number, Principal, Annual interest rate %, TDS %, Payout cycle value/unit, Opening date, First payout date, Maturity date, Tenure value/unit, Automatic renewal, Renewal stop date, Receiving bank account ID, Interest type and Notes.

Receipt columns include Receipt ID, Received amount/date, Receipt notes, Receipt investment name/type, Scheduled payment date, Payment kind, Gross interest, Tax deducted, Expected net amount, Principal returned, and the historical receiving bank account ID/name/title/number. A deposit with multiple receipts repeats its deposit fields on one row per receipt; a deposit without receipts has one row with empty receipt columns. Import groups rows by Deposit ID and preserves each Receipt ID.

The file also includes Currency, Number format, Dark mode, Deposit view, Reminder enabled/time, Deposit count, Receipt count and CSV backup version. Imported custom investment columns use `Extra: <original header>`. Money uses currency units with up to two decimal places; dates use YYYY-MM-DD. Yes/No columns distinguish false from an unspecified value.

## Bank Information

The columns are Bank account ID, Bank ID, Bank name, Account title, Account number, Branch name, Routing number, Account type, Current balance, Bank view, Bank account count, Currency, CSV backup version and Bank order. There is one row per saved account. Account numbers, routing numbers and certificate references remain text, including leading zeros. Current balance uses currency units with up to two decimal places; zero is valid. Accounts without a saved balance contribute zero to Home.

Older Bank Information CSV files without balance or currency columns remain readable. A file containing balances, including explicit zero balances, must include Currency. Older app versions reject the new bank CSV headers instead of silently discarding balances. Google and legacy JSON backups preserve the optional balance field.

## Custom bank order

Edit **Bank order** in the Bank Information CSV to number banks from 1 to 500. Lower numbers appear first in the bank icon row and Cards/Grid groups on the updated app and website. Account rows for the same bank must use the same number; extra rows may be blank and inherit that bank's rank. Different banks cannot share a number. Unranked banks remain alphabetical after ranked banks.

Both merge and replacement apply a supplied order. Merge preserves current account fields when they conflict with the file. A missing Bank order column keeps the current order; an explicitly present column with all cells blank resets alphabetical order. Older CSVs remain readable. Updated exports include this optional column; earlier app versions need the column removed before importing them. The preference survives restarts, JSON/Google backups and synchronization.

## Restore

Choose **Choose CSV file**, review the counts, then merge or replace. Import each file separately. On an empty app either order works. When replacing an existing collection, restore Deposits first, then Bank Information.

- Deposits replacement restores deposits, receipt history and its saved preferences. It keeps saved bank accounts, the bank view and custom bank order.
- Bank Information replacement restores bank accounts, their balances, the bank view and any supplied bank order. It keeps deposits, receipts, other display settings and reminders. It refuses to remove a bank still linked to a current deposit; use merge, or restore Deposits first. Imports with balances must use the same currency as money retained in the other category, so restoring one category cannot relabel the other's amounts.
- Merge adds missing records and keeps existing records and settings when values conflict, including account balances. A supplied Bank order column updates the display order during a bank merge. Repeated imports do not duplicate records. A first monetary import into an empty collection adopts its currency; merging monetary records with different currencies is blocked, including records with explicitly saved zero balances.

Empty exports contain a settings-only row with zero totals, so they can clear their own category without affecting the other. Cancelled imports, invalid files and failed local writes keep current records. Android notification permission and generated schedules are device-managed; credentials are excluded.

The reader checks required and duplicate headers, row lengths, CSV versions 2 and 3, consistent preferences, record totals, duplicate receipt IDs, repeated deposit consistency, bank/deposit limits and all existing financial/history rules. It rejects incomplete files before any import action. Export files respect the Android reader's 20 MB UTF-8 limit.

Older investment-only CSV files preserve saved banks and display preferences when replacing deposits. Build 16 combined CSV and legacy JSON data remain readable for migration. Google sync retains its internal protocol.

## Preserve identifiers in spreadsheets

New exports use CSV backup version 3. Numeric text such as account numbers, routing numbers and deposit/certificate references receives a leading apostrophe as a text marker. The updated app and website remove exactly one protection marker during import, retaining every original digit. Existing literal apostrophes remain intact. Balances, amounts, rates and bank-order ranks stay numeric.

Use updated clients to restore these files. Earlier readers reject version 3 instead of importing the protective marker as part of an account number. The updated readers also accept older version 2 CSVs, including unprefixed identifiers.

Scientific notation in identifier fields (for example, `1.23457E+16`) is rejected during CSV import and export with instructions to recover the original full digits. Expanding that value cannot recover omitted digits or leading zeros; the app never guesses account lengths or pads with zeros. Rejection keeps current records unchanged. Account numbers already shortened in an older import can still be edited in Bank Information or replaced using a corrected CSV.

Keep both original exports for restores. If a spreadsheet editor offers automatic number conversion, import identifier columns as Text and do not remove the text marker. A changed or rounded identifier cannot always be detected once saved as ordinary digits.
