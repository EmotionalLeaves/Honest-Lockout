package com.honestlockout.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.honestlockout.app.R
import kotlin.math.abs

/**
 * Small draggable circle with the minutes left. Tap shows an "I'm done" button; tap again hides it.
 * Only shown when Settings.canDrawOverlays() is true.
 */
@SuppressLint("ClickableViewAccessibility")
class TimerBubbleView(
    context: Context,
    private val windowManager: WindowManager,
    onDone: () -> Unit,
) : FrameLayout(context) {

    private val minutes: TextView
    private val done: Button
    var params: WindowManager.LayoutParams? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.overlay_bubble, this, true)
        minutes = findViewById(R.id.bubble_minutes)
        done = findViewById(R.id.bubble_done)
        done.setOnClickListener { onDone() }
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        minutes.setOnTouchListener { _, e ->
            val p = params ?: return@setOnTouchListener false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = p.x; startY = p.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (abs(dx) > 12 || abs(dy) > 12) moved = true
                    if (moved) {
                        p.x = startX + dx.toInt(); p.y = startY + dy.toInt()
                        runCatching { windowManager.updateViewLayout(this, p) }
                    }
                }
                MotionEvent.ACTION_UP -> if (!moved) toggle()
            }
            true
        }
    }

    private fun toggle() {
        done.visibility = if (done.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    fun setMinutesLeft(m: Int) {
        minutes.text = context.getString(R.string.bubble_minutes, m)
    }
}
