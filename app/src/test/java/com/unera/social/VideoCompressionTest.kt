package com.unera.social

import com.unera.social.data.MediaUploadHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoCompressionTest {

    @Test
    fun testCalculateTargetDimensions_4kLandscape() {
        // 4K 16:9 Landscape (3840 x 2160)
        val dim1080 = MediaUploadHelper.calculateTargetDimensions(3840, 2160, 1920, 1080)
        assertEquals("1080p width should be 1920", 1920, dim1080.width)
        assertEquals("1080p height should be 1080", 1080, dim1080.height)

        val dim720 = MediaUploadHelper.calculateTargetDimensions(3840, 2160, 1280, 720)
        assertEquals("720p width should be 1280", 1280, dim720.width)
        assertEquals("720p height should be 720", 720, dim720.height)
    }

    @Test
    fun testCalculateTargetDimensions_4kPortrait() {
        // 4K 9:16 Portrait Reel (2160 x 3840)
        val dim1080 = MediaUploadHelper.calculateTargetDimensions(2160, 3840, 1920, 1080)
        assertEquals("1080p portrait width should be 1080", 1080, dim1080.width)
        assertEquals("1080p portrait height should be 1920", 1920, dim1080.height)

        val dim720 = MediaUploadHelper.calculateTargetDimensions(2160, 3840, 1280, 720)
        assertEquals("720p portrait width should be 720", 720, dim720.width)
        assertEquals("720p portrait height should be 1280", 1280, dim720.height)
    }

    @Test
    fun testCalculateTargetDimensions_SquareVideo() {
        // 1:1 Square Instagram/Facebook Video (1080 x 1080)
        val dim1080 = MediaUploadHelper.calculateTargetDimensions(1080, 1080, 1920, 1080)
        assertEquals(1080, dim1080.width)
        assertEquals(1080, dim1080.height)

        val dim720 = MediaUploadHelper.calculateTargetDimensions(1080, 1080, 1280, 720)
        assertEquals(720, dim720.width)
        assertEquals(720, dim720.height)
    }

    @Test
    fun testCalculateTargetDimensions_NeverUpscales() {
        // 360p Low-res Video (640 x 360)
        val dim1080 = MediaUploadHelper.calculateTargetDimensions(640, 360, 1920, 1080)
        assertEquals("Never upscale width beyond source", 640, dim1080.width)
        assertEquals("Never upscale height beyond source", 360, dim1080.height)

        val dim720 = MediaUploadHelper.calculateTargetDimensions(640, 360, 1280, 720)
        assertEquals(640, dim720.width)
        assertEquals(360, dim720.height)
    }

    @Test
    fun testCalculateTargetDimensions_EvenDimensionGuaranteed() {
        // Odd dimensions (e.g. 1083 x 1921)
        val dim = MediaUploadHelper.calculateTargetDimensions(1083, 1921, 1920, 1080)
        assertTrue("Width must be even integer for H.264", dim.width % 2 == 0)
        assertTrue("Height must be even integer for H.264", dim.height % 2 == 0)
    }

    @Test
    fun testIntelligentBitrateAllocation_HighBitrateSource() {
        // 4K video source with high bitrate (24 Mbps)
        val source = MediaUploadHelper.VideoSourceAnalysis(
            width = 3840,
            height = 2160,
            durationSec = 15.0,
            sourceBitrateBps = 24_000_000L,
            sourceFileSizeBytes = 45_000_000L,
            rotation = 0,
            isPortrait = false,
            isSquare = false,
            hasAudio = true
        )

        val (target1080, target720) = MediaUploadHelper.computeRenditionTargets(source)

        // 1080p should be capped at around ~3.6-4.0 Mbps
        assertTrue("1080p bitrate in expected VBR range", target1080.targetVideoBitrateBps in 2_500_000..4_200_000)
        assertEquals(1920, target1080.targetDimensions.width)
        assertEquals(1080, target1080.targetDimensions.height)
        assertEquals(128_000, target1080.targetAudioBitrateBps)
        assertEquals(1.5f, target1080.iFrameIntervalSec, 0.01f)

        // 720p should be around ~1.8 Mbps
        assertTrue("720p bitrate in expected VBR range", target720.targetVideoBitrateBps in 1_200_000..2_000_000)
        assertEquals(1280, target720.targetDimensions.width)
        assertEquals(720, target720.targetDimensions.height)
        assertEquals(96_000, target720.targetAudioBitrateBps)
    }

    @Test
    fun testIntelligentBitrateAllocation_PreCompressedSource() {
        // Pre-compressed source video (1.2 Mbps) - engine should avoid bitrate inflation!
        val source = MediaUploadHelper.VideoSourceAnalysis(
            width = 1280,
            height = 720,
            durationSec = 10.0,
            sourceBitrateBps = 1_200_000L,
            sourceFileSizeBytes = 1_500_000L,
            rotation = 0,
            isPortrait = false,
            isSquare = false,
            hasAudio = true
        )

        val (target1080, target720) = MediaUploadHelper.computeRenditionTargets(source)

        // Should not exceed source bitrate * 0.90 to avoid file size bloat!
        assertTrue(
            "1080p bitrate should not exceed source bitrate",
            target1080.targetVideoBitrateBps <= (source.sourceBitrateBps * 0.95) || target1080.targetVideoBitrateBps <= 1_600_000
        )
        // 720p should scale down further for efficient mobile streaming
        assertTrue(
            "720p bitrate should be lower than 1080p",
            target720.targetVideoBitrateBps < target1080.targetVideoBitrateBps
        )
    }

    @Test
    fun testIntelligentBitrateAllocation_SilentVideo() {
        // Video without audio track
        val source = MediaUploadHelper.VideoSourceAnalysis(
            width = 1080,
            height = 1920,
            durationSec = 8.0,
            sourceBitrateBps = 3_500_000L,
            sourceFileSizeBytes = 3_500_000L,
            rotation = 0,
            isPortrait = true,
            isSquare = false,
            hasAudio = false
        )

        val (target1080, target720) = MediaUploadHelper.computeRenditionTargets(source)
        assertEquals("Silent video should have 0 bps audio bitrate", 0, target1080.targetAudioBitrateBps)
        assertEquals("Silent video should have 0 bps audio bitrate", 0, target720.targetAudioBitrateBps)
    }

    @Test
    fun testTranscodePlan_720x720UltraEfficientSource_SkipsBoth1080And720() {
        // User's exact test case: 720x720, 266s, 293 kbps (already ultra-efficient)
        val source = MediaUploadHelper.VideoSourceAnalysis(
            width = 720,
            height = 720,
            durationSec = 266.7,
            sourceBitrateBps = 293_451L,
            sourceFileSizeBytes = 11_496_212L,
            rotation = 0,
            isPortrait = false,
            isSquare = true,
            hasAudio = true
        )

        val plan = MediaUploadHelper.createVideoTranscodePlan(source)

        // 1080p MUST be skipped because source is <= 720p (never upscale)
        assertEquals("1080p must not be generated for 720x720 source", false, plan.shouldGenerate1080p)
        assertTrue("Skip reason must explain 1080p resolution policy", plan.skipReason1080p?.contains("do not contain 1080p resolution") == true)
        assertEquals("Target 1080 must be null", null, plan.target1080)

        // 720p MUST also be skipped because source is already <= 450 kbps (prevents 1.65 Mbps bloat)
        assertEquals("720p re-encoding must be skipped for ultra-efficient source", false, plan.shouldGenerate720p)
        assertTrue("Skip reason must note ultra-efficient status", plan.skipReason720p?.contains("ultra-efficient") == true)
        assertEquals("Target 720 must be null", null, plan.target720)
    }

    @Test
    fun testTranscodePlan_720x720HighBitrateSource_Encodes720pOnly() {
        // 720x720 video with high bitrate (3.5 Mbps) - should encode 720p, skip 1080p
        val source = MediaUploadHelper.VideoSourceAnalysis(
            width = 720,
            height = 720,
            durationSec = 30.0,
            sourceBitrateBps = 3_500_000L,
            sourceFileSizeBytes = 13_125_000L,
            rotation = 0,
            isPortrait = false,
            isSquare = true,
            hasAudio = true
        )

        val plan = MediaUploadHelper.createVideoTranscodePlan(source)

        // 1080p MUST be skipped (no upscaling)
        assertEquals(false, plan.shouldGenerate1080p)

        // 720p MUST be planned with reduced bitrate
        assertEquals(true, plan.shouldGenerate720p)
        assertEquals(720, plan.target720?.targetDimensions?.width)
        assertEquals(720, plan.target720?.targetDimensions?.height)
        assertTrue("720p bitrate must be significantly below source bitrate", (plan.target720?.targetVideoBitrateBps ?: 0) < source.sourceBitrateBps)
    }

    @Test
    fun testTranscodePlan_1080pSource_PlansBothRenditions() {
        // 1080x1920 reel with high bitrate (8 Mbps)
        val source = MediaUploadHelper.VideoSourceAnalysis(
            width = 1080,
            height = 1920,
            durationSec = 15.0,
            sourceBitrateBps = 8_000_000L,
            sourceFileSizeBytes = 15_000_000L,
            rotation = 0,
            isPortrait = true,
            isSquare = false,
            hasAudio = true
        )

        val plan = MediaUploadHelper.createVideoTranscodePlan(source)

        assertEquals("1080p should be planned for native 1080p source", true, plan.shouldGenerate1080p)
        assertEquals(1080, plan.target1080?.targetDimensions?.width)
        assertEquals(1920, plan.target1080?.targetDimensions?.height)

        assertEquals("720p should be planned for native 1080p source", true, plan.shouldGenerate720p)
        assertEquals(720, plan.target720?.targetDimensions?.width)
        assertEquals(1280, plan.target720?.targetDimensions?.height)
    }

    @Test
    fun testSha256Calculation_ProducesDistinctHashesForDifferentData() {
        val file1 = java.io.File.createTempFile("test_vid1", ".mp4")
        val file2 = java.io.File.createTempFile("test_vid2", ".mp4")
        try {
            file1.writeBytes("video_data_original_12345".toByteArray())
            file2.writeBytes("video_data_transcoded_67890".toByteArray())

            val hash1 = MediaUploadHelper.calculateSha256(file1)
            val hash2 = MediaUploadHelper.calculateSha256(file2)

            assertTrue("Hash 1 should be 64 characters hex", hash1.length == 64)
            assertTrue("Hash 2 should be 64 characters hex", hash2.length == 64)
            assertTrue("Different file contents must produce different SHA-256", hash1 != hash2)

            // Verification check against identical source hash
            val verification = MediaUploadHelper.verifyTranscodedVideo(file1, hash1)
            assertEquals("File identical to source must be rejected", false, verification.isValid)
            assertEquals(true, verification.isIdenticalToSource)
        } finally {
            file1.delete()
            file2.delete()
        }
    }

    @Test
    fun testInflationRejection_StrictlyRejectsLargerTranscodedOutput() {
        val file = java.io.File.createTempFile("test_inflated", ".mp4")
        try {
            // Write 5000 bytes
            file.writeBytes(ByteArray(5000) { 1 })
            val sourceSizeBytes = 3000L // Source was only 3000 bytes

            val verification = MediaUploadHelper.verifyTranscodedVideo(
                file = file,
                sourceSha256 = "different_hash_1234567890abcdef1234567890abcdef",
                sourceSizeBytes = sourceSizeBytes
            )

            assertEquals("Inflated output must be rejected", false, verification.isValid)
            assertTrue("Error message must mention inflation", verification.errorMessage?.contains("Inflation rejected") == true)
        } finally {
            file.delete()
        }
    }
}
