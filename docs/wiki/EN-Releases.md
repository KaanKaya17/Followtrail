# Releases and support

[User guide](EN-Home.md) · [Installation](EN-Installation.md) · [Changelog](../../CHANGELOG.md)

## Windows

[Windows 0.2.84 public preview](https://github.com/KaanKaya17/InstagramTracker/releases/tag/windows-v0.2.84) is a portable x64 ZIP. Its **InstagramTracker.exe** is unsigned. The release page provides SHA-256 checksums. The package passed 45 offline checks and was tested with two real Instagram exports. Live observations have been seen on Windows, but availability depends on the account and Instagram’s responses.

The current Windows and Android packages share portable history, comparison exceptions, ZIP coverage, previewing two archives and an engagement calendar. The historical Windows 0.2.73 and other older packages are listed in the [Turkish release history](Surum-ve-Destek.md). See [platform coverage](../PLATFORM-PARITY.md) for implementation and test details.

## Android

The current signed candidate is **Android 0.1.100**, version code **101**. It passed 195 unit tests, build, manifest and signature checks. The signature matches the earlier Android 0.1.92 APK. Lint reported zero errors and 71 warnings in the recorded build. The candidate was installed on a physical phone. Instagram sign-in reached a saved pending session, then the profile read returned HTTP 429. New Android connections through the first complete follow lists are still awaiting end-to-end confirmation.

[Android 0.1.92](https://github.com/KaanKaya17/InstagramTracker-Archive/releases/tag/android-v0.1.92), version code **93**, is an older preview for readers with access to the private source repository. It reads HTML and JSON relationship lists. Android 0.1.94 introduced the full archive browser candidate. Subsequent candidates added archive summary, two-ZIP comparison and portable history. Android is outside the current public Windows release.

## Web and app languages

The web interface is a sample-data preview. The applications support Turkish, English, Spanish and Brazilian Portuguese, including light, dark, OLED and system appearance options. Usernames, biographies and personal notes retain their original text.

## Support

When you report an issue, include the platform and version, short steps, the expected result, what happened, the last attempt time, any HTTP status or stage code, and the displayed cooldown. Use a synthetic example where possible. Keep passwords, codes, cookies, session backups, private keys, personal ZIPs and raw responses out of reports.

Windows users can use the [public downloads issue tracker](https://github.com/KaanKaya17/InstagramTracker/issues). For security reports, use the [project security policy](../../SECURITY.md).
