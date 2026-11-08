package com.unera.social.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaCodecInfo
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.EncoderUtil
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.unera.social.model.MediaMetaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.resume

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

    data class VideoDimensions(
        val width: Int,
        val height: Int
    )

    data class VideoSourceAnalysis(
        val width: Int,
        val height: Int,
        val durationSec: Double,
        val sourceBitrateBps: Long,
        val sourceFileSizeBytes: Long,
        val rotation: Int,
        val isPortrait: Boolean,
        val isSquare: Boolean,
        val hasAudio: Boolean
    )

    data class RenditionTarget(
        val label: String,
        val targetDimensions: VideoDimensions,
        val targetVideoBitrateBps: Int,
        val targetAudioBitrateBps: Int,
        val iFrameIntervalSec: Float
    )

    data class VideoMeta(
        val thumbnailBytes: ByteArray,
        val width: Int,
        val height: Int,
        val durationSec: Double,
        val mimeType: String = "image/webp"
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
     * Calculates correct dimensions preserving aspect ratio, capped at max bounding box.
     * Never upscales smaller videos.
     * Guarantees width and height are even integers (multiples of 2) for H.264 compatibility.
     * Matches web videoCompressor.ts calculateTargetDimensions.
     */
    fun calculateTargetDimensions(
        origW: Int,
        origH: Int,
        maxLong: Int,
        maxShort: Int
    ): VideoDimensions {
        if (origW <= 0 || origH <= 0) {
            return VideoDimensions(1280, 720)
        }

        val limitW: Int
        val limitH: Int

        if (origW > origH) {
            // Landscape
            limitW = maxLong
            limitH = maxShort
        } else if (origH > origW) {
            // Portrait
            limitW = maxShort
            limitH = maxLong
        } else {
            // Square
            limitW = maxShort
            limitH = maxShort
        }

        // Never upscale smaller videos
        val scale = minOf(1.0, limitW.toDouble() / origW.toDouble(), limitH.toDouble() / origH.toDouble())

        // Round to nearest even integers
        val width = maxOf(2, (Math.round((origW * scale) / 2.0) * 2).toInt())
        val height = maxOf(2, (Math.round((origH * scale) / 2.0) * 2).toInt())

        return VideoDimensions(width, height)
    }

    /**
     * Professional, high-quality client-side video thumbnail extractor matching web videoThumbnail.ts:
     * 1. Reads video dimensions, duration, and orientation metadata.
     * 2. Seeks to ~1.0 second (or 25% of the video duration if shorter than 1.5s).
     * 3. Uses OPTION_CLOSEST for accurate crisp keyframe extraction (fallback OPTION_CLOSEST_SYNC).
     * 4. Applies EXIF video rotation.
     * 5. Scales preserving aspect ratio up to max 1280x1280.
     * 6. Generates a crisp WebP (or JPEG 92 fallback) at 90% quality.
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

            var frame = retriever.getFrameAtTime(targetTimeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: retriever.getFrameAtTime(targetTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
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
            var mimeType = "image/webp"
            val compressed = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, 90, baos)
            } else {
                @Suppress("DEPRECATION")
                scaled.compress(Bitmap.CompressFormat.WEBP, 90, baos)
            }

            if (!compressed || baos.size() == 0) {
                baos.reset()
                scaled.compress(Bitmap.CompressFormat.JPEG, 92, baos)
                mimeType = "image/jpeg"
            }
            scaled.recycle()

            return VideoMeta(
                thumbnailBytes = baos.toByteArray(),
                width = actualWidth,
                height = actualHeight,
                durationSec = durationSec,
                mimeType = mimeType
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
     * Streams video file directly to POST /api/upload in 64KB chunks.
     */
    suspend fun uploadVideoFile(
        file: File,
        folder: String = "posts/videos/720p",
        onProgress: ((Float) -> Unit)? = null
    ): String? = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() <= 0L) return@withContext null
        var inputStream: java.io.FileInputStream? = null
        try {
            val fileSize = file.length()
            inputStream = java.io.FileInputStream(file)
            val fileName = "video_${System.currentTimeMillis()}_${file.name}"
            val mimeType = "video/mp4"

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
     * Dynamically queries hardware encoder capabilities to select the highest quality supported
     * H.264 profile (High Profile > Main Profile > Baseline Profile).
     */
    fun resolveBestH264Profile(): Int {
        return try {
            val encoders = EncoderUtil.getSupportedEncoders(MimeTypes.VIDEO_H264)
            var hasHigh = false
            var hasMain = false
            for (encoder in encoders) {
                val profiles = EncoderUtil.findSupportedEncodingProfiles(encoder, MimeTypes.VIDEO_H264)
                if (profiles.contains(MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)) {
                    hasHigh = true
                    break
                }
                if (profiles.contains(MediaCodecInfo.CodecProfileLevel.AVCProfileMain)) {
                    hasMain = true
                }
            }
            when {
                hasHigh -> MediaCodecInfo.CodecProfileLevel.AVCProfileHigh
                hasMain -> MediaCodecInfo.CodecProfileLevel.AVCProfileMain
                else -> MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline
            }
        } catch (e: Exception) {
            android.util.Log.w("MediaUploadHelper", "Error resolving H.264 profile: ${e.message}")
            MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline
        }
    }

    /**
     * Inspects the source video metadata, duration, orientation, and source bitrate to drive
     * intelligent compression decisions rather than applying blind, static parameters.
     */
    fun analyzeVideoSource(context: Context, videoUri: Uri): VideoSourceAnalysis {
        val retriever = MediaMetadataRetriever()
        var fileSize = -1L

        if (videoUri.scheme == "file") {
            try {
                fileSize = File(videoUri.path ?: "").length()
            } catch (_: Exception) {}
        }
        if (fileSize <= 0L) {
            try {
                context.contentResolver.openFileDescriptor(videoUri, "r")?.use { pfd ->
                    fileSize = pfd.statSize
                }
            } catch (_: Exception) {}
        }
        if (fileSize <= 0L) {
            try {
                context.contentResolver.query(videoUri, null, null, null, null)?.use { cursor ->
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1 && cursor.moveToFirst()) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            } catch (_: Exception) {}
        }

        var width = 1280
        var height = 720
        var durationSec = 1.0
        var sourceBitrate = -1L
        var rotation = 0
        var hasAudio = true

        try {
            retriever.setDataSource(context, videoUri)

            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durMs = durStr?.toLongOrNull() ?: 1000L
            durationSec = maxOf(0.1, durMs / 1000.0)

            val rawW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
            val rawH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
            val rotStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            rotation = rotStr?.toIntOrNull() ?: 0

            val isRotated = rotation == 90 || rotation == 270
            width = if (isRotated) rawH else rawW
            height = if (isRotated) rawW else rawH

            val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            sourceBitrate = bitrateStr?.toLongOrNull() ?: -1L
            if (sourceBitrate <= 0L && fileSize > 0L && durationSec > 0.0) {
                sourceBitrate = ((fileSize * 8) / durationSec).toLong()
            }

            val hasAudioStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            hasAudio = hasAudioStr?.equals("yes", ignoreCase = true) ?: true
        } catch (e: Exception) {
            android.util.Log.w("MediaUploadHelper", "Video analysis warning: ${e.message}")
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        return VideoSourceAnalysis(
            width = width,
            height = height,
            durationSec = durationSec,
            sourceBitrateBps = sourceBitrate,
            sourceFileSizeBytes = fileSize,
            rotation = rotation,
            isPortrait = height > width,
            isSquare = width == height,
            hasAudio = hasAudio
        )
    }

    /**
     * Calculates intelligent, source-aware rendition targets:
     * - 1080p: targets crisp high-definition VBR (approx 3.2-3.8 Mbps scaled by aspect/pixel ratio).
     * - 720p: targets smooth, lightweight VBR (approx 1.5-1.9 Mbps scaled by aspect/pixel ratio).
     * - Protects against file bloat by ensuring target bitrates never exceed source bitrate.
     * - Guarantees keyframe interval of 1.5s for instant playback seeking and social reel swiping.
     */
    data class VideoTranscodePlan(
        val shouldGenerate1080p: Boolean,
        val skipReason1080p: String? = null,
        val target1080: RenditionTarget? = null,
        val shouldGenerate720p: Boolean,
        val skipReason720p: String? = null,
        val target720: RenditionTarget? = null
    )

    data class TranscodeVerification(
        val isValid: Boolean,
        val file: File?,
        val actualWidth: Int = 0,
        val actualHeight: Int = 0,
        val actualSizeBytes: Long = 0L,
        val durationSec: Double = 0.0,
        val sha256: String = "",
        val isIdenticalToSource: Boolean = false,
        val errorMessage: String? = null
    )

    sealed class TranscodeResult {
        object Success : TranscodeResult()
        data class Failure(val reason: String) : TranscodeResult()
    }

    /**
     * Calculates the SHA-256 hash of a file for cryptographic verification against original/rendition collision.
     */
    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Rigorously verifies every transcoded video output before upload:
     * - Output file exists and is non-empty (>0 bytes)
     * - Valid video track and dimensions readable via MediaMetadataRetriever
     * - Positive duration
     * - Verifies SHA-256 is distinct from the original source file
     */
    fun verifyTranscodedVideo(
        file: File,
        sourceSha256: String?,
        sourceSizeBytes: Long = -1L
    ): TranscodeVerification {
        if (!file.exists()) {
            return TranscodeVerification(isValid = false, file = null, errorMessage = "Output file does not exist")
        }
        val size = file.length()
        if (size <= 0L) {
            return TranscodeVerification(isValid = false, file = null, errorMessage = "Output file is empty (0 bytes)")
        }

        // Strict inflation check: never allow transcoded output to exceed the original file size
        if (sourceSizeBytes > 0 && size > sourceSizeBytes) {
            return TranscodeVerification(
                isValid = false,
                file = null,
                actualSizeBytes = size,
                errorMessage = "Transcoded output ($size bytes) exceeds original file size ($sourceSizeBytes bytes). Inflation rejected."
            )
        }

        val sha = try {
            calculateSha256(file)
        } catch (e: Exception) {
            return TranscodeVerification(isValid = false, file = null, errorMessage = "Failed calculating SHA-256: ${e.message}")
        }

        val isIdentical = sourceSha256 != null && sourceSha256.equals(sha, ignoreCase = true)
        if (isIdentical) {
            return TranscodeVerification(
                isValid = false,
                file = null,
                actualSizeBytes = size,
                sha256 = sha,
                isIdenticalToSource = true,
                errorMessage = "Transcoded output has identical SHA-256 to source file ($sha). Duplicate rejected."
            )
        }

        val retriever = MediaMetadataRetriever()
        var actualW = 0
        var actualH = 0
        var durationSec = 0.0
        try {
            retriever.setDataSource(file.absolutePath)
            val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rotStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

            val rawW = wStr?.toIntOrNull() ?: 0
            val rawH = hStr?.toIntOrNull() ?: 0
            val rot = rotStr?.toIntOrNull() ?: 0
            val isRot = rot == 90 || rot == 270

            actualW = if (isRot) rawH else rawW
            actualH = if (isRot) rawW else rawH
            val durMs = durStr?.toLongOrNull() ?: 0L
            durationSec = durMs / 1000.0

            if (actualW <= 0 || actualH <= 0 || durationSec <= 0.0) {
                return TranscodeVerification(
                    isValid = false,
                    file = null,
                    errorMessage = "Invalid video metadata: ${actualW}x${actualH}, dur=${durationSec}s"
                )
            }
        } catch (e: Exception) {
            return TranscodeVerification(isValid = false, file = null, errorMessage = "Failed to inspect video: ${e.message}")
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        return TranscodeVerification(
            isValid = true,
            file = file,
            actualWidth = actualW,
            actualHeight = actualH,
            actualSizeBytes = size,
            durationSec = durationSec,
            sha256 = sha,
            isIdenticalToSource = false
        )
    }

    /**
     * Determines whether renditions are needed based on source dimensions and aspect ratio:
     * - Sources with max dimension < 1080 (e.g. 720x720, 720x1280) MUST NOT upscale to 1080p.
     * - Sources that are already ultra-efficient (<= 720p and bitrate <= 450 kbps) skip re-encoding
     *   to prevent quality loss and file size inflation.
     */
    fun createVideoTranscodePlan(source: VideoSourceAnalysis): VideoTranscodePlan {
        val origW = source.width
        val origH = source.height
        val srcBitrate = source.sourceBitrateBps
        val maxDim = maxOf(origW, origH)
        val minDim = minOf(origW, origH)

        // Rule 1: 1080p Generation:
        // Only generate 1080p if the source is natively >= 1080p.
        // Strictly forbid upscaling 720p or 720x720 videos to 1080p!
        val should1080 = maxDim >= 1080 && minDim >= 720
        val target1080 = if (should1080) {
            val dim1080 = calculateTargetDimensions(origW, origH, 1920, 1080)
            val full1080Pixels = 1920.0 * 1080.0
            val target1080Pixels = dim1080.width.toDouble() * dim1080.height.toDouble()
            val pixelRatio1080 = (target1080Pixels / full1080Pixels).coerceIn(0.35, 1.0)
            var bit1080 = (3_400_000 * pixelRatio1080).toInt().coerceIn(1_800_000, 3_800_000)
            if (srcBitrate > 0) {
                bit1080 = minOf(bit1080, (srcBitrate * 0.85).toInt()).coerceAtLeast(1_000_000)
            }
            RenditionTarget(
                label = "1080p",
                targetDimensions = dim1080,
                targetVideoBitrateBps = bit1080,
                targetAudioBitrateBps = if (source.hasAudio) 128_000 else 0,
                iFrameIntervalSec = 1.5f
            )
        } else null

        val skip1080Reason = if (!should1080) {
            "Source dimensions (${origW}x${origH}) do not contain 1080p resolution. Upscaling to 1080p skipped per policy."
        } else null

        // Rule 2: 720p Generation:
        // If the source video is already ultra-efficient (resolution <= 720p and bitrate <= 450 kbps, like 720x720 @ 293 kbps):
        // Re-encoding provides NO visual or size benefit and would risk quality degradation or file bloat!
        val isAlreadyUltraEfficient = maxDim <= 720 && srcBitrate in 1..450_000

        val should720 = !isAlreadyUltraEfficient
        val target720 = if (should720) {
            val dim720 = calculateTargetDimensions(origW, origH, 1280, 720)
            val full720Pixels = 1280.0 * 720.0
            val target720Pixels = dim720.width.toDouble() * dim720.height.toDouble()
            val pixelRatio720 = (target720Pixels / full720Pixels).coerceIn(0.35, 1.0)
            var bit720 = (1_600_000 * pixelRatio720).toInt().coerceIn(900_000, 1_800_000)
            if (srcBitrate > 0) {
                bit720 = minOf(bit720, (srcBitrate * 0.75).toInt()).coerceAtLeast(350_000)
            }
            RenditionTarget(
                label = "720p",
                targetDimensions = dim720,
                targetVideoBitrateBps = bit720,
                targetAudioBitrateBps = if (source.hasAudio) 96_000 else 0,
                iFrameIntervalSec = 1.5f
            )
        } else null

        val skip720Reason = if (isAlreadyUltraEfficient) {
            "Source is already ultra-efficient (${origW}x${origH} @ ${srcBitrate / 1000} kbps <= 450 kbps). Re-encoding would provide no quality or size benefit and is intentionally skipped."
        } else null

        return VideoTranscodePlan(
            shouldGenerate1080p = should1080,
            skipReason1080p = skip1080Reason,
            target1080 = target1080,
            shouldGenerate720p = should720,
            skipReason720p = skip720Reason,
            target720 = target720
        )
    }

    /**
     * Calculates intelligent, source-aware rendition targets (backward-compatible).
     */
    fun computeRenditionTargets(source: VideoSourceAnalysis): Pair<RenditionTarget, RenditionTarget> {
        val origW = source.width
        val origH = source.height
        val srcBitrate = source.sourceBitrateBps

        val dim1080 = calculateTargetDimensions(origW, origH, 1920, 1080)
        val dim720 = calculateTargetDimensions(origW, origH, 1280, 720)

        val full1080Pixels = 1920.0 * 1080.0
        val target1080Pixels = dim1080.width.toDouble() * dim1080.height.toDouble()
        val pixelRatio1080 = (target1080Pixels / full1080Pixels).coerceIn(0.35, 1.0)
        var bit1080 = (3_600_000 * pixelRatio1080).toInt().coerceIn(2_000_000, 4_200_000)

        if (srcBitrate > 500_000) {
            val maxAllowedFromSource = (srcBitrate * 0.90).toInt()
            bit1080 = minOf(bit1080, maxAllowedFromSource).coerceAtLeast(1_500_000)
        }

        val full720Pixels = 1280.0 * 720.0
        val target720Pixels = dim720.width.toDouble() * dim720.height.toDouble()
        val pixelRatio720 = (target720Pixels / full720Pixels).coerceIn(0.35, 1.0)
        var bit720 = (1_800_000 * pixelRatio720).toInt().coerceIn(1_000_000, 2_200_000)

        if (srcBitrate > 500_000) {
            val maxAllowed720 = (srcBitrate * 0.65).toInt()
            bit720 = minOf(bit720, maxAllowed720).coerceAtLeast(800_000)
        } else if (srcBitrate in 100_000..500_000) {
            bit720 = (srcBitrate * 0.80).toInt().coerceAtLeast(180_000)
        }

        if (bit1080 <= bit720 * 1.25) {
            bit1080 = (bit720 * 1.35).toInt()
        }

        val iFrameInterval = 1.5f

        val target1080 = RenditionTarget(
            label = "1080p",
            targetDimensions = dim1080,
            targetVideoBitrateBps = bit1080,
            targetAudioBitrateBps = if (source.hasAudio) 128_000 else 0,
            iFrameIntervalSec = iFrameInterval
        )

        val target720 = RenditionTarget(
            label = "720p",
            targetDimensions = dim720,
            targetVideoBitrateBps = bit720,
            targetAudioBitrateBps = if (source.hasAudio) 96_000 else 0,
            iFrameIntervalSec = iFrameInterval
        )

        return Pair(target1080, target720)
    }

    /**
     * Transcodes a video using AndroidX Media3 Transformer configured with:
     * - Intelligent Content-Aware Bitrate
     * - Keyframe interval matching social standards (1.5s for instant seeks and reel swiping)
     * - High-fidelity AAC audio encoding (128 kbps for 1080p, 96 kbps for 720p)
     * - Direct file descriptor / local file source for reliable random-access I/O
     * - Automatic OpenGL bypass when source and target dimensions match 1:1
     */
    suspend fun transcodeVideoMedia3(
        context: Context,
        inputFile: File,
        outputFile: File,
        targetW: Int,
        targetH: Int,
        sourceW: Int,
        sourceH: Int,
        targetVideoBitrateBps: Int = 2_200_000,
        targetAudioBitrateBps: Int = 128_000,
        hasAudio: Boolean = true,
        iFrameIntervalSec: Float = 1.5f
    ): TranscodeResult = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            try {
                if (outputFile.exists()) {
                    outputFile.delete()
                }

                val mediaItem = MediaItem.fromUri(Uri.fromFile(inputFile))
                val editedMediaItemBuilder = EditedMediaItem.Builder(mediaItem)

                // Only apply Presentation effect if target dimensions differ from source dimensions.
                // Omitting effects when dimensions match bypasses OpenGL ES shader initialization!
                if (targetW != sourceW || targetH != sourceH) {
                    val presentationEffect = Presentation.createForWidthAndHeight(
                        targetW,
                        targetH,
                        Presentation.LAYOUT_SCALE_TO_FIT
                    )
                    editedMediaItemBuilder.setEffects(Effects(emptyList(), listOf(presentationEffect)))
                } else {
                    editedMediaItemBuilder.setEffects(Effects(emptyList(), emptyList()))
                }

                val editedMediaItem = editedMediaItemBuilder.build()

                // Configure intelligent H.264 encoder settings:
                // Let DefaultEncoderFactory automatically negotiate the optimal profile and level
                // based on dimensions and frame rate, avoiding macroblock limit errors on 720x720 / high fps.
                val videoEncoderSettingsBuilder = VideoEncoderSettings.Builder()
                    .setBitrate(targetVideoBitrateBps)
                    .setiFrameIntervalSeconds(iFrameIntervalSec)

                val videoEncoderSettings = videoEncoderSettingsBuilder.build()

                val encoderFactoryBuilder = DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(videoEncoderSettings)
                    .setEnableFallback(true)

                if (hasAudio && targetAudioBitrateBps > 0) {
                    val audioEncoderSettings = AudioEncoderSettings.Builder()
                        .setBitrate(targetAudioBitrateBps.coerceAtLeast(64_000))
                        .build()
                    encoderFactoryBuilder.setRequestedAudioEncoderSettings(audioEncoderSettings)
                }

                val encoderFactory = encoderFactoryBuilder.build()

                val transformerBuilder = Transformer.Builder(context)
                    .setEncoderFactory(encoderFactory)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)

                if (hasAudio && targetAudioBitrateBps > 0) {
                    transformerBuilder.setAudioMimeType(MimeTypes.AUDIO_AAC)
                }

                var finished = false

                val transformer = transformerBuilder
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (!finished && continuation.isActive) {
                                finished = true
                                continuation.resume(TranscodeResult.Success)
                            }
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            val errorMsg = "${exportException.errorCodeName}: ${exportException.message}"
                            android.util.Log.e("MediaUploadHelper", "Media3 transcode error: $errorMsg", exportException)
                            if (!finished && continuation.isActive) {
                                finished = true
                                continuation.resume(TranscodeResult.Failure(errorMsg))
                            }
                        }
                    })
                    .build()

                continuation.invokeOnCancellation {
                    try {
                        transformer.cancel()
                    } catch (_: Exception) {}
                }

                transformer.start(editedMediaItem, outputFile.absolutePath)
            } catch (e: Exception) {
                val errorMsg = "Media3 transcode start failed: ${e.message}"
                android.util.Log.e("MediaUploadHelper", errorMsg, e)
                if (continuation.isActive) {
                    continuation.resume(TranscodeResult.Failure(errorMsg))
                }
            }
        }
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
     * Complete professional dual-stream video compressor and uploader:
     * 1. Extracts high-quality thumbnail frame at 1.0s (or 25%) in WebP 90% (matching Facebook).
     * 2. Analyzes source video (resolution, duration, bitrate, orientation) to drive intelligent encoding.
     * 3. Calculates content-aware VBR bitrates and proportional dimensions for 1080p and 720p.
     * 4. Transcodes 720p stream with Media3 Transformer VBR, measures actual file size, uploads to posts/videos/720p.
     * 5. Transcodes 1080p stream with Media3 Transformer VBR, measures actual file size, uploads to posts/videos/1080p.
     * 6. Uploads untouched original video to posts/videos/original.
     * 7. Failsafe passthrough ensures uploading never fails or stalls.
     * 8. Returns VideoUploadResult with originalUrl, url1080, url720, thumbUrl.
     */
    suspend fun processAndUploadVideo(
        context: Context,
        uri: Uri,
        onProgress: ((String, Float) -> Unit)? = null
    ): VideoUploadResult? = withContext(Dispatchers.IO) {
        onProgress?.invoke("Extracting HD thumbnail...", 0.05f)
        val meta = extractVideoThumbnail(context, uri)

        var thumbUrl: String? = null
        if (meta?.thumbnailBytes != null) {
            onProgress?.invoke("Uploading thumbnail...", 0.15f)
            val thumbExt = if (meta.mimeType == "image/jpeg") "jpg" else "webp"
            val thumbName = "thumb_${System.currentTimeMillis()}.$thumbExt"
            val thumbJson = uploadSingleFile(
                fileBytes = meta.thumbnailBytes,
                fileName = thumbName,
                mimeType = meta.mimeType,
                folder = "posts/thumbnails"
            )
            thumbUrl = thumbJson?.optString("url")?.takeIf { it.isNotBlank() }
        }

        // 1. Copy source URI to a local temporary cache file for reliable random access and hash calculation
        onProgress?.invoke("Preparing video for analysis...", 0.20f)
        val cacheDir = context.cacheDir
        val sourceTempFile = File(cacheDir, "source_input_${System.currentTimeMillis()}.mp4")
        try {
            context.contentResolver.openInputStream(uri)?.use { inStream ->
                FileOutputStream(sourceTempFile).use { outStream ->
                    inStream.copyTo(outStream)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaUploadHelper", "Failed copying input URI to cache: ${e.message}")
        }

        val sourceFile = if (sourceTempFile.exists() && sourceTempFile.length() > 0) sourceTempFile else null
        val sourceSha256 = sourceFile?.let { calculateSha256(it) }
        val sourceSizeBytes = sourceFile?.length() ?: -1L

        // 2. Intelligent source video analysis
        val sourceAnalysis = analyzeVideoSource(context, sourceFile?.let { Uri.fromFile(it) } ?: uri)
        val plan = createVideoTranscodePlan(sourceAnalysis)

        val origW = sourceAnalysis.width
        val origH = sourceAnalysis.height
        val durationSec = sourceAnalysis.durationSec

        android.util.Log.i(
            "FeatheredCompressor",
            "=== SOURCE VIDEO ANALYSIS ===" +
                "\nResolution: ${origW}x${origH}" +
                "\nDuration: ${durationSec}s" +
                "\nBitrate: ${sourceAnalysis.sourceBitrateBps} bps" +
                "\nSize: $sourceSizeBytes bytes" +
                "\nSHA-256: $sourceSha256" +
                "\nHas Audio: ${sourceAnalysis.hasAudio}"
        )
        android.util.Log.i(
            "FeatheredCompressor",
            "Transcode Plan: Generate 1080p = ${plan.shouldGenerate1080p} (skip: ${plan.skipReason1080p}), Generate 720p = ${plan.shouldGenerate720p}"
        )

        val temp720File = File(cacheDir, "transcode_720_${System.currentTimeMillis()}.mp4")
        val temp1080File = File(cacheDir, "transcode_1080_${System.currentTimeMillis()}.mp4")

        var url720: String? = null
        var url1080: String? = null
        var originalUrl: String? = null

        try {
            // 3. Transcode 720p stream if planned
            if (plan.shouldGenerate720p && plan.target720 != null && sourceFile != null) {
                val t720 = plan.target720
                onProgress?.invoke("Compressing 720p stream...", 0.28f)
                val transcodeRes = transcodeVideoMedia3(
                    context = context,
                    inputFile = sourceFile,
                    outputFile = temp720File,
                    targetW = t720.targetDimensions.width,
                    targetH = t720.targetDimensions.height,
                    sourceW = origW,
                    sourceH = origH,
                    targetVideoBitrateBps = t720.targetVideoBitrateBps,
                    targetAudioBitrateBps = t720.targetAudioBitrateBps,
                    hasAudio = sourceAnalysis.hasAudio,
                    iFrameIntervalSec = t720.iFrameIntervalSec
                )

                if (transcodeRes is TranscodeResult.Success) {
                    val verification = verifyTranscodedVideo(temp720File, sourceSha256, sourceSizeBytes)
                    if (verification.isValid) {
                        android.util.Log.i(
                            "FeatheredCompressor",
                            "=== 720p TRANSCODE VERIFIED ===" +
                                "\nFile: ${temp720File.name}" +
                                "\nSize: ${verification.actualSizeBytes} bytes (original: $sourceSizeBytes bytes)" +
                                "\nDimensions: ${verification.actualWidth}x${verification.actualHeight}" +
                                "\nDuration: ${verification.durationSec}s" +
                                "\nSHA-256: ${verification.sha256}"
                        )
                        onProgress?.invoke("Uploading verified 720p stream...", 0.45f)
                        url720 = uploadVideoFile(temp720File, "posts/videos/720p") { pct ->
                            onProgress?.invoke("Uploading 720p stream...", 0.45f + (pct * 0.15f))
                        }
                    } else {
                        android.util.Log.e(
                            "FeatheredCompressor",
                            "720p transcode verification failed: ${verification.errorMessage}. Will not upload invalid rendition."
                        )
                    }
                } else if (transcodeRes is TranscodeResult.Failure) {
                    android.util.Log.e("FeatheredCompressor", "720p transcode failed: ${transcodeRes.reason}")
                }
            } else {
                android.util.Log.i("FeatheredCompressor", "720p rendition skipped: ${plan.skipReason720p}")
            }

            // 4. Transcode 1080p stream if planned
            if (plan.shouldGenerate1080p && plan.target1080 != null && sourceFile != null) {
                val t1080 = plan.target1080
                onProgress?.invoke("Compressing 1080p HD stream...", 0.62f)
                val transcodeRes = transcodeVideoMedia3(
                    context = context,
                    inputFile = sourceFile,
                    outputFile = temp1080File,
                    targetW = t1080.targetDimensions.width,
                    targetH = t1080.targetDimensions.height,
                    sourceW = origW,
                    sourceH = origH,
                    targetVideoBitrateBps = t1080.targetVideoBitrateBps,
                    targetAudioBitrateBps = t1080.targetAudioBitrateBps,
                    hasAudio = sourceAnalysis.hasAudio,
                    iFrameIntervalSec = t1080.iFrameIntervalSec
                )

                if (transcodeRes is TranscodeResult.Success) {
                    val verification = verifyTranscodedVideo(temp1080File, sourceSha256, sourceSizeBytes)
                    if (verification.isValid) {
                        android.util.Log.i(
                            "FeatheredCompressor",
                            "=== 1080p TRANSCODE VERIFIED ===" +
                                "\nFile: ${temp1080File.name}" +
                                "\nSize: ${verification.actualSizeBytes} bytes (original: $sourceSizeBytes bytes)" +
                                "\nDimensions: ${verification.actualWidth}x${verification.actualHeight}" +
                                "\nDuration: ${verification.durationSec}s" +
                                "\nSHA-256: ${verification.sha256}"
                        )
                        onProgress?.invoke("Uploading verified 1080p HD stream...", 0.77f)
                        url1080 = uploadVideoFile(temp1080File, "posts/videos/1080p") { pct ->
                            onProgress?.invoke("Uploading 1080p HD stream...", 0.77f + (pct * 0.12f))
                        }
                    } else {
                        android.util.Log.e(
                            "FeatheredCompressor",
                            "1080p transcode verification failed: ${verification.errorMessage}. Will not upload invalid rendition."
                        )
                    }
                } else if (transcodeRes is TranscodeResult.Failure) {
                    android.util.Log.e("FeatheredCompressor", "1080p transcode failed: ${transcodeRes.reason}")
                }
            } else {
                android.util.Log.i("FeatheredCompressor", "1080p rendition skipped: ${plan.skipReason1080p}")
            }

            // 5. Upload untouched pristine original video to posts/videos/original
            onProgress?.invoke("Uploading original video...", 0.90f)
            originalUrl = if (sourceFile != null) {
                uploadVideoFile(sourceFile, "posts/videos/original") { pct ->
                    onProgress?.invoke("Uploading original video...", 0.90f + (pct * 0.09f))
                }
            } else {
                uploadVideoFromUri(context, uri, "posts/videos/original")
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaUploadHelper", "Video processing caught error: ${e.message}", e)
        } finally {
            try { sourceTempFile.delete() } catch (_: Exception) {}
            try { temp720File.delete() } catch (_: Exception) {}
            try { temp1080File.delete() } catch (_: Exception) {}
        }

        // 6. Final URL Resolution:
        // NEVER upload duplicate copies of the original into 720p or 1080p folders!
        if (originalUrl.isNullOrBlank()) {
            originalUrl = uploadVideoFromUri(context, uri, "posts/videos/original")
        }

        if (originalUrl.isNullOrBlank() && url720.isNullOrBlank() && url1080.isNullOrBlank()) {
            return@withContext null
        }

        val finalOriginal = originalUrl ?: url720 ?: url1080!!

        // If 720p was successfully transcoded, use its URL. If it failed, reuse finalOriginal directly.
        val final720 = url720 ?: finalOriginal

        // If 1080p was successfully transcoded, use its URL.
        // If 1080p was intentionally skipped (e.g. source was <= 720p), reuse final720 (or finalOriginal).
        val final1080 = url1080 ?: final720

        onProgress?.invoke("Video ready!", 1.0f)

        android.util.Log.i(
            "FeatheredCompressor",
            "=== FINAL RENDITIONS READY ===" +
                "\nOriginal: $finalOriginal" +
                "\n1080p: $final1080" +
                "\n720p: $final720" +
                "\nThumbnail: $thumbUrl"
        )

        VideoUploadResult(
            originalUrl = finalOriginal,
            url1080 = final1080,
            url720 = final720,
            thumbUrl = thumbUrl,
            width = origW,
            height = origH,
            durationSec = durationSec
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
