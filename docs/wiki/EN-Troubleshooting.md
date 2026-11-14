# Troubleshooting

[User guide](EN-Home.md) · [What to include in a report](EN-Releases.md#support)

## Automatic reads are paused

Open **Request information → Read diagnostics**. Tracker reports profile, follower and following reads separately. Repeated incompatible responses can pause the affected operation after three failures. A changed endpoint is only a possible cause. Missing profiles, incomplete lists and HTTP 429 have separate states.

A continuation request that receives HTTP 400 or 404 can restart from page one on the next eligible scan. The last complete snapshot is kept. A successful manual read resumes that operation. On Windows, new list scans wait if a fresh profile read is required.

## Sign-in, codes and CAPTCHA

Confirm that a fresh sign-in works in Instagram itself. If it works there but stalls in Tracker, report the version and redacted diagnostic status. HTTP 200 alone does not establish a completed login. Complete a supported verification challenge in the displayed Instagram page. Submit a code once and wait for the result.

If Android shows that the Instagram session opened and asks to finish the connection, see [Finish a pending connection](EN-Accounts.md#finish-a-pending-connection). The continuation button waits until it may send a single profile check. Closing the screen preserves the pending session.

## Instagram cooldown or HTTP 429

429 means Instagram limited the request. Check **Request information** for the last attempt and the next eligible time, then wait for the displayed cooldown. After it expires, try the relevant action once. The first local cooldown is at least an hour and can grow on repeated restrictions. A longer server-provided wait is respected. Instagram may still reject a request after the local wait ends. Switching accounts, updating and deleting data do not clear the cooldown.

## List does not complete

Check the direction, counts and continuation state. Keep the previous full list distinct from a partial result. If you report it, include the expected and observed counts and the error or wait state. [How incomplete lists work](EN-Follow-Lists.md#incomplete-lists).

## Notifications are missing

Look for the change in the in-app log. Then check watch and automation settings, type preferences, OS permissions and the system’s scheduling. If the record exists, review notification or focus settings before starting another profile read.

## Threads or a photo is missing

The Threads shortcut requires an explicit address or an active badge. An older record may gain the field at the next successful profile read. A missing picture can be temporary because images load separately.

## Windows local data service closed

Use **Quit completely** from the tray, check that another Tracker version is not running, and reopen the current EXE. Extract the entire ZIP before running the app and keep its companion files beside the EXE. Existing local data is kept when the service closes. If it keeps happening, report the version and the error text.
