package dev.kaankaya.instagramtracker

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

internal object ProfileAboutView {
    fun button(activity: Activity, username: String, value: ProfileAbout?, save: ((ProfileAbout?) -> Boolean)? = null) =
        AppUi.button(activity, activity.getString(R.string.profile_about_title), false) {
            open(activity, username, value, save)
        }

    private fun open(activity: Activity, username: String, value: ProfileAbout?, save: ((ProfileAbout?) -> Boolean)?) {
        val padding = AppUi.dp(activity, 20)
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, AppUi.dp(activity, 8), padding, 0)
        }
        val number = java.text.NumberFormat.getIntegerInstance(AppLanguage.locale(activity))
        val facts = listOf(
            R.string.profile_about_joined to value?.monthLabel(AppLanguage.locale(activity)),
            R.string.profile_about_country to value?.country,
            R.string.profile_about_changes to value?.usernameChanges?.let(number::format),
        )
        facts.forEach { (label, text) ->
            if (text != null) box.addView(AppUi.text(activity, activity.getString(label) + " · " + text, 14f))
        }
        if (value != null) box.addView(AppUi.text(activity, activity.getString(R.string.profile_about_source), 12f, AppUi.muted))
        box.addView(AppUi.text(activity, activity.getString(R.string.profile_about_instructions), 13f, AppUi.muted))
        ProfileLinks.url(username)?.let { url ->
            box.addView(AppUi.button(activity, activity.getString(R.string.profile_about_open), false) {
                try { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                catch (_: android.content.ActivityNotFoundException) {
                    Toast.makeText(activity, R.string.profile_link_unavailable, Toast.LENGTH_SHORT).show()
                }
            })
        }
        val dialog = AlertDialog.Builder(activity).setTitle(R.string.profile_about_title)
            .setView(ScrollView(activity).apply { addView(box) }).setNegativeButton(R.string.ui_close, null)
        if (save == null) { dialog.show(); return }

        fun input(label: Int, initial: String?, length: Int, type: Int): EditText {
            val caption = AppUi.text(activity, activity.getString(label), 13f, AppUi.muted)
            val field = EditText(activity).apply {
                id = android.view.View.generateViewId()
                inputType = type
                setSingleLine(true)
                filters = arrayOf(InputFilter.LengthFilter(length))
                setText(initial)
            }
            caption.labelFor = field.id
            box.addView(caption)
            box.addView(field)
            return field
        }
        val joined = input(R.string.profile_about_joined_input, value?.joined, 7, InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_DATE)
        joined.keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789-")
        val country = input(R.string.profile_about_country, value?.country, 80, InputType.TYPE_CLASS_TEXT)
        val changes = input(R.string.profile_about_changes, value?.usernameChanges?.toString(), 7, InputType.TYPE_CLASS_NUMBER)
        box.addView(AppUi.text(activity, activity.getString(R.string.profile_about_clear), 12f, AppUi.muted))
        val feedback = AppUi.text(activity, "", 13f, AppUi.coral)
        box.addView(feedback)
        val shown = dialog.setPositiveButton(R.string.ui_save, null).show()
        shown.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val month = joined.text.toString().trim().ifEmpty { null }
            val place = country.text.toString().trim().ifEmpty { null }
            val amount = changes.text.toString()
            val result = runCatching {
                val current = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.ROOT).format(java.util.Date())
                require(month == null || month <= current)
                val count = amount.takeIf { it.isNotEmpty() }?.toInt()
                if (month == null && place == null && count == null) null else ProfileAbout(month, place, count)
            }
            if (result.isFailure) feedback.setText(R.string.profile_about_invalid)
            else if (save(result.getOrNull())) shown.dismiss()
            else feedback.setText(R.string.profile_about_failed)
        }
    }
}
