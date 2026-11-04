package com.honestlockout.app.overlay

import android.content.Context
import android.os.Build
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import com.honestlockout.app.R

/** Root for every overlay: Back does the same as the overlay's primary dismiss button. */
open class BackAwareFrameLayout(context: Context, private val onBack: () -> Unit) : FrameLayout(context) {

    // With targetSdk 36, Android 16 routes Back to OnBackInvokedCallback instead of KEYCODE_BACK.
    private var backCallback: Any? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Build.VERSION.SDK_INT >= 33) {
            val cb = OnBackInvokedCallback { onBack() }
            findOnBackInvokedDispatcher()?.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, cb)
            backCallback = cb
        }
    }

    override fun onDetachedFromWindow() {
        if (Build.VERSION.SDK_INT >= 33) {
            (backCallback as? OnBackInvokedCallback)?.let { findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(it) }
            backCallback = null
        }
        super.onDetachedFromWindow()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) onBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

/** Everything the lock screen shows, prepared by the accessibility service. */
data class LockInfo(
    val packageName: String,
    val appName: String,
    val headline: String?,
    val reasonLine: String,
    val nextOverrideLine: String,
    /** Null when an override may start; otherwise why not (shown instead of the button). */
    val refusal: String?,
)

/** Full-screen, opaque lock screen. Back = Close. */
class LockOverlayView(
    context: Context,
    info: LockInfo,
    onOverride: () -> Unit,
    onClose: () -> Unit,
) : BackAwareFrameLayout(context, onClose) {
    init {
        LayoutInflater.from(context).inflate(R.layout.overlay_lock, this, true)
        findViewById<ImageView>(R.id.lock_icon).apply {
            val icon = runCatching { context.packageManager.getApplicationIcon(info.packageName) }.getOrNull()
            if (icon != null) setImageDrawable(icon) else visibility = View.GONE
        }
        findViewById<TextView>(R.id.lock_app_name).text = info.appName
        findViewById<TextView>(R.id.lock_headline).apply {
            if (info.headline != null) { text = info.headline; visibility = View.VISIBLE }
        }
        findViewById<TextView>(R.id.lock_reason).text = info.reasonLine
        findViewById<TextView>(R.id.lock_next).text = info.refusal ?: info.nextOverrideLine
        findViewById<Button>(R.id.lock_override).apply {
            if (info.refusal != null) visibility = View.GONE
            setOnClickListener { onOverride() }
        }
        findViewById<Button>(R.id.lock_close).setOnClickListener { onClose() }
        isClickable = true // swallow touches so the blocked app behind cannot respond
    }
}
