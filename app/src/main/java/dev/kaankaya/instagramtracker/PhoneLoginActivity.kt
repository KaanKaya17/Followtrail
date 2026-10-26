package dev.kaankaya.instagramtracker

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView

/** First-class phone-only connection; no WebView form extraction, computer, cookie copy or file transfer. */
class PhoneLoginActivity : LocalizedActivity() {
    private lateinit var controller: PhoneLoginController
    private lateinit var gate: RequestGate
    private var phase: PhoneLoginPhase? = null
    private var rendered: PhoneLoginUi? = null
    private var status: android.widget.TextView? = null
    private var contact: EditText? = null
    private var active = false
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val wake = Runnable { rendered = null; render() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); AppUi.systemBars(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        gate = RequestGate(this)
        try {
            gate.setPhoneLoginActive(true); MonitorScheduler.cancel(this)
            @Suppress("DEPRECATION")
            controller = lastNonConfigurationInstance as? PhoneLoginController ?: PhoneLoginController(this)
        } catch (_: Exception) { finish(); return }
        if (android.os.Build.VERSION.SDK_INT >= 33) onBackInvokedDispatcher.registerOnBackInvokedCallback(0, ::close)
        render()
    }
    override fun onRetainNonConfigurationInstance(): Any = controller
    private fun dp(value: Int) = AppUi.dp(this, value)
    private fun render() {
        if (!::controller.isInitialized || isFinishing || isDestroyed) return
        main.removeCallbacks(wake)
        val value = controller.ui
        if (value.phase == PhoneLoginPhase.BUSY) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (value.phase == PhoneLoginPhase.LINKED) {
            android.widget.Toast.makeText(this, R.string.phone_connected, android.widget.Toast.LENGTH_SHORT).show(); close(); return
        }
        if (phase == value.phase && value.phase == PhoneLoginPhase.BUSY) { status?.text = progress(value); return }
        if (rendered == value) return
        val previousContact = contact?.text?.toString().orEmpty()
        rendered = value; phase = value.phase
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(20), dp(24), dp(28)); setBackgroundColor(AppUi.background)
        }
        fun gap(n: Int) { page.addView(View(this), LinearLayout.LayoutParams(1, dp(n))) }
        fun text(id: Int, size: Float = 14f) { page.addView(AppUi.text(this, getString(id), size, AppUi.muted)); gap(12) }
        fun button(id: Int, primary: Boolean = true, enabled: Boolean = true, action: () -> Unit) {
            page.addView(AppUi.button(this, getString(id), primary, enabled, action = action), LinearLayout.LayoutParams(-1, -2)); gap(12)
        }
        fun input(id: Int, password: Boolean = false, code: Boolean = false) = EditText(this).apply {
            hint = getString(id); textSize = 16f; setTextColor(AppUi.ink); setHintTextColor(AppUi.muted)
            isSaveEnabled = false; setSingleLine(true)
            inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                else if (code) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            filters = arrayOf(android.text.InputFilter.LengthFilter(if (password) 1024 else if (code) 32 else 254))
            if (android.os.Build.VERSION.SDK_INT >= 26 && !code) setAutofillHints(if (password) View.AUTOFILL_HINT_PASSWORD else View.AUTOFILL_HINT_USERNAME)
            page.addView(this, LinearLayout.LayoutParams(-1, dp(58))); gap(12)
        }
        page.addView(AppUi.iconButton(this, "back", getString(R.string.list_back), ::close), LinearLayout.LayoutParams(dp(48), dp(48))); gap(20)
        page.addView(AppUi.heading(this, getString(R.string.phone_title), 30f)); gap(12)
        text(R.string.phone_intro, 15f)
        val error = value.failure?.let { ApiFailure(it).userMessage(this) } ?: value.reason?.let { reason -> getString(when (reason) {
            PhoneLoginFailure.Reason.INPUT -> R.string.phone_input_error
            PhoneLoginFailure.Reason.CREDENTIALS -> R.string.phone_credentials_error
            PhoneLoginFailure.Reason.UNSUPPORTED -> R.string.phone_unsupported
            PhoneLoginFailure.Reason.CODE -> R.string.phone_code_error
            PhoneLoginFailure.Reason.EXPIRED -> R.string.phone_expired
        }) }
        if (error != null) { page.addView(AppUi.notice(this, getString(R.string.phone_attention), error, true)); gap(16) }
        if (error != null) value.diagnostic?.let {
            page.addView(AppUi.text(this, getString(R.string.phone_failure_detail, it.operation.code, it.status?.toString() ?: "—"), 12f, AppUi.muted)); gap(12)
        }
        when (value.phase) {
            PhoneLoginPhase.FORM, PhoneLoginPhase.ERROR -> {
                text(R.string.phone_access)
                contact = input(R.string.phone_contact).apply { setText(previousContact) }
                val password = input(R.string.phone_password, password = true)
                val until = gate.nextPrototypeAt()
                if (until > System.currentTimeMillis()) {
                    page.addView(AppUi.text(this, getString(R.string.connection_next_try, ObservationTimes.format(this, until)), 13f, AppUi.coral)); gap(12)
                    main.postDelayed(wake, until - System.currentTimeMillis())
                }
                button(R.string.phone_start, enabled = until <= System.currentTimeMillis()) {
                    val name = contact?.text?.toString()?.trim().orEmpty()
                    val secret = CharArray(password.text.length) { password.text[it] }
                    password.text.clear()
                    controller.start(name, secret)
                }
                text(R.string.phone_password_notice, 12f)
                if (value.phase == PhoneLoginPhase.ERROR) button(R.string.phone_open_instagram, false) {
                    startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.instagram.com/")))
                }
            }
            PhoneLoginPhase.BUSY -> {
                page.addView(android.widget.ProgressBar(this)); gap(16)
                status = AppUi.text(this, progress(value), 16f, AppUi.violet).also(page::addView)
                gap(12); text(R.string.phone_wait)
                button(R.string.ui_cancel, false) { controller.cancelWork() }
            }
            PhoneLoginPhase.METHOD -> {
                text(R.string.phone_method_hint)
                button(R.string.phone_method_totp) { controller.chooseMethod("totp") }
                button(R.string.phone_method_sms, false) { controller.chooseMethod("sms") }
                button(R.string.phone_method_backup, false) { controller.chooseMethod("backup_codes") }
            }
            PhoneLoginPhase.CODE -> {
                text(R.string.phone_code_hint)
                val code = input(R.string.phone_code, code = true)
                button(R.string.phone_verify) {
                    val entered = code.text.toString(); code.text.clear(); controller.submitCode(entered)
                }
                text(R.string.phone_code_notice, 12f)
            }
            PhoneLoginPhase.LINKED -> Unit
        }
        gap(12); text(R.string.phone_session_notice, 12f)
        val scroll = ScrollView(this).apply {
            isFillViewport = true; addView(page)
            setOnApplyWindowInsetsListener { view, insets ->
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                insets
            }
        }
        setContentView(scroll); scroll.requestApplyInsets()
    }
    private fun progress(value: PhoneLoginUi): String {
        val stage = getString(when (value.step) {
            PhoneLoginStep.PREPARING -> R.string.phone_preparing; PhoneLoginStep.SIGNING_IN -> R.string.phone_signing_in
            PhoneLoginStep.VERIFYING -> R.string.phone_verifying; PhoneLoginStep.PROFILE -> R.string.phone_reading_profile
        })
        return if (value.waiting > 0) stage + "\n" + resources.getQuantityString(R.plurals.phone_spacing, value.waiting.toInt(), value.waiting.toInt()) else stage
    }
    override fun onStart() {
        super.onStart(); active = true
        if (::controller.isInitialized) { controller.listener = { if (active) render() }; rendered = null; render() }
    }
    override fun onStop() {
        active = false; main.removeCallbacks(wake)
        if (::controller.isInitialized) { controller.listener = null; if (!isChangingConfigurations) controller.cancelWork() }
        super.onStop()
    }
    private fun close() {
        if (::controller.isInitialized) controller.close()
        try { if (::gate.isInitialized) gate.setPhoneLoginActive(false); finish() }
        catch (_: Exception) { android.widget.Toast.makeText(this, R.string.phone_close_error, android.widget.Toast.LENGTH_LONG).show() }
    }
    @Deprecated("API 24-32 back callback")
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Suppress("DEPRECATION")
    override fun onBackPressed() { close() }
    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        if (!isChangingConfigurations) {
            if (::controller.isInitialized) controller.close()
            try { if (::gate.isInitialized) gate.setPhoneLoginActive(false) } catch (_: Exception) { }
        }
        super.onDestroy()
    }
}
