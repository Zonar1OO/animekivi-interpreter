package com.animekivi.livetranslate

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var caption: TextView? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        caption = TextView(this).apply {
            text = "ПереводчикKivi готов к переводу"
            setTextColor(Color.WHITE)
            textSize = 17f
            setPadding(24, 16, 24, 16)
            setBackgroundColor(0xDD111827.toInt())
            gravity = Gravity.CENTER
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = 100 }
        try { wm.addView(caption, params) } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra("caption")?.let { updateCaption(it) }
        return START_NOT_STICKY
    }

    private fun updateCaption(text: String) {
        caption?.post { caption?.text = text }
    }

    override fun onDestroy() {
        try { caption?.let { wm.removeView(it) } } catch (_: Exception) {}
        caption = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
