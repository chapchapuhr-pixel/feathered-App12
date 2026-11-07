package com.unera.social.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.unera.social.model.MediaMetaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * MediaUploadHelper
 *
 * Professional, high-performance client-side media processor for Android
 * matching the exact Feathered web pipeline:
 *
 * 1. Image Pipeline:
 *    - Dual/triple variant bundle (thumb ~320px 72%, feed ~1080px 82%, original/full ~1920px 88%)
 *    - EXIF orientation auto-rotation
 *    - Uploads as multipart bundle: thumbnail, feed, original to POST /api/upload
 *    - Target host: https://media.featheredsocial.site
 *
 * 2. Video Pipeline:
 *    - Thumbnail frame extraction at 1.0s (or 25% if < 1.5s)
 *    - Uploads thumbnail to posts/thumbnails
 *    - Streams video upload in 64KB chunks to posts/videos/720p with progress callback
 *    - Generates 4-slot media_urls [orig, 1080p, 720p, thumb]
 *    - Returns complete MediaMetaItem
 */
object MediaUploadHelper {

    private const val BASE_URL = "https://featheredsocial.site"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) Feathered/1.0"

    data class VideoMeta(
        val thumbnailBytes: ByteArray,
        val width: Int,
        val height: Int,
        val durationSec: Double
    )

    data class ImageBundleResult(
        val thumbUrl: String,
        val feedUrl: String,
        val originalUrl: String,
        val width: Int,
        val height: Int
    )

    data class VideoUploadResult(
        val originalUrl: String,
        val url1080: String,
        val url720: String,
        val thumbUrl: String?,
        val width: Int,
        val height: Int,
        val durationSec: Double
    )

    data class StoryMediaBundleResult(
        val mediaUrl: String,
        val mediaUrls: List<String>,
        val mediaTypes: List<String>,
        val mediaMeta: List<Map<String, Any?>>
    )

    data class UploadedMediaResult(
        val originalUrl: String,
        val url1080: String?,
        val url720: String?,
        val thumbUrl: String?,
        val feedUrl: String?,
        val isVideo: Boolean,
        val width: Int,
        val height: Int,
        val durationSec: Double?
    ) {
        fun toMediaMetaItem(): MediaMetaItem {
            return if (isVideo) {
                MediaMetaItem(
                    original = originalUrl,
                    resolution1080 = url1080 ?: originalUrl,
                    resolution720 = url720 ?: feedUrl ?: originalUrl,
                    thumbnailUrl = thumbUrl,
                    thumb = thumbUrl,
                    feed = feedUrl ?: url720 ?: originalUrl,
                    full = url1080 ?: originalUrl,
                    type = "video",
                    width = width,
                    height = height,
                    duration = durationSec
                )
            } else {
                MediaMetaItem(
                    original = null,
                    resolution1080 = null,
                    resolution720 = null,
                    thumbnailUrl = null,
                    thumb = thumbUrl ?: feedUrl,
                    feed = feedUrl ?: originalUrl,
                    full = originalUrl ?: feedUrl,
                    type = "image",
                    width = width,
                    height = height,
                    duration = null
                )
            }
        }
    }

    /**
     * Decodes and corrects EXIF orientation of an image from a content Uri.
     */
    fun decodeAndOrientBitmap(context: Context, uri: Uri): Pair<Bitmap?, Pair<Int, Int>> {
        return try {
            val orientation = try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = android.media.ExifInterface(stream)
                    exif.getAttributeInt(
                        android.media.ExifInterface.TAG_ORIENTATION,
                        android.media.ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: android.media.ExifInterface.ORIENTATION_NORMAL
            } catch (_: Exception) {
                android.media.ExifInterface.ORIENTATION_NORMAL
            }

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            val origW = options.outWidth
            val origH = options.outHeight

            // Sample down to avoid OOM for large camera pictures
            val maxSide = maxOf(origW, origH)
            var sampleSize = 1
            while (maxSide / (sampleSize * 2) >= 1920) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return Pair(null, Pair(0, 0))

            val matrix = Matrix()
            when (orientation) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }

            val rotated = if (!matrix.isIdentity) {
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                    if (it != decoded) decoded.recycle()
                }
            } else {
                decoded
            }

            val finalW = if (orientation == android.media.ExifInterface.ORIENTATION_ROTATE_90 ||
                orientation == android.media.ExifInterface.ORIENTATION_ROTATE_270) origH else origW
            val finalH = if (orientation == android.media.ExifInterface.ORIENTATION_ROTATE_90 ||
                orientation == android.media.ExifInterface.ORIENTATION_ROTATE_270) origW else origH

            Pair(rotated, Pair(finalW, finalH))
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(null, Pair(0, 0))
        }
    }

    /**
     * Compresses a bitmap to JPEG matching the web Imagecompression.ts pipeline:
     * - Preserves aspect ratio with max dimension constraint
     * - High fidelity JPEG compression
     */
    fun compressBitmap(source: Bitmap, maxDimension: Int, quality: Int): ByteArray {
        val width = source.width
        val height = source.height
        val maxSide = maxOf(width, height)
        val scale = if (maxSide > maxDimension) maxDimension.toFloat() / maxSide.toFloat() else 1f

        val scaled = if (scale < 1f) {
            val targetW = (width * scale).toInt().coerceAtLeast(1)
            val targetH = (height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(source, targetW, targetH, true)
        } else {
            source
        }

        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)

        if (scaled != source) {
            scaled.recycle()
        }
        return baos.toByteArray()
    }

    /**
     * High-performance profile picture compressor matching web Imagecompression.ts:
     * - Square center-crop or proportional downscale to max 720px
     * - High quality JPEG 86% (secondary pass 78% if >160KB)
     */
    fun compressProfileImage(source: Bitmap): ByteArray {
        val width = source.width
        val height = source.height
        val minSide = minOf(width, height)
        val sx = maxOf(0, (width - minSide) / 2)
        val sy = maxOf(0, (height - minSide) / 2)
        val outDim = minOf(720, minSide)

        val cropped = Bitmap.createBitmap(source, sx, sy, minSide, minSide)
        val scaled = if (minSide > outDim) {
            Bitmap.createScaledBitmap(cropped, outDim, outDim, true).also {
                if (it != cropped) cropped.recycle()
            }
        } else {
            cropped
        }

        var quality = 86
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)

        if (baos.size() > 160 * 1024) {
            baos.reset()
            quality = 78
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        }

        if (scaled != source) {
            scaled.recycle()
        }
        return baos.toByteArray()
    }

    /**
     * Cover banner photo compressor matching web Imagecompression.ts:
     * - Max 1920px, JPEG 88%
     */
    fun compressCoverImage(source: Bitmap): ByteArray {
        return compressBitmap(source, 1920, 88)
    }

    /**
     * Compresses and uploads a user cover photo to /api/upload with folder="covers".
     * Returns the uploaded URL, matching the web implementation.
     */
    suspend fun uploadCoverImage(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val (bitmap, _) = decodeAndOrientBitmap(context, uri)
        if (bitmap == null) return@withContext null
        try {
            val compressedBytes = compressCoverImage(bitmap)
            val filename = "cover_${System.currentTimeMillis()}.jpg"
            val resp = uploadSingleFile(
                fileBytes = compressedBytes,
                fileName = filename,
                mimeType = "image/jpeg",
                folder = "covers"
            )
            val rawUrl = resp?.optString("url")?.takeIf { it.isNotBlank() && it != "null" }
            if (rawUrl != null) {
                val cleanUrl = rawUrl.replace(Regex("\\?.*$"), "")
                "$cleanUrl?t=${System.currentTimeMillis()}"
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Builds and uploads the 3-variant image bundle (thumb, feed, full) matching web Imagecompression.ts:
     * - thumb: ~320px JPEG, quality 72%
     * - feed: ~1080px JPEG, quality 82%
     * - full: ~1600px JPEG, quality 86%
     */
    suspend fun buildImageBundleAndUpload(
        context: Context,
        uri: Uri
    ): ImageBundleResult? = withContext(Dispatchers.IO) {
        val (bitmap, dims) = decodeAndOrientBitmap(context, uri)
        if (bitmap == null) return@withContext null

        try {
            val thumbBytes = compressBitmap(bitmap, 320, 72)
            val feedBytes = compressBitmap(bitmap, 1080, 82)
            val fullBytes = compressBitmap(bitmap, 1600, 86)

            val boundary = "==FeatheredBundle-${UUID.randomUUID()}=="
            val urlString = "$BASE_URL/api/upload"
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 30000
                readTimeout = 30000
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setRequestProperty("User-Agent", USER_AGENT)
            }

            val dos = DataOutputStream(conn.outputStream)

            // Part 1: thumbnail
            dos.writeBytes("--$boundary\r\n")
            dos.writeBytes("Content-Disposition: form-data; name=\"thumbnail\"; filename=\"thumb.jpg\"\r\n")
            dos.writeBytes("Content-Type: image/jpeg\r\n\r\n")
            dos.write(thumbBytes)
            dos.writeBytes("\r\n")

            // Part 2: feed
            dos.writeBytes("--$boundary\r\n")
            dos.writeBytes("Content-Disposition: form-data; name=\"feed\"; filename=\"feed.jpg\"\r\n")
            dos.writeBytes("Content-Type: image/jpeg\r\n\r\n")
            dos.write(feedBytes)
            dos.writeBytes("\r\n")

            // Part 3: original (full)
            dos.writeBytes("--$boundary\r\n")
            dos.writeBytes("Content-Disposition: form-data; name=\"original\"; filename=\"full.jpg\"\r\n")
            dos.writeBytes("Content-Type: image/jpeg\r\n\r\n")
            dos.write(fullBytes)
            dos.writeBytes("\r\n")

            // Part 4: folder
            dos.writeBytes("--$boundary\r\n")
            dos.writeBytes("Content-Disposition: form-data; name=\"folder\"\r\n\r\n")
            dos.writeBytes("posts\r\n")

            dos.writeBytes("--$boundary--\r\n")
            dos.flush()
            dos.close()

            if (conn.responseCode in 200..299) {
                val text = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }.trim()
                if (text.startsWith("{")) {
                    val json = JSONObject(text)
                    val uploaded = json.optJSONObject("uploaded")
                    val mediaUrls = json.optJSONObject("media_urls")

                    val thumbUrl = uploaded?.optJSONObject("thumbnail")?.optString("url")
                        ?: uploaded?.optJSONObject("thumb")?.optString("url")
                        ?: mediaUrls?.optString("thumb")
                        ?: mediaUrls?.optString("thumbnail")
                        ?: ""
                    val feedUrl = uploaded?.optJSONObject("feed")?.optString("url")
                        ?: mediaUrls?.optString("feed")
                        ?: json.optString("url")
                    val fullUrl = uploaded?.optJSONObject("original")?.optString("url")
                        ?: uploaded?.optJSONObject("full")?.optString("url")
                        ?: mediaUrls?.optString("full")
                        ?: mediaUrls?.optString("original")
                        ?: feedUrl

                    if (feedUrl.isNotBlank()) {
                        return@withContext ImageBundleResult(
                            thumbUrl = thumbUrl.ifBlank { feedUrl },
                            feedUrl = feedUrl,
                            originalUrl = fullUrl.ifBlank { feedUrl },
                            width = dims.first.coerceAtLeast(bitmap.width),
                            height = dims.second.coerceAtLeast(bitmap.height)
                        )
                    }
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            bitmap.recycle()
        }
        null
    }

    /**
     * Extracts video thumbnail at 1.0s (or 25% if shorter than 1.5s) and metadata.
     */
    fun extractVideoThumbnail(context: Context, videoUri: Uri): VideoMeta? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            val durationSec = durationMs / 1000.0

            val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
            val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
            val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            val rotation = rotationStr?.toIntOrNull() ?: 0

            val isRotated = rotation == 90 || rotation == 270
            val actualWidth = if (isRotated) rawHeight else rawWidth
            val actualHeight = if (isRotated) rawWidth else rawHeight

            // Seek logic matching web videoThumbnail.ts:
            val targetTimeUs: Long = when {
                durationMs in 1..999 -> ((durationMs * 0.25).coerceAtLeast(50.0) * 1000).toLong()
                durationMs in 1000..1499 -> ((durationMs * 0.25).coerceIn(100.0, 1000.0) * 1000).toLong()
                else -> 1_000_000L
            }

            var frame = retriever.getFrameAtTime(targetTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime

            if (frame == null) {
                return null
            }

            // Apply rotation if needed
            if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                val rotated = Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, matrix, true)
                if (rotated != frame) {
                    frame.recycle()
                    frame = rotated
                }
            }

            // Scale preserving aspect ratio to max 1280x1280
            val maxBound = 1280f
            val scale = minOf(1f, maxBound / frame.width.toFloat(), maxBound / frame.height.toFloat())
            val targetW = (frame.width * scale).toInt().coerceAtLeast(1)
            val targetH = (frame.height * scale).toInt().coerceAtLeast(1)

            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(frame, targetW, targetH, true).also {
                    if (it != frame) frame.recycle()
                }
            } else {
                frame
            }

            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            scaled.recycle()

            return VideoMeta(
                thumbnailBytes = baos.toByteArray(),
                width = actualWidth,
                height = actualHeight,
                durationSec = durationSec
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * Uploads bytes to POST /api/upload as a single file.
     */
    suspend fun uploadSingleFile(
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String,
        folder: String = "posts"
    ): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val boundary = "==FeatheredSingle-${UUID.randomUUID()}=="
            val urlString = "$BASE_URL/api/upload"
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 30000
                readTimeout = 30000
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setRequestProperty("User-Agent", USER_AGENT)
            }

            val dos = DataOutputStream(conn.outputStream)

            // file
            dos.writeBytes("--$boundary\r\n")
            dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n")
            dos.writeBytes("Content-Type: $mimeType\r\n\r\n")
            dos.write(fileBytes)
            dos.writeBytes("\r\n")

            // filename
            dos.writeBytes("--$boundary\r\n")
            dos.writeBytes("Content-Disposition: form-data; name=\"filename\"\r\n\r\n")
            dos.writeBytes("$fileName\r\n")

            // type
            dos.writeBytes("--$boundary\r\n")
            dos.writeBytes("Content-Disposition: form-data; name=\"type\"\r\n\r\n")
            dos.writeBytes("$mimeType\r\n")

            // folder
            dos.writeBytes("--$boundary\r\n")
            dos.writeBytes("Content-Disposition: form-data; name=\"folder\"\r\n\r\n")
            dos.writeBytes("$folder\r\n")

            // timestamp
            dos.writeBytes("--$boundary\r\n")
            dos.writeBytes("Content-Disposition: form-data; name=\"timestamp\"\r\n\r\n")
            dos.writeBytes("${System.currentTimeMillis()}\r\n")

            dos.writeBytes("--$boundary--\r\n")
            dos.flush()
            dos.close()

            if (conn.responseCode in 200..299) {
                val text = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }.trim()
                if (text.startsWith("{")) {
                    return@withContext JSONObject(text)
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    /**
     * Streams video from Uri to POST /api/upload to avoid OutOfMemoryError.
     */
    suspend fun uploadVideoFromUri(
        context: Context,
        uri: Uri,
        folder: String = "posts/videos/720p",
        onProgress: ((Float) -> Unit)? = null
    ): String? = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        try {
            var fileSize = -1L
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1 && cursor.moveToFirst()) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            } catch (_: Exception) {}

            inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val fileName = "video_${System.currentTimeMillis()}.mp4"
            val mimeType = context.contentResolver.getType(uri) ?: "video/mp4"

            val boundary = "==FeatheredVideo-${UUID.randomUUID()}=="
            val urlString = "$BASE_URL/api/upload"
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 60000
                readTimeout = 60000
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setRequestProperty("User-Agent", USER_AGENT)
            }

            val dos = DataOutputStream(conn.outputStream)

            // file header
            dos.writeBytes("--$boundary\r\n")
            dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n")
            dos.writeBytes("Content-Type: $mimeType\r\n\r\n")

            // stream bytes in 64KB chunks
            val buffer = ByteArray(65536)
            var bytesRead: Int
            var totalTransferred = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                dos.write(buffer, 0, bytesRead)
                totalTransferred += bytesRead
                if (fileSize > 0) {
                    val pct = (totalTransferred.toFloat() / fileSize.toFloat()).coerceIn(0f, 1f)
                    onProgress?.invoke(pct)
                }
            }
            dos.writeBytes("\r\n")

            // filename
            dos.writeBytes("--$boundary\r\n")
            dos.writeBytes("Content-Disposition: form-data; name=\"filename\"\r\n\r\n")
            dos.writeBytes("$fileName\r\n")

            // type
            dos.writeBytes("--$boundary\r\n")
            dos.writeBytes("Content-Disposition: form-data; name=\"type\"\r\n\r\n")
            dos.writeBytes("$mimeType\r\n")

            // folder
            dos.writeBytes("--$boundary\r\n")
            dos.writeBytes("Content-Disposition: form-data; name=\"folder\"\r\n\r\n")
            dos.writeBytes("$folder\r\n")

            // timestamp
            dos.writeBytes("--$boundary\r\n")
            dos.writeBytes("Content-Disposition: form-data; name=\"timestamp\"\r\n\r\n")
            dos.writeBytes("${System.currentTimeMillis()}\r\n")

            dos.writeBytes("--$boundary--\r\n")
            dos.flush()
            dos.close()

            if (conn.responseCode in 200..299) {
                val text = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }.trim()
                if (text.startsWith("{")) {
                    val json = JSONObject(text)
                    val url = json.optString("url")
                    if (url.isNotBlank()) return@withContext url
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
        }
        null
    }

    /**
     * Complete video processor:
     * 1. Extracts thumbnail and dimensions/duration.
     * 2. Uploads thumbnail to posts/thumbnails.
     * 3. Streams video to posts/videos/720p.
     * 4. Returns complete VideoUploadResult.
     */
    suspend fun processAndUploadVideo(
        context: Context,
        uri: Uri,
        onProgress: ((String, Float) -> Unit)? = null
    ): VideoUploadResult? = withContext(Dispatchers.IO) {
        onProgress?.invoke("Extracting video thumbnail & optimizing...", 0.2f)
        val meta = extractVideoThumbnail(context, uri)

        var thumbUrl: String? = null
        if (meta?.thumbnailBytes != null) {
            onProgress?.invoke("Uploading thumbnail...", 0.35f)
            val thumbName = "thumb_${System.currentTimeMillis()}.webp"
            val thumbJson = uploadSingleFile(
                fileBytes = meta.thumbnailBytes,
                fileName = thumbName,
                mimeType = "image/webp",
                folder = "posts/thumbnails"
            )
            thumbUrl = thumbJson?.optString("url")?.takeIf { it.isNotBlank() }
        }

        onProgress?.invoke("Uploading video file...", 0.5f)
        val videoUrl = uploadVideoFromUri(
            context = context,
            uri = uri,
            folder = "posts/videos/720p"
        ) { streamPct ->
            // Scale stream progress between 0.5 and 0.95
            val current = 0.5f + (streamPct * 0.45f)
            onProgress?.invoke("Uploading video file...", current)
        }

        if (videoUrl.isNullOrBlank()) {
            return@withContext null
        }

        VideoUploadResult(
            originalUrl = videoUrl,
            url1080 = videoUrl,
            url720 = videoUrl,
            thumbUrl = thumbUrl,
            width = meta?.width ?: 1280,
            height = meta?.height ?: 720,
            durationSec = meta?.durationSec ?: 0.0
        )
    }

    /**
     * Backward-compatible entrypoint used across existing screens.
     */
    suspend fun processAndUploadMedia(
        context: Context,
        uri: Uri,
        isVideoHint: Boolean = false,
        onProgress: ((String, Float) -> Unit)? = null
    ): UploadedMediaResult? = withContext(Dispatchers.IO) {
        val uriStr = uri.toString().lowercase()
        val isExplicitImage = uriStr.endsWith(".jpg") || uriStr.endsWith(".jpeg") || uriStr.endsWith(".png") ||
                uriStr.endsWith(".webp") || uriStr.endsWith(".gif") || uriStr.contains(".jpg?") || uriStr.contains(".png?")
        val mimeType = try { context.contentResolver.getType(uri)?.lowercase() ?: "" } catch (_: Exception) { "" }
        val isExplicitVideo = uriStr.endsWith(".mp4") || uriStr.endsWith(".mov") || uriStr.endsWith(".webm") || uriStr.endsWith(".mkv")
        val isVideo = !isExplicitImage && (isExplicitVideo || mimeType.startsWith("video") || (isVideoHint && !mimeType.startsWith("image")))

        if (isVideo) {
            val vResult = processAndUploadVideo(context, uri) { status, pct ->
                onProgress?.invoke(status, pct * 100f)
            } ?: return@withContext null

            UploadedMediaResult(
                originalUrl = vResult.originalUrl,
                url1080 = vResult.url1080,
                url720 = vResult.url720,
                thumbUrl = vResult.thumbUrl,
                feedUrl = vResult.url720,
                isVideo = true,
                width = vResult.width,
                height = vResult.height,
                durationSec = vResult.durationSec
            )
        } else {
            onProgress?.invoke("Optimizing & compressing image...", 30f)
            val bundleResult = buildImageBundleAndUpload(context, uri) ?: return@withContext null
            onProgress?.invoke("Image uploaded", 90f)

            UploadedMediaResult(
                originalUrl = bundleResult.originalUrl,
                url1080 = null,
                url720 = null,
                thumbUrl = bundleResult.thumbUrl,
                feedUrl = bundleResult.feedUrl,
                isVideo = false,
                width = bundleResult.width,
                height = bundleResult.height,
                durationSec = null
            )
        }
    }

    /**
     * Exact Feathered Web Story Image Variant Bundle:
     * - original: full file (or WebP 92%)
     * - feed: max 1080px WebP 82%
     * - thumbnail: max 320px WebP 72%
     * Uploads via POST /api/upload as multipart form data.
     */
    suspend fun uploadStoryImageBundle(
        context: Context,
        uri: Uri
    ): StoryMediaBundleResult? = withContext(Dispatchers.IO) {
        val (bitmap, dims) = decodeAndOrientBitmap(context, uri)
        if (bitmap == null) return@withContext null

        try {
            val thumbBytes = compressBitmap(bitmap, 480, 80)
            val feedBytes = compressBitmap(bitmap, 1440, 90)
            val fullBytes = compressBitmap(bitmap, 2160, 95)

            val boundary = "==FeatheredStoryImage-${UUID.randomUUID()}=="
            val urlString = "$BASE_URL/api/upload"
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 30000
                readTimeout = 30000
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setRequestProperty("User-Agent", USER_AGENT)
            }

            val dos = DataOutputStream(conn.outputStream)

            // Part 1: original (high-res)
            dos.writeBytes("--$boundary\r\n")
            dos.writeBytes("Content-Disposition: form-data; name=\"original\"; filename=\"${System.currentTimeMillis()}-original.jpg\"\r\n")
            dos.writeBytes("Content-Type: image/jpeg\r\n\r\n")
            dos.write(fullBytes)
            dos.writeBytes("\r\n")

            // Part 2: feed
            dos.writeBytes("--$boundary\r\n")
            dos.writeBytes("Content-Disposition: form-data; name=\"feed\"; filename=\"${System.currentTimeMillis()}-feed.webp\"\r\n")
            dos.writeBytes("Content-Type: image/webp\r\n\r\n")
            dos.write(feedBytes)
            dos.writeBytes("\r\n")

            // Part 3: thumbnail
            dos.writeBytes("--$boundary\r\n")
            dos.writeBytes("Content-Disposition: form-data; name=\"thumbnail\"; filename=\"${System.currentTimeMillis()}-thumbnail.webp\"\r\n")
            dos.writeBytes("Content-Type: image/webp\r\n\r\n")
            dos.write(thumbBytes)
            dos.writeBytes("\r\n")

            dos.writeBytes("--$boundary--\r\n")
            dos.flush()
            dos.close()

            if (conn.responseCode in 200..299) {
                val text = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }.trim()
                if (text.startsWith("{")) {
                    val json = JSONObject(text)
                    val uploaded = json.optJSONObject("uploaded")
                    val mediaUrls = json.optJSONObject("media_urls")

                    val thumbUrl = uploaded?.optJSONObject("thumbnail")?.optString("url")
                        ?: mediaUrls?.optString("thumb")
                        ?: ""
                    val origUrl = uploaded?.optJSONObject("original")?.optString("url")
                        ?: mediaUrls?.optString("full")
                        ?: ""
                    val feedUrl = uploaded?.optJSONObject("feed")?.optString("url")
                        ?: mediaUrls?.optString("feed")
                        ?: json.optString("url")

                    val effectiveUrl = if (origUrl.isNotBlank()) origUrl else if (feedUrl.isNotBlank()) feedUrl else json.optString("url")
                    if (effectiveUrl.isNotBlank()) {
                        val effectiveThumb = if (thumbUrl.isNotBlank()) thumbUrl else effectiveUrl
                        val meta = mapOf(
                            "thumb" to effectiveThumb,
                            "feed" to (if (feedUrl.isNotBlank()) feedUrl else effectiveUrl),
                            "full" to effectiveUrl,
                            "type" to "image"
                        )
                        return@withContext StoryMediaBundleResult(
                            mediaUrl = effectiveUrl,
                            mediaUrls = listOf(effectiveUrl),
                            mediaTypes = listOf("image"),
                            mediaMeta = listOf(meta)
                        )
                    }
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            bitmap.recycle()
        }
        null
    }

    /**
     * Exact Feathered Web Story Video Bundle:
     * - Enforces max 90 seconds (1 min 30 sec) duration
     * - thumbnail: extracted at 20% mark, max 320px WebP 72%
     * - original: full video file
     * Uploads via POST /api/upload as multipart form data.
     */
    suspend fun uploadStoryVideoBundle(
        context: Context,
        videoUri: Uri
    ): StoryMediaBundleResult? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val thumbBytes: ByteArray
        try {
            retriever.setDataSource(context, videoUri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            val durationSec = durationMs / 1000.0

            if (durationSec > 90.0) {
                throw IllegalArgumentException("Story video must be 1 minute 30 seconds or less")
            }

            // Target seek at ~20% of duration
            val seekUs = (durationMs * 0.2 * 1000).toLong().coerceIn(100_000L, maxOf(100_000L, (durationMs - 100) * 1000))
            val frame = retriever.getFrameAtTime(seekUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
                ?: return@withContext null

            val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            val rotation = rotationStr?.toIntOrNull() ?: 0
            val rotated = if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, matrix, true).also {
                    if (it != frame) frame.recycle()
                }
            } else {
                frame
            }

            val maxBound = 320f
            val scale = minOf(1f, maxBound / rotated.width.toFloat(), maxBound / rotated.height.toFloat())
            val targetW = (rotated.width * scale).toInt().coerceAtLeast(1)
            val targetH = (rotated.height * scale).toInt().coerceAtLeast(1)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(rotated, targetW, targetH, true).also {
                    if (it != rotated) rotated.recycle()
                }
            } else {
                rotated
            }

            val baos = ByteArrayOutputStream()
            val format = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                Bitmap.CompressFormat.WEBP
            }
            val success = scaled.compress(format, 72, baos)
            if (!success || baos.size() == 0) {
                baos.reset()
                scaled.compress(Bitmap.CompressFormat.JPEG, 75, baos)
            }
            scaled.recycle()
            thumbBytes = baos.toByteArray()
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        try {
            val boundary = "==FeatheredStoryVideo-${UUID.randomUUID()}=="
            val urlString = "$BASE_URL/api/upload"
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 60000
                readTimeout = 60000
                setChunkedStreamingMode(64 * 1024)
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setRequestProperty("User-Agent", USER_AGENT)
            }

            val output = conn.outputStream

            // Part 1: thumbnail
            val thumbHeader = "--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"thumbnail\"; filename=\"${System.currentTimeMillis()}-thumbnail.webp\"\r\n" +
                "Content-Type: image/webp\r\n\r\n"
            output.write(thumbHeader.toByteArray(Charsets.UTF_8))
            output.write(thumbBytes)
            output.write("\r\n".toByteArray(Charsets.UTF_8))

            // Part 2: original (stream video)
            val videoHeader = "--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"original\"; filename=\"${System.currentTimeMillis()}-story.mp4\"\r\n" +
                "Content-Type: video/mp4\r\n\r\n"
            output.write(videoHeader.toByteArray(Charsets.UTF_8))

            context.contentResolver.openInputStream(videoUri)?.use { input ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
            }
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
                    val thumbUrl = uploaded?.optJSONObject("thumbnail")?.optString("url")

                    if (origUrl.isNotBlank()) {
                        val meta = mapOf(
                            "thumb" to thumbUrl,
                            "feed" to null,
                            "full" to origUrl,
                            "type" to "video"
                        )
                        return@withContext StoryMediaBundleResult(
                            mediaUrl = origUrl,
                            mediaUrls = listOf(origUrl),
                            mediaTypes = listOf("video"),
                            mediaMeta = listOf(meta)
                        )
                    }
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
            if (e is IllegalArgumentException) throw e
        }
        null
    }

    /**
     * Upload an audio file for story music to /api/upload
     */
    suspend fun uploadAudioFile(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val token = UserSessionManager.getAuthToken(context)
            val url = URL("$BASE_URL/api/upload")
            val boundary = "Boundary-" + UUID.randomUUID().toString()

            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                doInput = true
                useCaches = false
                connectTimeout = 60000
                readTimeout = 60000
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                if (!token.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
            }

            val fileName = "audio_${System.currentTimeMillis()}.mp3"
            val output = conn.outputStream

            val header = "--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n" +
                "Content-Type: audio/mpeg\r\n\r\n"
            output.write(header.toByteArray(Charsets.UTF_8))

            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(32 * 1024)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
            }
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
}
