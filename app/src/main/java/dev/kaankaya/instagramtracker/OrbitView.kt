package dev.kaankaya.instagramtracker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class OrbitView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = RectF()
    override fun onDraw(canvas: Canvas) {
        val size = minOf(width, height).toFloat()
        val cx = width / 2f; val cy = height / 2f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * .004f
        for (factor in listOf(.31f, .42f)) {
            paint.color = AppUi.line
            canvas.drawCircle(cx, cy, size * factor, paint)
        }
        paint.color = AppUi.violet
        paint.strokeWidth = size * .012f
        val r = size * .31f
        arc.set(cx-r, cy-r, cx+r, cy+r)
        canvas.drawArc(arc, 220f, 96f, false, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(255, 151, 110)
        canvas.drawCircle(cx + size*.42f*cos(-.45f), cy + size*.42f*sin(-.45f), size*.035f, paint)
        paint.color = AppUi.violet
        canvas.drawCircle(cx-size*.25f, cy+size*.183f, size*.028f, paint)
        paint.color = AppUi.ink
        paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = size*.12f
        canvas.drawText(context.getString(R.string.welcome_hello), cx, cy+size*.04f, paint)
        paint.textSize = size*.038f
        paint.color = AppUi.muted
        canvas.drawText(context.getString(R.string.welcome_circle), cx, cy+size*.13f, paint)
    }
}
