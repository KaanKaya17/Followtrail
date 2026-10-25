package dev.kaankaya.instagramtracker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** A list member is a temporary profile, never an implicitly registered/watchable person. */
class MemberProfileActivity : Activity() {
    private data class Retained(val owner: String, val binding: String?, val member: FollowUser, val profile: Profile?)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var vault: SessionVault
    private lateinit var gate: RequestGate
    private lateinit var api: InstagramApi
    private lateinit var avatars: AvatarImages
    private lateinit var reference: Session
    private lateinit var member: FollowUser
    private var profile: Profile? = null
    private var account: SavedAccount? = null
    private var preview = false
    private var loading = false
    private var attempted = false
    private var generation = 0
    private var pending: Future<*>? = null
    private var message: String? = null
    private fun dp(value: Int) = AppUi.dp(this, value)
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun LinearLayout.gap(value: Int) { addView(View(this@MemberProfileActivity), LinearLayout.LayoutParams(1, dp(value))) }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); AppUi.systemBars(this)
        if (!BuildConfig.DEBUG) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        preview = PreviewMode.enabled(this)
        vault = SessionVault(this, if (preview) "previewaccount" else "account")
        gate = RequestGate(this, if (preview) "preview_gate" else "request_gate")
        api = PreviewMode.api(gate, preview)
        avatars = AvatarImages(this, PreviewMode.avatarFetcher(preview), if (preview) "previewavatars" else "avatars")
        attempted = state?.getBoolean("attempted") ?: false
        try {
            val saved = vault.read() ?: throw ApiFailure(ApiFailure.Kind.SESSION_EXPIRED)
            if (intent.getStringExtra("viewer_id") != saved.session.userId) throw ApiFailure(ApiFailure.Kind.WRONG_ACCOUNT)
            reference = saved.session; account = saved
            val id = InstagramIdentity.id(intent.getStringExtra("member_id"))
            val retained = lastNonConfigurationInstance as? Retained
            val target = InstagramIdentity.id(intent.getStringExtra("list_target_id"))
            val direction = FollowDirection.valueOf(intent.getStringExtra("list_direction") ?: "")
            if (target != saved.session.userId && saved.people.none { it.latest.userId == target }) throw ApiFailure(ApiFailure.Kind.NOT_FOUND)
            val row = FollowListVault(this, if (preview) "previewlists" else "followlists").read(reference, target)
                ?.run(direction)?.users?.firstOrNull { it.id == id }
            member = row ?: retained?.takeIf { it.owner == reference.userId && it.binding == reference.mobileBinding && it.member.id == id }?.member
                ?: throw ApiFailure(ApiFailure.Kind.NOT_FOUND)
            profile = (listOf(saved.profile) + saved.people.map { it.latest }).firstOrNull { it.userId == id }
            retained?.takeIf { it.owner == reference.userId && it.binding == reference.mobileBinding && it.member.id == id }
                ?.profile?.let { if (profile == null || it.observedAt > profile!!.observedAt) profile = it }
        } catch (failure: Exception) {
            setContentView(AppUi.notice(this, "Profil açılamadı", (failure as? ApiFailure)?.userMessage() ?: getString(R.string.list_storage_error), true))
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) onBackInvokedDispatcher.registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) { finish() }
        render()
        if (!attempted && (profile == null || PeoplePolicy.needsRefresh(profile!!, System.currentTimeMillis()))) {
            attempted = true
            if (readAllowed()) readProfile() else { message = waitReason(); render() }
        }
    }
    private fun readAllowed() = !loading && !gate.automationHeld() && !gate.requiresConnection() &&
        gate.nextAllowedAt() <= System.currentTimeMillis() && (profile == null || PeoplePolicy.needsRefresh(profile!!, System.currentTimeMillis()))
    private fun waitReason(): String = when {
        gate.requiresConnection() -> ApiFailure(ApiFailure.Kind.SESSION_EXPIRED).userMessage()
        gate.automationHeld() -> "Bağlantı işlemi sürüyor. Profil okuması bekliyor."
        gate.blockedUntil() > System.currentTimeMillis() -> ApiFailure(ApiFailure.Kind.LIMITED).userMessage()
        else -> "Yeni profil okuması için mevcut istek aralığı bekleniyor. Hazır olduğunda Profili getir düğmesini kullanabilirsin."
    }
    private val wake = Runnable { render() }
    private fun render() {
        if (isDestroyed || isFinishing || !::member.isInitialized) return
        val page = column().apply { setPadding(dp(22), dp(16), dp(22), dp(24)); setBackgroundColor(AppUi.background) }
        val scroll = ScrollView(this).apply {
            isFillViewport = true; addView(page)
            setOnApplyWindowInsetsListener { view, insets ->
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                insets
            }
        }
        setContentView(scroll); scroll.requestApplyInsets()
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(AppUi.iconButton(this, "back", "Listeye dön") { finish() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(AppUi.heading(this, "Profil", 24f).apply { setPadding(dp(12), 0, 0, 0) }); page.addView(header); page.gap(24)
        if (preview) { page.addView(AppUi.pill(this, "DEMO · ÖRNEK VERİ")); page.gap(16) }
        val shown = profile
        val username = shown?.username ?: member.username
        val picture = shown?.details?.pictureUrl?.value ?: member.picture.takeIf { shown == null }
        page.addView(ProfileAvatarView(this, 76).apply { bind(avatars, reference.userId, member.id, username ?: member.name ?: "?",
            picture.takeUnless { shown?.details?.anonymousPicture == true }, shown?.details?.pictureId?.value) }); page.gap(16)
        page.addView(AppUi.heading(this, username?.let { "@$it" } ?: getString(R.string.list_username_unknown), 28f)); page.gap(8)
        member.name?.takeIf { it.isNotBlank() }?.let { page.addView(AppUi.text(this, it, 15f, AppUi.muted)); page.gap(12) }
        message?.let { page.addView(AppUi.notice(this, "Profil durumu", it, true)); page.gap(16) }
        if (loading) { page.addView(AppUi.text(this, "Profil bilgileri alınıyor…", 14f, AppUi.violet)); page.gap(16) }
        val details = AppUi.card(this)
        if (shown != null) {
            listOf("Takipçi" to shown.followers, "Takip edilen" to shown.following).forEach { (title, count) ->
                details.addView(AppUi.text(this, "$title · $count", 19f, strong = true)); details.gap(12)
            }
            shown.posts?.let { details.addView(AppUi.text(this, "Gönderi · $it", 16f)); details.gap(12) }
            details.addView(AppUi.text(this, shown.details.biography.value?.ifEmpty { "Biyografi boş." } ?: "Bu okumada biyografi bilgisi yok.", 15f))
            details.gap(12)
            details.addView(AppUi.text(this, "Son profil okuması · " + java.text.SimpleDateFormat("d MMM · HH:mm", java.util.Locale.forLanguageTag("tr-TR")).format(java.util.Date(shown.observedAt)), 11f, AppUi.muted))
        } else details.addView(AppUi.text(this, "Liste kaydındaki kişi gösteriliyor. Takipçi sayıları ve biyografi henüz okunmadı.", 15f, AppUi.muted))
        page.addView(details); page.gap(18)
        page.addView(AppUi.button(this, if (shown == null) "Profili getir" else "Profili yenile", enabled = readAllowed()) { readProfile() }); page.gap(12)
        val registered = account?.let { it.profile.userId == member.id || it.people.any { person -> person.latest.userId == member.id } } == true
        if (!registered) {
            page.addView(AppUi.button(this, "Kişilere ekle", false, shown != null && !loading) { addPerson() }); page.gap(12)
            page.addView(AppUi.text(this, "Bu profili açmak kişilerine eklemez veya otomatik izlemeyi açmaz.", 12f, AppUi.muted)); page.gap(12)
        } else {
            page.addView(AppUi.button(this, "Listelerini incele", false, !loading) {
                startActivity(Intent(this, FollowListActivity::class.java).putExtra("target_id", member.id).putExtra("offline_preview", preview))
            }); page.gap(12)
        }
        page.addView(AppUi.button(this, "Instagram’da aç", false, ProfileLinks.url(username) != null) {
            try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(ProfileLinks.url(username))).addCategory(Intent.CATEGORY_BROWSABLE)) }
            catch (_: android.content.ActivityNotFoundException) { android.widget.Toast.makeText(this, R.string.profile_link_unavailable, android.widget.Toast.LENGTH_SHORT).show() }
        })
        main.removeCallbacks(wake)
        val next = maxOf(gate.nextAllowedAt(), shown?.observedAt?.plus(PeoplePolicy.FRESH_MS) ?: 0)
        if (next > System.currentTimeMillis()) main.postDelayed(wake, next - System.currentTimeMillis())
    }
    private fun readProfile() {
        if (!readAllowed()) return
        attempted = true; loading = true; message = null; render(); val ticket = ++generation
        pending = worker.submit {
            val result = try {
                if (vault.read()?.session != reference) throw ApiFailure(ApiFailure.Kind.CANCELLED)
                Result.success(api.readId(reference, member.id))
            } catch (failure: ApiFailure) { Result.failure(failure) }
            catch (_: Exception) { Result.failure(ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)) }
            main.post {
                if (isDestroyed || isFinishing || ticket != generation) return@post
                loading = false
                result.onSuccess { fresh ->
                    try {
                        vault.inspect { current ->
                            if (current?.session != reference) throw ApiFailure(ApiFailure.Kind.CANCELLED)
                            profile = fresh
                            if (current.profile.userId == member.id && fresh.observedAt > current.profile.observedAt)
                                account = vault.update(reference) { it.copy(profile = fresh) }
                            else if (current.people.any { it.latest.userId == member.id }) {
                                val commit = Observations.person(vault, reference, fresh, existingRequired = true)
                                account = commit.account
                                commit.change?.let { change -> Observations.deliver(vault, reference, change, ChangeNotifications(this, preview)::allowed, ChangeNotifications(this, preview)::post) }
                            }
                        }
                    } catch (_: Exception) { message = "Profil okuması kaydedilemedi. Mevcut kayıtlar korundu." }
                }.onFailure { message = (it as ApiFailure).userMessage() }
                render()
            }
        }
    }
    private fun addPerson() {
        val shown = profile ?: return
        try {
            account = vault.update(reference) { current ->
                if (current.profile.userId == shown.userId || current.people.any { it.latest.userId == shown.userId }) current
                else current.copy(people = PeoplePolicy.record(current.people, shown))
            }
            message = "Kişilerine eklendi. Otomatik izleme kapalıdır."
        } catch (failure: ApiFailure) { message = failure.userMessage() }
        catch (_: Exception) { message = ApiFailure(ApiFailure.Kind.STORAGE).userMessage() }
        render()
    }
    override fun onResume() {
        super.onResume(); if (!::member.isInitialized) return
        try { account = vault.read(); if (account?.session != reference) { finish(); return } }
        catch (_: Exception) { finish(); return }
        render()
    }
    override fun onPause() { generation++; api.cancel(); pending?.cancel(true); loading = false; main.removeCallbacks(wake); avatars.cancelAll(); super.onPause() }
    override fun onDestroy() { avatars.close(); main.removeCallbacksAndMessages(null); worker.shutdownNow(); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putBoolean("attempted", attempted); super.onSaveInstanceState(outState) }
    override fun onRetainNonConfigurationInstance(): Any? = if (::member.isInitialized) Retained(reference.userId, reference.mobileBinding, member, profile) else null
}
