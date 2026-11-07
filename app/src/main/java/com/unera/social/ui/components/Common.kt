package com.unera.social.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage

/**
 * Standard spinner matching web Spinner from Common.tsx:
 * <div className="flex justify-center p-8">
 *   <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[#1877F2]"></div>
 * </div>
 */
@Composable
fun Spinner(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    color: Color = Color(0xFF1877F2),
    strokeWidth: Dp = 3.dp,
    padding: Dp = 32.dp
) {
    Box(
        modifier = modifier
            .padding(padding)
            .testTag("common_spinner"),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(size),
            color = color,
            strokeWidth = strokeWidth,
            trackColor = Color(0xFF1E293B).copy(alpha = 0.3f)
        )
    }
}

/**
 * ProfessionalLoader matching web ProfessionalLoader from Common.tsx:
 * Full-screen loading screen with rotating globe, pulsing glow, UNERA gradient logo,
 * oscillating sailing track highlight, and 3 bouncing dots.
 */
@Composable
fun ProfessionalLoader(
    modifier: Modifier = Modifier,
    tagline: String = "Connecting your world",
    brandName: String = "UNERA"
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ProfessionalLoaderTransition")

    // 1. Globe continuous rotation (8s linear)
    val globeRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "GlobeRotation"
    )

    // 2. Background depth pulse
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowPulse"
    )

    // 3. Sailor glider glide animation (translates from 0% to 100% and back)
    val sailProgress by infiniteTransition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SailProgress"
    )

    // 4. Progress fill pulse
    val fillPulse by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FillPulse"
    )

    // 5. Tagline pulse
    val taglineAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "TaglinePulse"
    )

    // 6. Three bouncing dots with staggered offsets
    val dotBounce1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DotBounce1"
    )

    val dotBounce2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, delayMillis = 150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DotBounce2"
    )

    val dotBounce3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, delayMillis = 300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DotBounce3"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF050B18))
            .testTag("professional_loader"),
        contentAlignment = Alignment.Center
    ) {
        // Background depth element: 500px glowing radial bloom
        Box(
            modifier = Modifier
                .size(340.dp)
                .graphicsLayer {
                    scaleX = glowPulse
                    scaleY = glowPulse
                    alpha = 0.4f
                }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1877F2).copy(alpha = 0.22f),
                            Color(0xFF1877F2).copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Center Content Column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            // Logo Section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                // Rotating Globe with glowing halo
                Box(
                    modifier = Modifier.size(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Pulsing glow halo
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .graphicsLayer {
                                scaleX = glowPulse
                                scaleY = glowPulse
                            }
                            .clip(CircleShape)
                            .background(Color(0xFF1877F2).copy(alpha = 0.2f))
                    )

                    // Spinning Globe
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = "UNERA Globe",
                        tint = Color(0xFF1877F2),
                        modifier = Modifier
                            .size(50.dp)
                            .rotate(globeRotation)
                    )
                }

                // UNERA Gradient Text
                Text(
                    text = brandName,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1.5).sp,
                    style = LocalTextStyle.current.copy(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFF1877F2), Color(0xFF1D8AF2))
                        )
                    )
                )
            }

            // Sailing Track (w-64 = 256dp, h-1 = 4dp)
            val trackWidth = 256.dp
            val gliderWidth = 96.dp

            Box(
                modifier = Modifier
                    .width(trackWidth)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF0F172A))
            ) {
                // Background progress fill with pulse
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1877F2).copy(alpha = fillPulse))
                )

                // The "Sailor" highlight glider moving back and forth
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val maxWidthPx = maxWidth
                    val offsetDp = (maxWidthPx - gliderWidth) * sailProgress

                    Box(
                        modifier = Modifier
                            .offset(x = offsetDp)
                            .width(gliderWidth)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color(0xFF1877F2),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Subtitle / Tagline
            Text(
                text = tagline.uppercase(),
                color = Color(0xFF94A3B8).copy(alpha = taglineAlpha),
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 3 Bouncing Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .offset(y = dotBounce1.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1877F2))
                )
                Box(
                    modifier = Modifier
                        .offset(y = dotBounce2.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1877F2))
                )
                Box(
                    modifier = Modifier
                        .offset(y = dotBounce3.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1877F2))
                )
            }
        }
    }
}

/**
 * ImageViewer matching web ImageViewer from Common.tsx:
 * Full-screen modal overlay with black 95% backdrop, top-right close button,
 * and pinch-to-zoom / double-tap zoom capabilities.
 */
@Composable
fun ImageViewer(
    imageUrl: String?,
    onClose: () -> Unit
) {
    if (imageUrl.isNullOrBlank()) return

    // Intercept hardware / gesture back button
    BackHandler(onBack = onClose)

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .testTag("full-screen-image-viewer")
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            // Tap outside / backdrop closes the preview
                            if (scale <= 1.05f) {
                                onClose()
                            } else {
                                // Reset zoom if zoomed in
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            }
                        },
                        onDoubleTap = {
                            if (scale > 1.2f) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                scale = 2.5f
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Close Button (Fixed Top-Right)
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B).copy(alpha = 0.85f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                    .testTag("close-full-image-btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close image preview",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Image Container with gesture transform (pinch zoom and pan)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4f)
                            if (scale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = imageUrl,
                    contentDescription = "Full screen preview",
                    contentScale = ContentScale.Fit,
                    loading = {
                        Spinner(size = 40.dp, strokeWidth = 3.5.dp)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            // Prevent clicking on image from triggering backdrop close
                        }
                )
            }
        }
    }
}

private val AVATAR_GRADIENTS = listOf(
    listOf(Color(0xFF2563EB), Color(0xFF1D4ED8)), // Royal Blue
    listOf(Color(0xFF7C3AED), Color(0xFF6D28D9)), // Violet
    listOf(Color(0xFF059669), Color(0xFF047857)), // Emerald
    listOf(Color(0xFFEA580C), Color(0xFFC2410C)), // Orange
    listOf(Color(0xFFDB2777), Color(0xFFBE185D)), // Pink
    listOf(Color(0xFF0284C7), Color(0xFF0369A1))  // Sky Blue
)

/**
 * Calculates user initials (e.g., "Jastin Beda" -> "JB", "John" -> "JO", etc.)
 */
fun getUserInitials(name: String, username: String = ""): String {
    val clean = name.trim().ifBlank { username.trim().removePrefix("@") }
    if (clean.isBlank()) return "U"
    val parts = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> "${parts[0].firstOrNull()?.uppercaseChar() ?: 'U'}${parts[1].firstOrNull()?.uppercaseChar() ?: ""}"
        parts.isNotEmpty() && parts[0].length >= 2 -> parts[0].take(2).uppercase()
        parts.isNotEmpty() -> parts[0].take(1).uppercase()
        else -> "U"
    }
}

/**
 * FeatheredAvatar
 *
 * Displays user's profile image if present. If absent, empty, or fails to load,
 * gracefully renders the user's initials (e.g. "JB" for "Jastin Beda") centered
 * on a colorful modern gradient circle.
 */
@Composable
fun FeatheredAvatar(
    name: String,
    imageUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    username: String = "",
    fontSize: TextUnit = (size.value * 0.38f).sp,
    borderColor: Color = Color.Transparent,
    borderWidth: Dp = 0.dp,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
    onClick: (() -> Unit)? = null
) {
    val hasValidImage = !imageUrl.isNullOrBlank() &&
        imageUrl != "null" &&
        imageUrl != "undefined"
    val initials = remember(name, username) { getUserInitials(name, username) }
    val gradientColors = remember(name, username) {
        val cleanKey = name.ifBlank { username }
        val hash = (cleanKey.hashCode().toLong() and 0x7FFFFFFF).toInt()
        AVATAR_GRADIENTS[hash % AVATAR_GRADIENTS.size]
    }

    val clickableMod = if (onClick != null) modifier.clickable(onClick = onClick) else modifier

    Box(
        modifier = clickableMod
            .size(size)
            .clip(shape)
            .then(if (borderWidth > 0.dp) Modifier.border(borderWidth, borderColor, shape) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (hasValidImage) {
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.linearGradient(gradientColors)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            color = Color.White,
                            fontSize = fontSize,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(gradientColors)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    color = Color.White,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * PushButton - Small pill button matching web Feeds.tsx:
 * className="px-3 py-1 rounded-md text-sm font-semibold ml-2 bg-blue-100 text-blue-600 hover:bg-blue-200 transition-colors"
 *
 * Requirements:
 * - Label: Push
 * - No icon
 * - Appears only on logged-in owner of the post (or admin)
 * - Light blue background: #DBEAFE (bg-blue-100)
 * - Rich blue text: #2563EB (text-blue-600)
 * - 6dp rounded corners (rounded-md)
 * - text-sm (14sp) font-semibold
 * - px-3 py-1 (horizontal 12dp, vertical 4dp)
 */
@Composable
fun PushButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "push_btn"
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFFDBEAFE),
        modifier = modifier.testTag(testTag)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Push",
                color = Color(0xFF2563EB),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

