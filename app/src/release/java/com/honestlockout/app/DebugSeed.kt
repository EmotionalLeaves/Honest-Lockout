package com.honestlockout.app

import android.content.Intent

/** Release builds: no debug hooks. */
object DebugSeed {
    fun maybeSeed(@Suppress("UNUSED_PARAMETER") intent: Intent?) = Unit
}
