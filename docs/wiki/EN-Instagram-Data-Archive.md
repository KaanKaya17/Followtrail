# Instagram Data Archive

[User guide](EN-Home.md) · [Installation](EN-Installation.md) · [Follow lists](EN-Follow-Lists.md)

Open a ZIP from Instagram to browse available follow lists, messages and activity on your device. Compare two exports for the same account without connecting Instagram to Followtrail. These screenshots show Windows 0.2.84 with synthetic data.

[Get a ZIP](#get-a-zip) · [Open the archive](#open-the-archive) · [Compare two ZIPs](#compare-two-zips) · [Read results](#read-results)

## Get a ZIP

1. In Followtrail, use the help link under **Open Instagram ZIP** or in **Instagram Data Archive** to open Meta Accounts Center.
2. Under **Your information and permissions → Export your information**, select your Instagram account and export to your device. Include **Followers and following** in both exports for a comparison. Select messages, activity or media if you also want to browse them.
3. Choose **JSON** and **All time**. Use the same selections for both exports. Once Instagram prepares the files, download them. Followtrail opens the ZIP as downloaded.

## Open the archive

On Windows, use **Overview → Open Instagram ZIP** on first launch. Later, open **Instagram Data Archive** below **Timeline** and select **Open Instagram ZIP** or drag the file onto the drop area.

On Android 0.1.100 and later, use the ZIP action on the first screen. The older 0.1.92 preview opens the relationship lists through the options menu. See [release status](EN-Releases.md#android).

![Windows archive overview, with file details, coverage, follow lists, messages and activity](assets/en/zip-arsiv.png)

- **Archive coverage** shows the file name, account and export date when available. **Missing from this ZIP** means the category was absent. A count of **0** means Followtrail read an empty list. When a list could not be read, try a fresh export.
- Use categories to explore the available items, **Search the entire archive** to find readable fields, or **File browser** to inspect or save an individual file. Supported local media can be previewed.
- **Content and engagement** opens a calendar. Select a day to see posts, Reels or stories available in the export. Media and engagement counts depend on the archive. When applicable, the calendar can also show your linked account’s local story records.

- **Insights** (Windows) are calculated only from the open ZIP; nothing is sent to Instagram and nothing is saved:
  - **Interactions** match the accounts you follow with your own likes, comments, story reactions and saves: **Never interacted**, **No interaction in 12 months**, **Don’t follow you back** (with how long you have followed them) and **Most interacted**. Messages are not counted, and anything outside the ZIP’s date range is not visible.
  - **Activity hours** show a weekday × hour heat map of your dated activity in this computer’s current time zone.
  - **Account security** lists logins, devices, distinct IP addresses and password or privacy changes. A device first seen in the last 30 days is highlighted. IP addresses are shortened by default.
  - **Ads** shows advertisers using your activity, ads you saw or clicked, and the interests Instagram assigned to you.
  - **Year in review** shows yearly totals and a numbers-only image you can **save as PNG**.
  An empty section means the export did not include that data (for example **Security and login information** or **Ads information**).

![Content calendar with one sample post on the selected date](assets/en/zip-icerik-takvimi.png)

## Compare two ZIPs

1. Open the older ZIP. Choose **Compare two ZIPs → Choose the second ZIP** and select the newer ZIP.
2. Check both source cards. Compare the account names and dates when shown. Use **Swap order** if the newer file is first.
3. Select **Start comparison**. If Followtrail displays an account mismatch, check the files and any username change before interpreting the result.

![Source cards for the older and newer ZIP of the same account, with the swap and comparison controls](assets/en/zip-karsilastirma-kaynaklar.png)

## Read results

Under **Followers**, expand **New followers** or **Unfollowed you**; under **Following**, expand **You started following** or **You stopped following**. Each name shows its follow date. You can search the results, sort them by follow date and **Save as CSV**. ZIPs have no account IDs: a single name that disappears and another that appears with the same follow date are shown under **May have changed username**, which is not certain. Source cards show each ZIP's list counts; a **Partial** list had unreadable rows. You can also drop the second ZIP onto the comparison.

![Follower count goes from 3 to 4, with two names only in the newer file and one only in the older file](assets/en/zip-karsilastirma-sonuc.png)

Here the count is **3 → 4**. Two usernames appear only in the newer export and one appears only in the older export. A replacement can appear even if the total stays the same. Matching is based on usernames, so renames or different export settings can change the result. A direction absent from either ZIP cannot be compared. The result shows differences between two snapshots. Their exact event times and changes that were reversed in between are unknown.

For profiles monitored live, use [Follow Lists](EN-Follow-Lists.md) to compare snapshots collected after connecting.
