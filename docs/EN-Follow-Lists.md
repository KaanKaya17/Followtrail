# Follow lists

[User guide](EN-Home.md) · [ZIP archives](EN-Instagram-Data-Archive.md) · [Automatic checks](EN-Monitoring.md)

## Compare relationships within one ZIP

In Accounts Center, export **Followers and following** with **All time**. Both HTML and JSON are supported for relationship lists. Choose JSON to use follow dates. On Windows, open **Instagram Data Archive → Compare follows from a ZIP**. On Android, the relationship importer is available from the options menu.

If both directions are present, search for names or inspect mutual follows, accounts that do not follow you back, and accounts you do not follow back. The comparison matches usernames. Dated JSON entries can be sorted and filtered by an inclusive date range. A mutual relationship can show both dates. Its date filter uses the date the other account first followed you. ZIP records are displayed locally and stay separate from live scan history.

## Select a list

In **Follow Lists**, choose the list owner, then **Followers** or **Following**. Each saved profile and your own account have separate records. Instagram limits which lists can be read. For a private account, Tracker can explain that follow approval is required when the last complete list of accounts you follow is available and matches your profile count.

## First snapshot and later comparisons

The first complete scan becomes the baseline. With profile and list automation enabled, Windows and Android queue both directions for a newly saved profile. To keep checking a saved profile after preparation, turn on monitoring for it.

For example, if the first complete list is A, B and C, and the next is A, C and D, Tracker flags D as newly seen and B as absent. It shows the net difference between observations. Earlier changes and exact event times are unknown.

## Large and incomplete lists

Lists are read one page at a time. Progress shows the rows or pages that have been read, along with any wait and continuation status. Time depends on the list size, queue, network and budget. The list can also change during a scan.

<a name="incomplete-lists"></a>

A partial list can appear when the number read differs from the profile count or the scan stops early:

- Tracker keeps the last complete snapshot for comparison.
- It can resume from an eligible cursor or restart at the first page when the cursor is unusable. Partial runs are kept distinct.
- A partial result does not establish who disappeared. A new profile's first mismatch can get an early retry after around 30 minutes. The daily plan applies if the mismatch persists, subject to pacing and available budget.

[What to check when a list is incomplete](EN-Troubleshooting.md#list-does-not-complete).

## Show changed accounts

On Windows, compare two complete snapshots or choose **Show changed accounts** from a list-change record. A counter change by itself cannot identify a member.

For your own account on Windows, open **Following → Not following you back** and use the row menu. On Android, open the equivalent result under **Follow Lists → Compare** and tap the account. **Move to exceptions** keeps the row in a separate section without counting it in the main results. **Return to main list** restores it.

## Common followers and following

Choose the list owner, then the second profile in the common-accounts comparison. Switch between common followers and common following. Tracker prefers the latest complete snapshots and labels a comparison based on a partial snapshot as limited to the accounts it could read. The dates and coverage of both sources appear above the result. An unavailable list produces a missing-record message. Available photos and counts are reused without a profile request for every row.

## Automatic scans, search and photos

On Windows, an observed count change can bring forward a scan. The daily full-scan target still matters when the count stays the same, because one departure and one arrival can offset each other. These are separate from the profile-check interval.

Windows loads the next 100 saved rows as you scroll. Search covers the entire saved list, including rows not yet on screen. Photos load separately. Opening an account from a list does not save or watch it automatically.
