package com.honestlockout.app.data.db

enum class BlockMode { ALWAYS, BUDGET, SCHEDULE }

/** LISTEN (added after the spec): background audio; the app may sit in front only briefly. */
enum class ActivityShape { TYPING, READING, PASSIVE, ANY, LISTEN }

enum class EndReason { DONE, TIMEOUT, CONFESSED, STRIKE, TAMPER, BREAK_GLASS }

enum class StrikeReason { OFF_APP, OFF_SCREEN, WRONG_SHAPE, CHECKIN_MISSED, THIRD_WARNING, TAMPER, BREAK_GLASS, APPEAL_REVERSED }

enum class ScreenAction { BLOCK, ALLOW }

enum class Strictness { LENIENT, NORMAL, STRICT }
