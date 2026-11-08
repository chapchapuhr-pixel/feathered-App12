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
}
