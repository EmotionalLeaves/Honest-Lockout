package com.honestlockout.app.overlay

import android.content.Context
import android.text.Html
import android.text.TextUtils
import android.view.LayoutInflater
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.honestlockout.app.R

enum class BannerKind { WARNING, CHECKIN }

/**
 * Warning and check-in banner (top third of the screen, dim scrim below).
 * The banner window is not focusable so the user can keep typing in the app underneath; as a
 * result the Back key goes to that app rather than to the banner (an accepted exception).
 */
class WarningBannerView(
    context: Context,
    private val onPrimary: (BannerKind) -> Unit,
    private val onEnd: () -> Unit,
) : FrameLayout(context) {

    var kind: BannerKind = BannerKind.WARNING
        private set
    private val text: TextView
    private val seconds: TextView
    private val primary: Button

    init {
        LayoutInflater.from(context).inflate(R.layout.overlay_banner, this, true)
        text = findViewById(R.id.banner_text)
        seconds = findViewById(R.id.banner_seconds)
        primary = findViewById(R.id.banner_primary)
        primary.setOnClickListener { onPrimary(kind) }
        findViewById<Button>(R.id.banner_end).setOnClickListener { onEnd() }
    }

    fun bind(kind: BannerKind, statement: String, secondsLeft: Int, graceSec: Int) {
        this.kind = kind
        val safe = TextUtils.htmlEncode(statement)
        val html = when (kind) {
            BannerKind.WARNING -> context.getString(R.string.banner_warning_html, safe, graceSec)
            BannerKind.CHECKIN -> context.getString(R.string.banner_checkin_html, safe)
        }
        text.text = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
        primary.setText(if (kind == BannerKind.WARNING) R.string.banner_back_to_it else R.string.banner_yes)
        setSeconds(secondsLeft)
    }

    fun setSeconds(secondsLeft: Int) {
        seconds.text = context.getString(R.string.banner_seconds, secondsLeft)
    }
}
