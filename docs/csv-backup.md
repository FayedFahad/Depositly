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

The columns are Bank account ID, Bank ID, Bank name, Account title, Account number, Branch name, Routing number, Account type, Current balance, Bank view, Bank account count, Currency and CSV backup version. There is one row per saved account. Account numbers, routing numbers and certificate references remain text, including leading zeros. Current balance uses currency units with up to two decimal places; zero is valid. Accounts without a saved balance contribute zero to Home.

Older Bank Information CSV files without balance or currency columns remain readable. A file containing balances, including explicit zero balances, must include Currency. Older app versions reject the new bank CSV headers instead of silently discarding balances. Google and legacy JSON backups preserve the optional balance field.

## Restore

Choose **Choose CSV file**, review the counts, then merge or replace. Import each file separately. On an empty app either order works. When replacing an existing collection, restore Deposits first, then Bank Information.

- Deposits replacement restores deposits, receipt history and its saved preferences. It keeps saved bank accounts and the bank view.
- Bank Information replacement restores bank accounts, their balances, and the bank view. It keeps deposits, receipts, other display settings and reminders. It refuses to remove a bank still linked to a current deposit; use merge, or restore Deposits first. Imports with balances must use the same currency as money retained in the other category, so restoring one category cannot relabel the other's amounts.
- Merge adds missing records and keeps existing records and settings when values conflict, including account balances. Repeated imports do not duplicate records. A first monetary import into an empty collection adopts its currency; merging monetary records with different currencies is blocked, including records with explicitly saved zero balances.

Empty exports contain a settings-only row with zero totals, so they can clear their own category without affecting the other. Cancelled imports, invalid files and failed local writes keep current records. Android notification permission and generated schedules are device-managed; credentials are excluded.

The reader checks required and duplicate headers, row lengths, CSV version 2, consistent preferences, record totals, duplicate receipt IDs, repeated deposit consistency, bank/deposit limits and all existing financial/history rules. It rejects incomplete files before any import action. Export files respect the Android reader's 20 MB UTF-8 limit.

Older investment-only CSV files preserve saved banks and display preferences when replacing deposits. Build 16 combined CSV and legacy JSON data remain readable for migration. Google sync retains its internal protocol.

Keep both original exports for restores. Spreadsheet applications may change identifier strings or values when saving an edited copy.
