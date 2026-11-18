# Releases and support

[User guide](EN-Home.md) · [Installation](EN-Installation.md) · [Changelog](../../CHANGELOG.md)

## Windows

[Windows 0.2.89 public preview](https://github.com/KaanKaya17/InstagramTracker/releases/tag/windows-v0.2.89) is a portable x64 ZIP. Its **InstagramTracker.exe** is unsigned. The release page provides SHA-256 checksums. The package passed 46 offline checks, 305 Python and 71 interface tests. It restores password sign-in with the current Instagram encryption key endpoint; sign-in, profile verification and two linked accounts were confirmed with real accounts. Availability still depends on the account and Instagram’s responses. The previous [Windows 0.2.84 preview](https://github.com/KaanKaya17/InstagramTracker/releases/tag/windows-v0.2.84) remains available.

The current Windows and Android packages share portable history, comparison exceptions, ZIP coverage, previewing two archives and an engagement calendar. The historical Windows 0.2.73 and other older packages are listed in the [Turkish release history](Surum-ve-Destek.md). See [platform coverage](../PLATFORM-PARITY.md) for implementation and test details.

## Android

[Android 0.1.102](https://github.com/KaanKaya17/InstagramTracker/releases/tag/android-v0.1.102), version code **103**, is the current signed preview. On Android 7 it now reads and writes encrypted records larger than about 100 KB correctly. It also finishes a half-read follow list before starting the next one and continues an interrupted later scan while the app is open. Device tests passed on Android 7 and Android 16 emulators. It passed 195 unit tests, release lint with zero errors, manifest and signature checks. The signature matches the earlier Android 0.1.92 APK, so it installs over an existing installation. The candidate was installed on a physical phone. Instagram sign-in reached a saved pending session, then the profile read returned HTTP 429. New Android connections through the first complete follow lists are still awaiting end-to-end confirmation.

[Android 0.1.92](https://github.com/KaanKaya17/InstagramTracker/releases/tag/android-v0.1.92), version code **93**, is the previous preview. It reads HTML and JSON relationship lists. Android 0.1.94 introduced the full archive browser. Later versions added archive summary, two-ZIP comparison and portable history. [See all archived previews](https://github.com/KaanKaya17/InstagramTracker/releases).

## Web and app languages

The web interface is a sample-data preview. The applications support Turkish, English, Spanish and Brazilian Portuguese, including light, dark, OLED and system appearance options. Usernames, biographies and personal notes retain their original text.

## Support

When you report an issue, include the platform and version, short steps, the expected result, what happened, the last attempt time, any HTTP status or stage code, and the displayed cooldown. Use a synthetic example where possible. Keep passwords, codes, cookies, session backups, private keys, personal ZIPs and raw responses out of reports.

Windows users can use the [public downloads issue tracker](https://github.com/KaanKaya17/InstagramTracker/issues). For security reports, use the [project security policy](../../SECURITY.md).
