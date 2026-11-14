# Privacy and data

[User guide](EN-Home.md)

## Purpose and acceptable use

InstagramTracker helps you review your Instagram exports and understand changes in accounts you’re authorized to access. The app does not send ZIP files, passwords or account records to the project team, and it does not collect usage data on a project server. For live monitoring and verification, your device connects to Instagram/Meta.

Use Tracker only with data you’re authorized to access. Follow applicable rules and respect other people’s privacy. The project does not support unauthorized account access, harassment or unlawful use.

## Local storage and network access

Windows protects local records with DPAPI tied to the Windows user. Android uses Android Keystore and AES-GCM. Sessions, saved profiles and observations are encrypted locally and each linked account has its own data. Tracker provides no cloud backup or automatic device sync. Android app backup and transfer are disabled for these records. Clearing app data or losing the encryption key can make records inaccessible.

Live sign-in and reads connect directly to Instagram/Meta. Profile photos come from eligible Meta CDN hosts. Instagram verification may use Meta or Google CAPTCHA resources after you allow them. Website and social links open in your regular browser. Tracker has no data server, ads, analytics or crash-reporting SDK. Website icons are bundled locally.

## Read-only monitoring and credentials

Tracker does not offer following, unfollowing, likes or direct messages. The reader rejects disallowed operations before sending them. Sign-in and supported verification have separate narrowly scoped requests. Passwords and verification codes are not kept as a log or vault record. The resulting session may be stored encrypted for later reads. Protect it as you would a password.

**Request information** records limited diagnostic outcomes. It excludes raw credentials, codes, cookies and response bodies. Review screenshots for personal information before sharing them.
## Retention and photos

Daily summaries are encrypted by profile and year, separately from recent observations. Retention settings on Windows under **Settings → Local data**, or in the Android options menu, allow separate 30-, 90- or 365-day retention for detailed observations and daily statistics. The default keeps records until you delete them. Cleanup runs after confirmation and during later use, keeping the current profile and latest list. A failed cleanup can be retried. [Daily statistics](EN-Daily-Statistics.md).

Windows keeps temporary profile thumbnails in bounded memory. Android encrypts its bounded disk thumbnails. The optional avatar archive stores saved images separately. The manually entered **About this account** fields belong to the local profile record and can be included in a Windows backup.

## Backups and exports

**Portable history** creates a password-protected `.trackerhistory` file containing saved profiles, notes, favorites, profile observations, complete lists and daily summaries. On Windows, look under **Settings → Local data**. On Android, use **More → Portable history** and choose the save location before entering the backup password. Open the file on Windows or Android as a separate archive without connecting Instagram. It holds up to 16 MiB of data and excludes sessions, live-tracking settings and media.

**Windows backup** is a DPAPI-protected file for the same Windows user, without an Instagram session. Restore replaces the selected account’s local observations with those from the backup, merging daily summaries and keeping the newer overlapping day. Retention boundaries and the approximately 64 MiB limit still apply. Restoring does not enable automation, restore a session or remove cooldowns. A backup from another account may be rejected.

**CSV exports are unencrypted.** Store and share them accordingly. Windows provides CSV export and DPAPI backups.

## Deletion and disconnection

Under **Settings → Local data** on Windows, or **Manage connection** on Android, use the action to delete the selected account’s local data. Confirm the selected account. This removes its local session, saved profiles, notes, lists and history. Other accounts, exported files and shared cooldown history remain. Accounts are deleted individually.

Removing a saved profile also removes its associated local history, lists, notes, notifications and profile archives. A membership entry in somebody else’s list and exported files are managed separately. Removing a local session does not sign out every Instagram session. Use Instagram’s own security settings for server-side session management.
