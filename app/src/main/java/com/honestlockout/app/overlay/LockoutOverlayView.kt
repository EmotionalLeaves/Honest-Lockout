package com.honestlockout.app.overlay

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.honestlockout.app.R

/** Lines shown on the lockout screen after a strike. */
data class LockoutInfo(
    val title: String,
    val reasonLine: String,
    val appLockedLine: String?,
    val overridesReturnLine: String?,
)

/** Full-screen notice after a strike. OK (and Back) go HOME. */
class LockoutOverlayView(
    context: Context,
    info: LockoutInfo,
    onOk: () -> Unit,
) : BackAwareFrameLayout(context, onOk) {
    init {
        LayoutInflater.from(context).inflate(R.layout.overlay_notice, this, true)
        findViewById<TextView>(R.id.notice_title).text = info.title
        findViewById<TextView>(R.id.notice_line1).text = info.reasonLine
        setLine(R.id.notice_line2, info.appLockedLine)
        setLine(R.id.notice_line3, info.overridesReturnLine)
        findViewById<Button>(R.id.notice_button).apply {
            setText(R.string.lockout_ok)
            setOnClickListener { onOk() }
        }
        isClickable = true
    }

    private fun setLine(id: Int, text: String?) {
        findViewById<TextView>(id).apply { if (text == null) visibility = View.GONE else this.text = text }
    }
}

/** Covers this app's App info / Accessibility / Device admin pages in Settings. Go back (and Back) = BACK. */
class ChangeQueuedView(
    context: Context,
    onGoBack: () -> Unit,
) : BackAwareFrameLayout(context, onGoBack) {
    init {
        LayoutInflater.from(context).inflate(R.layout.overlay_notice, this, true)
        findViewById<TextView>(R.id.notice_title).apply {
            setText(R.string.guard_title)
            setTextColor(context.getColor(R.color.hl_warning))
        }
        findViewById<TextView>(R.id.notice_line1).setText(R.string.guard_body)
        findViewById<TextView>(R.id.notice_line2).visibility = View.GONE
        findViewById<TextView>(R.id.notice_line3).visibility = View.GONE
        findViewById<Button>(R.id.notice_button).apply {
            setText(R.string.guard_go_back)
            setOnClickListener { onGoBack() }
        }
        isClickable = true
    }
}
