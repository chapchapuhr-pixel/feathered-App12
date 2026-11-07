package com.unera.social.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.unera.social.model.Song
import com.unera.social.model.SongStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID

/**
 * SongsApiService
 *
 * Implements live REST integration for F-Music matching web MusicSystem / UploadStudio:
 * 1. GET /api/songs - Fetch all live songs from the backend
 * 2. POST /api/songs - Create a song record with title, artist_name, album_name, cover_image_url, audio_url, genre
 * 3. DELETE /api/songs/{id}?user_id={id} - Delete an uploaded track
 * 4. uploadCompressedCover - Compresses cover artwork to WebP (max 900px, quality 82) and uploads to /api/upload
 */
object SongsApiService {

    const val DEFAULT_MUSIC_COVER = "https://media.featheredsocial.site/task_01kftb3024ed7bm84gy6j485fh_1769336848_img_0.webp"
    private const val BASE_URL = "https://featheredsocial.site"
    private const val USER_AGENT = "FeatheredAndroid/1.0 (Linux; Android)"

    /**
     * Fetch all songs from GET /api/songs
     */
    suspend fun fetchSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songsList = mutableListOf<Song>()
        try {
            val url = URL("$BASE_URL/api/songs")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 12000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
            }

            if (conn.responseCode in 200..299) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }.trim()
                if (responseText.startsWith("{")) {
                    val root = JSONObject(responseText)
                    val arr = root.optJSONArray("songs")
                        ?: root.optJSONArray("data")
                        ?: root.optJSONArray("results")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            parseSong(obj)?.let { songsList.add(it) }
                        }
                    }
                } else if (responseText.startsWith("[")) {
                    val arr = JSONArray(responseText)
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        parseSong(obj)?.let { songsList.add(it) }
                    }
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        songsList
    }

    /**
     * Post song to POST /api/songs
     */
    suspend fun createSong(
        uploaderId: Long,
        title: String,
        artistName: String,
        albumName: String,
        coverImageUrl: String,
        audioUrl: String,
        genre: String?,
        token: String? = null
    ): Song? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/api/songs")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                if (!token.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
                setRequestProperty("x-user-id", uploaderId.toString())
            }

            val payload = JSONObject().apply {
                put("uploader_id", uploaderId)
                put("title", title.trim())
                put("artist_name", artistName.trim())
                put("album_name", albumName.trim().ifBlank { "Single" })
                put("cover_image_url", coverImageUrl.trim())
                put("audio_url", audioUrl.trim())
                put("duration_seconds", JSONObject.NULL)
                if (!genre.isNullOrBlank()) {
                    put("genre", genre.trim())
                } else {
                    put("genre", JSONObject.NULL)
                }
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(payload.toString())
                it.flush()
            }

            if (conn.responseCode in 200..299) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }.trim()
                conn.disconnect()
                if (responseText.startsWith("{")) {
                    val root = JSONObject(responseText)
                    val songObj = root.optJSONObject("data")
                        ?: root.optJSONObject("song")
                        ?: root
                    return@withContext parseSong(songObj)
                }
            } else {
                conn.disconnect()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    /**
     * Delete song at DELETE /api/songs/{id}?user_id={id}
     */
    suspend fun deleteSong(songId: String, userId: Long, token: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/api/songs/$songId?user_id=$userId")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                if (!token.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
                setRequestProperty("x-user-id", userId.toString())
            }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Compress cover image (max 900px, WebP 82%) and upload to /api/upload
     */
    suspend fun uploadCompressedCover(context: Context, imageUri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(imageUri) ?: return@withContext null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (originalBitmap == null) return@withContext null

            // Scale down to max 900px preserving aspect ratio (matching web calcCoverSize)
            val maxDimension = 900
            val width = originalBitmap.width
            val height = originalBitmap.height
            val scale = if (maxOf(width, height) > maxDimension) {
                maxDimension.toFloat() / maxOf(width, height).toFloat()
            } else 1.0f

            val targetWidth = maxOf(1, (width * scale).toInt())
            val targetHeight = maxOf(1, (height * scale).toInt())
            val scaledBitmap = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true)
            } else {
                originalBitmap
            }

            val baos = ByteArrayOutputStream()
            // Compress as WebP at 82% quality (matching web compressCoverImage)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                scaledBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 82, baos)
            } else {
                @Suppress("DEPRECATION")
                scaledBitmap.compress(Bitmap.CompressFormat.WEBP, 82, baos)
            }
            val imageBytes = baos.toByteArray()

            val token = UserSessionManager.getAuthToken(context)
            val url = URL("$BASE_URL/api/upload")
            val boundary = "Boundary-" + UUID.randomUUID().toString()

            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                doInput = true
                useCaches = false
                connectTimeout = 30000
                readTimeout = 30000
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                if (!token.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
            }

            val fileName = "cover_${System.currentTimeMillis()}.webp"
            val output = conn.outputStream

            val header = "--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n" +
                "Content-Type: image/webp\r\n\r\n"
            output.write(header.toByteArray(Charsets.UTF_8))
            output.write(imageBytes)
            output.write("\r\n".toByteArray(Charsets.UTF_8))
            output.write("--$boundary--\r\n".toByteArray(Charsets.UTF_8))
            output.flush()
            output.close()

            if (conn.responseCode in 200..299) {
                val text = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }.trim()
                if (text.startsWith("{")) {
                    val json = JSONObject(text)
                    val uploaded = json.optJSONObject("uploaded")
                    val origUrl = uploaded?.optJSONObject("original")?.optString("url")
                        ?: json.optString("url")
                        ?: json.optString("file_url")
                    if (origUrl.isNotBlank()) {
                        conn.disconnect()
                        return@withContext origUrl
                    }
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    /**
     * Parse song row from API matching web mapSongFromApi
     */
    fun parseSong(obj: JSONObject): Song? {
        val id = if (obj.has("id")) obj.optString("id") else null
        if (id.isNullOrBlank()) return null

        val title = obj.optString("title").takeIf { it.isNotBlank() && it != "null" } ?: "Untitled"
        val artist = obj.optString("artist_name").takeIf { it.isNotBlank() && it != "null" }
            ?: obj.optString("artist").takeIf { it.isNotBlank() && it != "null" }
            ?: "Unknown Artist"

        var cover = obj.optString("cover_image_url").takeIf { it.isNotBlank() && it != "null" }
            ?: obj.optString("cover").takeIf { it.isNotBlank() && it != "null" }
            ?: DEFAULT_MUSIC_COVER

        if (!cover.startsWith("http") || cover.contains("ui-avatars.com")) {
            cover = DEFAULT_MUSIC_COVER
        }

        val audioUrl = obj.optString("audio_url").takeIf { it.isNotBlank() && it != "null" }
            ?: obj.optString("audioUrl").takeIf { it.isNotBlank() && it != "null" }
            ?: ""

        val durationSec = if (obj.has("duration_seconds") && !obj.isNull("duration_seconds")) {
            obj.optInt("duration_seconds", 180)
        } else if (obj.has("duration") && !obj.isNull("duration")) {
            val d = obj.opt("duration")
            if (d is Number) d.toInt() else 180
        } else 180

        val durationStr = if (obj.has("duration") && obj.opt("duration") is String) {
            obj.optString("duration")
        } else {
            String.format(Locale.US, "%d:%02d", durationSec / 60, durationSec % 60)
        }

        val uploaderId = obj.optLong("uploader_id", obj.optLong("uploaderId", 0L))
        val uploadDate = obj.optString("created_at").takeIf { it.isNotBlank() && it != "null" }
            ?: obj.optString("uploadDate", "")
        val genre = obj.optString("genre").takeIf { it.isNotBlank() && it != "null" } ?: "Afrobeats"
        val album = obj.optString("album_name").takeIf { it.isNotBlank() && it != "null" }
            ?: obj.optString("album").takeIf { it.isNotBlank() && it != "null" }
            ?: "Single"

        val isVerified = obj.optBoolean("is_verified", obj.optBoolean("isVerified", false))

        val statsObj = obj.optJSONObject("stats")
        val plays = obj.optLong("plays_count", obj.optLong("plays", statsObj?.optLong("plays", 0L) ?: 0L))
        val likes = obj.optLong("likes_count", obj.optLong("likes", statsObj?.optLong("likes", 0L) ?: 0L))
        val shares = obj.optLong("shares_count", obj.optLong("shares", statsObj?.optLong("shares", 0L) ?: 0L))
        val downloads = obj.optLong("downloads_count", obj.optLong("downloads", statsObj?.optLong("downloads", 0L) ?: 0L))
        val reelsUse = obj.optLong("reels_use_count", obj.optLong("reelsUse", statsObj?.optLong("reelsUse", 0L) ?: 0L))

        return Song(
            id = id,
            title = title,
            artist = artist,
            cover = cover,
            audioUrl = audioUrl,
            duration = durationStr,
            durationSeconds = durationSec,
            uploaderId = uploaderId,
            uploadDate = uploadDate,
            genre = genre,
            album = album,
            isVerified = isVerified,
            stats = SongStats(
                plays = plays,
                likes = likes,
                shares = shares,
                downloads = downloads,
                reelsUse = reelsUse
            )
        )
    }
}
