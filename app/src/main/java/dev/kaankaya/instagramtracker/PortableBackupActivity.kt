package dev.kaankaya.instagramtracker

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

class PortableBackupActivity : LocalizedActivity() {
    private data class PasswordRequest(val document: Uri, val owner: String?)

    private val worker = Executors.newSingleThreadExecutor()
    private var busy = false
    private var pendingExportOwner: String? = null
    private var passwordRequest: PasswordRequest? = null
    private var passwordDialog: AlertDialog? = null
    private var archive: JSONObject? = null
    private lateinit var page: LinearLayout
    private lateinit var status: TextView
    private val sections = listOf("people" to R.string.portable_people, "history" to R.string.portable_history,
        "lists" to R.string.export_browser_follow_lists, "daily" to R.string.portable_daily)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppUi.systemBars(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        render()
        pendingExportOwner = savedInstanceState?.getString("pending_export_owner")
        savedInstanceState?.getString("password_document")?.let { document ->
            askPassword(PasswordRequest(Uri.parse(document), savedInstanceState.getString("password_owner")))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("pending_export_owner", pendingExportOwner)
        passwordRequest?.let { request ->
            outState.putString("password_document", request.document.toString())
            outState.putString("password_owner", request.owner)
        }
    }

    override fun onDestroy() {
        passwordDialog?.dismiss()
        archive = null
        worker.shutdown()
        super.onDestroy()
    }

    private fun render() {
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = AppUi.dp(this@PortableBackupActivity, 20)
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(AppUi.background)
        }
        val container = page
        action(container, getString(R.string.ui_close)) { finish() }
        container.addView(AppUi.heading(this, getString(R.string.portable_title)))
        container.addView(AppUi.text(this, getString(R.string.portable_scope), 14f, AppUi.muted))
        action(container, getString(R.string.portable_open)) { pick(false) }
        AccountCatalog.selected(this)?.let { owner ->
            action(container, getString(R.string.portable_create)) {
                pendingExportOwner = owner
                pick(true)
            }
        }
        status = AppUi.text(this, "", 14f, AppUi.muted).also { container.addView(it) }
        archive?.let { value ->
            val owner = value.getJSONObject("account")
            container.addView(AppUi.heading(this, handle(owner), 20f))
            if (owner.optLong("at") > 0) container.addView(AppUi.text(this, observed(owner.getLong("at")), 14f, AppUi.muted))
            if (metrics(owner).isNotEmpty()) container.addView(AppUi.text(this, metrics(owner), 14f))
            for ((key, label) in sections) {
                val array = value.getJSONArray(key)
                action(container, "${getString(label)} · ${array.length()}") { showRows(key, getString(label)) }
            }
        }
        setContentView(ScrollView(this).apply { addView(container) })
    }

    private fun action(parent: LinearLayout, label: String, block: () -> Unit) {
        parent.addView(AppUi.button(this, label, false) { if (!busy) block() })
    }

    private fun pick(saving: Boolean) {
        val intent = Intent(if (saving) Intent.ACTION_CREATE_DOCUMENT else Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(if (saving) "application/octet-stream" else "*/*")
        if (saving) intent.putExtra(Intent.EXTRA_TITLE, "InstagramTracker.trackerhistory")
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, if (saving) 51 else 52)
        } catch (_: android.content.ActivityNotFoundException) {
            pendingExportOwner = null
            status.setText(R.string.portable_error)
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val document = data?.data?.takeIf { resultCode == Activity.RESULT_OK }
        if (requestCode == 51) {
            val owner = pendingExportOwner
            pendingExportOwner = null
            if (document != null && owner != null) askPassword(PasswordRequest(document, owner))
            else if (document != null) status.setText(R.string.portable_error)
        } else if (requestCode == 52 && document != null) {
            askPassword(PasswordRequest(document, null))
        }
    }

    private fun askPassword(request: PasswordRequest) {
        passwordRequest = request
        val saving = request.owner != null
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = AppUi.dp(this@PortableBackupActivity, 18)
            setPadding(padding, 0, padding, 0)
        }
        fun field(label: Int) = EditText(this).apply {
            setHint(label)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSaveEnabled = false
            if (Build.VERSION.SDK_INT >= 26) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            filters = arrayOf(android.text.InputFilter.LengthFilter(128))
            setSingleLine()
            fields.addView(this)
        }
        val first = field(R.string.portable_password)
        val repeat = if (saving) field(R.string.portable_confirm) else null
        val prompt = AlertDialog.Builder(this).setTitle(if (saving) R.string.portable_create else R.string.portable_open)
            .setView(fields).setNegativeButton(R.string.ui_close, null)
            .setPositiveButton(android.R.string.ok, null).create()
        passwordDialog = prompt
        prompt.setOnDismissListener {
            if (passwordDialog === prompt) passwordDialog = null
            if (passwordRequest === request) passwordRequest = null
            first.text.clear()
            repeat?.text?.clear()
        }
        prompt.show()
        prompt.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val password = first.text.toString()
            if (password.isEmpty() || saving &&
                (password.codePointCount(0, password.length) < 12 || password != repeat?.text.toString())) {
                first.error = getString(if (saving) R.string.portable_password_rules else R.string.portable_password)
                return@setOnClickListener
            }
            passwordRequest = null
            prompt.dismiss()
            val owner = request.owner
            if (owner != null) {
                save(request.document, owner, password)
            } else {
                work({
                    requireNotNull(contentResolver.openInputStream(request.document)).use { PortableBackup.open(it, password) }
                }) {
                    archive = it
                    render()
                }
            }
        }
    }

    private fun save(document: Uri, owner: String, password: String) {
        work({
            val saved = SessionVault(this).read()?.takeIf { it.profile.userId == owner } ?: error("Account changed")
            val bytes = PortableBackup.seal(PortableBackup.snapshot(this, saved.session), password)
            requireNotNull(contentResolver.openOutputStream(document, "wt")).use { it.write(bytes) }
        }) { status.setText(R.string.portable_saved) }
    }

    private fun <T> work(task: () -> T, done: (T) -> Unit) {
        busy = true
        status.setText(R.string.export_browser_loading)
        worker.execute {
            val result = runCatching(task)
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                busy = false
                status.text = ""
                result.fold(done) { status.setText(R.string.portable_error) }
            }
        }
    }

    private fun observed(at: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT,
        resources.configuration.locales[0]).format(Date(at))

    private fun handle(profile: JSONObject): String = if (profile.has("username")) "@${profile.getString("username")}" else profile.getString("id")

    private fun metrics(row: JSONObject): String = listOf("followers" to R.string.list_followers,
        "following" to R.string.list_following, "posts" to R.string.portable_posts).mapNotNull { (key, label) ->
        if (row.has(key)) "${getString(label)} ${row.optJSONArray(key)?.getLong(1) ?: row.getLong(key)}" else null
    }.joinToString(" · ")

    private fun showRows(section: String, title: String, index: Int? = null, offset: Int = 0) {
        val value = archive ?: return
        val sectionRows = value.getJSONArray(section)
        val targets = buildMap {
            val own = value.getJSONObject("account")
            put(own.getString("id"), handle(own))
            val people = value.getJSONArray("people")
            for (i in 0 until people.length()) {
                val profile = people.getJSONObject(i).getJSONObject("profile")
                put(profile.getString("id"), handle(profile))
            }
        }
        val daily = if (index != null && section == "daily") sectionRows.getJSONObject(index).getJSONObject("days") else null
        val days = daily?.keys()?.asSequence()?.sortedDescending()?.toList()
        val rows = if (index != null && section == "lists") sectionRows.getJSONObject(index).getJSONArray("members") else sectionRows
        val count = days?.size ?: rows.length()
        val contents = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = AppUi.dp(this@PortableBackupActivity, 16)
            setPadding(padding, 0, padding, padding)
        }
        val prompt = AlertDialog.Builder(this).setTitle(title).setView(ScrollView(this).apply { addView(contents) })
            .setPositiveButton(R.string.ui_close, null).create()
        fun move(text: String, action: () -> Unit) = action(contents, text) { prompt.dismiss(); action() }
        if (index != null) move(getString(R.string.export_browser_back)) { showRows(section, title) }
        if (count == 0) contents.addView(AppUi.text(this, getString(R.string.export_browser_empty), 14f))
        val end = minOf(count, offset + 30)
        for (position in offset until end) {
            val row = if (days == null) rows.getJSONObject(position) else daily!!.getJSONObject(days[position])
            val card = AppUi.card(this)
            contents.addView(card)
            if (index == null && (section == "daily" || section == "lists")) {
                val target = row.getString("target")
                val label = if (section == "daily") row.getInt("year").toString()
                    else getString(if (row.getString("direction") == "followers") R.string.list_followers else R.string.list_following)
                card.addView(AppUi.heading(this, "${targets[target] ?: target} · $label", 16f))
                if (row.has("at")) card.addView(AppUi.text(this, observed(row.getLong("at")), 13f, AppUi.muted))
                card.addView(AppUi.button(this, getString(R.string.portable_view), false) {
                    prompt.dismiss()
                    showRows(section, title, position)
                })
            } else if (section == "daily") {
                val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                card.addView(AppUi.heading(this, date.format(Date(row.getLong("day"))), 16f))
                card.addView(AppUi.text(this, metrics(row), 14f))
            } else {
                val profile = row.optJSONObject("profile") ?: row
                card.addView(AppUi.heading(this, (if (row.optBoolean("favorite")) "★ " else "") + handle(profile), 16f))
                val details = listOf(profile.optString("name"), profile.optLong("at").takeIf { it > 0 }?.let(::observed),
                    metrics(profile), profile.optString("bio"), row.optString("group"), row.optString("note"))
                details.filterNotNull().filter { it.isNotEmpty() }.forEach { card.addView(AppUi.text(this, it, 14f)) }
            }
        }
        if (offset > 0) move(getString(R.string.instagram_archive_previous)) { showRows(section, title, index, maxOf(0, offset - 30)) }
        if (end < count) move(getString(R.string.instagram_archive_next)) { showRows(section, title, index, end) }
        prompt.show()
    }
}
