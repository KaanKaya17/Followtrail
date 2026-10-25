package dev.kaankaya.instagramtracker

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Shared visual language; owns no account, request or persistence state. */
object AppUi {
    val background = Color.rgb(250, 249, 246)
    val surface = Color.WHITE
    val ink = Color.rgb(37, 35, 57)
    val muted = Color.rgb(111, 109, 129)
    val violet = Color.rgb(103, 75, 216)
    val violetSoft = Color.rgb(239, 233, 255)
    val coral = Color.rgb(195, 76, 46)
    val peach = Color.rgb(255, 230, 216)
    val teal = Color.rgb(19, 115, 102)
    val mint = Color.rgb(220, 246, 233)
    val line = Color.rgb(233, 229, 238)
    fun dp(context: Context, value: Int) = (context.resources.displayMetrics.density * value).toInt()
    fun shape(context: Context, color: Int, radius: Int = 22, border: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(context, radius).toFloat()
        border?.let { setStroke(dp(context, 1), it) }
    }
    fun gradient(context: Context, start: Int = violet, end: Int = Color.rgb(139, 96, 235), radius: Int = 26) =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(start, end)).apply { cornerRadius = dp(context, radius).toFloat() }
    fun touch(context: Context, drawable: Drawable): Drawable {
        val focus = drawable.constantState?.newDrawable()?.mutate()
        if (focus is GradientDrawable) focus.setStroke(dp(context, 2), ink)
        val states = StateListDrawable().apply {
            if (focus != null) addState(intArrayOf(android.R.attr.state_focused), focus)
            addState(intArrayOf(), drawable)
        }
        return RippleDrawable(ColorStateList.valueOf(0x18674BD8), states, null)
    }
    fun text(context: Context, value: String, size: Float = 14f, color: Int = ink, strong: Boolean = false) = TextView(context).apply {
        text = value; textSize = size; setTextColor(color); includeFontPadding = false
        typeface = Typeface.create(if (strong) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        setLineSpacing(dp(context, 3).toFloat(), 1f)
    }
    fun heading(context: Context, value: String, size: Float = 22f) = text(context, value, size, ink, true).apply {
        if (android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
    }
    fun pill(context: Context, value: String, foreground: Int = violet, tint: Int = violetSoft) = text(context, value, 10f, foreground, true).apply {
        letterSpacing = .04f; setPadding(dp(context, 10), dp(context, 7), dp(context, 10), dp(context, 7))
        background = shape(context, tint, 12)
        layoutParams = LinearLayout.LayoutParams(-2, -2)
    }
    fun card(context: Context) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; background = shape(context, surface, 24, line)
        setPadding(dp(context, 18), dp(context, 18), dp(context, 18), dp(context, 18))
    }
    fun notice(context: Context, title: String, detail: String, warning: Boolean = false) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = shape(context, if (warning) peach else mint, 18)
        setPadding(dp(context, 14), dp(context, 13), dp(context, 14), dp(context, 13))
        addView(text(context, title, 13f, if (warning) coral else teal, true))
        addView(text(context, detail, 12f).apply { setPadding(0, dp(context, 5), 0, 0) })
    }
    /** Local connectivity evidence only; never probes Instagram or another host. */
    fun offline(context: Context): Boolean? = try {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork
        if (network == null) true else manager.getNetworkCapabilities(network)?.let { !it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) }
    } catch (_: SecurityException) { null }
    fun button(context: Context, title: String, primary: Boolean = true, enabled: Boolean = true, icon: String? = null, action: () -> Unit) = Button(context).apply {
        text = title; textSize = 14f; isAllCaps = false; isEnabled = enabled
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(if (primary) surface else violet)
        background = touch(context, if (primary) gradient(context, radius = 18) else shape(context, violetSoft, 18))
        stateListAnimator = null; minimumHeight = dp(context, 52); minHeight = dp(context, 52)
        setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12))
        alpha = if (enabled) 1f else .48f
        if (icon != null) {
            setCompoundDrawablesRelativeWithIntrinsicBounds(Icon(context, icon, if (primary) surface else violet, 19), null, null, null)
            compoundDrawablePadding = dp(context, 8)
        }
        setOnClickListener { action() }
    }
    fun iconButton(context: Context, name: String, description: String, action: () -> Unit) = button(context, "", false, action = action).apply {
        contentDescription = description; background = touch(context, shape(context, surface, 16, line))
        setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12))
        setCompoundDrawablesRelativeWithIntrinsicBounds(Icon(context, name, ink), null, null, null)
        minimumWidth = dp(context, 48); minWidth = dp(context, 48); minimumHeight = dp(context, 48); minHeight = dp(context, 48)
    }
    fun avatar(context: Context, username: String, size: Int = 44, tint: Int = violetSoft, foreground: Int = violet) =
        text(context, username.trimStart('@').take(1).uppercase(java.util.Locale.forLanguageTag("tr-TR")), size * .38f, foreground, true).apply {
            gravity = Gravity.CENTER; background = shape(context, tint, size / 2)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LinearLayout.LayoutParams(dp(context, size), dp(context, size))
        }
    @Suppress("DEPRECATION")
    fun systemBars(activity: Activity) {
        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            (if (android.os.Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
        activity.window.statusBarColor = background
        activity.window.navigationBarColor = if (android.os.Build.VERSION.SDK_INT >= 26) background else ink
    }
    class Icon(context: Context, private val name: String, color: Int = violet, size: Int = 22) : Drawable() {
        private val dimension = dp(context, size)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; style = Paint.Style.STROKE; strokeWidth = 1.8f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        override fun getIntrinsicWidth() = dimension
        override fun getIntrinsicHeight() = dimension
        override fun draw(canvas: Canvas) {
            val saved = canvas.save(); canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
            canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
            fun path(vararg points: Float) {
                val p = Path(); p.moveTo(points[0], points[1]); for (i in 2 until points.size step 2) p.lineTo(points[i], points[i + 1]); canvas.drawPath(p, paint)
            }
            when (name) {
                "home" -> { path(3f, 10f, 12f, 3f, 21f, 10f); path(5f, 9f, 5f, 21f, 10f, 21f, 10f, 15f, 14f, 15f, 14f, 21f, 19f, 21f, 19f, 9f) }
                "people" -> { canvas.drawCircle(9f, 8f, 3f, paint); canvas.drawArc(3f, 13f, 15f, 24f, 180f, 180f, false, paint); canvas.drawArc(14f, 5f, 20f, 11f, 270f, 180f, false, paint); canvas.drawArc(14f, 13f, 23f, 23f, 270f, 90f, false, paint) }
                "list" -> { for (y in listOf(6f, 12f, 18f)) { path(9f, y, 21f, y); canvas.drawCircle(4f, y, .6f, paint) } }
                "refresh" -> { canvas.drawArc(4f, 4f, 20f, 20f, 30f, 290f, false, paint); path(20f, 3f, 20f, 9f, 14f, 9f) }
                "plus" -> { path(12f, 5f, 12f, 19f); path(5f, 12f, 19f, 12f) }
                "back" -> path(14f, 5f, 7f, 12f, 14f, 19f)
                "chevron" -> path(9f, 5f, 16f, 12f, 9f, 19f)
                "arrow" -> { path(5f, 12f, 19f, 12f); path(13f, 6f, 19f, 12f, 13f, 18f) }
                "more" -> for (x in listOf(5f, 12f, 19f)) canvas.drawCircle(x, 12f, 1f, paint)
                "search" -> { canvas.drawCircle(10.5f, 10.5f, 6.5f, paint); path(16f, 16f, 21f, 21f) }
                "lock" -> { canvas.drawRoundRect(5f, 10f, 19f, 21f, 3f, 3f, paint); canvas.drawArc(8f, 2f, 16f, 15f, 180f, 180f, false, paint); path(12f, 14f, 12f, 17f) }
                "check" -> path(5f, 12f, 10f, 17f, 20f, 6f)
                "clock" -> { canvas.drawCircle(12f, 12f, 9f, paint); path(12f, 7f, 12f, 12f, 16f, 14f) }
                "link" -> { canvas.drawRoundRect(3f, 7f, 14f, 17f, 5f, 5f, paint); canvas.drawRoundRect(10f, 7f, 21f, 17f, 5f, 5f, paint) }
                "compare" -> { path(4f, 7f, 20f, 7f, 16f, 3f); path(20f, 17f, 4f, 17f, 8f, 21f) }
                "close" -> { path(6f, 6f, 18f, 18f); path(18f, 6f, 6f, 18f) }
                "info" -> { canvas.drawCircle(12f, 12f, 9f, paint); path(12f, 11f, 12f, 17f); canvas.drawCircle(12f, 7f, .4f, paint) }
                "star", "star_filled" -> {
                    if (name == "star_filled") paint.style = Paint.Style.FILL
                    path(12f, 3f, 14.8f, 8.7f, 21f, 9.6f, 16.5f, 14f, 17.6f, 20.5f, 12f, 17.5f, 6.4f, 20.5f, 7.5f, 14f, 3f, 9.6f, 9.2f, 8.7f, 12f, 3f)
                    paint.style = Paint.Style.STROKE
                }
                else -> { path(3f, 17f, 9f, 11f, 13f, 14f, 21f, 5f); path(15f, 5f, 21f, 5f, 21f, 11f) }
            }
            canvas.restoreToCount(saved)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
        @Deprecated("Drawable opacity is no longer used") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
