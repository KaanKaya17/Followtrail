# Daily statistics

[User guide](EN-Home.md) · [History](EN-History.md) · [Privacy and data](EN-Privacy.md)

## Open daily history

On Windows, select **Daily** in the Overview chart or **History → Daily history** for a saved profile. **Recent checks** shows individual observations. On Android, open **Daily history** from Overview or a profile's analysis. The [release page](EN-Releases.md#android) describes current device validation.

Choose followers or following and a 7-day, 30-day, one-year or all-time range. Long periods may be grouped. The number of observed days and checks is shown separately.

## Read a day

Tracker stores the first, last, minimum and maximum observed values, the times and the number of checks. Post counts are included when available. The chart uses the last value observed that day, and today’s point can still change. Open a chart point to inspect the range.

Days use UTC on both platforms. A day without observations remains missing. A net increase from 100 to 103 does not tell you how many accounts joined and left along the way. [Compare complete follow lists](EN-Follow-Lists.md).

## Retention and recovery

Daily summaries are stored by account, profile and year in separate encrypted files. Updates can summarize older observations still on the device before pruning. Deleted or unobserved days cannot be reconstructed. History is local, with no automatic device sync.

Use **Clear daily history** to delete the selected profile’s summaries. Recent detailed checks, list snapshots and the session are separate. Cleared days cannot reappear from old detailed checks. Removing a saved profile or an account also removes its relevant daily history. Exported files are managed separately. Storage failures can be reported without resetting the underlying files.

The encrypted Windows backup includes daily history. Restoring preserves the newer record for overlapping days and does not add their check counts together. A cleared day stays cleared. The approximate 64 MiB limit applies to the Windows backup file. If a new backup is too large, the prior file is preserved. [Backup types](EN-Privacy.md#backups-and-exports).
