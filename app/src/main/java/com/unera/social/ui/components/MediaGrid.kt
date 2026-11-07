package com.unera.social.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

/**
 * MediaItem - Represents an individual media image matching web MediaItem interface.
 */
data class MediaItem(
    val url: String,
    val alt: String? = null,
    val thumb: String? = null,
    val feed: String? = null,
    val full: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val versions: ImageVersions? = null
)

/**
 * MediaGrid - Complete Android Jetpack Compose counterpart of web MediaGrid.tsx.
 *
 * Implements:
 * - Single image: edge-to-edge container with natural aspect ratio support on #050B18
 * - 2 images: 2-column grid, 320dp height, 2dp gap
 * - 3 images: 2 columns, left tall 420dp image and right 2 stacked 420dp images
 * - 4 images: 2x2 grid, 260dp row heights, 2dp gap
 * - 5+ images (Grid 5):
 *     * Top row: 2 images, 250dp height, 2dp gap
 *     * Bottom row: 3 images, 170dp height, 2dp gap
 *     * 5th image (index 4) displays "+X" overlay if total > 5 (e.g. 10 images shows "+5")
 * - Built-in image preview modal (Lightbox) with:
 *     * Black 92% semi-transparent backdrop
 *     * Top-right close button (✕)
 *     * Top image index counter (e.g., 5 / 10)
 *     * Left (←) and Right (→) circular navigation buttons
 *     * Touch drag/swipe gesture support
 *     * Hardware BackHandler support
 */
@Composable
fun MediaGrid(
    media: List<MediaItem>,
    modifier: Modifier = Modifier,
    onOpen: ((String) -> Unit)? = null,
    onImageClick: ((Int) -> Unit)? = null,
    maxHeight: Dp = 540.dp,
    gap: Dp = 2.dp,
    enableInternalPreview: Boolean = true
) {
    if (media.isEmpty()) return

    var activeIndex by remember { mutableStateOf<Int?>(null) }

    val handleClick = { url: String, index: Int ->
        if (onImageClick != null) {
            onImageClick(index)
        } else if (enableInternalPreview) {
            activeIndex = index
        }
        onOpen?.invoke(url)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .testTag("media_grid_${media.size}")
    ) {
        when (media.size) {
            1 -> {
                // Single image layout - edge-to-edge (0 left, 0 right) with natural aspect ratio support
                val item = media[0]
                val displayUrl = item.full ?: item.feed ?: item.url
                val aspectRatio = if (item.width != null && item.height != null && item.width > 0 && item.height > 0) {
                    item.width.toFloat() / item.height.toFloat()
                } else null

                val versions = item.versions ?: ImageVersions(
                    original = displayUrl,
                    high = displayUrl,
                    medium = item.feed ?: item.url,
                    low = item.thumb ?: item.url
                )

                val effectiveRatio = aspectRatio?.coerceIn(0.65f, 1.91f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (effectiveRatio != null) {
                                Modifier.aspectRatio(effectiveRatio, matchHeightConstraintsFirst = false)
                            } else {
                                Modifier.heightIn(min = 220.dp, max = maxHeight)
                            }
                        )
                        .heightIn(max = maxHeight)
                        .clipToBounds()
                        .clickable { handleClick(displayUrl, 0) }
                        .testTag("media_grid_item_0")
                ) {
                    SeamlessImage(
                        urls = versions,
                        contentDescription = item.alt ?: "Post image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                    )
                }
            }

            2 -> {
                // Two images layout: 2 columns, height 320dp, gap 2dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                        .background(Color.Black),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    MediaGridTile(
                        item = media[0],
                        index = 0,
                        modifier = Modifier
                            .weight(1f)
                            .height(320.dp),
                        onClick = { handleClick(media[0].full ?: media[0].feed ?: media[0].url, 0) }
                    )
                    MediaGridTile(
                        item = media[1],
                        index = 1,
                        modifier = Modifier
                            .weight(1f)
                            .height(320.dp),
                        onClick = { handleClick(media[1].full ?: media[1].feed ?: media[1].url, 1) }
                    )
                }
            }

            3 -> {
                // Three images layout: left tall tile (420dp), right stacked 2 tiles (420dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .clipToBounds()
                        .background(Color.Black),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    // Left item (row-span-2)
                    MediaGridTile(
                        item = media[0],
                        index = 0,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = { handleClick(media[0].full ?: media[0].feed ?: media[0].url, 0) }
                    )

                    // Right column (2 stacked squares)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        MediaGridTile(
                            item = media[1],
                            index = 1,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            onClick = { handleClick(media[1].full ?: media[1].feed ?: media[1].url, 1) }
                        )
                        MediaGridTile(
                            item = media[2],
                            index = 2,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            onClick = { handleClick(media[2].full ?: media[2].feed ?: media[2].url, 2) }
                        )
                    }
                }
            }

            4 -> {
                // Four images layout: 2x2 square grid, height 260dp per row
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                        .background(Color.Black),
                    verticalArrangement = Arrangement.spacedBy(gap)
                ) {
                    // Row 1: items 0 & 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        MediaGridTile(
                            item = media[0],
                            index = 0,
                            modifier = Modifier
                                .weight(1f)
                                .height(260.dp),
                            onClick = { handleClick(media[0].full ?: media[0].feed ?: media[0].url, 0) }
                        )
                        MediaGridTile(
                            item = media[1],
                            index = 1,
                            modifier = Modifier
                                .weight(1f)
                                .height(260.dp),
                            onClick = { handleClick(media[1].full ?: media[1].feed ?: media[1].url, 1) }
                        )
                    }

                    // Row 2: items 2 & 3
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        MediaGridTile(
                            item = media[2],
                            index = 2,
                            modifier = Modifier
                                .weight(1f)
                                .height(260.dp),
                            onClick = { handleClick(media[2].full ?: media[2].feed ?: media[2].url, 2) }
                        )
                        MediaGridTile(
                            item = media[3],
                            index = 3,
                            modifier = Modifier
                                .weight(1f)
                                .height(260.dp),
                            onClick = { handleClick(media[3].full ?: media[3].feed ?: media[3].url, 3) }
                        )
                    }
                }
            }

            else -> {
                // 5 or more images (Grid 5 layout with +X overlay for extra items):
                // Top row: 2 images, height 250dp
                // Bottom row: 3 images, height 170dp, 5th image (index 4) has +X overlay
                val visibleItems = media.take(5)
                val extra = media.size - 5

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                        .background(Color.Black),
                    verticalArrangement = Arrangement.spacedBy(gap)
                ) {
                    // Top row: items 0 & 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        MediaGridTile(
                            item = visibleItems[0],
                            index = 0,
                            modifier = Modifier
                                .weight(1f)
                                .height(250.dp),
                            onClick = { handleClick(visibleItems[0].full ?: visibleItems[0].feed ?: visibleItems[0].url, 0) }
                        )
                        MediaGridTile(
                            item = visibleItems[1],
                            index = 1,
                            modifier = Modifier
                                .weight(1f)
                                .height(250.dp),
                            onClick = { handleClick(visibleItems[1].full ?: visibleItems[1].feed ?: visibleItems[1].url, 1) }
                        )
                    }

                    // Bottom row: items 2, 3, & 4 (item 4 shows +extra overlay if media.size > 5)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        MediaGridTile(
                            item = visibleItems[2],
                            index = 2,
                            modifier = Modifier
                                .weight(1f)
                                .height(170.dp),
                            onClick = { handleClick(visibleItems[2].full ?: visibleItems[2].feed ?: visibleItems[2].url, 2) }
                        )
                        MediaGridTile(
                            item = visibleItems[3],
                            index = 3,
                            modifier = Modifier
                                .weight(1f)
                                .height(170.dp),
                            onClick = { handleClick(visibleItems[3].full ?: visibleItems[3].feed ?: visibleItems[3].url, 3) }
                        )
                        MediaGridTile(
                            item = visibleItems[4],
                            index = 4,
                            extraCount = if (extra > 0) extra else 0,
                            modifier = Modifier
                                .weight(1f)
                                .height(170.dp),
                            onClick = { handleClick(visibleItems[4].full ?: visibleItems[4].feed ?: visibleItems[4].url, 4) }
                        )
                    }
                }
            }
        }
    }

    // Image preview modal (Lightbox) matching web React implementation
    if (activeIndex != null) {
        MediaPreviewModal(
            media = media,
            initialIndex = activeIndex!!,
            onDismiss = { activeIndex = null }
        )
    }
}

/**
 * Convenience overload for List<String> URLs.
 */
@JvmName("MediaGridFromStrings")
@Composable
fun MediaGrid(
    images: List<String>,
    modifier: Modifier = Modifier,
    onOpen: ((String) -> Unit)? = null,
    onImageClick: ((Int) -> Unit)? = null,
    maxHeight: Dp = 540.dp,
    gap: Dp = 2.dp,
    enableInternalPreview: Boolean = true
) {
    val mediaItems = remember(images) {
        images.mapIndexed { idx, url -> MediaItem(url = url, alt = "Photo ${idx + 1}") }
    }
    MediaGrid(
        media = mediaItems,
        modifier = modifier,
        onOpen = onOpen,
        onImageClick = onImageClick,
        maxHeight = maxHeight,
        gap = gap,
        enableInternalPreview = enableInternalPreview
    )
}

/**
 * MediaGridTile - Renders an individual tile in the media grid with optional +X overlay.
 */
@Composable
fun MediaGridTile(
    item: MediaItem,
    index: Int,
    modifier: Modifier = Modifier,
    extraCount: Int = 0,
    onClick: () -> Unit
) {
    val displayUrl = item.full ?: item.feed ?: item.url
    val versions = item.versions ?: ImageVersions(
        original = displayUrl,
        high = displayUrl,
        medium = item.feed ?: item.url,
        low = item.thumb ?: item.url
    )

    Box(
        modifier = modifier
            .clipToBounds()
            .background(Color(0xFF162032))
            .clickable(onClick = onClick)
            .testTag("media_grid_item_$index")
    ) {
        SeamlessImage(
            urls = versions,
            contentDescription = item.alt ?: "Post image ${index + 1}",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        )

        if (extraCount > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.58f))
                    .testTag("media_grid_extra_overlay"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$extraCount",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * MediaPreviewModal - Lightbox modal matching React `Image preview modal`.
 */
@Composable
fun MediaPreviewModal(
    media: List<MediaItem>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    var currentIndex by remember { mutableStateOf(initialIndex.coerceIn(0, media.size - 1)) }
    var dragAccumulator by remember { mutableStateOf(0f) }

    BackHandler {
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable { onDismiss() }
                .pointerInput(media.size) {
                    if (media.size > 1) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (dragAccumulator > 60f) {
                                    // Swipe Right -> previous
                                    currentIndex = if (currentIndex == 0) media.size - 1 else currentIndex - 1
                                } else if (dragAccumulator < -60f) {
                                    // Swipe Left -> next
                                    currentIndex = if (currentIndex == media.size - 1) 0 else currentIndex + 1
                                }
                                dragAccumulator = 0f
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                dragAccumulator += dragAmount
                            }
                        )
                    }
                }
                .testTag("media_preview_modal"),
            contentAlignment = Alignment.Center
        ) {
            // Header Bar: Counter & Close Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Counter badge: e.g. "1 / 4"
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "${currentIndex + 1} / ${media.size}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                // Close Button ✕
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .testTag("media_preview_close")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close preview",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Main Display Image (Centered, Fit, with max 90vh)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 72.dp)
                    .clickable(enabled = false) { /* Consume click so tapping image doesn't dismiss */ },
                contentAlignment = Alignment.Center
            ) {
                val currentItem = media[currentIndex]
                val fullPreviewUrl = currentItem.full ?: currentItem.feed ?: currentItem.url
                SeamlessImage(
                    urls = currentItem.versions ?: ImageVersions.fromUrl(fullPreviewUrl),
                    contentDescription = currentItem.alt ?: "Preview image ${currentIndex + 1}",
                    contentScale = ContentScale.Fit,
                    priority = ImagePriority.HIGH,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.9f)
                        .clip(RoundedCornerShape(8.dp))
                )
            }

            // Navigation Arrows (if media.size > 1)
            if (media.size > 1) {
                // Left Arrow ←
                IconButton(
                    onClick = {
                        currentIndex = if (currentIndex == 0) media.size - 1 else currentIndex - 1
                    },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .testTag("media_preview_prev")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous image",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Right Arrow →
                IconButton(
                    onClick = {
                        currentIndex = if (currentIndex == media.size - 1) 0 else currentIndex + 1
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .testTag("media_preview_next")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next image",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
