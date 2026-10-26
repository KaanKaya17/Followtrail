package dev.kaankaya.instagramtracker

import android.content.Context
import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Matrix
import android.graphics.LinearGradient
import android.graphics.Shader
import android.view.View
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date

/** Plots only actual observations, spaced by their observation times. No inferred daily samples. */
@SuppressLint("ViewConstructor") // Created programmatically with an explicit observation series; never inflated from XML.
class HistoryChartView(context: Context, private val observations: List<Profile>, private val followers: Boolean,
    private val accent: Int, private val muted: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val fill = Path()
    private val areaMatrix = Matrix()
    private val areaGradient = LinearGradient(0f, 0f, 0f, 1f, (accent and 0x00ffffff) or 0x28000000,
        accent and 0x00ffffff, Shader.TileMode.CLAMP)
    private val values = observations.map { if (followers) it.followers else it.following }
    private val number = NumberFormat.getIntegerInstance(AppLanguage.locale(context))
    private fun date(time: Long) = SimpleDateFormat("d MMM HH:mm", AppLanguage.locale(context)).format(Date(time))
    init {
        contentDescription = (if (followers) context.getString(R.string.ui_followers) else context.getString(R.string.ui_following)) + context.getString(R.string.ui_history_chart_horizontal_axis_observation_time_vertical) +
            observations.zip(values).joinToString("; ") { (p, v) -> "${date(p.observedAt)}: ${number.format(v)}" }
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (observations.size < 2) return
        val checkpoint = canvas.save()
        canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())
        val plotWidth = width - paddingLeft - paddingRight
        val plotHeight = height - paddingTop - paddingBottom
        val d = resources.displayMetrics.density
        val low = values.min(); val high = values.max()
        paint.textSize = 11 * resources.displayMetrics.scaledDensity
        val left = maxOf(52 * d, paint.measureText(number.format(high)) + 8 * d)
        val right = plotWidth - 12 * d; val top = 25 * d; val bottom = plotHeight - 38 * d
        if (right <= left || bottom <= top) { canvas.restoreToCount(checkpoint); return }
        fun x(index: Int) = left + ((observations[index].observedAt - observations.first().observedAt).toDouble() /
            (observations.last().observedAt - observations.first().observedAt).coerceAtLeast(1)).toFloat() * (right - left)
        fun y(value: Long) = if (low == high) (top + bottom) / 2 else bottom - ((value - low).toDouble() / (high - low)).toFloat() * (bottom - top)
        paint.color = AppUi.line; paint.strokeWidth = d
        for (i in 0..3) { val gridY = top + (bottom - top) * i / 3; canvas.drawLine(left, gridY, right, gridY, paint) }
        paint.color = muted; paint.strokeWidth = d
        canvas.drawLine(left, top, left, bottom, paint); canvas.drawLine(left, bottom, right, bottom, paint)
        canvas.drawText(number.format(high), 0f, y(high) + 4 * d, paint)
        if (low != high) canvas.drawText(number.format(low), 0f, bottom, paint)
        canvas.drawText(context.getString(R.string.ui_account_count), 0f, 12 * d, paint)
        canvas.drawText(date(observations.first().observedAt), left, bottom + 17 * d, paint)
        val end = date(observations.last().observedAt)
        canvas.drawText(end, maxOf(left, right - paint.measureText(end)), bottom + 32 * d, paint)
        path.reset()
        values.forEachIndexed { i, value -> if (i == 0) path.moveTo(x(i), y(value)) else path.lineTo(x(i), y(value)) }
        fill.set(path); fill.lineTo(x(values.lastIndex), bottom); fill.lineTo(x(0), bottom); fill.close()
        areaMatrix.setScale(1f, bottom - top); areaMatrix.postTranslate(0f, top); areaGradient.setLocalMatrix(areaMatrix)
        paint.shader = areaGradient
        canvas.drawPath(fill, paint); paint.shader = null
        paint.color = accent; paint.strokeWidth = 2.5f * d; paint.style = Paint.Style.STROKE
        canvas.drawPath(path, paint); paint.style = Paint.Style.FILL
        values.forEachIndexed { i, value -> canvas.drawCircle(x(i), y(value), 3 * d, paint) }
        canvas.restoreToCount(checkpoint)
    }
}
