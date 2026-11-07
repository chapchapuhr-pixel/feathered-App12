package com.unera.social.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.unera.social.R
import kotlinx.coroutines.delay

private const val SPLASH_LOGO_URL = "https://media.featheredsocial.site/file_00000000fcd8820ebece7f0c251fccf1.png"

/**
 * FacebookSplashScreen - 1:1 Facebook/Meta launch screen experience.
 *
 * Features:
 * - Pure dark app background (Color(0xFF020617)) matching Facebook cold launch.
 * - Center icon from https://media.featheredsocial.site/file_00000000fcd8820ebece7f0c251fccf1.png
 *   with bundled local high-res asset (ic_feathered_splash_logo) for instantaneous zero-latency render.
 * - Iconic Facebook entrance: fade-in with subtle scale up, followed by gentle breathing pulse.
 * - Footer:
 *         By
 *     featherTech
 * - Waits for feed data to be ready before seamlessly crossfading into the feed.
 */
@Composable
fun FacebookSplashScreen(
    isLoading: Boolean,
    onFinished: () -> Unit,
    minDurationMs: Long = 1800L,
    maxDurationMs: Long = 3200L,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isReadyToDismiss by remember { mutableStateOf(false) }

    // Initial entrance animation
    val entranceTransition = remember { Animatable(0f) }
    val entranceScale = remember { Animatable(0.86f) }

    LaunchedEffect(Unit) {
        entranceTransition.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
    }

    LaunchedEffect(Unit) {
        entranceScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    // Breathing pulse effect (active connection / loading pulse)
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Ensure minimum display time for smooth aesthetics, then finish when feed is loaded
    LaunchedEffect(isLoading) {
        val startTime = System.currentTimeMillis()
        delay(minDurationMs)
        
        val elapsed = System.currentTimeMillis() - startTime
        val remaining = (maxDurationMs - elapsed).coerceAtLeast(0L)
        
        if (!isLoading) {
            isReadyToDismiss = true
        } else {
            // Wait up to max duration for feed loading
            delay(remaining.coerceAtMost(1400L))
            isReadyToDismiss = true
        }
    }

    LaunchedEffect(isReadyToDismiss) {
        if (isReadyToDismiss) {
            delay(250) // brief hold
            onFinished()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF020617))
            .testTag("facebook_splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        // Soft centered radial aura glow
        Box(
            modifier = Modifier
                .size(240.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1877F2).copy(alpha = 0.12f * entranceTransition.value),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Center Icon
        Box(
            modifier = Modifier
                .size(100.dp)
                .scale(entranceScale.value * pulseScale)
                .alpha(entranceTransition.value * pulseAlpha)
                .testTag("splash_center_logo"),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(SPLASH_LOGO_URL)
                    .crossfade(false)
                    .placeholder(R.drawable.ic_feathered_splash_logo)
                    .error(R.drawable.ic_feathered_splash_logo)
                    .fallback(R.drawable.ic_feathered_splash_logo)
                    .build(),
                contentDescription = "Feathered Logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Footer at bottom: By featherTech
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 36.dp)
                .alpha(entranceTransition.value),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "By",
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF64748B),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "featherTech",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF8FAFC),
                letterSpacing = 0.6.sp
            )
        }
    }
}
