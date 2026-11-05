package dev.kaankaya.instagramtracker

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import android.widget.LinearLayout
import android.widget.SeekBar

/** Byte-window semantics are shared by playback and offline boundary tests. */
object StoryByteWindow {
    fun read(data: ByteArray?, position: Long, target: ByteArray, offset: Int, count: Int): Int {
        require(position >= 0 && offset >= 0 && count >= 0 && offset <= target.size - count)
        if (count == 0) return 0
        if (data == null || position >= data.size) return -1
        val copied = minOf(count.toLong(),data.size.toLong()-position).toInt()
        data.copyInto(target,offset,position.toInt(),position.toInt()+copied)
        return copied
    }
}

/** Owns one bounded decrypted file. No content provider, external player or plaintext temporary file. */
private class StoryDataSource(private var data: ByteArray?) : MediaDataSource() {
    @Synchronized override fun getSize() = data?.size?.toLong() ?: 0L
    @Synchronized override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int) =
        StoryByteWindow.read(data,position,buffer,offset,size)
    @Synchronized override fun close() { data = null }
}

class StoryVideoView(context: Context, bytes: ByteArray) : LinearLayout(context), AutoCloseable {
    private val main = Handler(Looper.getMainLooper())
    private val source = StoryDataSource(bytes)
    private val player = MediaPlayer()
    private val texture = TextureView(context)
    private val play = AppUi.button(context,context.getString(R.string.story_play),true,false) { toggle() }
    private val seek = SeekBar(context).apply { isEnabled = false; contentDescription = context.getString(R.string.story_seek) }
    private val status = AppUi.text(context,"",12f,AppUi.muted)
    private var surface: Surface? = null
    private var prepared = false
    private var closed = false
    private var seeking = false
    private var started = false
    private val pulse = object : Runnable {
        override fun run() {
            if (!closed && prepared) {
                try { if (!seeking) seek.progress = player.currentPosition; if (player.isPlaying) main.postDelayed(this,500) }
                catch (_: IllegalStateException) { failed() }
            }
        }
    }
    init {
        orientation = VERTICAL
        val height = (minOf(420,resources.configuration.screenHeightDp/2) * resources.displayMetrics.density).toInt()
        addView(texture,LayoutParams(-1,height)); addView(play); addView(seek); addView(status)
        player.setOnPreparedListener {
            if (!closed) {
                it.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                prepared = true; play.isEnabled = true; seek.isEnabled = true
                seek.max = maxOf(1,it.duration); status.text = context.getString(R.string.story_video_ready)
            }
        }
        player.setOnCompletionListener { if (!closed) { play.text = context.getString(R.string.story_play); main.removeCallbacks(pulse) } }
        player.setOnErrorListener { _, _, _ -> failed(); true }
        texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(value: SurfaceTexture, width: Int, height: Int) {
                if (closed || started) return
                started = true
                try { surface = Surface(value); player.setSurface(surface); player.setDataSource(source); player.prepareAsync() }
                catch (_: Exception) { failed() }
            }
            override fun onSurfaceTextureSizeChanged(value: SurfaceTexture,width: Int,height: Int) = Unit
            override fun onSurfaceTextureUpdated(value: SurfaceTexture) = Unit
            override fun onSurfaceTextureDestroyed(value: SurfaceTexture): Boolean { close(); return true }
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar,progress: Int,fromUser: Boolean) = Unit
            override fun onStartTrackingTouch(bar: SeekBar) { seeking = true }
            override fun onStopTrackingTouch(bar: SeekBar) {
                seeking = false
                if (!closed && prepared) try { player.seekTo(bar.progress) } catch (_: IllegalStateException) { failed() }
            }
        })
    }
    private fun toggle() {
        if (closed || !prepared) return
        try {
            if (player.isPlaying) { player.pause(); play.text = context.getString(R.string.story_play); main.removeCallbacks(pulse) }
            else { player.start(); play.text = context.getString(R.string.story_pause); main.post(pulse) }
        } catch (_: IllegalStateException) { failed() }
    }
    private fun failed() {
        if (closed) return
        status.text = context.getString(R.string.story_video_error); close()
    }
    override fun close() {
        if (closed) return
        closed = true; prepared = false; main.removeCallbacksAndMessages(null)
        play.isEnabled = false; seek.isEnabled = false
        player.setOnPreparedListener(null); player.setOnCompletionListener(null); player.setOnErrorListener(null)
        player.release(); source.close(); surface?.release(); surface = null
    }
}
