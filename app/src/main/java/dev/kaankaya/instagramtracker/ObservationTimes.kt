package dev.kaankaya.instagramtracker

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date

object ObservationTimes {
    fun format(context: Context, time: Long?): String = time?.let {
        SimpleDateFormat("d MMM yyyy · HH:mm:ss z", AppLanguage.locale(context)).format(Date(it))
    } ?: context.getString(R.string.timeline_unknown)
}
