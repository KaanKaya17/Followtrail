package dev.kaankaya.instagramtracker

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import okhttp3.HttpUrl

/** Offline preview thumbnails; no sockets. Production source set contains no fixture fetcher. */
class DemoAvatarFetcher : AvatarFetcher {
    override fun open(url: HttpUrl) = object : AvatarDownload {
        override fun cancel() = Unit
        override fun read(): ByteArray {
            val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap); val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val warm = url.encodedPath.hashCode() % 2 == 0
            canvas.drawColor(if (warm) Color.rgb(255, 221, 193) else Color.rgb(205, 238, 227))
            paint.color = Color.rgb(83, 60, 138)
            canvas.drawCircle(48f, 35f, 17f, paint)
            canvas.drawRoundRect(17f, 58f, 79f, 112f, 26f, 26f, paint)
            val output = java.io.ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); bitmap.recycle()
            return output.toByteArray()
        }
    }
}
