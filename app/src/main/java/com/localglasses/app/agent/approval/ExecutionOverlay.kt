package com.localglasses.app.agent.approval

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Shows the executing step and a stop control over the target app.
 * The overlay is only usable with the display-over-other-apps permission.
 */
class ExecutionOverlay : Service() {

    private var view: View? = null
    private var label: TextView? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!Settings.canDrawOverlays(this)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                stopRequested = false
                show(intent.getStringExtra(EXTRA_STEP) ?: "Running plan")
            }
            ACTION_UPDATE -> label?.text = intent.getStringExtra(EXTRA_STEP) ?: ""
            ACTION_STOP -> {
                stopRequested = true
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        hide()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun show(step: String) {
        if (view != null) {
            label?.text = step
            return
        }
        val manager = getSystemService(WindowManager::class.java)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(32, 20, 32, 20)
            setBackgroundColor(Color.argb(230, 20, 20, 20))
        }
        val text = TextView(this).apply {
            this.text = step
            setTextColor(Color.WHITE)
            textSize = 16f
        }
        val stop = Button(this).apply {
            this.text = "Stop"
            setOnClickListener {
                stopRequested = true
                stopSelf()
            }
        }
        container.addView(
            text,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        container.addView(stop)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 96
        }
        runCatching {
            manager.addView(container, params)
            view = container
            label = text
        }
    }

    private fun hide() {
        view?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }
        view = null
        label = null
    }

    companion object {
        const val ACTION_START = "com.localglasses.app.agent.OVERLAY_START"
        const val ACTION_UPDATE = "com.localglasses.app.agent.OVERLAY_UPDATE"
        const val ACTION_STOP = "com.localglasses.app.agent.OVERLAY_STOP"
        const val EXTRA_STEP = "step"

        @Volatile
        var stopRequested: Boolean = false
            private set

        fun start(context: Context, step: String) {
            context.startService(
                Intent(context, ExecutionOverlay::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_STEP, step),
            )
        }

        fun update(context: Context, step: String) {
            context.startService(
                Intent(context, ExecutionOverlay::class.java)
                    .setAction(ACTION_UPDATE)
                    .putExtra(EXTRA_STEP, step),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ExecutionOverlay::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
