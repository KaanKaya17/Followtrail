package dev.kaankaya.instagramtracker

import android.content.Context

/** Mobile authorization and device identity stay in their own encrypted banks. */
class MobileSessions(context: Context, namespace: String = "mobile") : SessionBanks<MobileSession>(context, namespace, "mobile",
    MobileSessionCodec::decode, MobileSessionCodec::encode, MobileSession::reference, { it.mobileBinding })
