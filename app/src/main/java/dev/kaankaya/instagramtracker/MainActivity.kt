package dev.kaankaya.instagramtracker

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.EditText
import android.text.InputFilter
import java.io.ByteArrayInputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private data class ProfileRead(val profile: Profile, val fetched: Boolean)
    private val bg = AppUi.background
    private val panel = AppUi.surface
    private val accent = AppUi.violet
    private val ink = AppUi.ink
    private val muted = AppUi.muted
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var api: InstagramApi
    private lateinit var gate: RequestGate
    private lateinit var vault: SessionVault
    private lateinit var root: FrameLayout
    private var account: SavedAccount? = null
    private var loading = true
    private var message: String? = null
    private var needsConnection = false
    private var web: WebView? = null
    private var loginStatus: TextView? = null
    private var completionButton: Button? = null
    private var generation = 0
    private var loginChecking = false
    private var loginAttempted = false
    private var loginLoaded = false
    private var peopleScreen = false
    private var selectedPersonId: String? = null
    private var rangeDays = 0
    private var chartFollowers = true
    private var preview = false
    private var pendingMobile = false
    private var pendingWatchId: String? = null
    private var pendingWatchOwner: String? = null
    private var pendingWatchHours = 1
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppUi.systemBars(this)
        if (!BuildConfig.DEBUG) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        root = FrameLayout(this).apply { setBackgroundColor(bg) }
        root.setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION")
            view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        setContentView(root)
        root.requestApplyInsets()
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                navigateBack()
            }
        }
        preview = PreviewMode.enabled(this)
        peopleScreen = savedInstanceState?.getBoolean("people") ?: false
        selectedPersonId = savedInstanceState?.getString("person")
        rangeDays = savedInstanceState?.getInt("range") ?: 0
        pendingWatchId = savedInstanceState?.getString("pendingWatch")
        pendingWatchOwner = savedInstanceState?.getString("pendingOwner")
        pendingWatchHours = savedInstanceState?.getInt("pendingHours", 1) ?: 1
        vault = SessionVault(this, if (preview) "previewaccount" else "account")
        gate = RequestGate(this, if (preview) "preview_gate" else "request_gate")
        api = PreviewMode.api(gate, preview)
        if (BuildConfig.DEBUG && intent.getBooleanExtra("connection_lab_preview", false)) {
            intent.removeExtra("connection_lab_preview")
            startActivity(Intent(this, ConnectionLabActivity::class.java).putExtra("offline_lab", true))
        }
        render()
        worker.execute {
            val saved = try { vault.read() ?: PreviewMode.seed(preview)?.also { vault.write(it) } } catch (_: Exception) {
                main.post { message = ApiFailure(ApiFailure.Kind.STORAGE).userMessage(); needsConnection = true }
                null
            }
            val pendingConnection = try { !preview && MobileSessions(this).pending() != null } catch (_: Exception) { false }
            main.post {
                if (isDestroyed) return@post
                account = saved; pendingMobile = pendingConnection; loading = false; needsConnection = needsConnection || gate.requiresConnection()
                openNotification(saved); syncMonitoring(); render()
                // An outstanding pause is restored without issuing a network request.
                if (saved != null && !peopleScreen && gate.autoAllowed(saved.profile)) refresh(automatic = true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::gate.isInitialized || web != null || loading) return
        try { account = vault.read(); pendingMobile = !preview && MobileSessions(this).pending() != null; needsConnection = gate.requiresConnection() }
        catch (_: Exception) { message = ApiFailure(ApiFailure.Kind.STORAGE).userMessage(); needsConnection = true }
        syncMonitoring()
        if (account != null && !peopleScreen && !needsConnection && gate.autoAllowed(account!!.profile)) refresh(automatic = true)
        else render()
    }

    private fun shape(color: Int, radius: Int = 24, stroke: Int? = null) = AppUi.shape(this, color, radius, stroke)
    private fun text(value: String, size: Float, color: Int = ink, medium: Boolean = false) = AppUi.text(this, value, size, color, medium)
    private fun stack() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun LinearLayout.gap(value: Int) { addView(View(this@MainActivity), LinearLayout.LayoutParams(1, dp(value))) }
    private fun button(label: String, primary: Boolean = true, networkAction: Boolean = false, action: () -> Unit) =
        AppUi.button(this, label, primary, !loading && (!networkAction || gate.nextAllowedAt() <= System.currentTimeMillis()),
            when (label) { "Yenile", "Kişiyi yenile" -> "refresh"; "Kişi ekle" -> "plus"; "Listeleri incele" -> "list"; else -> null }, action)
    private fun badge(label: String, color: Int = accent) = text(label, 10f, color, true).apply {
        letterSpacing = .06f; setPadding(dp(10), dp(7), dp(10), dp(7)); background = shape(AppUi.violetSoft, 12)
    }
    private var visibleScroll: ScrollView? = null
    private var screenIdentity = ""
    private fun render() {
        if (web != null || isDestroyed) return
        val identity = if (peopleScreen) "people:$selectedPersonId" else "overview"
        val position = if (identity == screenIdentity) visibleScroll?.scrollY ?: 0 else 0
        screenIdentity = identity
        root.removeAllViews()
        val saved = account
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false; isVerticalScrollBarEnabled = false }
        visibleScroll = scroll
        val page = stack().apply { setPadding(dp(22), dp(16), dp(22), dp(if (saved != null) 104 else 32)) }
        scroll.addView(page, ViewGroup.LayoutParams(-1, -2)); root.addView(scroll)
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(text("", 20f).apply {
            background = AppUi.gradient(this@MainActivity, radius = 16); gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setCompoundDrawablesWithIntrinsicBounds(AppUi.Icon(this@MainActivity, "chart", AppUi.surface, 24), null, null, null)
            contentDescription = "InstagramTracker"
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        val brand = stack().apply { setPadding(dp(12), 0, 0, 0) }
        brand.addView(text("Tracker", 21f, ink, true)); brand.gap(3); brand.addView(text("Sosyal çevren, bir arada.", 11f, muted))
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(AppUi.iconButton(this, "more", "Seçenekler") { options() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        page.addView(header); page.gap(24)
        if (preview) { page.addView(badge("DEMO · ÖRNEK VERİ").apply { contentDescription = "Çevrimdışı demo. Veriler örnektir; Instagram isteği yok." }); page.gap(16) }
        val pause = pauseMessage()
        if (pause != null) {
            val notice = stack().apply { background = shape(AppUi.peach, 20); setPadding(dp(18), dp(16), dp(18), dp(16)) }
            notice.addView(text(if (gate.blockedUntil() > System.currentTimeMillis()) "Biraz ara verelim" else "Yalnız sen yenilediğinde", 16f, AppUi.coral, true))
            notice.gap(6); notice.addView(text(pause, 13f, ink))
            notice.contentDescription = "Sınırlama bilgisi"; notice.setOnClickListener { requestInfo() }
            page.addView(notice); page.gap(16)
        }
        if (saved == null) {
            page.addView(text("Hesabın,\nbir bakışta.", 38f, ink, true)); page.gap(12)
            page.addView(text("Takipçilerin ve takip ettiklerin. Tek yerde.", 16f, muted))
            page.addView(OrbitView(this), LinearLayout.LayoutParams(-1, dp(204)))
        } else if (peopleScreen) renderPeople(page, saved)
        else renderOverview(page, saved)
        if (loading) {
            val progress = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            progress.addView(ProgressBar(this).apply { indeterminateTintList = android.content.res.ColorStateList.valueOf(accent) }, LinearLayout.LayoutParams(dp(20), dp(20)))
            progress.addView(text("  Güncelleniyor…", 14f, muted)); page.addView(progress); page.gap(16)
        }
        message?.takeIf { it != pause }?.let {
            page.addView(text(it, 14f, accent).apply { background = shape(AppUi.violetSoft, 16); setPadding(dp(16), dp(14), dp(16), dp(14)) }); page.gap(14)
        }
        if (saved == null || needsConnection) {
            page.addView(button(if (needsConnection) "Bağlantıyı yenile" else if (pendingMobile) "Bağlantıyı tamamla" else "Hesap bağla") {
                if (pendingMobile && !needsConnection) startActivity(Intent(this, DesktopLinkActivity::class.java)) else connectionOptions()
            }, LinearLayout.LayoutParams(-1, -2))
        } else if (!peopleScreen) {
            val allowed = maxOf(gate.nextAllowedAt(), saved.profile.observedAt + PeoplePolicy.FRESH_MS)
            val refreshRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            val detail = stack()
            detail.addView(text("Son kayıt", 11f, muted)); detail.gap(4); detail.addView(text(date(saved.profile.observedAt), 12f, ink, true))
            refreshRow.addView(detail, LinearLayout.LayoutParams(0, -2, 1f))
            refreshRow.addView(button("Yenile", false, networkAction = true) { refresh() }.apply {
                isEnabled = !loading && allowed <= System.currentTimeMillis(); alpha = if (isEnabled) 1f else .48f
            }, LinearLayout.LayoutParams(-2, -2))
            page.addView(refreshRow)
            if (allowed > System.currentTimeMillis() && pause == null) {
                page.gap(8); page.addView(text("Yeniden yenileme · ${clock(allowed)}", 11f, muted))
            }
        }
        if (gate.mobileLinkActive() || gate.prototypeActive()) {
            page.gap(16); page.addView(button("Yarım kalan bağlantıyı tamamla", false) {
                if (gate.mobileLinkActive()) startActivity(Intent(this, DesktopLinkActivity::class.java)) else openLab()
            }, LinearLayout.LayoutParams(-1, -2))
        }
        if (saved == null) { page.gap(20); page.addView(text("Verilerin bu cihazda şifrelenir.", 12f, muted).apply { gravity = Gravity.CENTER; setOnClickListener { privacy() } }) }
        if (saved != null) {
            val navigation = LinearLayout(this).apply {
                background = shape(panel, 28, AppUi.line); setPadding(dp(8), dp(7), dp(8), dp(7)); elevation = dp(6).toFloat()
            }
            fun tab(label: String, icon: String, selected: Boolean, action: () -> Unit) = button(label, false, action = action).apply {
                background = AppUi.touch(this@MainActivity, shape(if (selected) AppUi.violetSoft else panel, 21))
                setTextColor(if (selected) accent else muted); isSelected = selected; textSize = 13f
                setCompoundDrawablesRelativeWithIntrinsicBounds(AppUi.Icon(this@MainActivity, icon, if (selected) accent else muted, 20), null, null, null)
                compoundDrawablePadding = dp(8)
            }
            navigation.addView(tab("Özet", "home", !peopleScreen) { peopleScreen = false; selectedPersonId = null; message = null; render() }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(6) })
            navigation.addView(tab("Kişiler", "people", peopleScreen) { peopleScreen = true; selectedPersonId = null; message = null; render() }, LinearLayout.LayoutParams(0, dp(52), 1f))
            root.addView(navigation, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
                leftMargin = dp(22); rightMargin = dp(22); bottomMargin = dp(12)
            })
        }
        scroll.post { scroll.scrollTo(0, position) }; scheduleGateWake()
    }

    private fun renderOverview(page: LinearLayout, saved: SavedAccount) {
        page.addView(text("Bir bakışta", 30f, ink, true)); page.gap(10)
        val identity = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        identity.addView(AppUi.avatar(this, saved.profile.username, 28))
        identity.addView(text("@${saved.profile.username}", 15f, muted).apply { setPadding(dp(10), 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(identity); page.gap(18)
        val row = LinearLayout(this)
        fun stat(label: String, value: Long, direction: FollowDirection, purple: Boolean) = stack().apply {
            val foreground = if (purple) AppUi.surface else ink
            background = AppUi.touch(this@MainActivity, if (purple) AppUi.gradient(this@MainActivity) else shape(AppUi.peach, 26))
            setPadding(dp(17), dp(18), dp(17), dp(18)); minimumHeight = dp(156)
            addView(text(label, 13f, foreground, true)); gap(16)
            addView(text(number(value), 38f, foreground, true).apply {
                maxLines = 1
                if (android.os.Build.VERSION.SDK_INT >= 26) setAutoSizeTextTypeUniformWithConfiguration(16, 38, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            }); gap(10)
            addView(text("Listeyi aç", 11f, foreground).apply {
                setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, AppUi.Icon(this@MainActivity, "arrow", foreground, 16), null)
            })
            contentDescription = "$label: ${number(value)}. Listeyi aç"; isFocusable = true
            setOnClickListener { openLists(saved.profile, direction) }
        }
        row.addView(stat("Takipçi", saved.profile.followers, FollowDirection.FOLLOWERS, true), LinearLayout.LayoutParams(0, -1, 1f).apply { marginEnd = dp(12) })
        row.addView(stat("Takip edilen", saved.profile.following, FollowDirection.FOLLOWING, false), LinearLayout.LayoutParams(0, -1, 1f))
        page.addView(row, LinearLayout.LayoutParams(-1, -2)); page.gap(24)
        val quick = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; background = AppUi.touch(this@MainActivity, shape(panel, 22, AppUi.line))
            setPadding(dp(16), dp(16), dp(16), dp(16)); isFocusable = true
            setOnClickListener { peopleScreen = true; selectedPersonId = null; message = null; render() }
            contentDescription = "Kişiler. ${saved.people.size} kayıtlı profil. Kişilere git"
        }
        quick.addView(AppUi.avatar(this, "K", 42, AppUi.mint, AppUi.teal))
        val quickText = stack().apply { setPadding(dp(12), 0, dp(8), 0) }
        quickText.addView(text("Çevrene göz at", 16f, ink, true)); quickText.gap(5)
        quickText.addView(text(if (saved.people.isEmpty()) "İlk kişini ekle, birlikte inceleyelim." else "${saved.people.size} kayıtlı profil", 12f, muted))
        quick.addView(quickText, LinearLayout.LayoutParams(0, -2, 1f))
        quick.addView(text("", 12f).apply { setCompoundDrawablesWithIntrinsicBounds(AppUi.Icon(this@MainActivity, "chevron", muted, 18), null, null, null) })
        page.addView(quick); page.gap(20)
    }

    private fun clock(time: Long) = SimpleDateFormat("HH:mm", Locale.forLanguageTag("tr-TR")).format(Date(time))
    private fun connectionOptions(replacePending: Boolean = false) {
        if (preview) { android.widget.Toast.makeText(this, "Demo modunda hesap bağlantısı yok.", android.widget.Toast.LENGTH_SHORT).show(); return }
        val labels = arrayOf("Bilgisayardan bağlan · Mobil oturum", "Instagram ile giriş · Web deneyi")
        AlertDialog.Builder(this).setTitle("Hesabını bağla").setItems(labels) { _, index ->
            if (replacePending) {
                try { MobileSessions(this).discardPending(); DesktopLink(this).clear(); pendingMobile = false }
                catch (_: Exception) { showFailure(ApiFailure(ApiFailure.Kind.STORAGE)); render(); return@setItems }
            }
            if (index == 0) startActivity(Intent(this, DesktopLinkActivity::class.java)) else startLogin()
        }.setNegativeButton("Vazgeç", null).show()
    }
    private fun options() {
        val labels = mutableListOf("Bağlantıyı yönet", "Yenileme ayarları", "İstek bilgileri", "Gizlilik", "Gelişmiş")
        if (account != null) labels.add("Kendi listelerim")
        if (BuildConfig.DEBUG && !preview) labels.add("Çevrimdışı demoyu aç")
        AlertDialog.Builder(this).setTitle("Seçenekler").setItems(labels.toTypedArray()) { _, index ->
            when (labels[index]) {
                "Bağlantıyı yönet" -> {
                    val exists = account != null || vault.exists() || (!preview && MobileSessions(this).exists())
                    val actions = if (exists) arrayOf("Başka hesap bağla", "Bağlantıyı kaldır") else arrayOf("Hesap bağla")
                    AlertDialog.Builder(this).setTitle("Bağlantı").setItems(actions) { _, item ->
                        if (item == 0) connectionOptions(replacePending = true) else confirmRemove()
                    }.setNegativeButton("Kapat", null).show()
                }
                "Yenileme ayarları" -> AlertDialog.Builder(this).setTitle("Yenileme ayarları")
                    .setMultiChoiceItems(arrayOf("Açılışta otomatik yenile"), booleanArrayOf(gate.ownAutoRefresh())) { _, _, enabled ->
                        try { gate.setOwnAutoRefresh(enabled) } catch (_: Exception) { showFailure(ApiFailure(ApiFailure.Kind.STORAGE)) }
                    }
                    .setPositiveButton("Tamam", null).show()
                "İstek bilgileri" -> requestInfo()
                "Gizlilik" -> privacy()
                "Gelişmiş" -> AlertDialog.Builder(this).setTitle("Gelişmiş").setItems(arrayOf("Web bağlantı deneyi")) { _, _ -> openLab() }.setNegativeButton("Kapat", null).show()
                "Kendi listelerim" -> account?.profile?.let(::openLists)
                "Çevrimdışı demoyu aç" -> startActivity(Intent(this, MainActivity::class.java).putExtra("offline_preview", true))
            }
        }.setNegativeButton("Kapat", null).show()
    }
    private fun requestInfo() {
        val now = System.currentTimeMillis()
        val lines = mutableListOf("Başlatılan okuma denemesi: ${number(gate.attempts())}",
            "Son HTTP yanıtı: ${gate.lastHttpStatus()?.toString() ?: "Henüz yanıt yok"}")
        if (gate.lastAttemptAt() > 0) lines += "Son deneme: ${date(gate.lastAttemptAt())}"
        if (gate.restrictionPending()) {
            lines += "Sinyal: ${gate.limitReason()}"
            lines += "Son sınırlama: ${date(gate.lastLimitedAt())}"
            lines += "Sunucu bekleme bilgisi: ${gate.serverRetryAt()?.let { date(it) } ?: "Belirtilmedi"}"
            lines += if (gate.blockedUntil() > now) "Uygulamanın yerel molası: ${date(gate.blockedUntil())}" else "Yerel mola doldu."
            lines += "Bu saat, Instagram erişiminin açılacağını garanti etmez. Süre dolunca otomatik deneme yapılmaz."
        }
        lines += "Sayaç profil ve liste sayfası okuma girişimlerini kapsar. Giriş sayfasının ve Windows uygulamasının kendi trafiği dahil değildir. Instagram'ın toplam sayacı değildir; sunucunun isteği aldığına dair kanıt da değildir. Parola, çerez ve ham yanıt kaydedilmez."
        AlertDialog.Builder(this).setTitle("İstek bilgileri").setMessage(lines.joinToString("\n\n")).setPositiveButton("Tamam", null).show()
    }
    private fun openLab() {
        try {
            if (!preview) { gate.setPrototypeActive(true); syncMonitoring() }
            startActivity(Intent(this, ConnectionLabActivity::class.java).putExtra("offline_lab", preview))
        } catch (_: Exception) { showFailure(ApiFailure(ApiFailure.Kind.STORAGE)); render() }
    }

    private fun date(time: Long) = SimpleDateFormat("d MMM · HH:mm", Locale.forLanguageTag("tr-TR")).format(Date(time))
    private fun number(value: Long) = NumberFormat.getIntegerInstance(Locale.forLanguageTag("tr-TR")).format(value)
    private fun delta(value: Long) = (if (value > 0) "+" else "") + number(value)
    private fun openLists(profile: Profile, direction: FollowDirection = FollowDirection.FOLLOWERS) {
        startActivity(Intent(this, FollowListActivity::class.java).putExtra("target_id", profile.userId).putExtra("initial_direction", direction.name).putExtra("offline_preview", preview))
    }

    private fun renderPeople(page: LinearLayout, saved: SavedAccount) {
        val person = saved.people.firstOrNull { it.latest.userId == selectedPersonId }
        if (person == null) {
            selectedPersonId = null
            page.addView(text("Seçtiğin kişiler", 30f, ink, true)); page.gap(8)
            page.addView(text("Merak ettiğin profiller, sana özel bir alan.", 14f, muted)); page.gap(20)
            val actions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            actions.addView(text("${saved.people.size} kişi", 13f, muted), LinearLayout.LayoutParams(0, -2, 1f))
            actions.addView(button("Kişi ekle", networkAction = true) { addPerson() }, LinearLayout.LayoutParams(-2, -2))
            page.addView(actions); page.gap(20)
            if (saved.people.isEmpty()) {
                val empty = stack().apply { background = shape(panel, 24, AppUi.line); setPadding(dp(24), dp(28), dp(24), dp(28)) }
                empty.addView(AppUi.avatar(this, "+", 56, AppUi.mint, AppUi.teal)); empty.gap(18)
                empty.addView(text("İlk kişini eklemeye hazırsın.", 20f, ink, true)); empty.gap(8)
                empty.addView(text("Bir profil ekle; listelerine ve zaman içindeki değişimlerine buradan ulaş.", 14f, muted))
                page.addView(empty)
            }
            for ((index, entry) in saved.people.withIndex()) {
                val p = entry.latest
                val card = stack().apply { background = shape(panel, 24, AppUi.line); setPadding(dp(18), dp(18), dp(18), dp(16)) }
                val identity = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
                identity.addView(AppUi.avatar(this, p.username, 46, if (index % 2 == 0) AppUi.violetSoft else AppUi.peach, if (index % 2 == 0) accent else AppUi.coral))
                val name = stack().apply { setPadding(dp(12), 0, 0, 0) }
                name.addView(text("@${p.username}", 17f, ink, true).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
                name.gap(5); name.addView(text(entry.group.ifBlank { "Kayıtlı profil" }, 12f, muted))
                identity.addView(name, LinearLayout.LayoutParams(0, -2, 1f)); card.addView(identity); card.gap(18)
                card.addView(text("${number(p.followers)} takipçi · ${number(p.following)} takip edilen", 14f, ink, true)); card.gap(8)
                card.addView(text("Son kayıt · ${date(p.observedAt)}", 11f, muted))
                if (entry.watching) { card.gap(8); card.addView(text("Değişiklik bildirimi açık", 12f, AppUi.teal)) }
                card.gap(16)
                card.addView(button("Analizi aç · @${p.username}", false) { selectedPersonId = p.userId; rangeDays = 0; message = null; render() }, LinearLayout.LayoutParams(-1, -2))
                page.addView(card); page.gap(14)
            }
            return
        }
        val p = person.latest
        page.addView(button("Tüm kişiler", false) { selectedPersonId = null; message = null; render() }.apply {
            background = null; setPadding(0, dp(8), dp(12), dp(8))
            setCompoundDrawablesRelativeWithIntrinsicBounds(AppUi.Icon(this@MainActivity, "back", accent, 18), null, null, null)
            compoundDrawablePadding = dp(6)
        }); page.gap(12)
        page.addView(text("@${p.username}", 29f, ink, true)); page.gap(8)
        page.addView(badge(person.group.ifBlank { "PROFİL ANALİZİ" })); page.gap(20)
        page.addView(button(getString(R.string.list_open), false) { openLists(p) }); page.gap(12)
        val stats = stack().apply { background = shape(panel, 24, AppUi.line); setPadding(dp(20), dp(20), dp(20), dp(20)) }
        for ((label, value) in listOf("Takipçi" to number(p.followers), "Takip edilen" to number(p.following), "Gönderi" to (p.posts?.let { number(it) } ?: "Bilgi yok"))) {
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(text(label, 14f, muted), LinearLayout.LayoutParams(0, -2, 1f)); row.addView(text(value, 25f, accent, true))
            stats.addView(row); stats.gap(14)
        }
        page.addView(stats); page.gap(12)
        page.addView(text("Son başarılı okuma: ${date(p.observedAt)}", 12f, muted)); page.gap(16)
        if (person.history.size >= 2) {
            val previous = person.history[person.history.lastIndex - 1]
            page.addView(text("Son kayda göre", 12f, muted)); page.gap(6)
            page.addView(text("${delta(p.followers - previous.followers)} takipçi · ${delta(p.following - previous.following)} takip edilen", 17f, ink, true))
            page.gap(6); page.addView(text("${date(previous.observedAt)} → ${date(p.observedAt)}", 12f, muted))
        } else page.addView(text("Henüz karşılaştırma yok.", 14f, muted))
        page.gap(20)
        page.addView(button("Kişiyi yenile", networkAction = true) { readPerson(person = person) }.apply {
            isEnabled = !loading && gate.nextAllowedAt() <= System.currentTimeMillis() && PeoplePolicy.needsRefresh(p, System.currentTimeMillis())
            alpha = if (isEnabled) 1f else .5f
        }, LinearLayout.LayoutParams(-1, -2)); page.gap(24)
        val watch = android.widget.Switch(this).apply {
            setText(R.string.watch_toggle); textSize = 16f; setTextColor(ink)
            isChecked = person.watching; minimumHeight = dp(56)
            setOnClickListener {
                isChecked = person.watching
                if (person.watching) { setWatch(p.userId, saved.session.userId, person.intervalHours, false); render() }
                else watchSettings(person)
            }
        }
        page.addView(watch, LinearLayout.LayoutParams(-1, -2))
        if (person.watching) page.addView(text("${person.intervalHours} saatte bir", 13f, accent).apply {
            minimumHeight = dp(48); gravity = Gravity.CENTER_VERTICAL; setOnClickListener { watchSettings(person) }
        })
        if (person.watching && !ChangeNotifications(this, preview).allowed()) {
            page.addView(text("Bildirim izni kapalı.", 13f, accent))
        }
        page.gap(24)
        page.addView(text("Gözlem geçmişi", 22f, ink, true)); page.gap(10)
        val filters = LinearLayout(this)
        for ((label, days) in listOf("7 gün" to 7, "30 gün" to 30, "Tümü" to 0)) {
            filters.addView(button(label, rangeDays == days) { rangeDays = days; render() }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5) })
        }
        page.addView(filters); page.gap(10)
        val observations = person.history.filter { rangeDays == 0 || it.observedAt >= System.currentTimeMillis() - rangeDays * 86400000L }
        if (observations.isNotEmpty()) {
            val metric = LinearLayout(this)
            metric.addView(button("Takipçi", chartFollowers) { chartFollowers = true; render() }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
            metric.addView(button("Takip edilen", !chartFollowers) { chartFollowers = false; render() }, LinearLayout.LayoutParams(0, -2, 1f))
            page.addView(metric); page.gap(12)
            if (observations.size >= 2) {
                page.addView(HistoryChartView(this, observations, chartFollowers, accent, muted).apply {
                    background = shape(panel, 20, AppUi.line); setPadding(dp(12), dp(8), dp(12), dp(8))
                }, LinearLayout.LayoutParams(-1, dp(224)))
                val first = observations.first(); val last = observations.last()
                page.addView(text("Dönem net farkı: ${delta(last.followers - first.followers)} takipçi, ${delta(last.following - first.following)} takip edilen", 13f, accent)); page.gap(10)
            }
            page.addView(button("Kayıtları gör", false) {
                val records = observations.takeLast(10).asReversed().joinToString("\n\n") {
                    "${date(it.observedAt)}\n${number(it.followers)} takipçi · ${number(it.following)} takip edilen"
                }
                AlertDialog.Builder(this).setTitle("Son kayıtlar").setMessage(records).setPositiveButton("Kapat", null).show()
            })
        } else page.addView(text("Bu tarih aralığında kayıt yok. Tümü ile önceki gözlemleri görebilirsin.", 14f, muted))
        page.gap(12)
        page.addView(text("Özel notun", 18f, ink, true)); page.gap(8)
        if (person.note.isNotBlank()) { page.addView(text(person.note, 14f, muted)); page.gap(10) }
        page.addView(button("Not ve grup düzenle", false) { editPerson(person) }, LinearLayout.LayoutParams(-1, -2)); page.gap(10)
        page.addView(button("Kişiyi kaldır", false) {
            AlertDialog.Builder(this).setTitle("Kişi kaldırılsın mı?").setMessage("Bu kişinin kayıtlı geçmişi ve notu bu cihazdan silinir.")
                .setNegativeButton("Vazgeç", null).setPositiveButton("Kaldır") { _, _ ->
                    if (savePeople { people -> people.filterNot { it.latest.userId == p.userId } }) {
                        try { FollowListVault(this, if (preview) "previewlists" else "followlists").clearTarget(saved.session, p.userId) }
                        catch (_: Exception) { showFailure(ApiFailure(ApiFailure.Kind.STORAGE)) }
                        ChangeNotifications(this, preview).cancel(saved.session.userId, p.userId); selectedPersonId = null
                    }
                    render()
                }.show()
        }, LinearLayout.LayoutParams(-1, -2)); page.gap(20)
    }

    private fun input(hintValue: String, limit: Int) = EditText(this).apply {
        hint = hintValue; setTextColor(ink); setHintTextColor(muted); textSize = 16f
        background = shape(panel, 14, AppUi.line); setPadding(dp(14), dp(14), dp(14), dp(14))
        minimumHeight = dp(52)
        filters = arrayOf(InputFilter.LengthFilter(limit))
        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        if (android.os.Build.VERSION.SDK_INT >= 26) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
    }
    private fun addPerson() {
        val saved = account ?: return
        if (saved.people.size >= PeoplePolicy.MAX_PEOPLE) { message = "Bu sürümde en fazla ${PeoplePolicy.MAX_PEOPLE} kişi saklanır. Önce bir kişiyi kaldırabilirsin."; render(); return }
        val field = input("@kullanıcıadı veya profil bağlantısı", 256).apply { setSingleLine(true) }
        val box = stack().apply { setPadding(dp(24), dp(10), dp(24), 0); addView(field) }
        val dialog = AlertDialog.Builder(this).setTitle("Kişi ekle")
            .setMessage("Profil bir kez sorgulanır. Sonraki yenilemeler senin kontrolünde.")
            .setView(box).setNegativeButton("Vazgeç", null).setPositiveButton("Profili getir", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val username = ProfileInput.username(field.text.toString())
                if (username == null) { field.error = "Geçerli bir kullanıcı adı veya Instagram profil bağlantısı gir."; return@setOnClickListener }
                val existing = saved.people.firstOrNull { it.latest.username.equals(username, ignoreCase = true) }
                dialog.dismiss()
                if (existing != null) { selectedPersonId = existing.latest.userId; render() }
                else readPerson(username = username)
            }
        }
        dialog.show()
    }
    private fun readPerson(username: String? = null, person: TrackedPerson? = null) {
        val saved = account ?: return
        if (loading) return
        if (needsConnection) { message = "Önce hesabını yeniden bağla."; render(); return }
        if (person != null && !PeoplePolicy.needsRefresh(person.latest, System.currentTimeMillis())) return
        if (gate.nextAllowedAt() > System.currentTimeMillis()) { message = ApiFailure(ApiFailure.Kind.TOO_SOON).userMessage(); render(); return }
        loading = true; message = null; render()
        val ticket = ++generation
        worker.execute {
            val result = try {
                val latest = vault.read()?.takeIf { it.session == saved.session } ?: throw ApiFailure(ApiFailure.Kind.CANCELLED)
                val cached = if (person != null) latest.people.firstOrNull { it.latest.userId == person.latest.userId }?.latest
                    ?: throw ApiFailure(ApiFailure.Kind.CANCELLED)
                    else latest.people.firstOrNull { it.latest.username.equals(username, true) }?.latest
                Result.success(if (cached != null && !PeoplePolicy.needsRefresh(cached, System.currentTimeMillis())) ProfileRead(cached, false)
                    else ProfileRead(if (person != null) api.readId(saved.session, person.latest.userId)
                        else api.readUsername(saved.session, requireNotNull(username)), true))
            } catch (failure: ApiFailure) { Result.failure(failure) }
            catch (_: Exception) { Result.failure(ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)) }
            main.post {
                if (isDestroyed || ticket != generation) return@post
                loading = false
                result.onSuccess { read ->
                    try {
                        if (read.fetched) {
                            val committed = Observations.person(vault, saved.session, read.profile, existingRequired = person != null)
                            account = committed.account; syncMonitoring()
                            committed.change?.let { change -> notifyCurrent(saved.session, change) }
                            message = "Yeni gözlem bu cihazda kaydedildi."
                        } else { account = vault.read(); message = null }
                        selectedPersonId = read.profile.userId
                    } catch (_: Exception) { message = "Gözlem kaydedilemedi. Önceki kayıtlar korunuyor." }
                }.onFailure { failure ->
                    val error = failure as ApiFailure
                    if (error.kind in setOf(ApiFailure.Kind.SESSION_EXPIRED, ApiFailure.Kind.VERIFICATION)) showFailure(error)
                    else message = if (error.kind == ApiFailure.Kind.WRONG_ACCOUNT) "Yanıt istenen profille eşleşmedi. Kayıt değiştirilmedi." else error.userMessage()
                }
                render()
            }
        }
    }
    private fun savePeople(update: (List<TrackedPerson>) -> List<TrackedPerson>): Boolean {
        val saved = account ?: return false
        return try { account = vault.update(saved.session) { it.copy(people = update(it.people)) }; syncMonitoring(); true }
        catch (_: Exception) { message = "Kişiler kaydedilemedi. Önceki kayıtlar korunuyor."; false }
    }
    private fun editPerson(person: TrackedPerson) {
        val group = input("Grup (isteğe bağlı)", 40).apply { setSingleLine(true); setText(person.group) }
        val note = input("Yalnız bu cihazda saklanan notun", 500).apply { minLines = 3; setText(person.note); inputType = inputType or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE }
        val box = stack().apply { setPadding(dp(24), dp(12), dp(24), 0); addView(group); gap(12); addView(note) }
        AlertDialog.Builder(this).setTitle("Not ve grup").setView(box).setNegativeButton("Vazgeç", null)
            .setPositiveButton("Kaydet") { _, _ ->
                savePeople { people -> people.map { if (it.latest.userId == person.latest.userId) it.copy(note = note.text.toString(), group = group.text.toString()) else it } }; render()
            }.show()
    }

    private fun syncMonitoring() {
        try {
            if (preview) PreviewMode.sync(this, account, gate) else MonitorScheduler.sync(this, account, gate)
        } catch (_: Exception) { message = "Arka plan işi planlanamadı. İzleme ayarlarını yeniden kontrol et." }
    }
    private fun notifyCurrent(session: Session, change: CountChange) {
        val notices = ChangeNotifications(this, preview)
        Observations.deliver(vault, session, change, notices::allowed, notices::post)
    }
    private fun watchSettings(person: TrackedPerson) {
        var selection = listOf(1, 6, 24).indexOf(person.intervalHours).coerceAtLeast(0)
        AlertDialog.Builder(this).setTitle("Değişiklik bildirimi")
            .setSingleChoiceItems(arrayOf("En az 1 saat arayla", "En az 6 saat arayla", "En az 24 saat arayla"), selection) { _, index -> selection = index }
            .setNegativeButton("Vazgeç", null).setPositiveButton("İzlemeyi aç") { _, _ ->
                val saved = account ?: return@setPositiveButton
                pendingWatchId = person.latest.userId; pendingWatchOwner = saved.session.userId; pendingWatchHours = listOf(1, 6, 24)[selection]
                val notices = ChangeNotifications(this, preview); notices.ensureChannel()
                if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1401)
                } else finishWatchPermission()
            }.show()
    }
    private fun finishWatchPermission() {
        val id = pendingWatchId; val owner = pendingWatchOwner; val hours = pendingWatchHours
        pendingWatchId = null; pendingWatchOwner = null
        if (id == null || owner == null) return
        if (!ChangeNotifications(this, preview).allowed()) {
            message = "Bildirim izni veya kanalı kapalı. Sistem ayarlarından izin verip izlemeyi yeniden açabilirsin."; render(); return
        }
        setWatch(id, owner, hours, true)
    }
    private fun setWatch(id: String, owner: String, hours: Int, enabled: Boolean) {
        if (account?.session?.userId != owner) return
        if (savePeople { people -> people.map { person -> if (person.latest.userId != id) person else person.copy(
                watching = enabled, intervalHours = hours,
                nextCheckAt = if (enabled) maxOf(System.currentTimeMillis(), person.latest.observedAt + hours * 3600000L) else 0)
            } }) {
            message = if (enabled) "İzleme açıldı. Kontroller Android'in planlamasına göre gecikebilir." else "Bu kişide arka plan izleme kapatıldı."
            if (!enabled) ChangeNotifications(this, preview).cancel(owner, id)
        }
        render()
    }
    @Deprecated("Platform permission callback retained without an additional UI dependency")
    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1401) finishWatchPermission()
    }
    private fun openNotification(saved: SavedAccount?) {
        val owner = intent.getStringExtra("notification_owner")
        val person = intent.getStringExtra("notification_person")
        intent.removeExtra("notification_owner"); intent.removeExtra("notification_person")
        if (saved?.session?.userId == owner && saved?.people?.any { it.latest.userId == person } == true) {
            peopleScreen = true; selectedPersonId = person
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        if (!::vault.isInitialized || loading || web != null) return
        try { account = vault.read(); openNotification(account); render() }
        catch (_: Exception) { message = ApiFailure(ApiFailure.Kind.STORAGE).userMessage(); render() }
    }

    private fun pauseMessage(): String? {
        if (!gate.restrictionPending()) return null
        return if (gate.blockedUntil() > System.currentTimeMillis()) "Yerel mola · ${date(gate.blockedUntil())}"
        else "Otomatik deneme kapalı. Hazır olduğunda yenileyebilirsin."
    }
    private val gateWake = Runnable {
        if (!isDestroyed) {
            if (web == null) render()
            else {
                completionButton?.isEnabled = !loginAttempted && !loginChecking && gate.nextPrototypeAt() <= System.currentTimeMillis()
                completionButton?.alpha = if (completionButton?.isEnabled == true) 1f else .5f
                // No automatic retry when a pause expires.
                if (!loginAttempted) loginStatus?.text = pauseMessage() ?: getString(R.string.connection_ready)
            }
        }
    }
    private fun scheduleGateWake() {
        main.removeCallbacks(gateWake)
        val displayed = if (peopleScreen) account?.people?.firstOrNull { it.latest.userId == selectedPersonId }?.latest else account?.profile
        val freshUntil = displayed?.observedAt?.plus(PeoplePolicy.FRESH_MS) ?: 0L
        val next = if (web != null) gate.nextPrototypeAt() else maxOf(gate.nextAllowedAt(), freshUntil)
        val wait = next - System.currentTimeMillis()
        if (wait > 0) main.postDelayed(gateWake, wait)
    }

    private fun refresh(automatic: Boolean = false) {
        val saved = account ?: return
        if (loading) return
        if (!PeoplePolicy.needsRefresh(saved.profile, System.currentTimeMillis())) return
        if (gate.nextAllowedAt() > System.currentTimeMillis()) {
            message = pauseMessage() ?: ApiFailure(ApiFailure.Kind.TOO_SOON).userMessage(); render(); return
        }
        if (automatic) {
            try { if (!gate.reserveAutomatic()) return }
            catch (failure: ApiFailure) { showFailure(failure); render(); return }
        }
        loading = true; message = null; render()
        val ticket = ++generation
        worker.execute {
            val result = try {
                val latest = vault.read()?.takeIf { it.session == saved.session } ?: throw ApiFailure(ApiFailure.Kind.CANCELLED)
                Result.success(if (!PeoplePolicy.needsRefresh(latest.profile, System.currentTimeMillis())) ProfileRead(latest.profile, false)
                    else ProfileRead(api.read(saved.session), true))
            } catch (failure: ApiFailure) { Result.failure(failure) }
            main.post {
                if (isDestroyed || ticket != generation) return@post
                loading = false
                result.onSuccess {
                    try {
                        account = if (it.fetched) vault.update(saved.session) { current -> current.copy(profile = it.profile) } else vault.read()
                        needsConnection = gate.requiresConnection(); syncMonitoring()
                    }
                    catch (_: Exception) { showFailure(ApiFailure(ApiFailure.Kind.STORAGE)) }
                }
                    .onFailure { showFailure(it as ApiFailure) }
                render()
            }
        }
    }

    private fun showFailure(failure: ApiFailure) {
        message = pauseMessage() ?: failure.userMessage()
        needsConnection = failure.kind in setOf(ApiFailure.Kind.SESSION_EXPIRED, ApiFailure.Kind.VERIFICATION,
            ApiFailure.Kind.WRONG_ACCOUNT, ApiFailure.Kind.STORAGE)
        syncMonitoring()
    }

    private fun privacy() {
        AlertDialog.Builder(this).setTitle("Gizlilik, sade haliyle")
            .setMessage("Web bağlantısında giriş Instagram'ın kendi sayfasındadır; uygulama şifre alanını okumaz. Bilgisayardan aktarımda parola istenmez; mevcut mobil oturum bu cihazın anahtarına şifrelenerek ADB'den gelir.\n\n" +
                "Oturum, seçtiğin kişiler, geçmiş ve notların Android Keystore ile şifrelenir. Giriş sırasında WebView kendi geçici tarayıcı verilerini oluşturabilir; bağlantı tamamlanınca veya iptal edilince temizlenir.\n\n" +
                "İstekler Instagram'a gider. Kendi sunucumuz, reklam, analitik veya veri toplama SDK'mız yok. Bulut yedekleme ve cihaz aktarımı kapalıdır.\n\n" +
                "Bu bağımsız bir uygulamadır. Instagram giriş veya doğrulama isteyebilir; kesintisiz çalışma garantisi yoktur.")
            .setPositiveButton("Anladım", null).show()
    }

    private fun confirmRemove() {
        if (loading) return
        AlertDialog.Builder(this).setTitle("Bağlantı kaldırılsın mı?")
            .setMessage("Bu uygulamadaki oturum, kişiler, listeler, geçmiş ve notların silinir.")
            .setNegativeButton("Vazgeç", null).setPositiveButton("Kaldır") { _, _ ->
                loading = true; render()
                worker.execute {
                    val ok = try {
                        if (preview) PreviewMode.cancel(this) else MonitorScheduler.cancel(this)
                        if (!preview) { MobileSessions(this).clear(); DesktopLink(this).clear() }
                        FollowListVault(this, if (preview) "previewlists" else "followlists").clear()
                        vault.clear(); ChangeNotifications(this, preview).clear(); true
                    } catch (_: Exception) { false }
                    main.post {
                        if (isDestroyed) return@post
                        loading = false
                        if (ok) { account = null; peopleScreen = false; selectedPersonId = null; needsConnection = false; message = "Bu cihazdaki bağlantı kaldırıldı." }
                        else message = "Bağlantı silinemedi. Lütfen tekrar dene."
                        render()
                    }
                }
            }.show()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun startLogin() {
        if (preview) { message = "Demo modunda gerçek hesap bağlantısı yok."; render(); return }
        if (loading || web != null) return
        if (gate.nextPrototypeAt() > System.currentTimeMillis()) { message = "Yeni bağlantı denemesi · ${clock(gate.nextPrototypeAt())}"; render(); return }
        try { gate.pauseAutomatic(); syncMonitoring() } catch (_: Exception) { showFailure(ApiFailure(ApiFailure.Kind.STORAGE)); render(); return }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        loading = false; loginChecking = false; loginAttempted = false; loginLoaded = false
        val page = stack().apply { setBackgroundColor(bg) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)) }
        header.addView(button("Vazgeç", false) { leaveLogin() }, LinearLayout.LayoutParams(-2, dp(48)))
        header.addView(text("  instagram.com", 16f, accent, true))
        page.addView(header)
        loginStatus = text("Girişini tamamla, sonra Devam et'e dokun.", 13f, muted).apply { setPadding(dp(18), dp(8), dp(18), dp(8)) }
        page.addView(loginStatus)
        val browser = try { WebView(this) } catch (_: Exception) {
            message = "Android System WebView açılamadı. WebView'i güncelleyip tekrar dene."
            if (BuildConfig.DEBUG) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            render(); return
        }
        web = browser
        WebView.setWebContentsDebuggingEnabled(false)
        browser.setBackgroundColor(Color.WHITE)
        browser.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true
            allowFileAccess = false; allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_NO_CACHE
            setSupportMultipleWindows(false); javaScriptCanOpenWindowsAutomatically = false
            @Suppress("DEPRECATION") setSaveFormData(false)
        }
        CookieManager.getInstance().apply { setAcceptCookie(true); setAcceptThirdPartyCookies(browser, false) }
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = !NavigationPolicy.isInstagram(request.url.toString())
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                if (NavigationPolicy.isLoginResource(request.url.toString())) null
                else WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(byteArrayOf()))
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                main.post { loginStatus?.setText(R.string.connection_tls_error) }
            }
            override fun onPageFinished(view: WebView, url: String) {
                if (NavigationPolicy.isInstagram(url)) {
                    loginLoaded = true
                    completionButton?.isEnabled = !loginAttempted && !loginChecking && gate.nextPrototypeAt() <= System.currentTimeMillis()
                    completionButton?.alpha = if (completionButton?.isEnabled == true) 1f else .5f
                }
            }
        }
        page.addView(browser, LinearLayout.LayoutParams(-1, 0, 1f))
        completionButton = button("Devam et", false, networkAction = true) { checkLogin() }
        completionButton?.isEnabled = false
        page.addView(completionButton, LinearLayout.LayoutParams(-1, dp(54)))
        root.removeAllViews(); root.addView(page)
        CookieManager.getInstance().removeAllCookies {
            if (web === browser) {
                WebStorage.getInstance().deleteAllData(); browser.clearCache(true)
                browser.loadUrl("https://www.instagram.com/accounts/login/")
            }
        }
    }

    private fun checkLogin() {
        val browser = web ?: return
        if (!loginLoaded || loginChecking || loginAttempted) return
        if (gate.nextPrototypeAt() > System.currentTimeMillis()) {
            loginStatus?.text = getString(R.string.connection_next_try, clock(gate.nextPrototypeAt()))
            scheduleGateWake(); return
        }
        val session = SessionCookies.fromHeader(CookieManager.getInstance().getCookie("https://www.instagram.com/"), browser.settings.userAgentString)
        if (session == null) {
            loginStatus?.setText(R.string.connection_incomplete)
            return
        }
        loginChecking = true; loginAttempted = true
        completionButton?.isEnabled = false; completionButton?.alpha = .5f
        loginStatus?.setText(R.string.connection_checking)
        val ticket = ++generation
        worker.execute {
            val result = try {
                val linked = SavedAccount(session, api.testWebConnection(session), account?.takeIf { it.session.userId == session.userId }?.people.orEmpty())
                Result.success(linked)
            } catch (failure: ApiFailure) { Result.failure(failure) }
            catch (_: Exception) { Result.failure(ApiFailure(ApiFailure.Kind.STORAGE)) }
            main.post {
                if (isDestroyed || ticket != generation || web !== browser) return@post
                loginChecking = false
                result.onSuccess {
                    // Commit on the UI owner after checking the active login ticket.
                    // A cancelled WebView result must never save a new session later.
                    try {
                        account = vault.link(it.session, it.profile)
                        gate.succeeded()
                        message = null
                        needsConnection = false; syncMonitoring(); leaveLogin()
                    } catch (_: Exception) { loginStatus?.text = ApiFailure(ApiFailure.Kind.STORAGE).userMessage() }
                }.onFailure { loginStatus?.text = pauseMessage() ?: (it as ApiFailure).userMessage() }
                completionButton?.isEnabled = !loginAttempted && !loginChecking && gate.nextPrototypeAt() <= System.currentTimeMillis()
                completionButton?.alpha = if (completionButton?.isEnabled == true) 1f else .5f
                scheduleGateWake()
            }
        }
    }

    private fun leaveLogin() {
        ++generation; api.cancel()
        val browser = web
        web = null; loginStatus = null; completionButton = null; loginChecking = false
        browser?.apply {
            stopLoading(); loadUrl("about:blank"); clearHistory(); clearCache(true)
            (parent as? ViewGroup)?.removeView(this); removeAllViews(); destroy()
        }
        CookieManager.getInstance().removeAllCookies { CookieManager.getInstance().flush() }
        WebStorage.getInstance().deleteAllData()
        if (BuildConfig.DEBUG) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        loading = false; render()
    }

    @Deprecated("Back handling retained for API 24-32 compatibility")
    @SuppressLint("GestureBackNavigation") // API 33+ uses the registered platform callback; this is the legacy path.
    @Suppress("DEPRECATION")
    override fun onBackPressed() { navigateBack() }

    private fun navigateBack() {
        if (web != null) leaveLogin()
        else if (peopleScreen && selectedPersonId != null) { selectedPersonId = null; message = null; render() }
        else if (peopleScreen) { peopleScreen = false; message = null; render() }
        else finish()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("people", peopleScreen); outState.putString("person", selectedPersonId); outState.putInt("range", rangeDays)
        outState.putString("pendingWatch", pendingWatchId); outState.putString("pendingOwner", pendingWatchOwner); outState.putInt("pendingHours", pendingWatchHours)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        ++generation; main.removeCallbacksAndMessages(null); api.cancel()
        if (web != null) {
            CookieManager.getInstance().removeAllCookies { CookieManager.getInstance().flush() }
            WebStorage.getInstance().deleteAllData()
        }
        web?.apply { stopLoading(); (parent as? ViewGroup)?.removeView(this); destroy() }
        web = null
        worker.shutdownNow()
        super.onDestroy()
    }
}
