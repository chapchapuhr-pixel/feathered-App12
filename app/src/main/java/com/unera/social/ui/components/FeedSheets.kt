package com.unera.social.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.unera.social.data.FeedInteractionApiService
import com.unera.social.model.Comment
import com.unera.social.model.Post
import com.unera.social.model.User
import com.unera.social.model.UserReaction
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.unera.social.model.Product
import com.unera.social.model.Event
import com.unera.social.model.Song
import com.unera.social.ui.screens.WebPostCard
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.automirrored.filled.Reply
import kotlinx.coroutines.delay

private fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fk", count / 1_000.0)
        else -> count.toString()
    }
}

// Reaction Emojis
val REACTION_EMOJIS = listOf(
    "like" to "👍",
    "love" to "❤️",
    "care" to "🥰",
    "haha" to "😂",
    "wow" to "😮",
    "sad" to "😢",
    "angry" to "😡",
    "fire" to "🔥",
    "party" to "🎉",
    "clap" to "👏",
    "star" to "⭐"
)

fun getReactionEmoji(type: String): String {
    return REACTION_EMOJIS.find { it.first == type }?.second ?: "👍"
}

/**
 * Fullscreen Gallery Viewer matching web GalleryViewer
 */
@Composable
fun GalleryViewerDialog(
    images: List<String>,
    initialIndex: Int = 0,
    post: Post,
    onDismiss: () -> Unit,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit
) {
    if (images.isEmpty()) return

    var currentIndex by remember { mutableIntStateOf(initialIndex.coerceIn(0, images.size - 1)) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF050B18))
                .testTag("gallery_viewer_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar - Solid Opaque (#0B1120)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(Color(0xFF0B1120))
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${currentIndex + 1}/${images.size}",
                        color = Color(0xFFF8FAFC),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Middle Image Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF050B18)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = images[currentIndex],
                        contentDescription = "Gallery photo ${currentIndex + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Prev / Next touch areas if multiple images
                    if (currentIndex > 0) {
                        IconButton(
                            onClick = { currentIndex-- },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 12.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "Previous",
                                tint = Color.White
                            )
                        }
                    }

                    if (currentIndex < images.size - 1) {
                        IconButton(
                            onClick = { currentIndex++ },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 12.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Next",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Bottom Engagement Bar (#0B1120)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0B1120))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${post.likesCount} Reactions",
                            color = Color(0xFFF8FAFC),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${post.commentsCount} Discussions • ${post.sharesCount} Shares",
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp
                        )
                    }

                    HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onLike) {
                                Icon(
                                    imageVector = if (post.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = "Like",
                                    tint = if (post.isLiked) Color(0xFFF43F5E) else Color(0xFFF8FAFC)
                                )
                            }
                            IconButton(onClick = onComment) {
                                Icon(
                                    imageVector = Icons.Outlined.ChatBubbleOutline,
                                    contentDescription = "Comment",
                                    tint = Color(0xFFF8FAFC)
                                )
                            }
                            IconButton(onClick = onShare) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Share",
                                    tint = Color(0xFFF8FAFC)
                                )
                            }
                        }

                        IconButton(onClick = onSave) {
                            Icon(
                                imageVector = if (post.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "Save",
                                tint = if (post.isSaved) Color(0xFF1877F2) else Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Reactions Sheet Dialog matching web ReactionsSheet
 */
@Composable
fun ReactionsSheetDialog(
    post: Post,
    allUsers: List<User>,
    onDismiss: () -> Unit,
    onUserClick: (User) -> Unit
) {
    var liveReactions by remember(post.id) { mutableStateOf(post.reactions) }
    var selectedTab by remember { mutableStateOf("all") }

    LaunchedEffect(post.id) {
        val (fetched, _) = FeedInteractionApiService.fetchReactions(post, 0L)
        if (fetched.isNotEmpty()) {
            liveReactions = fetched
        }
    }

    val reactionTypes = remember(liveReactions) {
        liveReactions.map { it.type }.distinct()
    }

    val filteredReactions = remember(selectedTab, liveReactions) {
        if (selectedTab == "all") liveReactions
        else liveReactions.filter { it.type == selectedTab }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF050B18))
                .testTag("reactions_sheet_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(Color(0xFF0B1120))
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFFF8FAFC)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "People who reacted",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF8FAFC)
                    )
                }

                // Filter Tabs Row
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0B1120))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedTab == "all",
                            onClick = { selectedTab = "all" },
                            label = { Text("All ${liveReactions.size}") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF1877F2),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF1E293B),
                                labelColor = Color(0xFF94A3B8)
                            )
                        )
                    }

                    items(reactionTypes) { type ->
                        val count = liveReactions.count { it.type == type }
                        FilterChip(
                            selected = selectedTab == type,
                            onClick = { selectedTab = type },
                            label = { Text("${getReactionEmoji(type)} $count") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF1877F2),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF1E293B),
                                labelColor = Color(0xFF94A3B8)
                            )
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

                // List of reactors
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (filteredReactions.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No reactions yet.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 16.sp
                                )
                            }
                        }
                    } else {
                        items(filteredReactions, key = { it.userId }) { reaction ->
                            val reactorUser = allUsers.find { it.id == reaction.userId }
                            val resolvedName = when {
                                reaction.userName.isNotBlank() && !reaction.userName.equals("User", ignoreCase = true) && !reaction.userName.startsWith("User ", ignoreCase = true) -> reaction.userName
                                reactorUser != null && reactorUser.name.isNotBlank() && !reactorUser.name.equals("User", ignoreCase = true) && !reactorUser.name.startsWith("User ", ignoreCase = true) -> reactorUser.name
                                reaction.username.isNotBlank() -> reaction.username.removePrefix("@")
                                reactorUser?.username?.isNotBlank() == true -> reactorUser.username.removePrefix("@")
                                reaction.userName.isNotBlank() -> reaction.userName
                                else -> reactorUser?.name?.takeIf { it.isNotBlank() } ?: "Member"
                            }
                            val resolvedAvatar = reaction.userAvatar.ifBlank { reactorUser?.profileImageUrl ?: "" }
                            val resolvedUsername = reaction.username.ifBlank { reactorUser?.username ?: "" }
                            val isVerified = reaction.isVerified || (reactorUser?.isVerified == true)
                            val finalUser = reactorUser ?: User(
                                id = reaction.userId,
                                name = resolvedName,
                                username = resolvedUsername,
                                profileImageUrl = resolvedAvatar,
                                isVerified = isVerified
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onUserClick(finalUser) }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box {
                                    FeatheredAvatar(
                                        name = resolvedName,
                                        imageUrl = resolvedAvatar,
                                        username = resolvedUsername,
                                        size = 46.dp
                                    )
                                    // Mini 3D reaction emoji badge
                                    val fb3dReaction = getFacebookReaction(reaction.type)
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .align(Alignment.BottomEnd)
                                            .clip(CircleShape)
                                            .background(Color(0xFF0B1120))
                                            .border(1.dp, Color(0xFF1E293B), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (fb3dReaction != null) {
                                            androidx.compose.foundation.Image(
                                                painter = androidx.compose.ui.res.painterResource(id = fb3dReaction.resId),
                                                contentDescription = fb3dReaction.label,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Text(text = getReactionEmoji(reaction.type), fontSize = 11.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = resolvedName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = Color(0xFFF8FAFC)
                                        )
                                        if (isVerified) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            VerifiedBadge(size = 14.dp)
                                        }
                                    }
                                    if (resolvedUsername.isNotBlank()) {
                                        Text(
                                            text = if (resolvedUsername.startsWith("@")) resolvedUsername else "@$resolvedUsername",
                                            fontSize = 13.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Share Bottom Sheet Dialog matching web ShareBottomSheet
 */
@Composable
fun ShareBottomSheetDialog(
    post: Post,
    onDismiss: () -> Unit,
    onShareToFeed: () -> Unit,
    onShareToGroup: () -> Unit,
    onSendMessage: () -> Unit,
    onCopyLink: () -> Unit
) {
    val context = LocalContext.current
    val isVideo = post.postType == "video" || post.mediaType == "video" || !post.videoUrl.isNullOrBlank()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // Scrim over the whole screen (covers bottom navigation and status/nav bars), clicking outside dismisses
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Floating bottom sheet Card occupying 100% width and ~42-52% height
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(if (isVideo) 0.54f else 0.48f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* intercept clicks inside card to prevent dismiss */ }
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .testTag("share_bottom_sheet_dialog"),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    // Drag handle bar
                    Box(
                        modifier = Modifier
                            .size(44.dp, 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF475569))
                            .align(Alignment.CenterHorizontally)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Header with title and close button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Share",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFFF8FAFC)
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Scrollable container for post preview & options
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Post Mini Preview Card
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E293B))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!post.mediaUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = post.mediaUrl,
                                    contentDescription = "Post preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            } else {
                                FeatheredAvatar(
                                    name = post.author.name,
                                    imageUrl = post.author.profileImageUrl,
                                    size = 44.dp,
                                    username = post.author.username
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = post.author.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFFF8FAFC)
                                )
                                Text(
                                    text = post.content.ifBlank { "Shared post" },
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Option: Share to Feed
                        ShareOptionRow(
                            icon = Icons.Default.Newspaper,
                            iconTint = Color(0xFF1877F2),
                            iconBg = Color(0xFF1877F2).copy(alpha = 0.15f),
                            title = "Share to Feed / Profile",
                            subtitle = "Share to your profile & public feed",
                            onClick = onShareToFeed
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Option: Share to Groups
                        ShareOptionRow(
                            icon = Icons.Default.Groups,
                            iconTint = Color(0xFF45BD62),
                            iconBg = Color(0xFF45BD62).copy(alpha = 0.15f),
                            title = "Share to Groups & Brands",
                            subtitle = "Share with your groups & communities",
                            onClick = onShareToGroup
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Option: Send as Message
                        ShareOptionRow(
                            icon = Icons.Default.ChatBubble,
                            iconTint = Color(0xFF1877F2),
                            iconBg = Color(0xFF1877F2).copy(alpha = 0.15f),
                            title = "Send as a Message",
                            subtitle = "Share via direct message",
                            onClick = onSendMessage
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Option: Copy Post Link
                        ShareOptionRow(
                            icon = Icons.Default.Link,
                            iconTint = Color(0xFF1877F2),
                            iconBg = Color(0xFF1877F2).copy(alpha = 0.15f),
                            title = "Copy Post Link",
                            subtitle = "https://featheredsocial.site/post/${post.id}",
                            onClick = onCopyLink
                        )

                        // Option: Download Original (Slot 0 untouch upload for videos)
                        if (isVideo) {
                            Spacer(modifier = Modifier.height(10.dp))
                            ShareOptionRow(
                                icon = Icons.Default.Download,
                                iconTint = Color(0xFF38BDF8),
                                iconBg = Color(0xFF38BDF8).copy(alpha = 0.15f),
                                title = "Download Original Video",
                                subtitle = "Full untouched original upload",
                                onClick = {
                                    val downloadUrl = post.getOriginalDownloadUrl()
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                                        context.startActivity(intent)
                                        Toast.makeText(context, "Opening original download...", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Unable to open download", Toast.LENGTH_SHORT).show()
                                    }
                                    onDismiss()
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Text(text = "Cancel", color = Color(0xFFF8FAFC), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFFF8FAFC))
            Text(text = subtitle, fontSize = 12.sp, color = Color(0xFF94A3B8), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color(0xFF64748B),
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Discussions & Comments Sheet Dialog matching web CommentsSheet
 */
@Composable
fun DiscussionsSheetDialog(
    post: Post,
    currentUser: User,
    onDismiss: () -> Unit,
    onAddComment: (String) -> Unit,
    onAddReply: ((Long, String) -> Unit)? = null,
    onLikeComment: (Long) -> Unit,
    onDeleteComment: ((Long) -> Unit)? = null,
    onToggleHideComment: ((Long, Boolean) -> Unit)? = null,
    onProfileClick: ((User) -> Unit)? = null,
    onLikePost: (() -> Unit)? = null,
    onReactPost: ((String) -> Unit)? = null,
    onSharePost: (() -> Unit)? = null,
    onSavePost: (() -> Unit)? = null,
    onOpenGallery: ((List<String>, Int) -> Unit)? = null,
    onOpenProduct: ((Product) -> Unit)? = null,
    onOpenEvent: ((Event) -> Unit)? = null,
    onOpenGroup: ((Long) -> Unit)? = null,
    onPlayMusic: ((Song) -> Unit)? = null,
    isFollowing: Boolean = false,
    onFollowToggle: (() -> Unit)? = null,
    allUsers: List<User> = emptyList()
) {
    val coroutineScope = rememberCoroutineScope()
    val resolvedAuthor = remember(post.author, allUsers, currentUser) {
        val uId = if (post.userId > 0L) post.userId else post.author.id
        val matched = allUsers.find { it.id == uId }
            ?: if (currentUser.id == uId && currentUser.id > 0) currentUser else null
        if (matched != null) {
            val realName = matched.name.ifBlank { matched.username.removePrefix("@") }
            post.author.copy(
                id = matched.id,
                name = if (realName.isNotBlank() && !realName.startsWith("User")) realName else post.author.name.ifBlank { realName },
                username = matched.username.ifBlank { post.author.username },
                profileImageUrl = matched.profileImageUrl.ifBlank { post.author.profileImageUrl },
                isVerified = matched.isVerified || post.author.isVerified
            )
        } else post.author
    }
    var postState by remember(post, resolvedAuthor) {
        mutableStateOf(post.copy(author = resolvedAuthor))
    }
    var localIsFollowing by remember(post.author.id, post.author.isFollowing, isFollowing) {
        mutableStateOf(isFollowing || post.author.isFollowing)
    }
    LaunchedEffect(post.author.isFollowing, isFollowing) {
        localIsFollowing = isFollowing || post.author.isFollowing
    }
    var newCommentText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var replyingToComment by remember { mutableStateOf<Comment?>(null) }
    var replyingToAuthor by remember { mutableStateOf<String?>(null) }
    var expandedCommentIds by remember { mutableStateOf(setOf<Long>()) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var selectedCommentForActions by remember { mutableStateOf<Comment?>(null) }
    val listState = rememberLazyListState()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }

    var liveComments by remember(post.id) {
        mutableStateOf(FeedInteractionApiService.organizeCommentsIntoThreads(post.comments))
    }
    var isLoadingComments by remember(post.id) { mutableStateOf(post.comments.isEmpty()) }
    var hasAutoScrolledToLatest by remember(post.id) { mutableStateOf(false) }

    LaunchedEffect(post.id) {
        val fetched = FeedInteractionApiService.fetchComments(post, currentUser.id)
        if (fetched.isNotEmpty() || post.comments.isEmpty()) {
            val confirmed = fetched.filter { it.id > 0 }
            val pending = liveComments.filter { it.id < 0 }.filterNot { pend ->
                confirmed.any { conf ->
                    conf.author.id == pend.author.id && conf.text.trim() == pend.text.trim()
                }
            }.distinctBy { "${it.author.id}_${it.text.trim()}" }
            val allMerged = (confirmed + pending).distinctBy { it.id }
            liveComments = FeedInteractionApiService.organizeCommentsIntoThreads(allMerged)
        }
        isLoadingComments = false
    }

    LaunchedEffect(post.comments) {
        if (post.comments.isNotEmpty()) {
            val confirmed = post.comments.filter { it.id > 0 }
            val pending = liveComments.filter { it.id < 0 }.filterNot { pend ->
                confirmed.any { conf ->
                    conf.author.id == pend.author.id && conf.text.trim() == pend.text.trim()
                }
            }.distinctBy { "${it.author.id}_${it.text.trim()}" }
            val allMerged = (confirmed + pending).distinctBy { it.id }
            liveComments = FeedInteractionApiService.organizeCommentsIntoThreads(allMerged)
        }
    }

    // Auto-scroll down to the latest discussion when opening the sheet
    LaunchedEffect(liveComments.size, isLoadingComments) {
        if (!isLoadingComments && liveComments.isNotEmpty() && !hasAutoScrolledToLatest) {
            hasAutoScrolledToLatest = true
            delay(150)
            val target = (liveComments.size + 1).coerceAtLeast(0)
            listState.animateScrollToItem(target)
        }
    }

    val quickEmojis = listOf("❤️", "🔥", "😂", "👏", "🙌", "😍", "✨", "💯", "🎉")
    val totalDiscussions = if (liveComments.isNotEmpty()) {
        liveComments.sumOf { 1 + it.replies.size }
    } else postState.commentsCount

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF050B18))
                .testTag("discussions_sheet_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header (Matching Web CommentsSheet: sticky, #0B1120, border-b #1E293B)
                val isVideoPost = postState.postType == "video" || postState.postType == "reel" ||
                    postState.videoUrl != null || postState.mediaType == "video" ||
                    postState.mediaUrls.any { it.endsWith(".mp4") } || postState.mediaUrl?.endsWith(".mp4") == true

                val sheetTitle = when {
                    postState.postType == "event" || postState.event != null -> "Event Discussion"
                    isVideoPost -> "Video Discussion"
                    else -> "Post"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0B1120))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color(0xFFE4E6EB),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Text(
                            text = sheetTitle,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE4E6EB)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "${formatCount(totalDiscussions)} discussions",
                            color = Color(0xFFCBD5E1),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        TextButton(
                            onClick = onDismiss,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "See less",
                                color = Color(0xFF1877F2),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

                // Scrollable content: Full Original Post Preview + List of comments
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // Full Original Post Card (Matching Web: <Post hideCommentPreview={true} ... />)
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(width = 1.dp, color = Color(0xFF1E293B))
                        ) {
                            WebPostCard(
                                post = postState.copy(author = postState.author.copy(isFollowing = localIsFollowing)),
                                currentUser = currentUser,
                                isFollowing = localIsFollowing,
                                onFollowToggle = {
                                    localIsFollowing = !localIsFollowing
                                    postState = postState.copy(author = postState.author.copy(isFollowing = localIsFollowing))
                                    onFollowToggle?.invoke()
                                },
                                onLike = {
                                    val newLiked = !postState.isLiked
                                    val newCount = if (newLiked) postState.likesCount + 1 else (postState.likesCount - 1).coerceAtLeast(0)
                                    postState = postState.copy(isLiked = newLiked, likesCount = newCount)
                                    onLikePost?.invoke()
                                },
                                onReact = { type ->
                                    postState = postState.copy(myReaction = type)
                                    onReactPost?.invoke(type)
                                },
                                onComment = { /* already viewing discussion */ },
                                onShare = { onSharePost?.invoke() },
                                onSave = {
                                    postState = postState.copy(isSaved = !postState.isSaved)
                                    onSavePost?.invoke()
                                },
                                onUserClick = { onProfileClick?.invoke(postState.author) },
                                onOpenGallery = { urls, idx -> onOpenGallery?.invoke(urls, idx) },
                                onOpenProduct = { onOpenProduct?.invoke(it) },
                                onOpenEvent = { onOpenEvent?.invoke(it) },
                                onOpenGroup = { onOpenGroup?.invoke(it) },
                                onPlayMusic = { onPlayMusic?.invoke(it) },
                                allUsers = allUsers,
                                hideCommentPreview = true
                            )
                        }
                    }

                    item {
                        HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Comments list
                    if (isLoadingComments && liveComments.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFF1877F2),
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 3.dp
                                )
                            }
                        }
                    } else if (liveComments.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "No discussions yet",
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFB0B3B8)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Be the first to start a discussion!",
                                        fontSize = 14.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    } else {
                        items(liveComments, key = { it.id }) { comment ->
                            val canDeleteComment = currentUser.role in listOf("admin", "moderator") || comment.author.id == currentUser.id
                            val isExpanded = expandedCommentIds.contains(comment.id)
                            val itemType = when {
                                post.postType == "group_post" || post.groupId != null -> "group_post"
                                post.song != null || post.postType in listOf("song", "music") -> "song"
                                post.product != null || post.postType in listOf("product", "marketplace") -> "product"
                                post.event != null || post.postType == "event" -> "event"
                                post.story != null || post.postType == "story" -> "story"
                                else -> "post"
                            }
                            Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                                CommentItemRow(
                                    comment = comment,
                                    canDelete = canDeleteComment,
                                    isExpanded = isExpanded,
                                    onToggleExpand = {
                                        expandedCommentIds = if (isExpanded) {
                                            expandedCommentIds - comment.id
                                        } else {
                                            expandedCommentIds + comment.id
                                        }
                                    },
                                    onLike = {
                                        liveComments = liveComments.map { c ->
                                            if (c.id == comment.id) {
                                                val newLiked = !c.isLiked
                                                val newCount = if (newLiked) c.likesCount + 1 else (c.likesCount - 1).coerceAtLeast(0)
                                                c.copy(isLiked = newLiked, myReaction = if (newLiked) "like" else null, likesCount = newCount)
                                            } else c
                                        }
                                        coroutineScope.launch {
                                            FeedInteractionApiService.likeComment(itemType, comment.id, currentUser.id)
                                        }
                                        onLikeComment(comment.id)
                                    },
                                    onReact = { reactionType ->
                                        liveComments = liveComments.map { c ->
                                            if (c.id == comment.id) {
                                                val wasLiked = c.isLiked
                                                val newCount = if (!wasLiked) c.likesCount + 1 else c.likesCount
                                                c.copy(isLiked = true, myReaction = reactionType, likesCount = newCount)
                                            } else c
                                        }
                                        coroutineScope.launch {
                                            FeedInteractionApiService.likeComment(itemType, comment.id, currentUser.id)
                                        }
                                        onLikeComment(comment.id)
                                    },
                                    onReply = {
                                        replyingToComment = comment
                                        replyingToAuthor = comment.author.name
                                    },
                                    onLikeReply = { reply ->
                                        liveComments = liveComments.map { p ->
                                            if (p.id == comment.id) {
                                                p.copy(replies = p.replies.map { r ->
                                                    if (r.id == reply.id) {
                                                        val newLiked = !r.isLiked
                                                        val newCount = if (newLiked) r.likesCount + 1 else (r.likesCount - 1).coerceAtLeast(0)
                                                        r.copy(isLiked = newLiked, likesCount = newCount)
                                                    } else r
                                                })
                                            } else p
                                        }
                                        coroutineScope.launch {
                                            FeedInteractionApiService.likeComment(itemType, reply.id, currentUser.id)
                                        }
                                        onLikeComment(reply.id)
                                    },
                                    onReplyToUser = { _, targetName ->
                                        replyingToComment = comment
                                        replyingToAuthor = targetName
                                    },
                                    onToggleHide = {
                                        val newHidden = !comment.isHidden
                                        liveComments = liveComments.map { if (it.id == comment.id) it.copy(isHidden = newHidden) else it }
                                        coroutineScope.launch {
                                            FeedInteractionApiService.hideComment(itemType, comment.id, currentUser.id, post.id, if (newHidden) "hide" else "unhide")
                                        }
                                        onToggleHideComment?.invoke(comment.id, newHidden)
                                    },
                                    onDelete = {
                                        liveComments = liveComments.filter { it.id != comment.id }
                                        coroutineScope.launch {
                                            FeedInteractionApiService.deleteComment(itemType, comment.id, currentUser.id, post.id)
                                        }
                                        onDeleteComment?.invoke(comment.id)
                                    },
                                    onMoreOptions = { selectedCommentForActions = comment },
                                    onUserClick = { onProfileClick?.invoke(comment.author) },
                                    allUsers = allUsers,
                                    currentUser = currentUser
                                )
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Replying to banner if active
                if (replyingToAuthor != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF162137))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Reply,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Replying to ", fontSize = 12.sp, color = Color(0xFF94A3B8))
                            Text(text = replyingToAuthor ?: "", fontSize = 12.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                        }
                        IconButton(
                            onClick = {
                                replyingToComment = null
                                replyingToAuthor = null
                            },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel reply", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Selected image preview if chosen
                if (selectedImageUri != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0B1120))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Box(modifier = Modifier.wrapContentSize()) {
                            AsyncImage(
                                model = selectedImageUri,
                                contentDescription = "Selected comment attachment",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .height(72.dp)
                                    .widthIn(max = 100.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                            )
                            IconButton(
                                onClick = { selectedImageUri = null },
                                modifier = Modifier
                                    .size(22.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(x = 6.dp, y = (-6).dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove attachment",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }

                // Quick Emojis Row
                if (showEmojiPicker) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0B1120))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        quickEmojis.forEach { emoji ->
                            Text(
                                text = emoji,
                                fontSize = 22.sp,
                                modifier = Modifier
                                    .clickable {
                                        newCommentText += emoji
                                    }
                                    .padding(4.dp)
                            )
                        }
                    }
                }

                // Sticky Bottom Input bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0B1120))
                        .border(1.dp, Color(0xFF1E293B))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Image attachment button
                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = "Attach image",
                            tint = if (selectedImageUri != null) Color(0xFF1877F2) else Color(0xFF94A3B8),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Emoji toggle button
                    IconButton(
                        onClick = { showEmojiPicker = !showEmojiPicker },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SentimentSatisfied,
                            contentDescription = "Emoji",
                            tint = if (showEmojiPicker) Color(0xFF1877F2) else Color(0xFF94A3B8),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Input Text Field
                    OutlinedTextField(
                        value = newCommentText,
                        onValueChange = { newCommentText = it },
                        placeholder = {
                            Text(
                                text = if (replyingToAuthor != null) "Reply to $replyingToAuthor..." else "Comment as ${currentUser.name.split(" ").firstOrNull() ?: "you"}...",
                                color = Color(0xFF64748B),
                                fontSize = 14.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A),
                            focusedTextColor = Color(0xFFF8FAFC),
                            unfocusedTextColor = Color(0xFFF8FAFC),
                            focusedBorderColor = Color(0xFF1877F2),
                            unfocusedBorderColor = Color(0xFF1E293B)
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp),
                        singleLine = true
                    )

                    // Send Button
                    val canSend = newCommentText.isNotBlank() || selectedImageUri != null
                    IconButton(
                        onClick = {
                            if (canSend) {
                                val trimmedText = newCommentText.trim()
                                val tempId = -System.currentTimeMillis()
                                if (replyingToComment != null) {
                                    val parentId = replyingToComment!!.id
                                    val targetAuthorName = replyingToAuthor ?: replyingToComment!!.author.name
                                    val optimisticReply = Comment(
                                        id = tempId,
                                        author = currentUser,
                                        text = trimmedText,
                                        timestamp = "Just now",
                                        parentCommentId = parentId,
                                        replyToAuthorName = if (!targetAuthorName.equals(replyingToComment!!.author.name, ignoreCase = true)) targetAuthorName else null,
                                        imageUrl = selectedImageUri?.toString()
                                    )
                                    // Add reply at the bottom of parent comment's replies
                                    liveComments = liveComments.map { p ->
                                        if (p.id == parentId) {
                                            p.copy(replies = (p.replies.filter { it.id != tempId } + optimisticReply).distinctBy { it.id })
                                        } else p
                                    }
                                    expandedCommentIds = expandedCommentIds + parentId
                                    coroutineScope.launch {
                                        val result = FeedInteractionApiService.addComment(
                                            post = post,
                                            userId = currentUser.id,
                                            text = trimmedText,
                                            parentCommentId = parentId,
                                            imageUrl = selectedImageUri?.toString()
                                        )
                                        if (result?.comment != null) {
                                            val serverComment = result.comment
                                            liveComments = liveComments.map { p ->
                                                if (p.id == parentId) {
                                                    val updatedReplies = p.replies.map { r ->
                                                        if (r.id == tempId) serverComment else r
                                                    }.distinctBy { it.id }
                                                    p.copy(replies = updatedReplies)
                                                } else p
                                            }
                                        }
                                    }
                                    // Notify reply listener rather than calling onAddComment
                                    onAddReply?.invoke(parentId, trimmedText)
                                } else {
                                    val optimistic = Comment(
                                        id = tempId,
                                        author = currentUser,
                                        text = trimmedText,
                                        timestamp = "Just now",
                                        imageUrl = selectedImageUri?.toString()
                                    )
                                    // Prevent adding duplicate pending comments with identical text and author
                                    val withoutDuplicates = liveComments.filterNot { it.id < 0 && it.text.trim() == trimmedText && it.author.id == currentUser.id }
                                    liveComments = withoutDuplicates.filter { it.id != tempId } + optimistic
                                    onAddComment(trimmedText)
                                    coroutineScope.launch {
                                        delay(100)
                                        val target = (liveComments.size + 1).coerceAtLeast(0)
                                        listState.animateScrollToItem(target)
                                    }
                                }
                                newCommentText = ""
                                selectedImageUri = null
                                replyingToComment = null
                                replyingToAuthor = null
                                showEmojiPicker = false
                            }
                        },
                        enabled = canSend,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (canSend) Color(0xFF1877F2) else Color(0xFF1E293B))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (canSend) Color.White else Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Comment Action Modal
        selectedCommentForActions?.let { c ->
            val itemType = when {
                post.postType == "group_post" || post.groupId != null -> "group_post"
                post.song != null || post.postType in listOf("song", "music") -> "song"
                post.product != null || post.postType in listOf("product", "marketplace") -> "product"
                post.event != null || post.postType == "event" -> "event"
                post.story != null || post.postType == "story" -> "story"
                else -> "post"
            }
            CommentActionModal(
                isOpen = true,
                onClose = { selectedCommentForActions = null },
                comment = c,
                currentUserId = currentUser.id,
                isAdmin = currentUser.role in listOf("admin", "moderator"),
                onToggleHide = { commentId ->
                    val newHidden = !c.isHidden
                    liveComments = liveComments.map { if (it.id == commentId) it.copy(isHidden = newHidden) else it }
                    coroutineScope.launch {
                        FeedInteractionApiService.hideComment(
                            itemType = itemType,
                            commentId = commentId,
                            userId = currentUser.id,
                            parentId = post.id,
                            action = if (newHidden) "hide" else "unhide"
                        )
                    }
                    onToggleHideComment?.invoke(commentId, newHidden)
                    selectedCommentForActions = null
                },
                onDelete = { commentId ->
                    liveComments = liveComments.filter { it.id != commentId }
                    coroutineScope.launch {
                        FeedInteractionApiService.deleteComment(
                            itemType = itemType,
                            commentId = commentId,
                            userId = currentUser.id,
                            parentId = post.id
                        )
                    }
                    onDeleteComment?.invoke(commentId)
                    selectedCommentForActions = null
                },
                onReply = { comment ->
                    replyingToComment = comment
                    replyingToAuthor = comment.author.name
                }
            )
        }
    }
}

@Composable
private fun CommentItemRow(
    comment: Comment,
    canDelete: Boolean = false,
    isExpanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
    onLike: () -> Unit,
    onReact: (String) -> Unit = {},
    onReply: () -> Unit,
    onLikeReply: (Comment) -> Unit = {},
    onReplyToUser: (Comment, String) -> Unit = { _, _ -> },
    onToggleHide: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onMoreOptions: (() -> Unit)? = null,
    onUserClick: (() -> Unit)? = null,
    allUsers: List<User> = emptyList(),
    currentUser: User? = null
) {
    var showReactionDock by remember { mutableStateOf(false) }
    val currentReaction = getFacebookReaction(comment.myReaction)

    val authorUser = remember(comment.author, allUsers, currentUser) {
        val uId = if (comment.userId > 0L) comment.userId else comment.author.id
        val matched = allUsers.find { it.id == uId }
            ?: if (currentUser != null && currentUser.id == uId && currentUser.id > 0) currentUser else null
        if (matched != null) {
            val realName = matched.name.ifBlank { matched.username.removePrefix("@") }
            comment.author.copy(
                id = matched.id,
                name = if (realName.isNotBlank() && !realName.startsWith("User")) realName else comment.author.name.ifBlank { realName },
                username = matched.username.ifBlank { comment.author.username },
                profileImageUrl = matched.profileImageUrl.ifBlank { comment.author.profileImageUrl },
                isVerified = matched.isVerified || comment.author.isVerified
            )
        } else comment.author
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    if (comment.replies.isNotEmpty()) {
                        val startX = 18.dp.toPx()
                        val startY = 36.dp.toPx()
                        if (size.height > startY) {
                            drawLine(
                                color = Color(0xFF334155),
                                start = androidx.compose.ui.geometry.Offset(startX, startY),
                                end = androidx.compose.ui.geometry.Offset(startX, size.height),
                                strokeWidth = 1.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
        ) {
            FeatheredAvatar(
                name = authorUser.name,
                imageUrl = authorUser.profileImageUrl,
                size = 36.dp,
                username = authorUser.username,
                onClick = onUserClick
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Bubble (#162137)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF162137))
                        .clickable { onMoreOptions?.invoke() }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { onUserClick?.invoke() }
                            ) {
                                Text(
                                    text = authorUser.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 19.sp,
                                    color = Color(0xFFF8FAFC)
                                )
                                if (authorUser.isVerified) {
                                    Spacer(modifier = Modifier.width(5.dp))
                                    VerifiedBadge(size = 17.dp)
                                }
                                if (comment.isHidden) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF334155))
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text("Hidden", fontSize = 10.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

                            if (onMoreOptions != null) {
                                IconButton(
                                    onClick = onMoreOptions,
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreHoriz,
                                        contentDescription = "Options",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = comment.text,
                            fontSize = 20.sp,
                            color = Color(0xFFCBD5E1),
                            lineHeight = 26.sp
                        )
                    }
                }

                // Sub-bar: Time, Like (Tap / Hold for 7 3D Reactions Dock), Reply, Options, Count
                Row(
                    modifier = Modifier
                        .padding(start = 8.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = comment.timestamp,
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )

                    // 7 3D Facebook Reaction dock & button on comment
                    Box {
                        if (showReactionDock) {
                            Box(
                                modifier = Modifier
                                    .offset(y = (-58).dp)
                                    .zIndex(30f)
                            ) {
                                FacebookReactionDock(
                                    onSelectReaction = { type ->
                                        onReact(type)
                                        showReactionDock = false
                                    },
                                    onDismiss = { showReactionDock = false }
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .pointerInput(comment.id, showReactionDock) {
                                    detectTapGestures(
                                        onLongPress = { showReactionDock = true },
                                        onTap = {
                                            if (showReactionDock) showReactionDock = false
                                            else onLike()
                                        }
                                    )
                                }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (currentReaction != null) {
                                    Box(
                                        modifier = Modifier.size(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = currentReaction.gifRes,
                                            contentDescription = currentReaction.label,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = currentReaction.label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = currentReaction.color
                                    )
                                } else {
                                    Text(
                                        text = if (comment.isLiked) "❤️ Liked" else "🤍 Like",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (comment.isLiked) Color(0xFFF43F5E) else Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "Reply",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.clickable(onClick = onReply)
                    )

                    if (onToggleHide != null) {
                        Text(
                            text = if (comment.isHidden) "Unhide" else "Hide",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (comment.isHidden) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                            modifier = Modifier.clickable(onClick = onToggleHide)
                        )
                    }

                    if (canDelete && onDelete != null) {
                        Text(
                            text = "Delete",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFEF4444),
                            modifier = Modifier.clickable(onClick = onDelete)
                        )
                    }

                    if (onMoreOptions != null) {
                        Text(
                            text = "Options",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            modifier = Modifier.clickable(onClick = onMoreOptions)
                        )
                    }

                    if (comment.likesCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            if (currentReaction != null) {
                                Box(modifier = Modifier.size(14.dp), contentAlignment = Alignment.Center) {
                                    AsyncImage(
                                        model = currentReaction.gifRes,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            } else {
                                Text("❤️", fontSize = 11.sp)
                            }
                            Text(
                                text = "${comment.likesCount}",
                                fontSize = 11.sp,
                                color = currentReaction?.color ?: Color(0xFFF43F5E),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Facebook-Style Compact Threaded Replies
        if (comment.replies.isNotEmpty()) {
            CompactThreadedReplies(
                parentComment = comment,
                isExpanded = isExpanded,
                onToggleExpand = onToggleExpand,
                onLikeReply = onLikeReply,
                onReplyToUser = onReplyToUser,
                allUsers = allUsers,
                currentUser = currentUser
            )
        }
    }
}

@Composable
private fun CompactThreadedReplies(
    parentComment: Comment,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onLikeReply: (Comment) -> Unit,
    onReplyToUser: (Comment, String) -> Unit,
    allUsers: List<User> = emptyList(),
    currentUser: User? = null
) {
    val totalReplies = parentComment.replies.size
    if (totalReplies == 0) return

    Column(modifier = Modifier.fillMaxWidth()) {
        if (!isExpanded && totalReplies > 1) {
            // View previous replies row
            val hiddenCount = totalReplies - 1
            val label = if (hiddenCount == 1) "View previous reply" else "View previous $hiddenCount replies"

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val startX = 18.dp.toPx()
                        val curveEndX = 38.dp.toPx()
                        val targetY = 16.dp.toPx()
                        val stroke = 1.5.dp.toPx()

                        // Vertical continuation line to next reply below
                        drawLine(
                            color = Color(0xFF334155),
                            start = androidx.compose.ui.geometry.Offset(startX, 0f),
                            end = androidx.compose.ui.geometry.Offset(startX, size.height),
                            strokeWidth = stroke
                        )

                        // Curved branch to the right towards the text
                        val path = Path().apply {
                            moveTo(startX, 0f)
                            quadraticBezierTo(
                                startX, targetY,
                                curveEndX, targetY
                            )
                        }
                        drawPath(path, color = Color(0xFF334155), style = Stroke(width = stroke, cap = StrokeCap.Round))
                    }
                    .padding(start = 42.dp, top = 6.dp, bottom = 4.dp)
                    .clickable(onClick = onToggleExpand),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF8FAFC)
                )
            }

            // Latest reply shown underneath
            val latestReply = parentComment.replies.last()
            CompactReplyItem(
                reply = latestReply,
                parentComment = parentComment,
                isLast = true,
                isExpanded = false,
                onLike = { onLikeReply(latestReply) },
                onReply = { onReplyToUser(latestReply, latestReply.author.name) },
                allUsers = allUsers,
                currentUser = currentUser
            )
        } else {
            // Expanded: display all replies
            parentComment.replies.forEachIndexed { index, reply ->
                val isLastReply = (index == parentComment.replies.lastIndex)
                val isLastRow = isLastReply && (!isExpanded || totalReplies <= 1)

                CompactReplyItem(
                    reply = reply,
                    parentComment = parentComment,
                    isLast = isLastRow,
                    isExpanded = isExpanded,
                    onLike = { onLikeReply(reply) },
                    onReply = { onReplyToUser(reply, reply.author.name) },
                    allUsers = allUsers,
                    currentUser = currentUser
                )
            }

            if (isExpanded && totalReplies > 1) {
                // "Hide replies" button at the bottom
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            val startX = 18.dp.toPx()
                            val curveEndX = 38.dp.toPx()
                            val targetY = 14.dp.toPx()
                            val stroke = 1.5.dp.toPx()

                            // Terminal branch for "Hide replies"
                            val path = Path().apply {
                                moveTo(startX, 0f)
                                quadraticBezierTo(
                                    startX, targetY,
                                    curveEndX, targetY
                                )
                            }
                            drawPath(path, color = Color(0xFF334155), style = Stroke(width = stroke, cap = StrokeCap.Round))
                        }
                        .padding(start = 42.dp, top = 4.dp, bottom = 6.dp)
                        .clickable(onClick = onToggleExpand),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Hide replies",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactReplyItem(
    reply: Comment,
    parentComment: Comment,
    isLast: Boolean,
    isExpanded: Boolean,
    onLike: () -> Unit,
    onReply: () -> Unit,
    allUsers: List<User> = emptyList(),
    currentUser: User? = null
) {
    var isTextExpanded by remember { mutableStateOf(false) }
    var hasOverflow by remember { mutableStateOf(false) }

    val replyAuthorUser = remember(reply.author, allUsers, currentUser) {
        val uId = if (reply.userId > 0L) reply.userId else reply.author.id
        val matched = allUsers.find { it.id == uId }
            ?: if (currentUser != null && currentUser.id == uId && currentUser.id > 0) currentUser else null
        if (matched != null) {
            val realName = matched.name.ifBlank { matched.username.removePrefix("@") }
            reply.author.copy(
                id = matched.id,
                name = if (realName.isNotBlank() && !realName.startsWith("User")) realName else reply.author.name.ifBlank { realName },
                username = matched.username.ifBlank { reply.author.username },
                profileImageUrl = matched.profileImageUrl.ifBlank { reply.author.profileImageUrl },
                isVerified = matched.isVerified || reply.author.isVerified
            )
        } else reply.author
    }

    // Replying format:
    // Bold replyer's name, followed by the name of the person being replied to in normal text.
    // If the reply is long, truncate it with ellipsis and provide a 'v' chevron to expand.
    val targetUser = reply.replyToAuthorName ?: if (!reply.author.name.equals(parentComment.author.name, ignoreCase = true)) {
        parentComment.author.name
    } else null

    val cleanText = if (targetUser != null && reply.text.startsWith("@$targetUser", ignoreCase = true)) {
        reply.text.removePrefix("@$targetUser").trimStart()
    } else {
        reply.text
    }

    val isLongReply = cleanText.length > 50 || cleanText.contains("\n") || hasOverflow

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val startX = 18.dp.toPx()
                val curveEndX = 38.dp.toPx()
                val targetY = 16.dp.toPx()
                val stroke = 1.5.dp.toPx()

                if (!isLast) {
                    drawLine(
                        color = Color(0xFF334155),
                        start = androidx.compose.ui.geometry.Offset(startX, 0f),
                        end = androidx.compose.ui.geometry.Offset(startX, size.height),
                        strokeWidth = stroke
                    )
                } else {
                    drawLine(
                        color = Color(0xFF334155),
                        start = androidx.compose.ui.geometry.Offset(startX, 0f),
                        end = androidx.compose.ui.geometry.Offset(startX, targetY - 6.dp.toPx()),
                        strokeWidth = stroke
                    )
                }

                val path = Path().apply {
                    moveTo(startX, if (isLast) targetY - 6.dp.toPx() else 0f)
                    quadraticBezierTo(
                        startX, targetY,
                        curveEndX, targetY
                    )
                }
                drawPath(path, color = Color(0xFF334155), style = Stroke(width = stroke, cap = StrokeCap.Round))
            }
            .padding(start = 42.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Replyer's profile picture (24.dp)
        FeatheredAvatar(
            name = replyAuthorUser.name,
            imageUrl = replyAuthorUser.profileImageUrl,
            size = 24.dp,
            username = replyAuthorUser.username
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Compact Bubble
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF162137))
                    .clickable {
                        if (isLongReply) {
                            isTextExpanded = !isTextExpanded
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Column {
                    // Author Row: Author name + VerifiedBadge (immediately next to name) + optional target user
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = replyAuthorUser.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = Color(0xFFF8FAFC)
                        )
                        // Verification tick appears right next to the user name
                        if (replyAuthorUser.isVerified) {
                            VerifiedBadge(size = 17.dp)
                        }
                        if (!targetUser.isNullOrBlank()) {
                            Text(
                                text = "replied to",
                                fontSize = 13.sp,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                text = targetUser,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = Color(0xFF93C5FD)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Reply Text Row: 20sp text size matching comments with 'v' chevron to expand
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = cleanText,
                            fontSize = 20.sp,
                            color = Color(0xFFCBD5E1),
                            lineHeight = 26.sp,
                            maxLines = if (isTextExpanded) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { textLayoutResult ->
                                if (!isTextExpanded && textLayoutResult.hasVisualOverflow) {
                                    hasOverflow = true
                                }
                            },
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (isLongReply) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (isTextExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isTextExpanded) "Collapse reply" else "Expand reply",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { isTextExpanded = !isTextExpanded }
                            )
                        }
                    }
                }
            }

            // Sub-actions: Time, Like, Reply, Count
            Row(
                modifier = Modifier.padding(start = 6.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = reply.timestamp,
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )

                Text(
                    text = if (reply.isLiked) "❤️ Liked" else "Like",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (reply.isLiked) Color(0xFFF43F5E) else Color(0xFF94A3B8),
                    modifier = Modifier.clickable(onClick = onLike)
                )

                Text(
                    text = "Reply",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.clickable(onClick = onReply)
                )

                if (reply.likesCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text("❤️", fontSize = 10.sp)
                        Text(
                            text = "${reply.likesCount}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF43F5E)
                        )
                    }
                }
            }
        }
    }
}

