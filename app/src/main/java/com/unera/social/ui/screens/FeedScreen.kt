package com.unera.social.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.unera.social.data.MockData
import com.unera.social.data.PostsApiService
import com.unera.social.data.CommentsCache
import com.unera.social.data.EventsApiService
import com.unera.social.model.*
import com.unera.social.ui.components.*
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Centered circular play button using the exact styling from the Song Card:
 * Same 58dp circular shape, #1877F2 background at 92% opacity, centered white PlayArrow triangle (34dp).
 */
@Composable
fun SongCardPlayButton(
    modifier: Modifier = Modifier,
    contentDescription: String = "Play"
) {
    Box(
        modifier = modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(Color(0xFF1877F2).copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(34.dp)
        )
    }
}

/**
 * Professional Video Thumbnail Card for FeedScreen:
 * Displays video poster thumbnail directly below caption, occupies available width,
 * preserves aspect ratio (portrait & landscape), and overlays the exact centered Song Card play button.
 * Tapping opens VideoReel.
 */
@Composable
fun FeedVideoPostThumbnail(
    post: Post,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val rawThumbnail = remember(post) {
        post.getPosterUrl().ifBlank {
            post.mediaMeta.firstOrNull()?.thumbnailUrl
                ?: post.mediaMeta.firstOrNull()?.thumb
                ?: post.mediaUrl
                ?: post.videoUrl
                ?: ""
        }
    }

    val meta = post.mediaMeta.firstOrNull()
    val metaRatio: Float? = remember(meta?.width, meta?.height) {
        if (meta?.width != null && meta?.height != null && meta.width > 0 && meta.height > 0) {
            (meta.width.toFloat() / meta.height.toFloat()).coerceIn(0.56f, 2.2f)
        } else null
    }

    var dynamicRatio by remember(post.id) { mutableStateOf(metaRatio) }
    val effectiveRatio = dynamicRatio ?: metaRatio

    val imageRequest = remember(rawThumbnail, post.id) {
        val model = rawThumbnail.ifBlank { post.getDefaultPlayableVideoUrl() }
        ImageRequest.Builder(context)
            .data(model)
            .crossfade(true)
            .decoderFactory(VideoFrameDecoder.Factory())
            .build()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (effectiveRatio != null) {
                    Modifier.aspectRatio(effectiveRatio, matchHeightConstraintsFirst = false)
                } else {
                    Modifier.heightIn(min = 220.dp, max = 520.dp)
                }
            )
            .background(Color(0xFF0A0F1D))
            .clipToBounds()
            .clickable(onClick = onClick)
            .testTag("video_post_thumbnail_${post.id}"),
        contentAlignment = Alignment.Center
    ) {
        val candidate = rawThumbnail.ifBlank { post.getDefaultPlayableVideoUrl() }
        if (candidate.isNotBlank()) {
            AsyncImage(
                model = imageRequest,
                contentDescription = post.content.ifBlank { "Video thumbnail" },
                contentScale = ContentScale.Crop,
                onSuccess = { successState ->
                    val drawable = successState.result.drawable
                    val dw = drawable.intrinsicWidth
                    val dh = drawable.intrinsicHeight
                    if (dw > 0 && dh > 0 && dynamicRatio == null) {
                        dynamicRatio = (dw.toFloat() / dh.toFloat()).coerceIn(0.56f, 2.2f)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        SongCardPlayButton(
            contentDescription = "Play Video",
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

internal fun formatCount(count: Int): String {
    if (count <= 0) return "0"
    if (count >= 1_000_000) {
        val formatted = "%.1f".format(Locale.US, count / 1_000_000.0)
        return if (formatted.endsWith(".0")) "${formatted.substringBefore(".0")}M" else "${formatted}M"
    }
    if (count >= 1_000) {
        val formatted = "%.1f".format(Locale.US, count / 1_000.0)
        return if (formatted.endsWith(".0")) "${formatted.substringBefore(".0")}k" else "${formatted}k"
    }
    return count.toString()
}

@Composable
fun InlinePostVideoPlayer(
    videoUrl: String,
    posterUrl: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }

    val exoPlayer = remember(videoUrl) {
        try {
            if (videoUrl.isNotBlank()) {
                ExoPlayer.Builder(context).build().apply {
                    repeatMode = Player.REPEAT_MODE_ALL
                    val mediaItem = MediaItem.fromUri(videoUrl)
                    setMediaItem(mediaItem)
                    prepare()
                }
            } else null
        } catch (_: Throwable) {
            null
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            try {
                exoPlayer?.release()
            } catch (_: Throwable) {}
        }
    }

    LaunchedEffect(isPlaying) {
        exoPlayer?.playWhenReady = isPlaying
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 240.dp, max = 460.dp)
            .background(Color.Black)
            .clickable { isPlaying = !isPlaying },
        contentAlignment = Alignment.Center
    ) {
        if (!posterUrl.isNullOrBlank()) {
            AsyncImage(
                model = posterUrl,
                contentDescription = "Video Poster",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isPlaying && exoPlayer != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        this.player = exoPlayer
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (!isPlaying) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play Video",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
    }
}


@Composable
fun FeedScreen(
    currentUser: User,
    stories: List<Story>,
    posts: List<Post>,
    ads: List<SponsoredFeedAd> = emptyList(),
    products: List<Product> = emptyList(),
    adCampaigns: List<AdCampaign> = emptyList(),
    onViewProduct: ((Long) -> Unit)? = null,
    isGuest: Boolean = false,
    onRequestAuth: () -> Unit = {},
    onOpenCreatePost: () -> Unit,
    onPhotoClick: () -> Unit = onOpenCreatePost,
    onVideoClick: () -> Unit = onOpenCreatePost,
    onStoryClick: (Story) -> Unit,
    onLikePost: (Long) -> Unit,
    onCommentPost: (Post) -> Unit,
    onAddComment: ((Long, String) -> Unit)? = null,
    onAddReply: ((Long, Long, String) -> Unit)? = null,
    onLikeComment: ((Long, Long) -> Unit)? = null,
    onDeleteComment: ((Long, Long) -> Unit)? = null,
    onToggleHideComment: ((Long, Long, Boolean) -> Unit)? = null,
    onSavePost: (Long) -> Unit,
    onUserClick: (User) -> Unit,
    onAddStoryClick: () -> Unit = onOpenCreatePost,
    allUsers: List<User> = emptyList(),
    peopleYouMayKnow: List<User> = emptyList(),
    onFollowFromPymk: ((Long) -> Unit)? = null,
    onFollowUser: ((Long) -> Unit)? = null,
    onDismissPymk: ((Long) -> Unit)? = null,
    pymkLoading: Boolean = false,
    groups: List<Group> = emptyList(),
    isFeedLoading: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    onReactPost: ((Long, String) -> Unit)? = null,
    onSharePost: ((Long) -> Unit)? = null,
    onOpenProduct: ((Product) -> Unit)? = null,
    onOpenEvent: ((Event) -> Unit)? = null,
    onOpenGroup: ((Long) -> Unit)? = null,
    onJoinGroup: ((Long) -> Unit)? = null,
    onRsvpEvent: ((Long, String, String) -> Unit)? = null,
    onPlayMusic: ((Song) -> Unit)? = null,
    onOpenVideos: ((Post?) -> Unit)? = null,
    onDeletePost: ((Long) -> Unit)? = null,
    onEditPost: ((Long, String) -> Unit)? = null,
    onHidePost: ((Long) -> Unit)? = null,
    onReportPost: ((Long, String) -> Unit)? = null,
    onPushMore: ((Post) -> Unit)? = null,
    pushedPosts: Map<Long, Boolean> = emptyMap(),
    uploadState: PostUploadState? = null,
    onDismissUpload: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var galleryState by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    var activePostForGallery by remember { mutableStateOf<Post?>(null) }
    var reactionsSheetPost by remember { mutableStateOf<Post?>(null) }
    var shareSheetPost by remember { mutableStateOf<Post?>(null) }
    var discussionsSheetPost by remember { mutableStateOf<Post?>(null) }
    var followStates by remember { mutableStateOf(mapOf<Long, Boolean>()) }
    var groupJoinedStates by remember { mutableStateOf(mapOf<Long, Boolean>()) }

    // Professionally filter out groups user has already joined so they never appear in Groups You May Join
    val unjoinedGroups = remember(groups, groupJoinedStates, currentUser.id) {
        groups.filter { group ->
            val isExplicitlyJoined = groupJoinedStates[group.id]
            if (isExplicitlyJoined != null) {
                !isExplicitlyJoined
            } else {
                !group.isMember && (currentUser.id <= 0 || !group.members.contains(currentUser.id))
            }
        }
    }

    val listState = rememberRestoredLazyListState(key = "FEED")

    // Merge real live ads from /api/ads/feeds every 5 posts only when real ads exist. Never fake ads.
    val feedPosts = remember(posts, ads) {
        if (ads.isEmpty()) {
            posts
        } else {
            val result = mutableListOf<Post>()
            var adIndex = 0
            posts.forEachIndexed { i, p ->
                result.add(p)
                if ((i + 1) % 5 == 0 && adIndex < ads.size) {
                    val ad = ads[adIndex]
                    val adPostId = ad.postId ?: if (ad.id > 0) ad.id else (-100000L - ad.id)
                    val adPost = Post(
                        id = adPostId,
                        userId = ad.brandId ?: 0L,
                        author = User(
                            id = ad.brandId ?: 0L,
                            name = ad.sponsorName.ifBlank { "Sponsored Partner" },
                            username = ad.sponsorName.lowercase().replace(" ", ""),
                            profileImageUrl = ad.sponsorAvatar.ifBlank { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400" },
                            isVerified = true
                        ),
                        content = ad.description.trim().let { if (it.equals("null", ignoreCase = true) || it.equals("undefined", ignoreCase = true)) "" else it },
                        timestamp = "Ad",
                        mediaUrl = ad.imageUrl ?: ad.videoUrl,
                        mediaUrls = if (!ad.imageUrl.isNullOrBlank()) listOf(ad.imageUrl) else emptyList(),
                        videoUrl = ad.videoUrl,
                        isSponsored = true,
                        postType = "ad",
                        sponsoredHeadline = ad.headline.ifBlank { ad.title },
                        sponsoredCtaText = ad.ctaText.ifBlank { "Learn More" },
                        sponsoredCtaUrl = ad.ctaUrl,
                        likesCount = 0,
                        commentsCount = 0,
                        sharesCount = 0
                    )
                    result.add(adPost)
                    adIndex++
                }
            }
            result
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF050B18))
            .testTag("feed_screen_list")
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // 1. Stories Section
            item(key = "stories_section") {
                StoriesSection(
                    currentUser = currentUser,
                    stories = stories,
                    onStoryClick = onStoryClick,
                    onAddStoryClick = {
                        if (isGuest) onRequestAuth() else onAddStoryClick()
                    }
                )
            }

            // 2. Create Post Bar (Web Style)
            item(key = "create_post_card") {
                CreatePostSection(
                    currentUser = currentUser,
                    onClick = {
                        if (isGuest) onRequestAuth() else onOpenCreatePost()
                    },
                    onPhotoClick = {
                        if (isGuest) onRequestAuth() else onPhotoClick()
                    },
                    onVideoClick = {
                        if (isGuest) onRequestAuth() else onVideoClick()
                    },
                    onEventClick = {
                        if (isGuest) onRequestAuth() else onOpenCreatePost()
                    }
                )
                // 8dp gap
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(Color(0xFF050B18))
                )
            }

            // 2b. Post Upload Progress Banner (matching Facebook mobile upload interface)
            if (uploadState != null) {
                item(key = "post_upload_progress_banner") {
                    PostUploadProgressBanner(
                        uploadState = uploadState,
                        onDismiss = onDismissUpload
                    )
                }
            }

            // 3. Posts with Interleaved Widgets
            itemsIndexed(feedPosts, key = { index, post -> "post_${post.id}_$index" }) { index, rawPost ->
                val post = remember(rawPost, allUsers) {
                    val uId = if (rawPost.userId > 0L) rawPost.userId else rawPost.author.id
                    val matchedUser = allUsers.find { it.id == uId }
                    if (matchedUser != null) {
                        val realName = matchedUser.name.ifBlank { matchedUser.username.removePrefix("@") }
                        val author = rawPost.author.copy(
                            id = matchedUser.id,
                            name = if (realName.isNotBlank() && !realName.startsWith("User")) realName else rawPost.author.name,
                            username = if (matchedUser.username.isNotBlank()) matchedUser.username else rawPost.author.username,
                            profileImageUrl = matchedUser.profileImageUrl.ifBlank { rawPost.author.profileImageUrl },
                            isVerified = matchedUser.isVerified || rawPost.author.isVerified,
                            role = matchedUser.role.ifBlank { rawPost.author.role }
                        )
                        rawPost.copy(author = author, userId = matchedUser.id)
                    } else {
                        rawPost
                    }
                }
                val isGroupPost = post.groupId != null || post.postType == "group_post"
                val isMarketplace = post.product != null || post.postType == "product" || post.postType == "marketplace"
                val isSharedPost = post.sharedPost != null || post.postType == "shared_post" ||
                        post.postType == "product_share" || post.postType == "event_share" ||
                        post.postType == "song_share" || post.postType == "story_share"

                // Exact rule matching web Feeds.tsx line 2235:
                // Unified normal post card matching Facebook feed design
                WebPostCard(
                    post = post,
                    currentUser = currentUser,
                    isFollowing = post.author.isFollowing,
                    onFollowToggle = {
                        if (isGuest) {
                            onRequestAuth()
                        } else {
                            onFollowUser?.invoke(post.author.id)
                        }
                    },
                    onLike = {
                        if (isGuest) onRequestAuth() else onLikePost(post.id)
                    },
                    onReact = { reaction ->
                        if (isGuest) onRequestAuth()
                        else onReactPost?.invoke(post.id, reaction) ?: onLikePost(post.id)
                    },
                    onComment = {
                        if (isGuest) {
                            onRequestAuth()
                        } else {
                            discussionsSheetPost = post
                        }
                    },
                    onShare = {
                        if (isGuest) onRequestAuth() else shareSheetPost = post
                    },
                    onSave = {
                        if (isGuest) onRequestAuth() else onSavePost(post.id)
                    },
                    onUserClick = { onUserClick(post.author) },
                    onOpenGallery = { imgs, idx ->
                        activePostForGallery = post
                        galleryState = Pair(imgs, idx)
                    },
                    onOpenReactions = {
                        if (isGuest) onRequestAuth() else reactionsSheetPost = post
                    },
                    onOpenProduct = { onOpenProduct?.invoke(it) },
                    onOpenEvent = { onOpenEvent?.invoke(it) },
                    onOpenGroup = { onOpenGroup?.invoke(it) },
                    onPlayMusic = { onPlayMusic?.invoke(it) },
                    onOpenVideo = { postToOpen ->
                        onOpenVideos?.invoke(postToOpen)
                    },
                    onStoryClick = { onStoryClick(it) },
                    onDeletePost = { onDeletePost?.invoke(it) },
                    onEditPost = { id, text -> onEditPost?.invoke(id, text) },
                    onHidePost = { onHidePost?.invoke(it) },
                    onReportPost = { id, reason -> onReportPost?.invoke(id, reason) },
                    onPushMore = onPushMore,
                    pushedPosts = pushedPosts,
                    allUsers = allUsers,
                    onLikeComment = onLikeComment,
                    onRequestAuth = onRequestAuth,
                    onRsvpEvent = onRsvpEvent
                )

                // 8dp gap between posts
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(Color(0xFF050B18))
                )

                // Interleaved Widget 0: MarketPoint for you (Suggested Products)
                if (index == 1 && products.isNotEmpty()) {
                    val marketplaceContext = LocalMarketplaceContext.current
                    SuggestedProductsWidget(
                        products = products,
                        currentUser = currentUser,
                        onViewProduct = { prod ->
                            onOpenProduct?.invoke(prod) ?: marketplaceContext.onViewProduct(prod.id)
                        },
                        onSeeAll = {
                            marketplaceContext.onViewProduct(products.firstOrNull()?.id ?: 0L)
                        }
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(Color(0xFF050B18))
                    )
                }

                // Interleaved Widget 1: People You May Know
                val effectivePymk = if (peopleYouMayKnow.isNotEmpty()) peopleYouMayKnow else allUsers.filter { it.id != currentUser.id }
                val pymkInsertIndex1 = when {
                    posts.size < 6 -> if (posts.isNotEmpty()) (posts.size - 1).coerceAtMost(1) else -1
                    posts.size <= 10 -> posts.size - 1
                    else -> 7
                }
                val pymkInsertIndex2 = if (posts.size >= 22) 21 else -1

                if ((index == pymkInsertIndex1 || index == pymkInsertIndex2) && effectivePymk.isNotEmpty()) {
                    PeopleYouMayKnowSection(
                        currentUser = currentUser,
                        allUsers = effectivePymk,
                        followStates = emptyMap(),
                        onFollowToggle = { uid ->
                            if (isGuest) {
                                onRequestAuth()
                            } else {
                                onFollowFromPymk?.invoke(uid)
                            }
                        },
                        onDismiss = { uid ->
                            onDismissPymk?.invoke(uid)
                        },
                        onUserClick = onUserClick
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(Color(0xFF050B18))
                    )
                }

                // Interleaved Widget 2: Groups You May Join after 4th post (only if unjoined)
                if (index == 3 && unjoinedGroups.isNotEmpty()) {
                    GroupsYouMayJoinSection(
                        groups = unjoinedGroups,
                        joinedStates = groupJoinedStates,
                        onJoinToggle = { gid ->
                            groupJoinedStates = groupJoinedStates + (gid to true)
                            onJoinGroup?.invoke(gid)
                        },
                        onGroupClick = { gid ->
                            onOpenGroup?.invoke(gid)
                        }
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(Color(0xFF050B18))
                    )
                }
            }

            // Empty or loading state for feed
            if (posts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isFeedLoading) {
                            CircularProgressIndicator(
                                color = Color(0xFF1877F2),
                                modifier = Modifier.size(36.dp)
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "No posts available",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                onRefresh?.let { doRefresh ->
                                    Button(
                                        onClick = doRefresh,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Refresh Feed", color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Gallery Viewer Modal
        galleryState?.let { (imgs, idx) ->
            activePostForGallery?.let { currentPost ->
                GalleryViewerDialog(
                    images = imgs,
                    initialIndex = idx,
                    post = currentPost,
                    onDismiss = {
                        galleryState = null
                        activePostForGallery = null
                    },
                    onLike = { onLikePost(currentPost.id) },
                    onComment = {
                        galleryState = null
                        discussionsSheetPost = currentPost
                    },
                    onShare = {
                        galleryState = null
                        shareSheetPost = currentPost
                    },
                    onSave = { onSavePost(currentPost.id) }
                )
            }
        }

        // Reactions Sheet Modal
        reactionsSheetPost?.let { currentPost ->
            ReactionsSheetDialog(
                post = currentPost,
                allUsers = allUsers,
                onDismiss = { reactionsSheetPost = null },
                onUserClick = {
                    reactionsSheetPost = null
                    onUserClick(it)
                }
            )
        }

        // Share Sheet Modal
        shareSheetPost?.let { currentPost ->
            ShareBottomSheetDialog(
                post = currentPost,
                onDismiss = { shareSheetPost = null },
                onShareToFeed = {
                    shareSheetPost = null
                    if (isGuest) onRequestAuth() else onSharePost?.invoke(currentPost.id)
                },
                onShareToGroup = {
                    shareSheetPost = null
                    if (isGuest) onRequestAuth() else onSharePost?.invoke(currentPost.id)
                },
                onSendMessage = {
                    shareSheetPost = null
                    if (isGuest) onRequestAuth() else onSharePost?.invoke(currentPost.id)
                },
                onCopyLink = {
                    shareSheetPost = null
                    if (isGuest) onRequestAuth() else onSharePost?.invoke(currentPost.id)
                }
            )
        }

        // Discussions & Comments Sheet Modal
        discussionsSheetPost?.let { curPost ->
            val livePost = posts.find { it.id == curPost.id } ?: curPost
            DiscussionsSheetDialog(
                post = livePost,
                currentUser = currentUser,
                isFollowing = livePost.author.isFollowing,
                allUsers = allUsers,
                onFollowToggle = {
                    if (isGuest) {
                        discussionsSheetPost = null
                        onRequestAuth()
                    } else {
                        onFollowUser?.invoke(livePost.author.id)
                    }
                },
                onDismiss = { discussionsSheetPost = null },
                onAddComment = { commentText ->
                    if (isGuest) {
                        discussionsSheetPost = null
                        onRequestAuth()
                    } else {
                        onAddComment?.invoke(livePost.id, commentText)
                    }
                },
                onAddReply = { parentId, replyText ->
                    if (isGuest) {
                        discussionsSheetPost = null
                        onRequestAuth()
                    } else {
                        onAddReply?.invoke(livePost.id, parentId, replyText)
                    }
                },
                onLikeComment = { commentId ->
                    if (isGuest) {
                        discussionsSheetPost = null
                        onRequestAuth()
                    } else {
                        onLikeComment?.invoke(livePost.id, commentId)
                    }
                },
                onDeleteComment = { commentId ->
                    onDeleteComment?.invoke(livePost.id, commentId)
                },
                onToggleHideComment = { commentId, isHidden ->
                    onToggleHideComment?.invoke(livePost.id, commentId, isHidden)
                },
                onProfileClick = { author ->
                    discussionsSheetPost = null
                    onUserClick(author)
                },
                onLikePost = {
                    if (isGuest) onRequestAuth() else onLikePost?.invoke(livePost.id)
                },
                onReactPost = { type ->
                    if (isGuest) onRequestAuth() else onReactPost?.invoke(livePost.id, type)
                },
                onSharePost = {
                    if (isGuest) onRequestAuth() else onSharePost?.invoke(livePost.id)
                },
                onSavePost = {
                    if (isGuest) onRequestAuth() else onSavePost?.invoke(livePost.id)
                },
                onOpenGallery = { _, _ ->
                    activePostForGallery = livePost
                },
                onOpenProduct = { onOpenProduct?.invoke(it) },
                onOpenEvent = { onOpenEvent?.invoke(it) },
                onOpenGroup = { onOpenGroup?.invoke(it) },
                onPlayMusic = { onPlayMusic?.invoke(it) }
            )
        }

        // Floating bottom progress pill shown when scrolling through timeline
        if (uploadState != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                PostUploadBottomPill(
                    uploadState = uploadState,
                    onDismiss = onDismissUpload
                )
            }
        }
    }
}

/**
 * Top Create Post Section matching web CreatePost
 */
@Composable
fun CreatePostSection(
    currentUser: User,
    onClick: () -> Unit,
    onPhotoClick: () -> Unit,
    onVideoClick: () -> Unit,
    onEventClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("create_post_section")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = currentUser.profileImageUrl,
                contentDescription = currentUser.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color(0xFF1E293B), CircleShape)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1E293B).copy(alpha = 0.7f))
                    .clickable(onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "What's on your mind?",
                    fontSize = 15.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Green Photo quick button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onPhotoClick)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "Photo",
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Photo",
                    color = Color(0xFFCBD5E1),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = Color(0xFF1E293B).copy(alpha = 0.6f), thickness = 1.dp)
        Spacer(modifier = Modifier.height(6.dp))

        // Bottom Action Bar: Live, Photo, Video, Event
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Live Video
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClick)
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.Videocam, contentDescription = "Live", tint = Color(0xFF1877F2), modifier = Modifier.size(19.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Live", color = Color(0xFF94A3B8), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }

            // Photo
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onPhotoClick)
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = "Photo", tint = Color(0xFF10B981), modifier = Modifier.size(19.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Photo", color = Color(0xFF94A3B8), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }

            // Video
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onVideoClick)
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.CameraAlt, contentDescription = "Video", tint = Color(0xFFF43F5E), modifier = Modifier.size(19.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Video", color = Color(0xFF94A3B8), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }

            // Event
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onEventClick)
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.Event, contentDescription = "Event", tint = Color(0xFFF59E0B), modifier = Modifier.size(19.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Event", color = Color(0xFF94A3B8), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Stories Row matching web Stories
 */
@Composable
fun StoriesSection(
    currentUser: User,
    stories: List<Story>,
    onStoryClick: (Story) -> Unit,
    onAddStoryClick: () -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A))
            .padding(vertical = 12.dp)
            .testTag("stories_section"),
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Create Story Card (112x190dp)
        item(key = "create_story_card") {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier
                    .width(112.dp)
                    .height(190.dp)
                    .clickable(onClick = onAddStoryClick)
                    .testTag("add_story_btn")
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = currentUser.profileImageUrl,
                        contentDescription = "Create Story",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.72f)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.28f)
                            .align(Alignment.BottomCenter)
                            .background(Color(0xFF0F172A)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Text(
                            text = "Create Story",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF8FAFC),
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }

                    // Floating blue plus icon
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 36.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF166FE5), Color(0xFF1877F2))
                                )
                            )
                            .border(3.dp, Color(0xFF0F172A), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create Story",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Story Cards
        items(stories, key = { it.id }) { story ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier
                    .width(112.dp)
                    .height(190.dp)
                    .clickable { onStoryClick(story) }
                    .testTag("story_item_${story.id}")
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (story.type == "text" || story.mediaUrl.isBlank()) {
                        val gradientColors = remember(story.backgroundStyle) {
                            when {
                                story.backgroundStyle.contains("#F3425F") -> listOf(Color(0xFFF3425F), Color(0xFFE41E3F))
                                story.backgroundStyle.contains("#45BD62") -> listOf(Color(0xFF45BD62), Color(0xFF31A24C))
                                story.backgroundStyle.contains("#F7B928") -> listOf(Color(0xFFF7B928), Color(0xFFE3A300))
                                story.backgroundStyle.contains("#A033FF") -> listOf(Color(0xFFA033FF), Color(0xFF7B1FA2))
                                else -> listOf(Color(0xFF1877F2), Color(0xFF0055FF))
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.linearGradient(gradientColors))
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = story.textContent.ifBlank { "Story" },
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 4,
                                textAlign = TextAlign.Center,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        AsyncImage(
                            model = story.mediaUrl,
                            contentDescription = story.author.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Dark vignette overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.35f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.75f)
                                    )
                                )
                            )
                    )

                    // Top-left author avatar with blue ring
                    AsyncImage(
                        model = story.author.profileImageUrl,
                        contentDescription = story.author.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color(0xFF1877F2), CircleShape)
                    )

                    // Bottom-left author name
                    Text(
                        text = story.author.name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Main Web Post Card Component matching web Post
 */
@Composable
fun WebPostCard(
    post: Post,
    currentUser: User,
    isFollowing: Boolean = false,
    onFollowToggle: () -> Unit = {},
    onLike: () -> Unit = {},
    onReact: (String) -> Unit = {},
    onComment: () -> Unit = {},
    onShare: () -> Unit = {},
    onSave: () -> Unit = {},
    onUserClick: () -> Unit = {},
    onOpenGallery: (List<String>, Int) -> Unit = { _, _ -> },
    onOpenReactions: () -> Unit = {},
    onOpenProduct: (Product) -> Unit = {},
    onOpenEvent: (Event) -> Unit = {},
    onOpenGroup: (Long) -> Unit = {},
    onPlayMusic: (Song) -> Unit = {},
    onOpenVideo: ((Post) -> Unit)? = null,
    onStoryClick: ((Story) -> Unit)? = null,
    onDeletePost: (Long) -> Unit = {},
    onEditPost: (Long, String) -> Unit = { _, _ -> },
    onHidePost: (Long) -> Unit = {},
    onReportPost: ((Long, String) -> Unit)? = null,
    onPushMore: ((Post) -> Unit)? = null,
    pushedPosts: Map<Long, Boolean> = emptyMap(),
    allUsers: List<User> = emptyList(),
    onLikeComment: ((Long, Long) -> Unit)? = null,
    onRequestAuth: () -> Unit = {},
    onRsvpEvent: ((Long, String, String) -> Unit)? = null,
    hideCommentPreview: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showReactionDock by remember { mutableStateOf(false) }
    var isExpandedText by remember { mutableStateOf(false) }

    // Observe cache updates so preview comment reflects likes, additions, and network fetches
    val cacheVersion by CommentsCache.cacheVersion.collectAsState()

    // 1 & 2: Try post.comments[0], else cached comments
    var previewComment by remember(post.id, post.comments, cacheVersion) {
        mutableStateOf(CommentsCache.getPreviewCommentSync(post))
    }

    // 3: If not cached and commentsCount > 0, fetch asynchronously
    LaunchedEffect(post.id, post.commentsCount) {
        if (!hideCommentPreview && previewComment == null && post.commentsCount > 0) {
            val fetched = CommentsCache.ensurePreviewComment(post, currentUser.id)
            if (fetched != null) {
                previewComment = fetched
            }
        }
    }

    // 4: Resolve previewAuthor from previewComment.user_id via allUsers (with fallbacks)
    val previewAuthor = remember(previewComment, allUsers) {
        previewComment?.let { CommentsCache.resolvePreviewAuthor(it, allUsers) }
    }

    // Media list (single or multiple)
    val mediaList = remember(post.mediaUrl, post.mediaUrls) {
        if (post.mediaUrls.isNotEmpty()) post.mediaUrls
        else if (!post.mediaUrl.isNullOrBlank()) listOf(post.mediaUrl)
        else emptyList()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .testTag("post_card_${post.id}"),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
        ) {
            // Header: Author / Group / Ad info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar with group sub-icon or regular
                if (post.groupId != null) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clickable { post.groupId?.let { onOpenGroup(it) } }
                    ) {
                        if (!post.groupImageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = post.groupImageUrl,
                                contentDescription = post.groupName ?: "Group",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(38.dp)
                                    .align(Alignment.TopStart)
                                    .clip(CircleShape)
                                    .border(1.dp, Color(0xFF1E293B), CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .align(Alignment.TopStart)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1877F2).copy(alpha = 0.25f))
                                    .border(1.dp, Color(0xFF1E293B), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Groups,
                                    contentDescription = "Group",
                                    tint = Color(0xFF1877F2),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        FeatheredAvatar(
                            name = post.author.name,
                            username = post.author.username,
                            imageUrl = post.author.profileImageUrl,
                            size = 22.dp,
                            borderWidth = 2.dp,
                            fontSize = 9.sp,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .clickable { onUserClick() }
                        )
                    }
                } else {
                    FeatheredAvatar(
                        name = post.author.name,
                        username = post.author.username,
                        imageUrl = post.author.profileImageUrl,
                        size = 42.dp,
                        borderWidth = 1.dp,
                        fontSize = 16.sp,
                        onClick = onUserClick
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (post.groupId != null) {
                            Text(
                                text = post.groupName ?: "Group",
                                fontWeight = FontWeight.Bold,
                                fontSize = 21.sp,
                                color = Color(0xFFF8FAFC),
                                modifier = Modifier.clickable { post.groupId?.let { onOpenGroup(it) } }
                            )
                        } else {
                            val authorDisplayName = post.author.name.takeIf { it.isNotBlank() && !it.startsWith("User") }
                                ?: post.author.username.takeIf { it.isNotBlank() && !it.startsWith("@user") }?.removePrefix("@")
                                ?: post.author.name.ifBlank { "Feathered User" }
                            Text(
                                text = authorDisplayName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 21.sp,
                                color = Color(0xFFF8FAFC),
                                modifier = Modifier.clickable(onClick = onUserClick)
                            )
                        }

                        if (post.author.isVerified) {
                            Spacer(modifier = Modifier.width(6.dp))
                            VerifiedBadge(size = 21.dp)
                        }

                        val isAdPost = post.isSponsored || post.postType == "ad" || post.postType == "sponsored"
                        if (isAdPost) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF1E293B))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Ad", fontSize = 10.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Sub-line: author / timestamp • globe • feeling / group / etc.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val isAdPost = post.isSponsored || post.postType == "ad" || post.postType == "sponsored"
                        val displayTime = if (isAdPost) "Ad" else PostsApiService.formatRelativeTime(post.timestamp).let { if (it.isBlank() || it.equals("Sponsored", ignoreCase = true)) "Ad" else it }
                        if (post.groupId != null) {
                            Text(
                                text = post.author.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFCBD5E1),
                                modifier = Modifier.clickable(onClick = onUserClick)
                            )
                            Text(
                                text = " • $displayTime • ",
                                fontSize = 13.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = "Group",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier
                                    .size(13.dp)
                                    .clickable { post.groupId?.let { onOpenGroup(it) } }
                            )
                        } else {
                            Text(
                                text = displayTime,
                                fontSize = 13.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Text("•", fontSize = 13.sp, color = Color(0xFF94A3B8))
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = "Public",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(12.dp)
                            )
                        }

                        if (!post.feeling.isNullOrBlank()) {
                            Text("•", fontSize = 13.sp, color = Color(0xFF94A3B8))
                            Text(
                                text = "feeling ${post.feeling}",
                                fontSize = 13.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }

                        if (!post.location.isNullOrBlank()) {
                            Text("•", fontSize = 13.sp, color = Color(0xFF94A3B8))
                            Text(
                                text = post.location,
                                fontSize = 13.sp,
                                color = Color(0xFF94A3B8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Follow button if not current user and not ad
                val isAdPost = post.isSponsored || post.postType == "ad" || post.postType == "sponsored"
                if (post.author.id != currentUser.id && !isAdPost) {
                    val actualIsFollowing = isFollowing || post.author.isFollowing
                    Button(
                        onClick = onFollowToggle,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (actualIsFollowing) Color(0xFF1E293B) else Color(0xFF1877F2)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(
                            imageVector = if (actualIsFollowing) Icons.Filled.Check else Icons.Filled.PersonAdd,
                            contentDescription = null,
                            tint = if (actualIsFollowing) Color(0xFFF8FAFC) else Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (actualIsFollowing) "Following" else "Follow",
                            color = if (actualIsFollowing) Color(0xFFF8FAFC) else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                // 3-dots PostMenu component matching web
                PostMenu(
                    item = post,
                    currentUser = currentUser,
                    onShare = { onShare() },
                    onPushMore = onPushMore,
                    onDeleteSuccess = { onDeletePost(post.id) },
                    onEditSuccess = { onEditPost(post.id, it.content) },
                    onHidePost = { onHidePost(post.id) },
                    onReportPost = { id, reason -> onReportPost?.invoke(id, reason) }
                )
            }

            // Sponsored Headline
            if (post.isSponsored && post.sponsoredHeadline.isNotBlank()) {
                Text(
                    text = post.sponsoredHeadline,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF8FAFC),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
                )
            }

            // Post Text Content
            // Post Text Content (avoid duplicating event/product/song titles that are already inside their respective cards)
            val cleanContent = remember(post.content) {
                post.content.trim().let { if (it.equals("null", ignoreCase = true) || it.equals("undefined", ignoreCase = true)) "" else it }
            }
            val isDuplicateText = (post.event != null && (cleanContent.equals(post.event.title, ignoreCase = true) || cleanContent.equals(post.event.description, ignoreCase = true))) ||
                    (post.product != null && (cleanContent.equals(post.product.title, ignoreCase = true) || cleanContent.equals(post.product.description, ignoreCase = true))) ||
                    (post.song != null && cleanContent.contains(post.song.title, ignoreCase = true))

            val hasMedia = mediaList.isNotEmpty() || !post.mediaUrl.isNullOrBlank() || post.videoUrl != null
            val hasBackground = !hasMedia &&
                    !post.backgroundStyle.isNullOrBlank() &&
                    !post.backgroundStyle.equals("none", ignoreCase = true) &&
                    !post.backgroundStyle.equals("null", ignoreCase = true) &&
                    !post.backgroundStyle.startsWith("style:none") &&
                    !post.backgroundStyle.startsWith("none|")

            if (!isDuplicateText && cleanContent.isNotBlank() && !hasBackground) {
                val words = remember(cleanContent) {
                    cleanContent.split(Regex("\\s+")).filter { it.isNotBlank() }
                }
                val hasMoreThan8Words = words.size > 8
                val truncatedContent = remember(cleanContent, words) {
                    if (words.size > 8) words.take(8).joinToString(" ") + "…" else cleanContent
                }

                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Text(
                        text = if (!hasMoreThan8Words || isExpandedText) cleanContent else truncatedContent,
                        fontSize = 20.sp,
                        lineHeight = 28.sp,
                        color = Color(0xFFE4E6EB)
                    )
                    if (hasMoreThan8Words) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { isExpandedText = !isExpandedText }
                                .padding(vertical = 4.dp, horizontal = 2.dp)
                        ) {
                            Text(
                                text = if (isExpandedText) "See Less" else "See More",
                                color = Color(0xFF1877F2),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (isExpandedText) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color(0xFF1877F2),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            } else if (hasBackground && cleanContent.isNotBlank()) {
                // Text post with animated or styled background
                TextPostBackgroundView(
                    backgroundStyle = post.backgroundStyle,
                    content = cleanContent,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Special Post: Job Posting
            if (post.jobTitle != null) {
                JobPostingCard(
                    jobTitle = post.jobTitle,
                    company = post.company ?: "",
                    location = post.location ?: "Tanzania",
                    salary = post.salary ?: "",
                    jobType = post.jobType ?: "Full-time",
                    onApply = { /* Apply */ }
                )
            }

            // Special Post: Product / MarketPoint (standalone)
            if (post.product != null && post.sharedPost == null) {
                ProductPostCard(
                    product = post.product,
                    onViewProduct = { onOpenProduct(post.product) }
                )
            }

            // Special Post: Event Post (standalone)
            if (post.event != null && post.sharedPost == null) {
                EventPostCard(
                    event = post.event,
                    currentUser = currentUser,
                    onRequestAuth = onRequestAuth,
                    onRsvp = onRsvpEvent,
                    onEventClick = { onOpenEvent(post.event) }
                )
            }

            // Special Post: Music (standalone)
            if (post.song != null && post.sharedPost == null) {
                MusicPostCard(
                    song = post.song,
                    onPlay = { onPlayMusic(post.song) }
                )
            }

            // Special Post: Story Post
            if (post.story != null || post.postType == "story") {
                val storyItem = post.story ?: Story(
                    id = post.id,
                    author = post.author,
                    mediaUrl = post.mediaUrl ?: (post.mediaUrls.firstOrNull() ?: ""),
                    type = if (post.mediaUrl?.endsWith(".mp4", ignoreCase = true) == true) "video" else if (post.mediaUrl.isNullOrBlank()) "text" else "image",
                    textContent = post.content,
                    backgroundStyle = post.backgroundStyle ?: "",
                    timestamp = post.timestamp
                )
                FeedStoryCard(
                    story = storyItem,
                    post = post,
                    onStoryClick = { onStoryClick?.invoke(storyItem) }
                )
            }

            // Special Post: Shared Post
            if (post.sharedPost != null) {
                val isSharedVideo = post.sharedPost.postType == "video" ||
                        post.sharedPost.postType == "reel" ||
                        post.sharedPost.mediaUrl?.endsWith(".mp4", ignoreCase = true) == true ||
                        post.sharedPost.mediaUrl?.contains("video", ignoreCase = true) == true
                if (isSharedVideo) {
                    InstagramVideoShareCard(
                        post = post,
                        originalPost = post.sharedPost,
                        ownerAuthor = post.sharedPost.author,
                        currentUser = currentUser,
                        onProfileClick = { _ -> onUserClick() },
                        onVideoClick = { _ ->
                            onOpenVideo?.invoke(post.sharedPost) ?: run {
                                val url = post.sharedPost.mediaUrl
                                if (url != null) onOpenGallery(listOf(url), 0)
                            }
                        },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                } else {
                    SharedPostNestedCard(
                        sharedPost = post.sharedPost,
                        onOpenGallery = onOpenGallery,
                        onOpenProduct = onOpenProduct,
                        onOpenEvent = onOpenEvent,
                        onPlayMusic = onPlayMusic,
                        onStoryClick = onStoryClick,
                        onOpenGroup = onOpenGroup
                    )
                }
            }

            // Attached Video or Regular Media Grid (1, 2, 3, 4, 5, 6+ images)
            val isPostVideo = post.postType == "video" || post.postType == "reel" ||
                    post.mediaType == "video" ||
                    PostsApiService.isValidVideoUrl(post.videoUrl) ||
                    (mediaList.size == 1 && PostsApiService.isValidVideoUrl(mediaList[0])) ||
                    (post.mediaType == "video" && PostsApiService.isValidVideoUrl(post.mediaUrl)) ||
                    (post.mediaMeta.firstOrNull()?.type == "video" && PostsApiService.isValidVideoUrl(post.mediaMeta.firstOrNull()?.resolution720 ?: post.mediaMeta.firstOrNull()?.original)) ||
                    (post.getDefaultPlayableVideoUrl().isNotBlank() && PostsApiService.isValidVideoUrl(post.getDefaultPlayableVideoUrl()))

            if (post.product == null && post.event == null && post.sharedPost == null && (post.story == null && post.postType != "story") && !hasBackground) {
                if (isPostVideo) {
                    Spacer(modifier = Modifier.height(6.dp))
                    FeedVideoPostThumbnail(
                        post = post,
                        onClick = {
                            onOpenVideo?.invoke(post)
                        }
                    )
                } else if (mediaList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clipToBounds()
                    ) {
                        MediaGrid(
                            images = mediaList,
                            onImageClick = { idx -> onOpenGallery(mediaList, idx) }
                        )
                    }
                }
            }

            // Sponsored CTA Button (labeled Ad)
            val isAd = post.isSponsored || post.postType == "ad" || post.postType == "sponsored"
            if (isAd) {
                val cta = post.sponsoredCtaText.ifBlank { if (post.product != null) "Shop Now" else "Learn More" }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Button(
                        onClick = {
                            if (post.product != null) {
                                onOpenProduct(post.product)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2))
                    ) {
                        Text(
                            text = cta,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                    }
                }
            }

            // Engagement Stats Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 3D Non-moving Overlapping Reactions & Count
                FacebookReactionSummaryRow(
                    likesCount = post.likesCount,
                    reactions = post.reactions,
                    onOpenReactions = onOpenReactions
                )

                // Discussions & Shares
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${post.commentsCount} Discussions",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable(onClick = onComment)
                    )
                    Text("•", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    Text(
                        text = "${post.sharesCount} Shares",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable(onClick = onShare)
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

            // Action Buttons Row: React, Discuss, Share, Save (Matching Web: px-3.5 py-2.5 border-t border-[#1E293B])
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // React Button: 3D animated Facebook reactions dock on hold/long-press
                    FacebookReactionButton(
                        isLiked = post.isLiked,
                        myReaction = post.myReaction,
                        likesCount = post.likesCount,
                        onLike = onLike,
                        onReact = onReact,
                        showReactionDock = showReactionDock,
                        onToggleReactionDock = { showReactionDock = it },
                        modifier = Modifier.testTag("like_post_btn_${post.id}")
                    )

                    // Discuss Button (Exact Web: far fa-comment 22px #F8FAFC, hover #38BDF8)
                    DiscussButton(
                        commentCount = post.commentsCount,
                        onClick = onComment,
                        testTag = "comment_post_btn_${post.id}"
                    )

                    // Share Button (Facing Northeast: ↗, Send icon rotated -35deg)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onShare)
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                            .testTag("share_post_btn_${post.id}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Share",
                            tint = Color(0xFFF8FAFC),
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer(rotationZ = -35f)
                        )
                        if (post.sharesCount > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = formatCount(post.sharesCount),
                                color = Color(0xFFF8FAFC),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Save and Push Button matching web Feeds.tsx:
                // <div className="flex items-center gap-1.5">
                //   <SavePostButton post={p} />
                //   {pushButton && <div className="ml-1">{pushButton}</div>}
                // </div>
                val postAuthorId = post.userId.takeIf { it != 0L } ?: post.author.id
                val isPostOwner = currentUser.id != 0L && (currentUser.id == postAuthorId || currentUser.id == post.author.id)
                val isAdminUser = currentUser.role.equals("admin", ignoreCase = true)
                val isPushed = post.isPushed || (pushedPosts[post.id] == true)
                val showPushButton = (isPostOwner || isAdminUser) && onPushMore != null && !isPushed

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SavePostButton(
                        isSaved = post.isSaved,
                        onToggle = onSave,
                        size = 22.dp,
                        testTag = "save_post_btn_${post.id}"
                    )

                    if (showPushButton) {
                        PushButton(
                            onClick = { onPushMore?.invoke(post) },
                            testTag = "push_post_btn_${post.id}"
                        )
                    }
                }
            }

            // Inline Single Comment Preview (Matching Facebook / Web style)
            if (!hideCommentPreview && previewComment != null && previewAuthor != null) {
                val currentComment = previewComment!!
                HorizontalDivider(color = Color(0xFF1E293B).copy(alpha = 0.5f), thickness = 1.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    FeatheredAvatar(
                        name = previewAuthor.name,
                        imageUrl = previewAuthor.profileImageUrl,
                        username = previewAuthor.username,
                        size = 38.dp
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        // Bubble: click opens comments sheet
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF162137))
                                .clickable(onClick = onComment)
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = previewAuthor.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 19.sp,
                                        color = Color(0xFFF8FAFC)
                                    )
                                    if (previewAuthor.isVerified) {
                                        Spacer(modifier = Modifier.width(5.dp))
                                        VerifiedBadge(size = 17.dp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentComment.text,
                                    fontSize = 20.sp,
                                    lineHeight = 26.sp,
                                    color = Color(0xFFCBD5E1),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Sub-actions row: timestamp, Like button with like count, Reply button
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.padding(start = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            if (currentComment.timestamp.isNotBlank()) {
                                Text(
                                    text = currentComment.timestamp,
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }

                            val isLiked = currentComment.isLiked
                            val likesCount = currentComment.likesCount
                            Row(
                                modifier = Modifier.clickable {
                                    onLikeComment?.invoke(post.id, currentComment.id)
                                },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (isLiked) "Liked" else "Like",
                                    fontSize = 13.sp,
                                    fontWeight = if (isLiked) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isLiked) Color(0xFF38BDF8) else Color(0xFF94A3B8)
                                )
                                if (likesCount > 0) {
                                    Text(
                                        text = formatCount(likesCount),
                                        fontSize = 12.sp,
                                        color = if (isLiked) Color(0xFF38BDF8) else Color(0xFF94A3B8)
                                    )
                                }
                            }

                            Text(
                                text = "Reply",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.clickable(onClick = onComment)
                            )
                        }
                    }
                }
            }
    }
}
}

/**
 * Media Grid Component (1, 2, 3, 4, 5, 6+ images)
 */
@Composable
fun MediaGrid(
    images: List<String>,
    onImageClick: (Int) -> Unit
) {
    com.unera.social.ui.components.MediaGrid(
        images = images,
        onImageClick = onImageClick
    )
}

/**
 * Special Post: Event Post Card
 * Exact Feed Events RSVP matching web Feeds.tsx handleRSVPClick / rsvpEventDirect
 */
@Composable
fun EventPostCard(
    event: Event,
    currentUser: User? = null,
    onRequestAuth: () -> Unit = {},
    onRsvp: ((Long, String, String) -> Unit)? = null,
    onEventClick: () -> Unit
) {
    val initialStatus = remember(event.userRsvpStatus, event.isGoing, event.isInterested) {
        when {
            event.userRsvpStatus.isNotBlank() -> event.userRsvpStatus
            event.isGoing -> "going"
            event.isInterested -> "interested"
            else -> ""
        }
    }
    var rsvpStatus by remember(event.id, initialStatus) { mutableStateOf(initialStatus) }
    var attendeesCount by remember(event.id, event.goingCount) { mutableStateOf(event.goingCount) }
    var interestedCount by remember(event.id, event.interestedCount) { mutableStateOf(event.interestedCount) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val isPast = remember(event.date) {
        try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val evDate = sdf.parse(event.date.trim())
            evDate != null && evDate.before(java.util.Date())
        } catch (_: Exception) {
            false
        }
    }

    val handleRSVPClick: (String) -> Unit = { target ->
        if (currentUser == null || currentUser.id <= 0) {
            onRequestAuth()
        } else if (!loading && !isPast) {
            loading = true
            val prevStatus = rsvpStatus
            val nextStatus = if (prevStatus == target) "" else target

            val prevAtt = attendeesCount
            val prevInt = interestedCount

            var nextAtt = prevAtt
            var nextInt = prevInt

            if (target == "going") {
                if (prevStatus == "going") nextAtt = maxOf(0, prevAtt - 1)
                else if (prevStatus == "interested") {
                    nextAtt = prevAtt + 1
                    nextInt = maxOf(0, prevInt - 1)
                } else nextAtt = prevAtt + 1
            } else {
                if (prevStatus == "interested") nextInt = maxOf(0, prevInt - 1)
                else if (prevStatus == "going") {
                    nextInt = prevInt + 1
                    nextAtt = maxOf(0, prevAtt - 1)
                } else nextInt = prevInt + 1
            }

            rsvpStatus = nextStatus
            attendeesCount = nextAtt
            interestedCount = nextInt

            scope.launch {
                try {
                    if (onRsvp != null) {
                        onRsvp(event.id, if (nextStatus.isBlank()) "not_going" else nextStatus, prevStatus)
                    } else {
                        val res = EventsApiService.rsvpEventDirect(
                            eventId = event.id,
                            userId = currentUser.id,
                            newStatus = if (nextStatus.isBlank()) "not_going" else nextStatus,
                            prevStatus = prevStatus
                        )
                        if (res.success) {
                            if (res.attendingCount != null) attendeesCount = res.attendingCount
                            if (res.interestedCount != null) interestedCount = res.interestedCount
                            if (res.myStatus != null) rsvpStatus = res.myStatus
                        } else {
                            rsvpStatus = prevStatus
                            attendeesCount = prevAtt
                            interestedCount = prevInt
                        }
                    }
                } catch (_: Exception) {
                    rsvpStatus = prevStatus
                    attendeesCount = prevAtt
                    interestedCount = prevInt
                } finally {
                    loading = false
                }
            }
        }
    }

    val dateParts = remember(event.date) {
        val clean = event.date.trim()
        val tokens = clean.split(" ", ",", "-", "/").filter { it.isNotBlank() }
        val month = tokens.firstOrNull { it.length >= 3 && !it.all { c -> c.isDigit() } }?.take(3)?.uppercase() ?: "OCT"
        val day = tokens.firstOrNull { it.all { c -> c.isDigit() } && it.length <= 2 } ?: "24"
        Pair(month, day)
    }

    Card(
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A101F)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEventClick)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                AsyncImage(
                    model = event.imageUrl,
                    contentDescription = event.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Date Box on top left
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1120).copy(alpha = 0.9f)),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier
                        .padding(10.dp)
                        .align(Alignment.TopStart)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(dateParts.first, fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF1877F2))
                        Text(dateParts.second, fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFFF8FAFC))
                    }
                }
            }

            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = event.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFFF8FAFC)
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${event.date} • ${event.location}",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "$attendeesCount going • $interestedCount interested",
                    fontSize = 12.sp,
                    color = Color(0xFF10B981),
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Going Button (Exact Web: h-11 text-[15px] font-bold rounded-lg)
                    Button(
                        onClick = { handleRSVPClick("going") },
                        enabled = !loading && !isPast,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (rsvpStatus == "going") Color(0xFF45BD62) else Color(0xFF1877F2),
                            disabledContainerColor = if (rsvpStatus == "going") Color(0xFF45BD62).copy(alpha = 0.5f) else Color(0xFF1877F2).copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text(
                            text = if (rsvpStatus == "going") "✓ Going" else "Going",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Interested Button (Exact Web: h-11 text-[15px] font-bold rounded-lg)
                    Button(
                        onClick = { handleRSVPClick("interested") },
                        enabled = !loading && !isPast,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (rsvpStatus == "interested") Color(0xFFF7B928) else Color(0xFF1E293B),
                            disabledContainerColor = if (rsvpStatus == "interested") Color(0xFFF7B928).copy(alpha = 0.5f) else Color(0xFF1E293B).copy(alpha = 0.5f)
                        ),
                        border = if (rsvpStatus == "interested") null else BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text(
                            text = if (rsvpStatus == "interested") "✓ Interested" else "Interested",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (rsvpStatus == "interested") Color.Black else Color(0xFFF8FAFC)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Special Post: Product / MarketPoint Card
 */
/**
 * Special Post: Product / MarketPoint Card
 */
@Composable
fun ProductPostCard(
    product: Product,
    onViewProduct: (() -> Unit)? = null
) {
    val marketplaceContext = LocalMarketplaceContext.current
    val handleViewProduct = {
        onViewProduct?.invoke() ?: marketplaceContext.onViewProduct(product.id)
    }

    val productImages = remember(product) {
        val list = mutableListOf<String>()
        if (product.images.isNotEmpty()) {
            list.addAll(product.images.filter { it.isNotBlank() })
        }
        if (list.isEmpty() && product.imageUrl.isNotBlank()) {
            list.add(product.imageUrl)
        }
        list
    }

    Card(
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A101F)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = handleViewProduct)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Product Media Grid (1, 2, 3, 4, 5+ images matching web Feeds.tsx)
            if (productImages.isNotEmpty()) {
                MediaGrid(
                    images = productImages,
                    onImageClick = { handleViewProduct() }
                )
            }

            // Price Bar with "View Product" button matching web Feeds.tsx
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0A101F))
                    .border(width = 1.dp, color = Color(0xFF1E293B))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .clickable(onClick = handleViewProduct),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (product.price > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = product.currency,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF8FAFC)
                        )
                        Text(
                            text = "%,d".format(product.price.toLong()),
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF8FAFC)
                        )
                    }
                } else {
                    Text(
                        text = product.location.ifBlank { "MarketPoint" },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF94A3B8)
                    )
                }

                Button(
                    onClick = handleViewProduct,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingBag,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "View Product",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }

            // Title, location, description
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Text(
                    text = product.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF8FAFC),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (product.location.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFFF43F5E), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(product.location, fontSize = 13.sp, color = Color(0xFF94A3B8))
                    }
                }
                if (product.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = product.description,
                        fontSize = 13.sp,
                        color = Color(0xFFCBD5E1),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

/**
 * SuggestedProductsWidget - "MarketPoint for you" 4-product grid matching web Feeds.tsx
 */
@Composable
fun SuggestedProductsWidget(
    products: List<Product>,
    currentUser: User,
    onViewProduct: (Product) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val suggested = remember(products, currentUser.id) {
        products.filter { it.seller.id != currentUser.id }.take(4)
    }

    if (suggested.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0B1120))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MarketPoint for you",
                color = Color(0xFFF8FAFC),
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp
            )
            Text(
                text = "See all",
                color = Color(0xFF1877F2),
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onSeeAll)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        val rows = suggested.chunked(2)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rows.forEach { rowProducts ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowProducts.forEach { prod ->
                        val prodImg = prod.images.firstOrNull() ?: prod.imageUrl
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onViewProduct(prod) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0F172A))
                                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                            ) {
                                if (prodImg.isNotBlank()) {
                                    AsyncImage(
                                        model = prodImg,
                                        contentDescription = prod.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                // Price badge bottom-left
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.Black.copy(alpha = 0.75f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "${prod.currency} %,d".format(prod.price.toLong()),
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (prod.isSponsored) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFFF97316))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "Ad",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = prod.title,
                                color = Color(0xFFF8FAFC),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (rowProducts.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * Compatibility PostCard composable for ProfileScreen and other screens
 */
@Composable
fun PostCard(
    post: Post,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onSave: () -> Unit,
    onUserClick: () -> Unit,
    currentUser: User? = null,
    isFollowing: Boolean = false,
    onFollowToggle: () -> Unit = {},
    onOpenProduct: ((Product) -> Unit)? = null,
    onOpenEvent: ((Event) -> Unit)? = null,
    onOpenGroup: ((Long) -> Unit)? = null,
    onPlayMusic: ((Song) -> Unit)? = null,
    onOpenGallery: ((List<String>, Int) -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onDeletePost: ((Long) -> Unit)? = null,
    onEditPost: ((Long, String) -> Unit)? = null,
    onHidePost: ((Long) -> Unit)? = null,
    onReportPost: ((Long, String) -> Unit)? = null,
    onPushMore: ((Post) -> Unit)? = null,
    pushedPosts: Map<Long, Boolean> = emptyMap(),
    modifier: Modifier = Modifier
) {
    WebPostCard(
        post = post,
        currentUser = currentUser ?: post.author,
        isFollowing = isFollowing,
        onFollowToggle = onFollowToggle,
        onLike = onLike,
        onReact = { onLike() },
        onComment = onComment,
        onShare = { onShare?.invoke() },
        onSave = onSave,
        onUserClick = onUserClick,
        onOpenGallery = { list, idx -> onOpenGallery?.invoke(list, idx) },
        onOpenReactions = {},
        onOpenProduct = { onOpenProduct?.invoke(it) },
        onOpenEvent = { onOpenEvent?.invoke(it) },
        onOpenGroup = { onOpenGroup?.invoke(it) },
        onPlayMusic = { onPlayMusic?.invoke(it) },
        onDeletePost = { onDeletePost?.invoke(it) },
        onEditPost = { id, text -> onEditPost?.invoke(id, text) },
        onHidePost = { onHidePost?.invoke(it) },
        onReportPost = onReportPost,
        onPushMore = onPushMore,
        pushedPosts = pushedPosts,
        modifier = modifier
    )
}

@Composable
fun JobPostingCard(
    jobTitle: String,
    company: String,
    location: String,
    salary: String,
    jobType: String,
    onApply: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF162137)),
        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF10B981).copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("JOB POSTING", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(jobType, fontSize = 12.sp, color = Color(0xFFF59E0B), fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(jobTitle, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF8FAFC))

            Spacer(modifier = Modifier.height(4.dp))
            Text("$company • $location", fontSize = 13.sp, color = Color(0xFF94A3B8))

            if (salary.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(salary, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
            }

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onApply,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Apply Now", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Special Post: Music Post Card
 */
@Composable
fun MusicPostCard(
    song: Song,
    onPlay: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1120)),
        border = null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = song.cover.ifBlank { "https://media.featheredsocial.site/task_01kftb3024ed7bm84gy6j485fh_1769336848_img_0.webp" },
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Large play button overlay (exact shared SongCardPlayButton)
                SongCardPlayButton(
                    contentDescription = "Play",
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFFF8FAFC),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${song.artist} • ${song.album}",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = onPlay,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Play", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Special Post: Shared Post Nested Card
 */
/**
 * Feed Story Card: Displayed in feed when post is a Story
 */
@Composable
fun FeedStoryCard(
    story: Story,
    post: Post,
    onStoryClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1120)),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clickable(onClick = onStoryClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                val isTextStory = story.type == "text" || (story.mediaUrl.isBlank() && (story.textContent.isNotBlank() || post.content.isNotBlank()))
                if (isTextStory) {
                    val brush = remember(story.backgroundStyle) {
                        when (story.backgroundStyle) {
                            "red" -> Brush.linearGradient(listOf(Color(0xFFFF0057), Color(0xFFE64C4C)))
                            "blue" -> Brush.linearGradient(listOf(Color(0xFF00C6FF), Color(0xFF0072FF)))
                            "green" -> Brush.linearGradient(listOf(Color(0xFFA8FF78), Color(0xFF78FFD6)))
                            "purple" -> Brush.linearGradient(listOf(Color(0xFFE65C00), Color(0xFFF9D423)))
                            "dark" -> Brush.linearGradient(listOf(Color(0xFF434343), Color(0xFF000000)))
                            else -> Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)))
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(brush)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = story.textContent.ifBlank { post.content },
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            lineHeight = 28.sp
                        )
                    }
                } else {
                    AsyncImage(
                        model = story.mediaUrl.ifBlank { post.mediaUrl },
                        contentDescription = "Story Media",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.7f)
                                    )
                                )
                            )
                    )
                    val caption = story.textContent.ifBlank { post.content }
                    if (caption.isNotBlank()) {
                        Text(
                            text = caption,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                        )
                    }
                }

                // Story Gradient Pill Badge
                Box(
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFF59E0B), Color(0xFFEC4899), Color(0xFF8B5CF6))
                            )
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "STORY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                }

                // Center Play Button
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Watch Story",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.RemoveRedEye,
                        contentDescription = "Views",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Story Update",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )
                }

                Button(
                    onClick = onStoryClick,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Watch Story", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Shared Product Card: When a post shares a marketplace product
 */
@Composable
fun SharedProductCard(
    product: Product,
    originalAuthor: User,
    timestamp: String,
    onViewProduct: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A101F)),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onViewProduct)
    ) {
        Column {
            // Nested Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = originalAuthor.profileImageUrl,
                    contentDescription = originalAuthor.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = originalAuthor.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFFF8FAFC)
                        )
                        if (originalAuthor.isVerified) {
                            Spacer(modifier = Modifier.width(4.dp))
                            VerifiedBadge(size = 12.dp)
                        }
                    }
                    Text(
                        text = "Shared a product from MarketPoint • ${PostsApiService.formatRelativeTime(timestamp)}",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1877F2).copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("MarketPoint", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1877F2))
                }
            }

            val sharedProductImages = remember(product) {
                val list = mutableListOf<String>()
                if (product.images.isNotEmpty()) {
                    list.addAll(product.images.filter { it.isNotBlank() })
                }
                if (list.isEmpty() && product.imageUrl.isNotBlank()) {
                    list.add(product.imageUrl)
                }
                list
            }

            // Product Photo Grid
            if (sharedProductImages.isNotEmpty()) {
                MediaGrid(
                    images = sharedProductImages,
                    onImageClick = { onViewProduct() }
                )
            }

            // Price Bar with "View Product" button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0A101F))
                    .border(width = 1.dp, color = Color(0xFF1E293B))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clickable(onClick = onViewProduct),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${product.currency} %,d".format(product.price.toLong()),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF8FAFC)
                )

                Button(
                    onClick = onViewProduct,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingBag,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "View",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }
            }

            // Title, location, action
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = product.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF8FAFC),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (product.location.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFFF43F5E), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(product.location, fontSize = 12.sp, color = Color(0xFF94A3B8))
                    }
                }
                if (product.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = product.description,
                        fontSize = 12.sp,
                        color = Color(0xFFCBD5E1),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onViewProduct,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ShoppingBag, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("View in MarketPoint", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * Shared Event Card: When a post shares an event
 */
@Composable
fun SharedEventCard(
    event: Event,
    originalAuthor: User,
    timestamp: String,
    onEventClick: () -> Unit
) {
    val dateParts = remember(event.date) {
        val clean = event.date.trim()
        val tokens = clean.split(" ", ",", "-", "/").filter { it.isNotBlank() }
        val month = tokens.firstOrNull { it.length >= 3 && !it.all { c -> c.isDigit() } }?.take(3)?.uppercase() ?: "EVENT"
        val day = tokens.firstOrNull { it.all { c -> c.isDigit() } && it.length <= 2 } ?: "•"
        Pair(month, day)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A101F)),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEventClick)
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = originalAuthor.profileImageUrl,
                    contentDescription = originalAuthor.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = originalAuthor.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFFF8FAFC)
                    )
                    Text(
                        text = "Shared an event • ${PostsApiService.formatRelativeTime(timestamp)}",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF8B5CF6).copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("EVENT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8B5CF6))
                }
            }

            // Cover Image with Date Badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
            ) {
                AsyncImage(
                    model = event.imageUrl,
                    contentDescription = event.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1120).copy(alpha = 0.9f)),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier
                        .padding(10.dp)
                        .align(Alignment.TopStart)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(dateParts.first, fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF1877F2))
                        Text(dateParts.second, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFFF8FAFC))
                    }
                }
            }

            // Details
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = event.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF8FAFC),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${event.date} • ${event.location}",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${event.goingCount} attending • ${event.interestedCount} interested",
                    fontSize = 11.sp,
                    color = Color(0xFF10B981),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onEventClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("View Event Details", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * Shared Story Card: When a post shares a story
 */
@Composable
fun SharedStoryCard(
    story: Story,
    originalAuthor: User,
    timestamp: String,
    onStoryClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A101F)),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onStoryClick)
    ) {
        Column {
            // Header with Story Ring
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFEC4899), Color(0xFF8B5CF6)))
                        )
                        .padding(2.dp)
                ) {
                    AsyncImage(
                        model = originalAuthor.profileImageUrl,
                        contentDescription = originalAuthor.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = originalAuthor.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFFF8FAFC)
                    )
                    Text(
                        text = "Shared a Story • ${PostsApiService.formatRelativeTime(timestamp)}",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFEC4899).copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("STORY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEC4899))
                }
            }

            // Visual Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                if (story.type == "text" || (story.mediaUrl.isBlank() && story.textContent.isNotBlank())) {
                    val brush = remember(story.backgroundStyle) {
                        when (story.backgroundStyle) {
                            "red" -> Brush.linearGradient(listOf(Color(0xFFFF0057), Color(0xFFE64C4C)))
                            "blue" -> Brush.linearGradient(listOf(Color(0xFF00C6FF), Color(0xFF0072FF)))
                            "green" -> Brush.linearGradient(listOf(Color(0xFFA8FF78), Color(0xFF78FFD6)))
                            else -> Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)))
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(brush)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = story.textContent,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    AsyncImage(
                        model = story.mediaUrl,
                        contentDescription = "Story Media",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Play icon overlay
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Watch", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }

            // Action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tap to view story",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
                Button(
                    onClick = onStoryClick,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Watch Story", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Special Post: Shared Post Nested Card supporting regular posts, products, events, stories, and songs
 */
@Composable
fun SharedPostNestedCard(
    sharedPost: Post,
    onOpenGallery: (List<String>, Int) -> Unit,
    onOpenProduct: (Product) -> Unit = {},
    onOpenEvent: (Event) -> Unit = {},
    onPlayMusic: (Song) -> Unit = {},
    onStoryClick: ((Story) -> Unit)? = null,
    onOpenGroup: (Long) -> Unit = {}
) {
    if (sharedPost.product != null || sharedPost.postType == "marketplace" || sharedPost.postType == "product") {
        val prod = sharedPost.product ?: Product(
            id = sharedPost.id,
            title = sharedPost.content.ifBlank { "Marketplace Item" },
            price = 50000.0,
            imageUrl = sharedPost.mediaUrl ?: "",
            category = "General",
            location = sharedPost.location ?: "Tanzania",
            description = sharedPost.content,
            seller = sharedPost.author
        )
        SharedProductCard(
            product = prod,
            originalAuthor = sharedPost.author,
            timestamp = sharedPost.timestamp,
            onViewProduct = { onOpenProduct(prod) }
        )
    } else if (sharedPost.event != null || sharedPost.postType == "event") {
        val ev = sharedPost.event ?: Event(
            id = sharedPost.id,
            title = sharedPost.content.ifBlank { "Event" },
            date = "Oct 24, 2026",
            location = sharedPost.location ?: "Tanzania",
            imageUrl = sharedPost.mediaUrl ?: "",
            description = sharedPost.content,
            organizer = sharedPost.author
        )
        SharedEventCard(
            event = ev,
            originalAuthor = sharedPost.author,
            timestamp = sharedPost.timestamp,
            onEventClick = { onOpenEvent(ev) }
        )
    } else if (sharedPost.story != null || sharedPost.postType == "story") {
        val st = sharedPost.story ?: Story(
            id = sharedPost.id,
            author = sharedPost.author,
            mediaUrl = sharedPost.mediaUrl ?: "",
            textContent = sharedPost.content,
            backgroundStyle = sharedPost.backgroundStyle ?: "",
            timestamp = sharedPost.timestamp
        )
        SharedStoryCard(
            story = st,
            originalAuthor = sharedPost.author,
            timestamp = sharedPost.timestamp,
            onStoryClick = { onStoryClick?.invoke(st) }
        )
    } else if (sharedPost.song != null || sharedPost.postType == "music" || sharedPost.postType == "song") {
        val song = sharedPost.song ?: Song(
            id = sharedPost.id.toString(),
            title = sharedPost.content.ifBlank { "Track" },
            artist = sharedPost.author.name,
            audioUrl = sharedPost.mediaUrl ?: "",
            cover = sharedPost.mediaUrl ?: ""
        )
        MusicPostCard(
            song = song,
            onPlay = { onPlayMusic(song) }
        )
    } else {
        // Standard regular post share & shared group post
        var isExpandedSharedText by remember { mutableStateOf(false) }
        val isGroup = sharedPost.groupId != null
        val mediaList = if (sharedPost.mediaUrls.isNotEmpty()) sharedPost.mediaUrls
            else if (!sharedPost.mediaUrl.isNullOrBlank()) listOf(sharedPost.mediaUrl)
            else emptyList()

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A101F)),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Nested Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isGroup) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clickable { sharedPost.groupId?.let { onOpenGroup(it) } }
                        ) {
                            if (!sharedPost.groupImageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = sharedPost.groupImageUrl,
                                    contentDescription = sharedPost.groupName ?: "Group",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .align(Alignment.TopStart)
                                        .clip(CircleShape)
                                        .border(1.dp, Color(0xFF1E293B), CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .align(Alignment.TopStart)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1877F2).copy(alpha = 0.25f))
                                        .border(1.dp, Color(0xFF1E293B), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Groups,
                                        contentDescription = "Group",
                                        tint = Color(0xFF1877F2),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            FeatheredAvatar(
                                name = sharedPost.author.name,
                                username = sharedPost.author.username,
                                imageUrl = sharedPost.author.profileImageUrl,
                                size = 20.dp,
                                borderWidth = 1.dp,
                                fontSize = 8.sp,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                            )
                        }
                    } else {
                        FeatheredAvatar(
                            name = sharedPost.author.name,
                            username = sharedPost.author.username,
                            imageUrl = sharedPost.author.profileImageUrl,
                            size = 36.dp,
                            borderWidth = 1.dp,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val authorDisplayName = sharedPost.author.name.takeIf { it.isNotBlank() && !it.startsWith("User") }
                                ?: sharedPost.author.username.takeIf { it.isNotBlank() && !it.startsWith("@user") }?.removePrefix("@")
                                ?: sharedPost.author.name.ifBlank { "Feathered User" }
                            if (isGroup) {
                                Text(
                                    text = sharedPost.groupName ?: "Group",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFFF8FAFC),
                                    modifier = Modifier.clickable { sharedPost.groupId?.let { onOpenGroup(it) } }
                                )
                            } else {
                                Text(
                                    text = authorDisplayName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFFF8FAFC)
                                )
                            }
                            if (sharedPost.author.isVerified) {
                                Spacer(modifier = Modifier.width(4.dp))
                                VerifiedBadge(size = 14.dp)
                            }
                        }
                        Text(
                            text = if (isGroup) {
                                "${sharedPost.author.name} • ${PostsApiService.formatRelativeTime(sharedPost.timestamp)}"
                            } else {
                                PostsApiService.formatRelativeTime(sharedPost.timestamp)
                            },
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                val hasSharedMedia = mediaList.isNotEmpty() || !sharedPost.mediaUrl.isNullOrBlank() || sharedPost.videoUrl != null
                val hasSharedBackground = !hasSharedMedia &&
                    !sharedPost.backgroundStyle.isNullOrBlank() &&
                    !sharedPost.backgroundStyle.equals("none", ignoreCase = true) &&
                    !sharedPost.backgroundStyle.equals("null", ignoreCase = true) &&
                    !sharedPost.backgroundStyle.startsWith("style:none") &&
                    !sharedPost.backgroundStyle.startsWith("none|")

                if (hasSharedBackground && sharedPost.content.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextPostBackgroundView(
                        backgroundStyle = sharedPost.backgroundStyle,
                        content = sharedPost.content,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else if (sharedPost.content.isNotBlank()) {
                    val words = remember(sharedPost.content) {
                        sharedPost.content.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                    }
                    val hasMoreThan8Words = words.size > 8
                    val truncatedContent = remember(sharedPost.content, words) {
                        if (words.size > 8) words.take(8).joinToString(" ") + "…" else sharedPost.content
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (!hasMoreThan8Words || isExpandedSharedText) sharedPost.content else truncatedContent,
                        fontSize = 20.sp,
                        lineHeight = 28.sp,
                        color = Color(0xFFCBD5E1)
                    )
                    if (hasMoreThan8Words) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { isExpandedSharedText = !isExpandedSharedText }
                                .padding(vertical = 4.dp, horizontal = 2.dp)
                        ) {
                            Text(
                                text = if (isExpandedSharedText) "See Less" else "See More",
                                color = Color(0xFF1877F2),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (isExpandedSharedText) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color(0xFF1877F2),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (mediaList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    MediaGrid(
                        images = mediaList,
                        onImageClick = { idx -> onOpenGallery(mediaList, idx) }
                    )
                }
            }
        }
    }
}

/**
 * Interleaved Widget: People You May Know Section
 */
@Composable
fun PeopleYouMayKnowSection(
    currentUser: User,
    allUsers: List<User>,
    followStates: Map<Long, Boolean> = emptyMap(),
    onFollowToggle: (Long) -> Unit,
    onDismiss: ((Long) -> Unit)? = null,
    onUserClick: (User) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A))
            .padding(vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "People You May Know",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF8FAFC)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(allUsers, key = { it.id }) { user ->
                val isFollowing = followStates[user.id] ?: user.isFollowing
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    border = BorderStroke(1.dp, Color(0xFF334155).copy(alpha = 0.5f)),
                    modifier = Modifier.width(160.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Square profile photo with optional dismiss button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                        ) {
                            AsyncImage(
                                model = user.profileImageUrl,
                                contentDescription = user.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { onUserClick(user) }
                            )

                            if (onDismiss != null) {
                                IconButton(
                                    onClick = { onDismiss(user.id) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(26.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = user.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFFF8FAFC),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onUserClick(user) }
                                )
                                if (user.isVerified) {
                                    Spacer(modifier = Modifier.width(3.dp))
                                    VerifiedBadge(size = 12.dp)
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Suggested for you",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = { onFollowToggle(user.id) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isFollowing) Color(0xFF1E293B) else Color(0xFF1877F2)
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFollowing) Icons.Filled.Check else Icons.Filled.PersonAdd,
                                    contentDescription = null,
                                    tint = if (isFollowing) Color(0xFFF8FAFC) else Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isFollowing) "Following" else "Follow",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFollowing) Color(0xFFF8FAFC) else Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Interleaved Widget: Groups You May Join Section
 */
@Composable
fun GroupsYouMayJoinSection(
    groups: List<Group>,
    joinedStates: Map<Long, Boolean>,
    onJoinToggle: (Long) -> Unit,
    onGroupClick: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A))
            .padding(vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Groups You May Join",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF8FAFC)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(groups, key = { it.id }) { group ->
                val isJoined = joinedStates[group.id] ?: group.isMember
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    border = BorderStroke(1.dp, Color(0xFF334155).copy(alpha = 0.5f)),
                    modifier = Modifier
                        .width(220.dp)
                        .clickable { onGroupClick(group.id) }
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Cover Image with Public/Private tag
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .clickable { onGroupClick(group.id) }
                        ) {
                            if (group.coverImageUrl.isNotBlank()) {
                                AsyncImage(
                                    model = group.coverImageUrl,
                                    contentDescription = group.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Brush.linearGradient(listOf(Color(0xFF1877F2), Color(0xFF166FE5))))
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .align(Alignment.TopEnd)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (group.isPrivate) "🔒 Private" else "🌍 Public",
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Info section
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = group.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFFF8FAFC),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { onGroupClick(group.id) }
                            )

                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${group.membersCount} members • ${group.category}",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = { onJoinToggle(group.id) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isJoined) Color(0xFF334155) else Color(0xFF1877F2)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isJoined) "Joined" else "Join Group",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
