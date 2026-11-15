package com.vinay.nexolock.protection

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * A single opaque window placed above everything before the lock activity is
 * launched. It hides the protected app during the launch transition and also
 * satisfies the documented "visible overlay" requirement for background
 * activity starts on Android 15+.
 */
class ShieldOverlay(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null

    fun show() {
        if (view != null) return
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#070A0F"))
            addView(
                TextView(context).apply {
                    text = "NEXO LOCK"
                    setTextColor(ContextCompat.getColor(context, com.vinay.nexolock.R.color.nexo_mint))
                    textSize = 18f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    letterSpacing = 0.28f
                },
            )
            addView(
                TextView(context).apply {
                    text = "Restoring protection"
                    setTextColor(Color.parseColor("#8A94A6"))
                    textSize = 12f
                },
            )
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            },
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_SECURE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE,
        )
        params.gravity = Gravity.TOP or Gravity.START
        runCatching { windowManager.addView(layout, params) }
            .onSuccess { view = layout }
    }

    fun hide() {
        val current = view ?: return
        view = null
        runCatching { windowManager.removeView(current) }
    }
}
