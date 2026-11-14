# Accounts and sign-in

[User guide](EN-Home.md) · [Troubleshooting](EN-Troubleshooting.md)

## Windows

Open the connection screen and enter your Instagram username and password. If Instagram presents a supported security step, follow the prompt once and wait. A completed Tracker connection shows your profile and counters. The first list preparation can then begin.

## Android

For a new connection, open **Connect with Instagram · Recommended**. If an account is already saved, open the options menu, manage the connection, then choose to connect another account. Approve loading the Instagram verification page if needed, complete the requested supported step yourself, and keep Tracker in the foreground until the profile check returns.

A test on Android 0.1.100 established an Instagram session and the subsequent profile read returned HTTP 429. For the current state of new connections and follow lists, see [Android release status](EN-Releases.md#android).

## Finish a pending connection

When Tracker has saved an Instagram session but still needs a successful profile read, open **Finish connection**. Tracker stores the pending session separately and encrypted on the phone.

- The action on the first dialog opens the continuation screen. The profile-check button on that screen sends a single eligible profile read.
- During a cooldown the button stays disabled. Closing the screen keeps the pending session.
- Deleting the pending connection removes only that unfinished local session. Saved accounts and cooldown history are retained. An expired session may require signing in again.

## Multiple accounts

Use the Windows account selector or Android’s account list. Each connected account has separate encrypted sessions, saved profiles and history. Adding another account does not require deleting an existing one. Device-wide pacing and cooldowns apply across accounts. [Remove an account or its data](EN-Privacy.md#deletion-and-disconnection).

## Confirm account details

If credentials are rejected, check that you can sign in from scratch in Instagram itself. An already signed-in browser does not verify the password. For support, share the app version and a redacted status. Keep passwords, codes and session data private.
