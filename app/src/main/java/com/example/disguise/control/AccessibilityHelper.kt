package com.example.disguise.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs

/**
 * AccessibilityHelper — خدمة إمكانية الوصول (AccessibilityService).
 *
 * هذه هي "الأيدي" التي تنفذ النقر والسحب والكتابة على الشاشة.
 *
 * المسؤوليات:
 * 1. tap(x, y)        — نقرة في إحداثيات محددة.
 * 2. swipe(x1, y1, x2, y2, ms) — سحب.
 * 3. setText(text)    — كتابة نص في الحقل المركّز.
 * 4. globalAction(a)  — زر الرجوع/الرئيسية/التطبيقات الحديثة/الإشعارات.
 * 5. dump()           — قراءة شجرة الشاشة (موجودة أيضاً في ScreenReader،
 *                        لكن نضع نسخة احتياطية هنا للاستقلالية).
 *
 * قيود Android 11+:
 * - يجب أن يفعّل المستخدم الخدمة يدوياً من الإعدادات (لا يمكن برمجياً).
 * - dispatchGesture متاح من API 24+ (لدينا minSdk 30، فلا مشكلة).
 * - بعض التطبيقات المحمية (مثل البنوك) تمنع الوصول لشجرة العناصر.
 *
 * ملاحظة: هذه الخدمة تعمل في الخلفية باستمرار بعد تفعيلها.
 * أي تغيير في الإعدادات أو الشاشة يُطلق onAccessibilityEvent، لكننا
 * لا نستخدمه هنا (ScreenReader قد يستخدمه). نكتفي بالتنفيذ المباشر.
 */
class AccessibilityHelper : AccessibilityService() {

    companion object {
        private const val TAG = "AccessibilityHelper"

        // ثوابت globalAction (متطابقة مع AccessibilityService)
        const val ACTION_BACK = GLOBAL_ACTION_BACK
        const val ACTION_HOME = GLOBAL_ACTION_HOME
        const val ACTION_RECENTS = GLOBAL_ACTION_RECENTS
        const val ACTION_NOTIFICATIONS = GLOBAL_ACTION_NOTIFICATIONS
        const val ACTION_LOCK_SCREEN = GLOBAL_ACTION_LOCK_SCREEN

        /**
         * مرجع ثابت للخدمة، حتى تتمكن CommandHandler من استخدامها.
         * يُضبط في onServiceConnected، ويُمسح في onDestroy.
         *
         * تحذير: هذا مرجع static، لكنه آمن لأن AccessibilityService
         * تعيش طوال عمر التطبيق تقريباً (بعد تفعيلها).
         */
        @Volatile
        var instance: AccessibilityHelper? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "AccessibilityService connected")
    }

    override fun onDestroy() {
        instance = null
        Log.d(TAG, "AccessibilityService destroyed")
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // لا نستخدم الأحداث — نعتمد على التنفيذ المباشر.
    }

    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt")
    }

    // ============================================================
    // 1. النقر
    // ============================================================

    /**
     * نقرة في إحداثيات (x, y).
     * نستخدم dispatchGesture مع مسار نقطة واحدة ومدة قصيرة جداً.
     */
    fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x, y)  // مسار بنقطة واحدة (يجب أن يكون له بداية ونهاية)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 50L)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        val ok = dispatchGesture(gesture, null, null)
        Log.d(TAG, "tap($x, $y) -> $ok")
        return ok
    }

    // ============================================================
    // 2. السحب
    // ============================================================

    /**
     * سحب من (x1, y1) إلى (x2, y2) خلال durationMs مللي ثانية.
     */
    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 300L): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        val ok = dispatchGesture(gesture, null, null)
        Log.d(TAG, "swipe($x1,$y1 -> $x2,$y2, $durationMs ms) -> $ok")
        return ok
    }

    // ============================================================
    // 3. الكتابة (setText)
    // ============================================================

    /**
     * كتابة نص في الحقل المركّز (EditText).
     *
     * الطريقة:
     * 1. البحث عن العقدة المركّزة (findFocus(FOCUS_INPUT)).
     * 2. إذا لم توجد، البحث في الشجرة عن أي EditText.
     * 3. استخدام ACTION_SET_TEXT.
     *
     * تعيد true إذا نجحت.
     */
    fun setText(text: String): Boolean {
        val root = rootInActiveWindow ?: run {
            Log.w(TAG, "setText: rootInActiveWindow is null")
            return false
        }

        // 1. الحقل المركّز
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && isEditable(focused)) {
            return applySetText(focused, text)
        }

        // 2. البحث في الشجرة
        val editable = findFirstEditable(root)
        if (editable != null) {
            return applySetText(editable, text)
        }

        Log.w(TAG, "setText: no editable field found")
        return false
    }

    /**
     * تطبيق ACTION_SET_TEXT على عقدة.
     */
    private fun applySetText(node: AccessibilityNodeInfo, text: String): Boolean {
        val args = android.os.Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
        }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        Log.d(TAG, "setText('$text') on ${node.className} -> $ok")
        return ok
    }

    /**
     * هل العقدة قابلة للتحرير؟
     */
    private fun isEditable(node: AccessibilityNodeInfo): Boolean {
        val cls = node.className?.toString() ?: ""
        if (cls.contains("EditText")) return true
        // بعض الحقول المخصصة لا تحمل اسم EditText لكنها editable
        return (node.actions and AccessibilityNodeInfo.ACTION_SET_TEXT) != 0
    }

    /**
     * البحث العميق (BFS) عن أول عقدة قابلة للتحرير.
     */
    private fun findFirstEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var safety = 0
        while (queue.isNotEmpty() && safety < 5000) {
            safety++
            val node = queue.removeFirst()
            if (isEditable(node)) return node
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                queue.add(child)
            }
        }
        return null
    }

    // ============================================================
    // 4. الأزرار العامة
    // ============================================================

    /**
     * تنفيذ globalAction (رجوع/رئيسية/حديثة/إشعارات/قفل).
     * تعيد true إذا نجحت.
     */
    fun globalAction(action: Int): Boolean {
        val ok = performGlobalAction(action)
        Log.d(TAG, "globalAction($action) -> $ok")
        return ok
    }

    // ============================================================
    // 5. قراءة الشاشة (نسخة احتياطية)
    // ============================================================

    /**
     * قراءة شجرة الشاشة الحالية كنص.
     * هذه نسخة مبسطة؛ ScreenReader يعطي تفاصيل أكثر.
     */
    fun dump(): String {
        val root = rootInActiveWindow ?: return "(لا يمكن قراءة الشاشة)"
        val sb = StringBuilder()
        walk(root, sb, 0)
        return sb.toString()
    }

    private fun walk(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        if (depth > 30) return  // حماية من التسلسل اللانهائي

        val indent = "  ".repeat(depth)
        val cls = node.className?.toString()?.substringAfterLast('.') ?: "?"
        val text = node.text?.toString()?.take(80) ?: ""
        val desc = node.contentDescription?.toString()?.take(80) ?: ""
        val bounds = android.graphics.Rect().also { node.getBoundsInScreen(it) }

        sb.append(indent).append(cls)
        if (text.isNotBlank()) sb.append(" text=\"").append(text).append("\"")
        if (desc.isNotBlank()) sb.append(" desc=\"").append(desc).append("\"")
        sb.append(" [").append(bounds.left).append(",").append(bounds.top)
            .append("-").append(bounds.right).append(",").append(bounds.bottom).append("]")
        if (node.isClickable) sb.append(" clickable")
        if (node.isEditable) sb.append(" editable")
        if (node.isFocused) sb.append(" focused")
        sb.append("\n")

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            walk(child, sb, depth + 1)
        }
    }
}