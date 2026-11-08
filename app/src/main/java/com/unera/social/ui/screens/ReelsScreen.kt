package com.unera.social.ui.screens

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.unera.social.R
import com.unera.social.data.VideosApiService
import com.unera.social.model.*
import com.unera.social.ui.components.FacebookReactionDock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class VideoItem(
    val id: String,
    val author: User,
    val videoUrl: String,
    val thumbnailUrl: String,
    val caption: String,
    val musicTitle: String,
    val likesCount: Int,
    val commentsCount: Int,
    val sharesCount: Int,
    val isLiked: Boolean,
    val isSaved: Boolean = false,
    val isSponsored: Boolean = false,
    val timestamp: String = "Recent",
    val originalPost: Post? = null,
)

enum class VideoStreamQuality {
    AUTO,
    HD_1080P,
    SD_720P
}

private val FbBlue = Color(0xFF1877F2)
private val FbRedBadge = Color(0xFFE41E3F)
private val HeartRed = Color(0xFFF43F5E)

private fun formatNumber(count: Int): String {
    if (count <= 0) return "0"
    if (count >= 1_000_000) {
        val f = "%.1f".format(java.util.Locale.US, count / 1_000_000.0)
        return if (f.endsWith(".0")) "${f.substringBefore(".0")}M" else "${f}M"
    }
    if (count >= 1_000) {
        val f = "%.1f".format(java.util.Locale.US, count / 1_000.0)
        return if (f.endsWith(".0")) "${f.substringBefore(".0")}K" else "${f}K"
    }
    return count.toString()
}

private fun formatVideoTime(millis: Long): String {
    if (millis <= 0L) return "0:00"
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/**
 * Downloads the original untouched video file using Android DownloadManager.
 * Strictly adheres to reserving the original URL for explicit downloads.
 */
private fun downloadOriginalVideo(context: Context, url: String, title: String) {
    if (url.isBlank()) {
        Toast.makeText(context, "Download link unavailable", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(title.ifBlank { "Original Video" })
            .setDescription("Downloading original high-resolution video…")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "video_${System.currentTimeMillis()}.mp4")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        Toast.makeText(context, "Downloading original video…", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(browserIntent)
        } catch (_: Exception) {
            Toast.makeText(context, "Could not start download", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Professional Full-Screen Facebook Watch & Reels Video Experience
 */
@Composable
fun ReelsScreen(
    posts: List<Post> = emptyList(),
    initialPostId: Long? = null,
    ads: List<SponsoredFeedAd> = emptyList(),
    reels: List<Reel> = emptyList(),
    currentUser: User? = null,
    users: List<User> = emptyList(),
    stories: List<Story> = emptyList(),
    isLoading: Boolean = false,
    onReact: ((Post, String) -> Unit)? = null,
    onOpenComments: ((Post) -> Unit)? = null,
    onShare: ((Long, Int) -> Unit)? = null,
    onFollow: ((Long) -> Unit)? = null,
    onProfileClick: ((Long) -> Unit)? = null,
    onStoryClick: ((Long) -> Unit)? = null,
    onLikeReel: ((Long) -> Unit)? = null,
    onSavePost: ((Long) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    onNavigate: ((Screen) -> Unit)? = null,
    onOpenNotifications: (() -> Unit)? = null,
    onOpenMenu: (() -> Unit)? = null,
    unreadNotificationsCount: Int = 2,
    unreadMessagesCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    // 1. Fetch live sponsored feed ads if empty
    var internalAds by remember { mutableStateOf<List<SponsoredFeedAd>>(ads) }
    LaunchedEffect(ads) {
        if (ads.isNotEmpty()) {
            internalAds = ads
            return@LaunchedEffect
        }
        coroutineScope.launch {
            val fetched = VideosApiService.fetchFeedAds()
            if (fetched.isNotEmpty()) internalAds = fetched
        }
    }

    // 2. Fetch live videos from backend if posts list is empty
    var internalPosts by remember { mutableStateOf<List<Post>>(posts) }
    LaunchedEffect(posts) {
        if (posts.isNotEmpty()) {
            internalPosts = posts
            return@LaunchedEffect
        }
        coroutineScope.launch {
            val fetched = VideosApiService.fetchVideos(userId = currentUser?.id, limit = 50)
            if (fetched.isNotEmpty()) internalPosts = fetched
        }
    }

    // 3. Extract and unify real video items
    val allVideos = remember(internalPosts, reels) {
        val videoMap = LinkedHashMap<String, Post>()

        internalPosts.forEach { post ->
            val defaultUrl = post.getDefaultPlayableVideoUrl().takeIf { it.isNotBlank() }
            val vUrl = post.videoUrl?.takeIf { it.isNotBlank() }
                ?: post.mediaUrl?.takeIf { isVideoUrl(it) }
                ?: defaultUrl?.takeIf { isVideoUrl(it) }

            val isVideo = post.mediaType == "video" ||
                    post.postType == "video" ||
                    post.mediaTypes.contains("video") ||
                    post.mediaMeta.any { it.type == "video" || it.feed != null || it.resolution720 != null } ||
                    vUrl != null

            if (isVideo && (vUrl != null || defaultUrl != null || (post.mediaUrl != null && isVideoUrl(post.mediaUrl)))) {
                val resolvedUrl = vUrl ?: defaultUrl ?: post.mediaUrl ?: ""
                val key = if (resolvedUrl.isNotBlank()) resolvedUrl else "post_${post.id}"
                if (!videoMap.containsKey(key)) {
                    val finalMeta = if (post.mediaMeta.isNotEmpty()) {
                        post.mediaMeta
                    } else {
                        listOf(
                            MediaMetaItem(
                                feed = resolvedUrl,
                                full = post.getHdPlayableVideoUrl().ifBlank { resolvedUrl },
                                resolution720 = resolvedUrl,
                                resolution1080 = post.getHdPlayableVideoUrl().ifBlank { resolvedUrl },
                                thumb = post.getPosterUrl(),
                                thumbnailUrl = post.getPosterUrl(),
                                original = post.getOriginalDownloadUrl().ifBlank { resolvedUrl },
                                type = "video"
                            )
                        )
                    }
                    videoMap[key] = post.copy(
                        postType = "video",
                        mediaType = "video",
                        videoUrl = resolvedUrl,
                        mediaUrl = resolvedUrl,
                        mediaMeta = finalMeta,
                        mediaUrls = if (post.mediaUrls.isNotEmpty()) post.mediaUrls else listOf(resolvedUrl),
                        createdAt = post.createdAt ?: post.timestamp
                    )
                }
            }
        }

        reels.forEach { reel ->
            val vUrl = reel.videoUrl.takeIf { it.isNotBlank() }
            if (vUrl != null) {
                if (!videoMap.containsKey(vUrl)) {
                    videoMap[vUrl] = Post(
                        id = reel.id,
                        author = reel.author,
                        content = reel.caption,
                        mediaUrl = vUrl,
                        videoUrl = vUrl,
                        mediaType = "video",
                        postType = "video",
                        timestamp = "Recent",
                        likesCount = reel.likesCount,
                        commentsCount = reel.commentsCount,
                        sharesCount = reel.sharesCount,
                        isLiked = reel.isLiked,
                        createdAt = "Recent",
                        userId = reel.author.id
                    )
                }
            }
        }

        videoMap.values.toList()
    }

    // 4. Mix organic videos and sponsored ads
    val mixedVideos = remember(allVideos, internalAds, internalPosts, users) {
        if (internalAds.isEmpty()) {
            allVideos
        } else {
            val result = mutableListOf<Post>()
            var adIndex = 0
            val minSpacing = 4

            allVideos.forEachIndexed { index, videoPost ->
                result.add(videoPost)
                if ((index + 1) % minSpacing == 0 && adIndex < internalAds.size) {
                    val ad = internalAds[adIndex % internalAds.size]
                    adIndex++
                    result.add(transformAdToPost(ad, internalPosts, users))
                }
            }

            if (allVideos.isEmpty()) {
                internalAds.forEach { ad ->
                    result.add(transformAdToPost(ad, internalPosts, users))
                }
            }

            result
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("facebook_video_page")
    ) {
        if (isLoading && mixedVideos.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = FbBlue, modifier = Modifier.size(40.dp))
                    Text(
                        text = "Loading Facebook Videos…",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        } else if (mixedVideos.isNotEmpty()) {
            val initialIndex = remember(mixedVideos, initialPostId) {
                if (initialPostId != null) {
                    val idx = mixedVideos.indexOfFirst { it.id == initialPostId }
                    if (idx >= 0) idx else 0
                } else 0
            }

            val pagerState = rememberPagerState(
                initialPage = initialIndex,
                pageCount = { mixedVideos.size }
            )

            LaunchedEffect(initialIndex, mixedVideos.size) {
                if (initialIndex in 0 until mixedVideos.size && pagerState.currentPage != initialIndex) {
                    pagerState.scrollToPage(initialIndex)
                }
            }

            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1
            ) { pageIndex ->
                val videoPost = mixedVideos[pageIndex]
                val isActive = pagerState.currentPage == pageIndex

                FacebookVideoItemView(
                    post = videoPost,
                    isActive = isActive,
                    currentUser = currentUser,
                    onReact = { p, type -> onReact?.invoke(p, type) },
                    onOpenComments = { p -> onOpenComments?.invoke(p) },
                    onShare = { postId, count -> onShare?.invoke(postId, count) },
                    onFollow = { authorId -> onFollow?.invoke(authorId) },
                    onProfileClick = { authorId -> onProfileClick?.invoke(authorId) },
                    onSavePost = { postId -> onSavePost?.invoke(postId) },
                    onBack = onBack,
                    onNavigate = onNavigate,
                    onOpenNotifications = onOpenNotifications,
                    onOpenMenu = onOpenMenu,
                    unreadNotificationsCount = unreadNotificationsCount,
                    unreadMessagesCount = unreadMessagesCount
                )
            }
        } else {
            // Empty State
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.OndemandVideo,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = "No Videos Available",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Videos from your feed and followed creators will appear here.",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    if (onRefresh != null) {
                        Button(
                            onClick = onRefresh,
                            colors = ButtonDefaults.buttonColors(containerColor = FbBlue)
                        ) {
                            Text("Refresh", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Single Facebook-Style Video Player Item View
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FacebookVideoItemView(
    post: Post,
    isActive: Boolean,
    currentUser: User?,
    onReact: (Post, String) -> Unit,
    onOpenComments: (Post) -> Unit,
    onShare: (Long, Int) -> Unit,
    onFollow: (Long) -> Unit,
    onProfileClick: (Long) -> Unit,
    onSavePost: (Long) -> Unit,
    onBack: (() -> Unit)?,
    onNavigate: ((Screen) -> Unit)?,
    onOpenNotifications: (() -> Unit)?,
    onOpenMenu: (() -> Unit)?,
    unreadNotificationsCount: Int,
    unreadMessagesCount: Int
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    // 1. Resolve Quality URLs
    val posterUrl = remember(post) {
        val calc = post.getPosterUrl()
        if (calc.isNotBlank()) calc else post.mediaUrl ?: ""
    }
    val defaultVideoUrl = remember(post) { post.getDefaultPlayableVideoUrl() }
    val hdVideoUrl = remember(post) { post.getHdPlayableVideoUrl() }
    val originalDownloadUrl = remember(post) { post.getOriginalDownloadUrl() }
    val hasHdQuality = remember(post) { post.hasHdQuality() }

    // Adaptive Network & Quality State (YouTube / Facebook style adaptive streaming)
    val connectivityManager = remember(context) {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }

    var isNetworkGoodFor1080p by remember {
        mutableStateOf(run {
            val cm = connectivityManager
            val activeNet = cm?.activeNetwork
            val caps = cm?.getNetworkCapabilities(activeNet)
            if (caps != null) {
                val isWifiOrEthernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                val isFastCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                        (caps.linkDownstreamBandwidthKbps <= 0 || caps.linkDownstreamBandwidthKbps >= 3000)
                isWifiOrEthernet || isFastCellular
            } else true
        })
    }

    DisposableEffect(connectivityManager) {
        val cm = connectivityManager ?: return@DisposableEffect onDispose {}
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                val isWifiOrEthernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                val isFastCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                        (capabilities.linkDownstreamBandwidthKbps <= 0 || capabilities.linkDownstreamBandwidthKbps >= 3000)
                isNetworkGoodFor1080p = isWifiOrEthernet || isFastCellular
            }

            override fun onLost(network: Network) {
                isNetworkGoodFor1080p = false
            }
        }
        try {
            cm.registerDefaultNetworkCallback(callback)
        } catch (_: Exception) {}
        onDispose {
            try {
                cm.unregisterNetworkCallback(callback)
            } catch (_: Exception) {}
        }
    }

    var qualityMode by remember { mutableStateOf(VideoStreamQuality.AUTO) }
    var bufferStallFallbackTo720 by remember { mutableStateOf(false) }

    // When connection recovers, immediately step up to 1080p
    LaunchedEffect(isNetworkGoodFor1080p) {
        if (isNetworkGoodFor1080p) {
            bufferStallFallbackTo720 = false
        }
    }

    val is1080pActive = remember(qualityMode, hasHdQuality, isNetworkGoodFor1080p, bufferStallFallbackTo720, hdVideoUrl) {
        hasHdQuality && hdVideoUrl.isNotBlank() && when (qualityMode) {
            VideoStreamQuality.AUTO -> isNetworkGoodFor1080p && !bufferStallFallbackTo720
            VideoStreamQuality.HD_1080P -> true
            VideoStreamQuality.SD_720P -> false
        }
    }

    val activeStreamingUrl = remember(is1080pActive, defaultVideoUrl, hdVideoUrl) {
        if (is1080pActive && hdVideoUrl.isNotBlank()) hdVideoUrl else defaultVideoUrl
    }

    // 2. Playback & Controller State
    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var isVideoReady by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var showHeartBurst by remember { mutableStateOf(false) }
    var isCaptionExpanded by remember { mutableStateOf(false) }
    var isFollowingState by remember { mutableStateOf(false) }

    // Dynamic reaction state
    var localLiked by remember(post.isLiked, post.myReaction) {
        mutableStateOf(post.isLiked || !post.myReaction.isNullOrBlank())
    }
    var localLikesCount by remember(post.likesCount) { mutableIntStateOf(post.likesCount) }
    var showReactionDock by remember { mutableStateOf(false) }
    var showMoreOptionsSheet by remember { mutableStateOf(false) }

    // Auto-downgrade to 720p if 1080p experiences buffer stalls > 3.5s
    LaunchedEffect(isBuffering, is1080pActive, qualityMode) {
        if (isBuffering && is1080pActive && qualityMode == VideoStreamQuality.AUTO) {
            delay(3500)
            if (isBuffering) {
                bufferStallFallbackTo720 = true
            }
        }
    }

    // ExoPlayer creation & management
    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            volume = if (isMuted) 0f else 1f
        }
    }

    // Manage MediaItem & Seamless Quality Switching (retains exact playback position)
    LaunchedEffect(activeStreamingUrl) {
        if (activeStreamingUrl.isNotBlank()) {
            val currPos = exoPlayer.currentPosition
            val wasPlaying = exoPlayer.playWhenReady
            val currentItemUri = exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
            if (currentItemUri != activeStreamingUrl) {
                val mediaItem = MediaItem.fromUri(activeStreamingUrl)
                exoPlayer.setMediaItem(mediaItem, currPos.coerceAtLeast(0L))
                exoPlayer.prepare()
                exoPlayer.playWhenReady = wasPlaying
            }
        }
    }

    // Attach Player Listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        isVideoReady = true
                        isBuffering = false
                        durationMs = exoPlayer.duration.coerceAtLeast(0L)
                    }
                    Player.STATE_BUFFERING -> {
                        isBuffering = true
                    }
                    Player.STATE_ENDED -> {
                        isBuffering = false
                    }
                    Player.STATE_IDLE -> {
                        isBuffering = false
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("FacebookVideo", "Playback error on $activeStreamingUrl: ${error.message}")
                isBuffering = false
                if (is1080pActive && defaultVideoUrl.isNotBlank() && activeStreamingUrl != defaultVideoUrl) {
                    bufferStallFallbackTo720 = true
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Play/Pause syncing with page visibility & user pause
    LaunchedEffect(isActive, isPlaying) {
        if (isActive && isPlaying) {
            exoPlayer.playWhenReady = true
        } else {
            exoPlayer.playWhenReady = false
        }
    }

    // Volume sync
    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    // Scrubber / Position polling loop while active
    LaunchedEffect(isActive, isPlaying) {
        while (isActive && isPlaying) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            if (exoPlayer.duration > 0) {
                durationMs = exoPlayer.duration
            }
            delay(250)
        }
    }

    // Auto-hide controls after 3.5 seconds
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3500)
            controlsVisible = false
        }
    }

    // Scale animation for reaction heart
    var heartAnimScale by remember { mutableFloatStateOf(1f) }
    val animatedHeartScale by animateFloatAsState(
        targetValue = heartAnimScale,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "heartScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        controlsVisible = !controlsVisible
                    },
                    onDoubleTap = {
                        // Double-tap bursts heart and toggles reaction
                        if (!localLiked) {
                            localLiked = true
                            localLikesCount++
                            onReact(post, "love")
                        }
                        showHeartBurst = true
                        coroutineScope.launch {
                            delay(900)
                            showHeartBurst = false
                        }
                    }
                )
            }
    ) {
        // 1. VIDEO SURFACE (PRESERVES NATURAL ASPECT RATIO, NO DISTORTION)
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. IMMEDIATE THUMBNAIL POSTER (WHILE BUFFERING / INITIAL LOAD)
        if (!isVideoReady && posterUrl.isNotBlank()) {
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }

        // 3. BUFFERING INDICATOR
        if (isBuffering && isActive) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        // 4. DOUBLE TAP BURSTING HEART ANIMATION
        AnimatedVisibility(
            visible = showHeartBurst,
            enter = scaleIn(initialScale = 0.3f) + fadeIn(),
            exit = scaleOut(targetScale = 1.6f) + fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = HeartRed,
                modifier = Modifier
                    .size(96.dp)
                    .shadow(12.dp, CircleShape)
            )
        }

        // 5. TOP-LEFT SLEEK CHEVRON BACK BUTTON ("<")
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)), CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier
                            .size(28.dp)
                            .offset(x = (-1).dp)
                    )
                }
            }
        }

        // 6. CENTER CONTROLS (10s REWIND, PLAY/PAUSE, 10s FAST-FORWARD)
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(250)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(36.dp)
            ) {
                // 10s Rewind
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable {
                            val target = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(target)
                            currentPositionMs = target
                            controlsVisible = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_replay_10),
                        contentDescription = "Rewind 10s",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Play / Pause Circle Button
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .clickable {
                            isPlaying = !isPlaying
                            controlsVisible = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }

                // 10s Fast-Forward
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable {
                            val dur = if (exoPlayer.duration > 0) exoPlayer.duration else Long.MAX_VALUE
                            val target = (exoPlayer.currentPosition + 10000L).coerceAtMost(dur)
                            exoPlayer.seekTo(target)
                            currentPositionMs = target
                            controlsVisible = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_forward_10),
                        contentDescription = "Forward 10s",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // 7. BOTTOM SCRIM & METADATA OVERLAYS
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.45f),
                            Color.Black.copy(alpha = 0.9f)
                        )
                    )
                )
                .padding(bottom = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
            ) {
                // Content Row: Creator/Description on left, Interaction Buttons on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // BOTTOM-LEFT CREATOR & DESCRIPTION
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp, bottom = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Creator Row (Avatar, Name, Verified, Follow Button)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val authorAvatar = post.author.profileImageUrl.ifBlank {
                                "https://ui-avatars.com/api/?name=${post.author.name}&background=1877F2&color=fff&bold=true"
                            }

                            AsyncImage(
                                model = authorAvatar,
                                contentDescription = post.author.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, Color.White, CircleShape)
                                    .clickable { onProfileClick(post.author.id) }
                            )

                            Text(
                                text = post.author.name,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = "Public",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )

                            // Follow Pill Button
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isFollowingState) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .height(30.dp)
                                    .clickable {
                                        isFollowingState = !isFollowingState
                                        onFollow(post.author.id)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isFollowingState) "Following" else "Follow",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Caption / Description
                        val contentText = post.content.ifBlank { post.sponsoredHeadline }
                        if (contentText.isNotBlank()) {
                            val maxLines = if (isCaptionExpanded) Int.MAX_VALUE else 2
                            Column {
                                Text(
                                    text = contentText,
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Normal,
                                    maxLines = maxLines,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { isCaptionExpanded = !isCaptionExpanded }
                                )
                                if (contentText.length > 60 && !isCaptionExpanded) {
                                    Text(
                                        text = "… more",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        modifier = Modifier.clickable { isCaptionExpanded = true }
                                    )
                                }
                            }
                        }

                        // Sponsored Call-to-Action Bar
                        if (post.isSponsored || post.postType == "sponsored") {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = FbBlue,
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .clickable {
                                        if (post.sponsoredCtaUrl.isNotBlank()) {
                                            try {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(post.sponsoredCtaUrl)))
                                            } catch (_: Exception) {}
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = post.sponsoredCtaText.ifBlank { "Learn More" },
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // RIGHT-SIDE VERTICAL INTERACTION COLUMN (Compact Spacing, Number on Right of Icon)
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        // 1. REACT HEART BUTTON (❤️ 1k)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black.copy(alpha = 0.35f))
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            coroutineScope.launch {
                                                heartAnimScale = 1.35f
                                                delay(120)
                                                heartAnimScale = 1.0f
                                            }
                                            localLiked = !localLiked
                                            localLikesCount = if (localLiked) localLikesCount + 1 else (localLikesCount - 1).coerceAtLeast(0)
                                            onReact(post, if (localLiked) "love" else "")
                                        },
                                        onLongPress = {
                                            showReactionDock = true
                                        }
                                    )
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (localLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "React Heart",
                                tint = if (localLiked) HeartRed else Color.White,
                                modifier = Modifier
                                    .size(24.dp)
                                    .scale(animatedHeartScale)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = formatNumber(localLikesCount),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // 2. COMMENT / DISCUSS BUTTON (💬 168)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black.copy(alpha = 0.35f))
                                .clickable { onOpenComments(post) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_far_comment),
                                contentDescription = "Comments",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = formatNumber(post.commentsCount),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // 3. SHARE BUTTON (Facing Northeast: ↗ 96)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black.copy(alpha = 0.35f))
                                .clickable { onShare(post.id, post.sharesCount) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Share",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(22.dp)
                                    .graphicsLayer(rotationZ = -35f)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = formatNumber(post.sharesCount),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // BOTTOM SCRUBBER & DURATION ROW
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Time Indicator (e.g. 0:02 / 2:13)
                    Text(
                        text = "${formatVideoTime(currentPositionMs)} / ${formatVideoTime(durationMs)}",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    // Smooth Video Scrubber
                    val progressRatio = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                    Slider(
                        value = progressRatio,
                        onValueChange = { newRatio ->
                            val target = (newRatio * durationMs).toLong()
                            exoPlayer.seekTo(target)
                            currentPositionMs = target
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                    )

                    // Adaptive HD Quality Indicator Badge (If 1080p exists)
                    if (hasHdQuality) {
                        val badgeLabel = when (qualityMode) {
                            VideoStreamQuality.AUTO -> if (is1080pActive) "HD • Auto" else "Auto (720p)"
                            VideoStreamQuality.HD_1080P -> "1080p HD"
                            VideoStreamQuality.SD_720P -> "720p"
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (is1080pActive) FbBlue else Color.White.copy(alpha = 0.22f),
                            modifier = Modifier
                                .clickable {
                                    qualityMode = when (qualityMode) {
                                        VideoStreamQuality.AUTO -> VideoStreamQuality.HD_1080P
                                        VideoStreamQuality.HD_1080P -> VideoStreamQuality.SD_720P
                                        VideoStreamQuality.SD_720P -> VideoStreamQuality.AUTO
                                    }
                                    bufferStallFallbackTo720 = false
                                    val msg = when (qualityMode) {
                                        VideoStreamQuality.AUTO -> "Quality: Auto (Adaptive HD)"
                                        VideoStreamQuality.HD_1080P -> "Quality: 1080p HD"
                                        VideoStreamQuality.SD_720P -> "Quality: 720p Standard"
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 2.dp)
                        ) {
                            Text(
                                text = badgeLabel,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Volume Mute / Unmute Toggle
                    IconButton(
                        onClick = { isMuted = !isMuted },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = if (isMuted) "Unmute" else "Mute",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // More Options (`...`)
                    IconButton(
                        onClick = { showMoreOptionsSheet = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = "More Options",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // 8. 3D REACTION DOCK POPUP (ON LONG PRESS)
        if (showReactionDock) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { showReactionDock = false },
                contentAlignment = Alignment.Center
            ) {
                FacebookReactionDock(
                    onSelectReaction = { selectedType ->
                        showReactionDock = false
                        localLiked = true
                        localLikesCount++
                        onReact(post, selectedType)
                    },
                    onDismiss = { showReactionDock = false }
                )
            }
        }

        // 9. MORE OPTIONS BOTTOM SHEET MODAL
        if (showMoreOptionsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showMoreOptionsSheet = false },
                containerColor = Color(0xFF1E293B),
                contentColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Video Options",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Download Original Video (Strict rule: Original URL exclusively reserved for download)
                    ListItem(
                        headlineContent = { Text("Download Original Video", color = Color.White, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Save pristine untouched file to device", color = Color.White.copy(alpha = 0.6f)) },
                        leadingContent = {
                            Icon(Icons.Default.Download, contentDescription = null, tint = FbBlue)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            showMoreOptionsSheet = false
                            val targetDownload = originalDownloadUrl.ifBlank { activeStreamingUrl }
                            downloadOriginalVideo(context, targetDownload, post.content.take(30))
                        }
                    )

                    // Video Quality Selector (Auto / 1080p HD / 720p Standard)
                    ListItem(
                        headlineContent = {
                            val activeLabel = when (qualityMode) {
                                VideoStreamQuality.AUTO -> "Quality: Auto (${if (is1080pActive) "1080p HD" else "720p"})"
                                VideoStreamQuality.HD_1080P -> "Quality: 1080p HD (Locked)"
                                VideoStreamQuality.SD_720P -> "Quality: 720p Standard (Locked)"
                            }
                            Text(
                                text = activeLabel,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        supportingContent = {
                            val supportText = when {
                                !hasHdQuality -> "Standard 720p stream available"
                                qualityMode == VideoStreamQuality.AUTO -> "Auto-adapts to your connection: 1080p on fast Wi-Fi/data, 720p on slower networks (YouTube style)"
                                qualityMode == VideoStreamQuality.HD_1080P -> "Streaming high definition 1080p. Tap to cycle quality."
                                else -> "Data saver 720p streaming. Tap to cycle quality."
                            }
                            Text(
                                text = supportText,
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 12.sp
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.HighQuality,
                                contentDescription = null,
                                tint = if (is1080pActive) FbBlue else Color.White
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(enabled = hasHdQuality) {
                            qualityMode = when (qualityMode) {
                                VideoStreamQuality.AUTO -> VideoStreamQuality.HD_1080P
                                VideoStreamQuality.HD_1080P -> VideoStreamQuality.SD_720P
                                VideoStreamQuality.SD_720P -> VideoStreamQuality.AUTO
                            }
                            bufferStallFallbackTo720 = false
                            showMoreOptionsSheet = false
                            val msg = when (qualityMode) {
                                VideoStreamQuality.AUTO -> "Switched to Auto (Adaptive HD)"
                                VideoStreamQuality.HD_1080P -> "Switched to 1080p HD"
                                VideoStreamQuality.SD_720P -> "Switched to 720p Standard"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    )

                    // Save / Bookmark Video
                    ListItem(
                        headlineContent = { Text("Save Video", color = Color.White) },
                        leadingContent = {
                            Icon(Icons.Default.BookmarkBorder, contentDescription = null, tint = Color.White)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            showMoreOptionsSheet = false
                            onSavePost(post.id)
                            Toast.makeText(context, "Saved to your bookmarks", Toast.LENGTH_SHORT).show()
                        }
                    )

                    // Copy Video Link
                    ListItem(
                        headlineContent = { Text("Copy Link", color = Color.White) },
                        leadingContent = {
                            Icon(Icons.Default.Link, contentDescription = null, tint = Color.White)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            showMoreOptionsSheet = false
                            val link = post.videoUrl ?: post.mediaUrl ?: "https://unera.social/posts/${post.id}"
                            clipboardManager.setText(AnnotatedString(link))
                            Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}


private fun transformAdToPost(ad: SponsoredFeedAd, posts: List<Post>, users: List<User>): Post {
    val originalPost = posts.find { it.id == ad.id }
    val matchedUser = users.find { it.id == (ad.brandId ?: ad.id) }
    val authorName = originalPost?.author?.name ?: matchedUser?.name ?: ad.sponsorName.ifBlank { "Sponsored" }
    val authorAvatar = originalPost?.author?.profileImageUrl ?: matchedUser?.profileImageUrl ?: ad.sponsorAvatar
    val authorId = originalPost?.author?.id ?: matchedUser?.id ?: -(ad.id.coerceAtLeast(1L))

    val authorObj = User(
        id = authorId,
        name = authorName,
        username = originalPost?.author?.username ?: matchedUser?.username ?: "@${authorName.lowercase().replace(" ", "")}",
        profileImageUrl = authorAvatar.ifBlank { "https://ui-avatars.com/api/?name=${authorName}&background=1877F2&color=fff" },
        isVerified = originalPost?.author?.isVerified ?: matchedUser?.isVerified ?: true
    )

    val mediaUrl = ad.videoUrl ?: ad.imageUrl ?: originalPost?.mediaUrl ?: ""
    val isVideo = ad.isVideo || (ad.videoUrl != null) || isVideoUrl(mediaUrl)

    return Post(
        id = -(ad.id.coerceAtLeast(1L)),
        author = authorObj,
        content = originalPost?.content ?: ad.headline.ifBlank { ad.description },
        mediaUrl = mediaUrl,
        mediaUrls = if (mediaUrl.isNotBlank()) listOf(mediaUrl) else emptyList(),
        videoUrl = if (isVideo) mediaUrl else null,
        postType = if (isVideo) "video" else "sponsored",
        mediaType = if (isVideo) "video" else "image",
        timestamp = "Sponsored",
        isSponsored = true,
        sponsoredHeadline = ad.headline.ifBlank { ad.title },
        sponsoredCtaText = ad.ctaText.ifBlank { "Learn More" },
        sponsoredCtaUrl = ad.ctaUrl,
        likesCount = originalPost?.likesCount ?: 0,
        commentsCount = originalPost?.commentsCount ?: 0,
        sharesCount = originalPost?.sharesCount ?: 0,
        createdAt = originalPost?.createdAt ?: "Sponsored",
        userId = authorId
    )
}

private fun isVideoUrl(url: String): Boolean {
    if (url.isBlank()) return false
    val clean = url.substringBefore("?").substringBefore("#").trim().lowercase()
    val lower = url.lowercase()
    return clean.endsWith(".mp4") || clean.endsWith(".mov") || clean.endsWith(".webm") ||
            clean.endsWith(".m4v") || clean.endsWith(".ogg") || clean.endsWith(".m3u8") ||
            clean.endsWith(".mkv") ||
            lower.contains(".mp4") || lower.contains(".mov") || lower.contains(".webm") ||
            lower.contains(".m4v") || lower.contains(".m3u8") ||
            lower.contains("/videos/") || lower.contains("/video/") ||
            lower.contains("reel") || lower.contains("1080p") || lower.contains("720p") || lower.contains("original")
}
