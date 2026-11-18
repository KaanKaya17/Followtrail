# Releases and support

[User guide](EN-Home.md) · [Installation](EN-Installation.md) · [Changelog](../../CHANGELOG.md)

## Windows

[Windows 0.2.91 public preview](https://github.com/KaanKaya17/InstagramTracker/releases/tag/windows-v0.2.91) adds a lasting follow history: **Came back** and **Left** views in Follow Lists and a **Follow history** section in the person panel, built from complete scans with no extra Instagram request. It passed 46 offline package checks, 7 follow-history package checks, 325 Python and 72 interface tests; a live list scan with a real account was not repeated. [Follow history](Takip-Listeleri.md#geri-gelenler-ve-ayrılanlar).

[Windows 0.2.90](https://github.com/KaanKaya17/InstagramTracker/releases/tag/windows-v0.2.90) is a portable x64 ZIP. Its **InstagramTracker.exe** is unsigned. The release page provides SHA-256 checksums. The package passed 46 offline checks, 310 Python and 71 interface tests. Story photos and videos now download on their own media lane (1–3 seconds apart, 400 per hour and 4,000 per day per device) instead of the Instagram API queue, so a story check spends one API budget slot instead of one per file. API spacing, budgets and HTTP 429 rules are unchanged. A live story check with a real account was not repeated for this release. [Windows 0.2.89](https://github.com/KaanKaya17/InstagramTracker/releases/tag/windows-v0.2.89), which restored password sign-in and was confirmed with real accounts, remains available.

The current Windows and Android packages share portable history, comparison exceptions, ZIP coverage, previewing two archives and an engagement calendar. The historical Windows 0.2.73 and other older packages are listed in the [Turkish release history](Surum-ve-Destek.md). See [platform coverage](../PLATFORM-PARITY.md) for implementation and test details.

## Android

[Android 0.1.103](https://github.com/KaanKaya17/InstagramTracker/releases/tag/android-v0.1.103), version code **104**, is the current signed preview. Like Windows 0.2.90, it downloads story media on a separate media lane so a story check spends one API budget slot; 196 unit tests and release lint passed, and 301 of 302 device tests passed on an Android 16 emulator (the remaining timing-sensitive profile panel test passed three times on its own). Since 0.1.102, Android 7 reads and writes encrypted records larger than about 100 KB correctly. It also finishes a half-read follow list before starting the next one and continues an interrupted later scan while the app is open. Device tests passed on Android 7 and Android 16 emulators. It passed 195 unit tests, release lint with zero errors, manifest and signature checks. The signature matches the earlier Android 0.1.92 APK, so it installs over an existing installation. The candidate was installed on a physical phone. Instagram sign-in reached a saved pending session, then the profile read returned HTTP 429. New Android connections through the first complete follow lists are still awaiting end-to-end confirmation.

[Android 0.1.92](https://github.com/KaanKaya17/InstagramTracker/releases/tag/android-v0.1.92), version code **93**, is the previous preview. It reads HTML and JSON relationship lists. Android 0.1.94 introduced the full archive browser. Later versions added archive summary, two-ZIP comparison and portable history. [See all archived previews](https://github.com/KaanKaya17/InstagramTracker/releases).

## Web and app languages

The web interface is a sample-data preview. The applications support Turkish, English, Spanish and Brazilian Portuguese, including light, dark, OLED and system appearance options. Usernames, biographies and personal notes retain their original text.

## Support

When you report an issue, include the platform and version, short steps, the expected result, what happened, the last attempt time, any HTTP status or stage code, and the displayed cooldown. Use a synthetic example where possible. Keep passwords, codes, cookies, session backups, private keys, personal ZIPs and raw responses out of reports.

Windows users can use the [public downloads issue tracker](https://github.com/KaanKaya17/InstagramTracker/issues). For security reports, use the [project security policy](../../SECURITY.md).
