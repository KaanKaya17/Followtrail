package dev.kaankaya.instagramtracker

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
    private val bg = Color.rgb(16, 19, 27)
    private val panel = Color.rgb(28, 33, 43)
    private val mint = Color.rgb(186, 243, 213)
    private val lilac = Color.rgb(216, 204, 250)
    private val white = Color.rgb(242, 244, 235)
    private val muted = Color.rgb(164, 174, 181)
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
    private var pendingWatchId: String? = null
    private var pendingWatchOwner: String? = null
    private var pendingWatchHours = 1
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            main.post {
                if (isDestroyed) return@post
                account = saved; loading = false; needsConnection = needsConnection || gate.requiresConnection()
                openNotification(saved); syncMonitoring(); render()
                // An outstanding pause is restored without issuing a network request.
                if (saved != null && !peopleScreen && gate.autoAllowed(saved.profile)) refresh(automatic = true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::gate.isInitialized || web != null || loading) return
        try { account = vault.read(); needsConnection = gate.requiresConnection() }
        catch (_: Exception) { message = ApiFailure(ApiFailure.Kind.STORAGE).userMessage(); needsConnection = true }
        syncMonitoring()
        if (account != null && !peopleScreen && !needsConnection && gate.autoAllowed(account!!.profile)) refresh(automatic = true)
        else render()
    }

    private fun shape(color: Int, radius: Int = 24, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }
    private fun text(value: String, size: Float, color: Int = white, medium: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        typeface = Typeface.create(if (medium) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        includeFontPadding = false
    }
    private fun stack() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun LinearLayout.gap(value: Int) { addView(View(this@MainActivity), LinearLayout.LayoutParams(1, dp(value))) }
    private fun button(label: String, primary: Boolean = true, networkAction: Boolean = false, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 16f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(if (primary) bg else white)
        background = shape(if (primary) mint else panel, 18)
        minimumHeight = dp(56); setPadding(dp(16), dp(12), dp(16), dp(12))
        setOnClickListener { action() }
        isEnabled = !loading && (!networkAction || gate.nextAllowedAt() <= System.currentTimeMillis())
        alpha = if (isEnabled) 1f else .5f
    }
    private fun badge(label: String, color: Int = mint) = text(label, 10f, color, true).apply {
        letterSpacing = .10f; setPadding(dp(12), dp(8), dp(12), dp(8))
        background = shape(panel, 20)
    }
    private fun render() {
        if (web != null || isDestroyed) return
        root.removeAllViews()
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        val page = stack().apply { setPadding(dp(24), dp(24), dp(24), dp(24)) }
        scroll.addView(page, ViewGroup.LayoutParams(-1, -2)); root.addView(scroll)
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(text("it", 25f, mint, true).apply { contentDescription = "InstagramTracker" }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(badge("BU CİHAZDA"))
        page.addView(header); page.gap(24)
        if (preview) {
            page.addView(badge("DEMO · ÖRNEK VERİ · AĞ İSTEĞİ YOK", lilac)); page.gap(18)
        }
        val saved = account
        if (saved != null) {
            val navigation = LinearLayout(this)
            navigation.addView(button("Özet", !peopleScreen) { peopleScreen = false; selectedPersonId = null; message = null; render() }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
            navigation.addView(button("Kişiler", peopleScreen) { peopleScreen = true; selectedPersonId = null; message = null; render() }, LinearLayout.LayoutParams(0, -2, 1f))
            page.addView(navigation); page.gap(26)
        }
        if (saved == null) {
            page.addView(text("Hesabın,\nbir bakışta.", 38f, white, true).apply { setLineSpacing(dp(3).toFloat(), 1f) })
            page.gap(14)
            page.addView(text("Kendi hesabın ve seçtiğin kişiler.\nDeğişimleri bu cihazda takip et.", 17f, muted).apply { setLineSpacing(dp(5).toFloat(), 1f) })
            page.addView(OrbitView(this), LinearLayout.LayoutParams(-1, dp(230)))
            if (BuildConfig.DEBUG && !preview) {
                page.addView(button("Çevrimdışı demoyu aç", false) {
                    startActivity(android.content.Intent(this, MainActivity::class.java).putExtra("offline_preview", true))
                }); page.gap(12)
            }
        } else if (peopleScreen) {
            renderPeople(page, saved)
        } else {
            page.addView(text("HESAP ÖZETİ", 11f, muted, true).apply { letterSpacing = .18f })
            page.gap(12)
            page.addView(text("@${saved.profile.username}", 29f, white, true).apply {
                maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
            })
            page.gap(10)
            page.addView(text("Sayıların burada.\nKontrol sende.", 17f, muted))
            page.gap(28)
            val row = LinearLayout(this)
            fun stat(label: String, value: Long, color: Int): LinearLayout = stack().apply {
                background = shape(color); minimumHeight = dp(155); setPadding(dp(18), dp(22), dp(18), dp(22))
                addView(text(label, 13f, bg, true)); gap(26)
                addView(text(NumberFormat.getIntegerInstance(Locale.forLanguageTag("tr-TR")).format(value), 30f, bg, true).apply {
                    maxLines = 1
                    if (android.os.Build.VERSION.SDK_INT >= 26) setAutoSizeTextTypeUniformWithConfiguration(16, 30, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
                })
            }
            row.addView(stat("Takipçi", saved.profile.followers, mint), LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(10) })
            row.addView(stat("Takip edilen", saved.profile.following, lilac), LinearLayout.LayoutParams(0, -2, 1f))
            page.addView(row); page.gap(22)
            page.addView(text("SON BAŞARILI OKUMA", 10f, muted, true).apply { letterSpacing = .12f })
            page.gap(7)
            page.addView(text(SimpleDateFormat("d MMMM · HH:mm:ss", Locale.forLanguageTag("tr-TR")).format(Date(saved.profile.observedAt)), 14f))
            page.gap(8)
            page.addView(text(if (loading) "Kayıtlı sayılar gösteriliyor; güncel yanıt bekleniyor." else "Son başarılı gözlemin. Yenile ile güncelleyebilirsin.", 12f, muted))
            page.gap(26)
        }
        if (loading) {
            val progress = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            progress.addView(ProgressBar(this).apply { indeterminateTintList = android.content.res.ColorStateList.valueOf(mint) }, LinearLayout.LayoutParams(dp(22), dp(22)))
            progress.addView(text(if (saved == null) "Bağlantı hazırlanıyor…" else "Güncel sayılar alınıyor…", 14f, muted), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(12) })
            page.addView(progress); page.gap(16)
        }
        val pause = pauseMessage()
        listOfNotNull(pause, message?.takeIf { it != pause }).forEach {
            page.addView(text(it, 14f, lilac).apply { setLineSpacing(dp(4).toFloat(), 1f) }); page.gap(16)
        }
        if (saved == null || needsConnection) {
            page.addView(button(if (saved == null) "Instagram ile bağlan" else "Bağlantıyı yenile", networkAction = true) { startLogin() }, LinearLayout.LayoutParams(-1, -2))
            if (saved == null && vault.exists()) {
                page.gap(10); page.addView(button("Kayıtlı bağlantıyı kaldır", false) { confirmRemove() }, LinearLayout.LayoutParams(-1, -2))
            }
        } else if (!peopleScreen) page.addView(button("Sayıları yenile", networkAction = true) { refresh() }, LinearLayout.LayoutParams(-1, -2))
        if (saved != null) {
            page.gap(12)
            page.addView(button("Bağlantıyı kaldır", false) { confirmRemove() }, LinearLayout.LayoutParams(-1, -2))
        }
        page.gap(16)
        if (gate.prototypeActive()) {
            page.addView(text("Bağlantı prototipi açık bırakılmış; otomatik kontroller duraklatıldı. Prototipi açıp denemeyi bitirebilirsin.", 13f, lilac)); page.gap(8)
        }
        page.addView(button(if (preview) "Bağlantı prototipi · çevrimdışı" else "Bağlantı prototipini aç", false) {
            try {
                if (!preview) { gate.setPrototypeActive(true); syncMonitoring() }
                startActivity(Intent(this, ConnectionLabActivity::class.java).putExtra("offline_lab", preview))
            } catch (_: Exception) { showFailure(ApiFailure(ApiFailure.Kind.STORAGE)); render() }
        }, LinearLayout.LayoutParams(-1, -2))
        page.gap(24)
        page.addView(text("Oturumun yalnızca bu cihazda saklanır.", 12f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
            setOnClickListener { privacy() }; contentDescription = "Gizlilik bilgilerini aç"
        })
        page.addView(text("INSTAGRAMTRACKER  /  ${BuildConfig.VERSION_NAME}", 9f, muted).apply { gravity = Gravity.CENTER; letterSpacing = .14f })
        scheduleGateWake()
    }

    private fun date(time: Long) = SimpleDateFormat("d MMM · HH:mm", Locale.forLanguageTag("tr-TR")).format(Date(time))
    private fun number(value: Long) = NumberFormat.getIntegerInstance(Locale.forLanguageTag("tr-TR")).format(value)
    private fun delta(value: Long) = (if (value > 0) "+" else "") + number(value)

    private fun renderPeople(page: LinearLayout, saved: SavedAccount) {
        val person = saved.people.firstOrNull { it.latest.userId == selectedPersonId }
        if (person == null) {
            selectedPersonId = null
            page.addView(text("Seçtiğin kişiler", 29f, white, true)); page.gap(10)
            page.addView(text("Bir profil ekle. Sonraki okumaları karşılaştır, değişimleri gör.", 15f, muted)); page.gap(20)
            page.addView(button("Kişi ekle", networkAction = true) { addPerson() }, LinearLayout.LayoutParams(-1, -2))
            page.gap(12)
            page.addView(text("${saved.people.size}/${PeoplePolicy.MAX_PEOPLE} kişi · Seçtiğin kişiler için değişiklik bildirimi açabilirsin.\nListeyi açmak Instagram isteği göndermez.", 12f, muted)); page.gap(22)
            if (saved.people.isEmpty()) {
                page.addView(text("İlk kişini eklemeye hazırsın.", 20f, lilac, true)); page.gap(8)
                page.addView(text("@kullanıcıadı veya Instagram profil bağlantısını kullanabilirsin. Tüm takipçilerin taranmaz.", 14f, muted))
            }
            for (entry in saved.people) {
                val p = entry.latest
                val card = stack().apply { background = shape(panel); setPadding(dp(18), dp(18), dp(18), dp(18)) }
                card.addView(text("@${p.username}", 20f, white, true)); card.gap(8)
                card.addView(text("${number(p.followers)} takipçi · ${number(p.following)} takip edilen", 14f, mint))
                card.gap(8); card.addView(text("${date(p.observedAt)}${entry.group.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}", 12f, muted))
                if (entry.watching) { card.gap(8); card.addView(text("Değişiklik bildirimi açık", 12f, lilac)) }
                card.gap(12)
                card.addView(button("Analizi aç · @${p.username}", false) { selectedPersonId = p.userId; rangeDays = 0; message = null; render() }, LinearLayout.LayoutParams(-1, -2))
                page.addView(card); page.gap(12)
            }
            return
        }
        val p = person.latest
        page.addView(button("Tüm kişiler", false) { selectedPersonId = null; message = null; render() }); page.gap(18)
        page.addView(text("@${p.username}", 29f, white, true)); page.gap(8)
        page.addView(text(person.group.ifBlank { "KİŞİ ANALİZİ" }, 12f, lilac, true)); page.gap(22)
        val stats = stack().apply { background = shape(panel); setPadding(dp(20), dp(20), dp(20), dp(20)) }
        for ((label, value) in listOf("Takipçi" to number(p.followers), "Takip edilen" to number(p.following), "Gönderi" to (p.posts?.let { number(it) } ?: "Bilgi yok"))) {
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(text(label, 14f, muted), LinearLayout.LayoutParams(0, -2, 1f)); row.addView(text(value, 25f, mint, true))
            stats.addView(row); stats.gap(12)
        }
        page.addView(stats); page.gap(12)
        page.addView(text("Son başarılı okuma: ${date(p.observedAt)}", 12f, muted)); page.gap(16)
        if (person.history.size >= 2) {
            val previous = person.history[person.history.lastIndex - 1]
            page.addView(text("Son kayda göre", 12f, muted)); page.gap(6)
            page.addView(text("${delta(p.followers - previous.followers)} takipçi · ${delta(p.following - previous.following)} takip edilen", 17f, white, true))
            page.gap(6); page.addView(text("${date(previous.observedAt)} → ${date(p.observedAt)}\nNet farktır; gelen/giden kişilerin kimliğini göstermez.", 12f, muted))
        } else page.addView(text("İlk kayıt alındı. Değişim, sonraki başarılı okumadan sonra görünür.", 14f, muted))
        page.gap(20)
        page.addView(button("Kişiyi yenile", networkAction = true) { readPerson(person = person) }, LinearLayout.LayoutParams(-1, -2)); page.gap(24)
        page.addView(text("Değişiklik bildirimi", 22f, white, true)); page.gap(10)
        val notices = ChangeNotifications(this, preview)
        page.addView(text(if (person.watching) "Açık · En az ${person.intervalHours} saat arayla kontrol edilir." else "Kapalı · Bu kişi arka planda sorgulanmaz.", 14f, lilac))
        page.gap(8)
        page.addView(text("Uygulama kapalıyken sayılardaki net farkı bildirir. Android ve kontrol sırası süreyi uzatabilir; anlık değildir. Kilit ekranında gizlilik için genel metin kullanılır; görünüm sistem ayarlarına bağlıdır.", 12f, muted))
        if (person.watching && !notices.allowed()) { page.gap(8); page.addView(text("Sistem bildirim izni kapalı; arka plan okumaları duruyor.", 13f, lilac)) }
        if (preview) { page.gap(8); page.addView(text("Demo izleme yalnız örnek veriler kullanır. Instagram'a istek gönderilmez.", 12f, muted)) }
        page.gap(12)
        page.addView(button(if (person.watching) "Bildirim ayarlarını değiştir" else "Değişiklik bildirimini aç", false) { watchSettings(person) }, LinearLayout.LayoutParams(-1, -2))
        if (person.watching) {
            page.gap(8); page.addView(button("Bu kişide izlemeyi kapat", false) { setWatch(p.userId, saved.session.userId, person.intervalHours, false) }, LinearLayout.LayoutParams(-1, -2))
        }
        page.gap(24)
        page.addView(text("Gözlem geçmişi", 22f, white, true)); page.gap(10)
        val filters = LinearLayout(this)
        for ((label, days) in listOf("7 gün" to 7, "30 gün" to 30, "Tümü" to 0)) {
            filters.addView(button(label, rangeDays == days) { rangeDays = days; render() }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5) })
        }
        page.addView(filters); page.gap(10)
        val observations = person.history.filter { rangeDays == 0 || it.observedAt >= System.currentTimeMillis() - rangeDays * 86400000L }
        if (observations.isNotEmpty()) {
            val metric = LinearLayout(this)
            metric.addView(button("Takipçi grafiği", chartFollowers) { chartFollowers = true; render() }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
            metric.addView(button("Takip edilen grafiği", !chartFollowers) { chartFollowers = false; render() }, LinearLayout.LayoutParams(0, -2, 1f))
            page.addView(metric); page.gap(12)
            if (observations.size >= 2) {
                page.addView(HistoryChartView(this, observations, chartFollowers, mint, muted), LinearLayout.LayoutParams(-1, dp(210)))
                val first = observations.first(); val last = observations.last()
                page.addView(text("Dönem net farkı: ${delta(last.followers - first.followers)} takipçi, ${delta(last.following - first.following)} takip edilen", 13f, lilac)); page.gap(10)
            }
            page.addView(text("Gerçek gözlem zamanları · kişi sayısı\nEn son ${PeoplePolicy.MAX_HISTORY} kayıt saklanır; son 10 kayıt aşağıda.", 12f, muted)); page.gap(12)
            for (observation in observations.takeLast(10).asReversed()) {
                page.addView(text("${date(observation.observedAt)}\n${number(observation.followers)} takipçi · ${number(observation.following)} takip edilen · ${observation.posts?.let { number(it) } ?: "?"} gönderi", 13f, white))
                page.gap(12)
            }
        } else page.addView(text("Bu tarih aralığında kayıt yok. Tümü ile önceki gözlemleri görebilirsin.", 14f, muted))
        page.gap(12)
        page.addView(text("Özel notun", 18f, white, true)); page.gap(8)
        page.addView(text(person.note.ifBlank { "Bu kişiyle ilgili bir not ekleyebilirsin." }, 14f, muted)); page.gap(10)
        page.addView(button("Not ve grup düzenle", false) { editPerson(person) }, LinearLayout.LayoutParams(-1, -2)); page.gap(10)
        page.addView(button("Kişiyi kaldır", false) {
            AlertDialog.Builder(this).setTitle("Kişi kaldırılsın mı?").setMessage("Bu kişinin kayıtlı geçmişi ve notu bu cihazdan silinir.")
                .setNegativeButton("Vazgeç", null).setPositiveButton("Kaldır") { _, _ ->
                    if (savePeople { people -> people.filterNot { it.latest.userId == p.userId } }) {
                        ChangeNotifications(this, preview).cancel(saved.session.userId, p.userId); selectedPersonId = null
                    }
                    render()
                }.show()
        }, LinearLayout.LayoutParams(-1, -2)); page.gap(20)
    }

    private fun input(hintValue: String, limit: Int) = EditText(this).apply {
        hint = hintValue; setTextColor(white); setHintTextColor(muted); textSize = 16f
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
        if (needsConnection) { message = "Önce Instagram bağlantısını yenilemelisin."; render(); return }
        if (gate.nextAllowedAt() > System.currentTimeMillis()) { message = ApiFailure(ApiFailure.Kind.TOO_SOON).userMessage(); render(); return }
        loading = true; message = null; render()
        val ticket = ++generation
        worker.execute {
            val result = try {
                Result.success(if (person != null) api.readId(saved.session, person.latest.userId) else api.readUsername(saved.session, requireNotNull(username)))
            } catch (failure: ApiFailure) { Result.failure(failure) }
            catch (_: Exception) { Result.failure(ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)) }
            main.post {
                if (isDestroyed || ticket != generation) return@post
                loading = false
                result.onSuccess { profile ->
                    try {
                        val committed = Observations.person(vault, saved.session, profile, existingRequired = person != null)
                        account = committed.account; syncMonitoring()
                        committed.change?.let { change -> notifyCurrent(saved.session, change) }
                        selectedPersonId = profile.userId; message = "Yeni gözlem bu cihazda kaydedildi."
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
        if (!gate.manualOnly()) return null
        val last = gate.lastLimitedAt().takeIf { it > 0 }?.let { "Son sınırlama yanıtı: ${date(it)} (${gate.limitReason()}). " }
            ?: "Önceki sınırlama kaydı korunuyor. "
        return last + if (gate.blockedUntil() > System.currentTimeMillis()) {
            "Uygulama ${date(gate.blockedUntil())} zamanına kadar yeni istek göndermeyecek. " +
                "Bu yerel mola, Instagram kısıtlamasının bitiş zamanı değildir. Otomatik deneme kapalı."
        } else "Yerel mola doldu; Instagram kısıtlamasının kalktığı doğrulanmadı. Otomatik deneme kapalı; yenileme yalnız düğmeyle yapılır."
    }
    private val gateWake = Runnable {
        if (!isDestroyed) {
            if (web == null) render()
            else {
                completionButton?.isEnabled = !loginChecking && gate.nextAllowedAt() <= System.currentTimeMillis()
                completionButton?.alpha = if (completionButton?.isEnabled == true) 1f else .5f
                // No automatic retry when a pause expires.
                loginStatus?.text = pauseMessage() ?: getString(R.string.connection_ready)
            }
        }
    }
    private fun scheduleGateWake() {
        main.removeCallbacks(gateWake)
        val wait = gate.nextAllowedAt() - System.currentTimeMillis()
        if (wait > 0) main.postDelayed(gateWake, wait)
    }

    private fun refresh(automatic: Boolean = false) {
        val saved = account ?: return
        if (loading) return
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
                val updated = saved.copy(profile = api.read(saved.session))
                Result.success(updated)
            } catch (failure: ApiFailure) { Result.failure(failure) }
            main.post {
                if (isDestroyed || ticket != generation) return@post
                loading = false
                result.onSuccess {
                    try {
                        account = vault.update(saved.session) { current -> current.copy(profile = it.profile) }
                        needsConnection = false; syncMonitoring()
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
            .setMessage("Şifren Instagram'ın kendi giriş sayfasına gider; uygulama şifre alanını okumaz.\n\n" +
                "Oturum, seçtiğin kişiler, geçmiş ve notların Android Keystore ile şifrelenir. Giriş sırasında WebView kendi geçici tarayıcı verilerini oluşturabilir; bağlantı tamamlanınca veya iptal edilince temizlenir.\n\n" +
                "İstekler Instagram'a gider. Kendi sunucumuz, reklam, analitik veya veri toplama SDK'mız yok. Bulut yedekleme ve cihaz aktarımı kapalıdır.\n\n" +
                "Bu bağımsız bir uygulamadır. Instagram giriş veya doğrulama isteyebilir; kesintisiz çalışma garantisi yoktur.")
            .setPositiveButton("Anladım", null).show()
    }

    private fun confirmRemove() {
        if (loading) return
        AlertDialog.Builder(this).setTitle("Bağlantı kaldırılsın mı?")
            .setMessage("Bu uygulamadaki oturum, kişiler, geçmiş ve notların silinir.")
            .setNegativeButton("Vazgeç", null).setPositiveButton("Kaldır") { _, _ ->
                loading = true; render()
                worker.execute {
                    val ok = try {
                        if (preview) PreviewMode.cancel(this) else MonitorScheduler.cancel(this)
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
        if (gate.nextAllowedAt() > System.currentTimeMillis()) { render(); return }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        loading = false; loginChecking = false; loginAttempted = false; loginLoaded = false
        val page = stack().apply { setBackgroundColor(bg) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)) }
        header.addView(button("Vazgeç", false) { leaveLogin() }, LinearLayout.LayoutParams(-2, dp(48)))
        header.addView(text("  instagram.com", 16f, mint, true))
        page.addView(header)
        loginStatus = text("Instagram'ın kendi giriş sayfası. Giriş tamamlanınca otomatik döneceksin.", 12f, muted).apply { setPadding(dp(18), dp(8), dp(18), dp(8)) }
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
                if (NavigationPolicy.isInstagram(url)) { loginLoaded = true; checkLogin() }
            }
        }
        page.addView(browser, LinearLayout.LayoutParams(-1, 0, 1f))
        completionButton = button("Giriş yaptım, bağlantıyı tamamla", false, networkAction = true) { checkLogin(manual = true) }
        page.addView(completionButton, LinearLayout.LayoutParams(-1, dp(54)))
        root.removeAllViews(); root.addView(page)
        CookieManager.getInstance().removeAllCookies {
            if (web === browser) {
                WebStorage.getInstance().deleteAllData(); browser.clearCache(true)
                browser.loadUrl("https://www.instagram.com/accounts/login/")
                main.postDelayed(loginPoll, 1500)
            }
        }
    }

    private val loginPoll = object : Runnable {
        override fun run() { if (web != null) { checkLogin(); main.postDelayed(this, 1500) } }
    }
    private fun checkLogin(manual: Boolean = false) {
        val browser = web ?: return
        if (!loginLoaded || loginChecking || (!manual && loginAttempted)) return
        if (gate.nextAllowedAt() > System.currentTimeMillis()) {
            if (manual) loginStatus?.text = pauseMessage() ?: ApiFailure(ApiFailure.Kind.TOO_SOON).userMessage()
            scheduleGateWake(); return
        }
        val session = SessionCookies.fromHeader(CookieManager.getInstance().getCookie("https://www.instagram.com/"), browser.settings.userAgentString)
        if (session == null) {
            if (manual) loginStatus?.setText(R.string.connection_incomplete)
            return
        }
        loginChecking = true; loginAttempted = true
        completionButton?.isEnabled = false; completionButton?.alpha = .5f
        loginStatus?.setText(R.string.connection_checking)
        val ticket = ++generation
        worker.execute {
            val result = try {
                val linked = SavedAccount(session, api.read(session), account?.takeIf { it.session.userId == session.userId }?.people.orEmpty())
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
                        message = "Bağlandı. Sonraki açılışta sayılar otomatik yenilenecek."
                        needsConnection = false; syncMonitoring(); leaveLogin()
                    } catch (_: Exception) { loginStatus?.text = ApiFailure(ApiFailure.Kind.STORAGE).userMessage() }
                }.onFailure { loginStatus?.text = pauseMessage() ?: (it as ApiFailure).userMessage() }
                completionButton?.isEnabled = !loginChecking && gate.nextAllowedAt() <= System.currentTimeMillis()
                completionButton?.alpha = if (completionButton?.isEnabled == true) 1f else .5f
                scheduleGateWake()
            }
        }
    }

    private fun leaveLogin() {
        ++generation; api.cancel()
        main.removeCallbacks(loginPoll)
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
