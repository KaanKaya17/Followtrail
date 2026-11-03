package dev.kaankaya.instagramtracker

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

@SuppressLint("ViewConstructor")
class DailyChartView(context: Context, private val series: DailySeries) : View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val path=Path()
    private val number=NumberFormat.getIntegerInstance(AppLanguage.locale(context))
    private fun date(at: Long)=SimpleDateFormat("d MMM yyyy",AppLanguage.locale(context)).apply { timeZone=TimeZone.getTimeZone("UTC") }.format(Date(at))
    init {
        contentDescription=context.getString(R.string.daily_title)+": "+series.points.joinToString("; ") { "${date(it.day)}: ${number.format(it.value)}" }
        importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val points=series.points;if(points.isEmpty())return
        val d=resources.displayMetrics.density
        val low=maxOf(0,(points.minOf { it.value }-5));val high=points.maxOf { it.value }+5
        paint.textSize=11*resources.displayMetrics.scaledDensity
        val left=maxOf(48*d,paint.measureText(number.format(high))+8*d);val right=width-12*d
        val top=24*d;val bottom=height-48*d
        if(right<=left || bottom<=top)return
        fun x(index: Int)=if(points.size==1)(left+right)/2 else left+((points[index].at-points.first().at).toDouble()/(points.last().at-points.first().at).coerceAtLeast(1)).toFloat()*(right-left)
        fun y(value: Long)=bottom-((value-low).toDouble()/(high-low).coerceAtLeast(1)).toFloat()*(bottom-top)
        paint.color=AppUi.line;paint.strokeWidth=d
        for(i in 0..3)canvas.drawLine(left,top+(bottom-top)*i/3,right,top+(bottom-top)*i/3,paint)
        paint.color=AppUi.muted
        canvas.drawText(number.format(high),0f,top+4*d,paint);canvas.drawText(number.format(low),0f,bottom,paint)
        canvas.drawText(date(points.first().day),left,bottom+20*d,paint)
        val end=date(points.last().day);canvas.drawText(end,maxOf(left,right-paint.measureText(end)),bottom+37*d,paint)
        path.reset()
        points.forEachIndexed { index,point ->
            if(index==0 || point.bucket!=points[index-1].bucket+1)path.moveTo(x(index),y(point.value)) else path.lineTo(x(index),y(point.value))
        }
        paint.color=AppUi.violet;paint.strokeWidth=2.5f*d;paint.style=Paint.Style.STROKE;canvas.drawPath(path,paint);paint.style=Paint.Style.FILL
        points.forEachIndexed { i,p -> canvas.drawCircle(x(i),y(p.value),3*d,paint) }
    }
}
