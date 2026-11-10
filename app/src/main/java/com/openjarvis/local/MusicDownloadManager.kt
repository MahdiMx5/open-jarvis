package com.openjarvis.local

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

object MusicDownloadManager {
    private val http = OkHttpClient.Builder()
        .callTimeout(35, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    data class DownloadResult(val title: String, val alreadyPresent: Boolean, val downloadId: Long = -1L)

    suspend fun download(context: Context, requestedTitle: String): Result<DownloadResult> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val query = requestedTitle.trim()
                require(query.length >= 2) { "نام آهنگ مشخص نیست." }

                if (!hasLibraryPermission(context)) {
                    throw IllegalStateException("برای بررسی آهنگ‌های موجود، اجازهٔ دسترسی به فایل‌های صوتی لازم است.")
                }
                if (isAlreadyInLibrary(context, query)) {
                    return@runCatching DownloadResult(query, true)
                }

                val searchUrl = "https://archive.org/advancedsearch.php?q=" +
                    URLEncoder.encode("title:($query) AND mediatype:audio", "UTF-8") +
                    "&fl[]=identifier&fl[]=title&rows=10&page=1&output=json"
                val searchJson = getJson(searchUrl)
                val docs = searchJson.optJSONObject("response")?.optJSONArray("docs")
                    ?: throw IllegalStateException("نتیجه‌ای برای این آهنگ پیدا نشد.")

                val candidates = (0 until docs.length()).mapNotNull { index ->
                    docs.optJSONObject(index)?.let { it.optString("identifier") to it.optString("title") }
                }.filter { it.first.isNotBlank() && it.second.isNotBlank() }
                    .sortedByDescending { (_, title) -> titleMatchScore(query, title) }

                for ((identifier, title) in candidates) {
                    if (titleMatchScore(query, title) < minimumMatchScore(query)) continue
                    if (isAlreadyInLibrary(context, title)) {
                        return@runCatching DownloadResult(title, true)
                    }

                    val metadata = getJson("https://archive.org/metadata/$identifier")
                    val metadataObject = metadata.optJSONObject("metadata") ?: continue
                    val license = metadataObject.optString("licenseurl").lowercase(Locale.ROOT)
                    val allowedLicense = license.contains("creativecommons.org/licenses/") ||
                        license.contains("creativecommons.org/publicdomain/")
                    if (!allowedLicense) continue

                    val files = metadata.optJSONArray("files") ?: continue
                    val audioFile = (0 until files.length()).mapNotNull { files.optJSONObject(it) }
                        .filter { file ->
                            val name = file.optString("name")
                            val format = file.optString("format").lowercase(Locale.ROOT)
                            val privateFile = file.optString("private").equals("true", true)
                            val size = file.optString("size").toLongOrNull() ?: 0L
                            name.endsWith(".mp3", true) && format.contains("mp3") &&
                                !privateFile && size in 1..MAX_FILE_SIZE
                        }
                        .minByOrNull { it.optString("size").toLongOrNull() ?: Long.MAX_VALUE }
                        ?: continue

                    val fileName = audioFile.optString("name")
                    val safeName = sanitizeFileName(fileName)
                    val fileUrl = "https://archive.org/download/$identifier/${Uri.encode(fileName)}"
                    val request = DownloadManager.Request(Uri.parse(fileUrl))
                        .setTitle(title)
                        .setDescription("دانلود موسیقی با مجوز آزاد — جارویس")
                        .setMimeType("audio/mpeg")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setAllowedOverMetered(true)
                        .setAllowedOverRoaming(false)
                        .setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_MUSIC,
                            "Jarvis/$safeName"
                        )

                    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val downloadId = manager.enqueue(request)
                    return@runCatching DownloadResult(title, false, downloadId)
                }

                throw IllegalStateException("فایل MP3 با تطبیق کافی و مجوز دانلود پیدا نشد؛ برای جلوگیری از دانلود آهنگ اشتباه، چیزی دانلود نکردم.")
            }
        }

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "OpenJarvis-Android/1.0")
            .header("Accept", "application/json")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("جست‌وجوی موسیقی ناموفق بود (HTTP ${response.code}).")
            return JSONObject(response.body?.string() ?: throw IllegalStateException("پاسخ جست‌وجو خالی بود."))
        }
    }

    private fun hasLibraryPermission(context: Context): Boolean {
        val permission = if (android.os.Build.VERSION.SDK_INT >= 33) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return androidx.core.content.ContextCompat.checkSelfPermission(
            context, permission
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /**
     * MediaStore indexes music from all visible shared-storage folders and external volumes.
     * This includes the phone's shared internal storage and removable SD-card volumes.
     * Android-private app folders and folders hidden with .nomedia are intentionally not scanned.
     */
    private fun isAlreadyInLibrary(context: Context, remoteTitle: String): Boolean {
        val queryTokens = normalize(remoteTitle).split(' ').filter { it.length > 1 }.toSet()
        if (queryTokens.isEmpty()) return false

        val projection = arrayOf(
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DISPLAY_NAME
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val volumes = runCatching { MediaStore.getExternalVolumeNames(context) }
            .getOrElse { setOf(MediaStore.VOLUME_EXTERNAL) }

        for (volume in volumes) {
            val collection = MediaStore.Audio.Media.getContentUri(volume)
            val cursor: Cursor? = runCatching {
                context.contentResolver.query(collection, projection, selection, null, null)
            }.getOrNull()

            cursor.use { rows ->
                if (rows == null) continue
                val titleIndex = rows.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistIndex = rows.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val nameIndex = rows.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)

                while (rows.moveToNext()) {
                    val title = if (titleIndex >= 0) rows.getString(titleIndex).orEmpty() else ""
                    val artist = if (artistIndex >= 0) rows.getString(artistIndex).orEmpty() else ""
                    val fileName = if (nameIndex >= 0) rows.getString(nameIndex).orEmpty() else ""
                    val titleTokens = normalize(title).split(' ').filter { it.length > 1 }.toSet()
                    val combinedTokens = normalize("$title $artist $fileName").split(' ').filter { it.length > 1 }.toSet()

                    val allQueryWordsFound = queryTokens.all { it in combinedTokens }
                    val titleCoverage = queryTokens.count { it in titleTokens }.toFloat() / queryTokens.size
                    if (allQueryWordsFound || titleCoverage >= 0.8f) return true
                }
            }
        }
        return false
    }

    private fun titleMatchScore(query: String, title: String): Int {
        val queryTokens = normalize(query).split(' ').filter { it.length > 1 }.toSet()
        val titleTokens = normalize(title).split(' ').filter { it.length > 1 }.toSet()
        if (queryTokens.isEmpty() || titleTokens.isEmpty()) return 0
        return queryTokens.count { it in titleTokens } * 100 / queryTokens.size
    }

    private fun minimumMatchScore(query: String): Int =
        if (normalize(query).split(' ').count { it.length > 1 } <= 2) 100 else 60

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT)
            .replace(Regex("\\.(mp3|m4a|ogg|wav)$"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    private fun sanitizeFileName(value: String): String =
        value.substringAfterLast('/')
            .replace(Regex("[^\\p{L}\\p{N}._ -]"), "_")
            .take(120)
            .ifBlank { "jarvis_music.mp3" }

    private const val MAX_FILE_SIZE = 100L * 1024L * 1024L
}
