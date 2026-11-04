package com.honestlockout.app.data.rules

import android.content.Context
import com.honestlockout.app.R
import com.honestlockout.app.data.db.ActivityShape
import com.honestlockout.app.data.db.daos.PurposeTemplateDao
import com.honestlockout.app.data.db.entities.PurposeTemplate

/*
 * The six built-in purpose templates. They are inserted with fixed ids 1..6 so code can
 * recognise them (Navigation and Pay stay allowed at 4+ strikes) without comparing
 * user-visible names. Names come from strings.xml at first run.
 * Custom has no fixed max in the spec ("user picks"); its starting max is 30 minutes.
 */
object PurposeTemplates {
    const val ID_NAVIGATION = 1L
    const val ID_REPLY = 2L
    const val ID_LOOKUP = 3L
    const val ID_WORK = 4L
    const val ID_PAY = 5L
    const val ID_CUSTOM = 6L

    /** Purposes still allowed while active strikes >= 4. */
    val allowedAtFourStrikes = setOf(ID_NAVIGATION, ID_PAY)

    fun builtIns(context: Context): List<PurposeTemplate> = listOf(
        PurposeTemplate(
            id = ID_NAVIGATION, name = context.getString(R.string.purpose_navigation),
            allowedPackages = "com.google.android.apps.maps,com.waze", blockedScreenLabels = "",
            expectedShape = ActivityShape.PASSIVE, maxMinutes = 60, builtIn = true,
        ),
        PurposeTemplate(
            id = ID_REPLY, name = context.getString(R.string.purpose_reply),
            allowedPackages = "", blockedScreenLabels = "feed,reels,stories,shorts,explore",
            expectedShape = ActivityShape.TYPING, maxMinutes = 10, builtIn = true,
        ),
        PurposeTemplate(
            id = ID_LOOKUP, name = context.getString(R.string.purpose_lookup),
            allowedPackages = "com.android.chrome,com.google.android.youtube", blockedScreenLabels = "shorts,home_feed",
            expectedShape = ActivityShape.READING, maxMinutes = 15, builtIn = true,
        ),
        PurposeTemplate(
            id = ID_WORK, name = context.getString(R.string.purpose_work),
            allowedPackages = "", blockedScreenLabels = "feed,reels,stories,shorts",
            expectedShape = ActivityShape.TYPING, maxMinutes = 30, builtIn = true,
        ),
        PurposeTemplate(
            id = ID_PAY, name = context.getString(R.string.purpose_pay),
            allowedPackages = "", blockedScreenLabels = "",
            expectedShape = ActivityShape.ANY, maxMinutes = 5, builtIn = true,
        ),
        PurposeTemplate(
            id = ID_CUSTOM, name = context.getString(R.string.purpose_custom),
            allowedPackages = "", blockedScreenLabels = "",
            expectedShape = ActivityShape.ANY, maxMinutes = 30, builtIn = true,
        ),
    )

    /** Inserts the built-ins on first run only; later edits by the user are kept. */
    suspend fun ensureInserted(context: Context, dao: PurposeTemplateDao) {
        if (dao.count() > 0) return
        builtIns(context).forEach { dao.insert(it) }
    }

    fun descriptionRes(id: Long): Int = when (id) {
        ID_NAVIGATION -> R.string.purpose_navigation_desc
        ID_REPLY -> R.string.purpose_reply_desc
        ID_LOOKUP -> R.string.purpose_lookup_desc
        ID_WORK -> R.string.purpose_work_desc
        ID_PAY -> R.string.purpose_pay_desc
        else -> R.string.purpose_custom_desc
    }

    fun split(csv: String): List<String> = csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}
