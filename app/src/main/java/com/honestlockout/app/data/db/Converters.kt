package com.honestlockout.app.data.db

import androidx.room.TypeConverter

/** Every enum is stored as its name (String). Unknown names fall back to a safe default. */
class Converters {
    @TypeConverter fun fromBlockMode(v: BlockMode?): String? = v?.name
    @TypeConverter fun toBlockMode(v: String?): BlockMode? =
        v?.let { runCatching { BlockMode.valueOf(it) }.getOrDefault(BlockMode.ALWAYS) }

    @TypeConverter fun fromShape(v: ActivityShape?): String? = v?.name
    @TypeConverter fun toShape(v: String?): ActivityShape? =
        v?.let { runCatching { ActivityShape.valueOf(it) }.getOrDefault(ActivityShape.ANY) }

    @TypeConverter fun fromEndReason(v: EndReason?): String? = v?.name
    @TypeConverter fun toEndReason(v: String?): EndReason? =
        v?.let { runCatching { EndReason.valueOf(it) }.getOrNull() }

    @TypeConverter fun fromStrikeReason(v: StrikeReason?): String? = v?.name
    @TypeConverter fun toStrikeReason(v: String?): StrikeReason? =
        v?.let { runCatching { StrikeReason.valueOf(it) }.getOrDefault(StrikeReason.TAMPER) }

    @TypeConverter fun fromScreenAction(v: ScreenAction?): String? = v?.name
    @TypeConverter fun toScreenAction(v: String?): ScreenAction? =
        v?.let { runCatching { ScreenAction.valueOf(it) }.getOrDefault(ScreenAction.BLOCK) }
}
