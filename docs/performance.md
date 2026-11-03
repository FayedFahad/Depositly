# UI performance

The interface queries recurring payments without building every payout through 2199 for each screen. The original `FDR.schedule` remains the compatibility oracle for financial calculations and older backup handling.

## Indexed schedules

`FDR.scheduleQuery(asset)` provides inclusive date-range counts and rows. Counts use payout and maturity indexes, with overlap correction for term-end payouts. Row queries seek to the requested dates and accept a limit. Amounts retain the existing month/day interest and TDS rules.

Date-shape caches exclude names, IDs and monetary terms. They share only recurrence dates and partial-period lengths. At most 16 shapes are retained, with at most 4096 values per map; evicted shapes stop caching. Amounts are calculated from the current asset.

`queryCashEvents` adds historical receipt rows and 100-day reconciliation to those schedules. Its `scheduleThrough` option preserves the earlier distinction between a full-horizon view and a Calendar month: a received principal whose maturity was edited can appear at its historical date before the replacement scheduled event exists.

Derived receipt indexes are invalidated when the assets array, paid-record object, or local day changes. Financial edits and account replacement must replace those references instead of mutating cached inputs in place.

Home loads the earliest three upcoming payments and its 30-day interest window. Payments generates its visible page while preserving the exact total count. Calendar queries its displayed month. Investment details initially show 50 schedule rows and retain scroll position when loading more.

Legacy receipt migration builds inference schedules only for investments with missing receipt snapshots. Modern backups and receipt-free investments skip this work.

## Reminder preparation

The complete future reminder plan is prepared in cancellable batches through 2199. Each task handles up to eight 128-row batches, stopping after a 6 ms time budget before yielding to interaction. Same-day interest and principal stay together at batch boundaries. Serialization also runs in batches.

Edits, account changes and local date rollover invalidate pending jobs. An unchanged financial state reuses the installed plan; a request to enable reminders waits until the latest complete plan has been installed. Notification timing still depends on Android permissions and alarm behavior.

## Background transfers

Android `DocumentTransfer` runs export preparation and document-provider IO on a worker. Requests carry the JavaScript vault owner into the native bridge, recheck ownership before delivery, and suppress stale Activity callbacks. Temporary exports have unique filenames and are cleaned up on completion or cancellation.

Imports retain the 20 MB read limit. Export streaming remains unbounded, preserving prior behavior. The import still opens the existing review flow before replacing or merging records.

## Verification

Run `scripts/test.ps1` and `scripts/gradle.ps1 -Tasks @('testReleaseUnitTest')`.

The schedule-query regression compares counts, IDs, dates, amounts and first pages against the original engine across 600 deterministic recurrence fixtures and four ranges each. It also checks bounded row generation for 500 deposits, distant payment access, unpaid principal entry, maturity-edited receipts, cache invalidation, lazy migration and detail pagination continuity. Reminder tests verify full-horizon plans, yields, same-day boundaries, cancellation, retry and enable ordering. Native transfer tests cover worker execution, errors, cancellation, callback suppression, UTF-8 and import/export size behavior.

Desktop VM benchmarks are useful for detecting computation regressions. They exclude WebView rendering, TalkBack, provider latency and Android frame timing. Confirm perceived responsiveness on Android hardware before making device-performance claims.
