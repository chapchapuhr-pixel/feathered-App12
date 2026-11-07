package com.unera.social.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.unera.social.data.PostsApiService
import com.unera.social.model.*
import com.unera.social.ui.components.*

// Feathered Design Tokens
private val BgDark = Color(0xFF050B18)
private val CardBgDark = Color(0xFF0B1120)
private val BorderDark = Color(0xFF1E293B)
private val TextWhite = Color(0xFFF8FAFC)
private val TextMuted = Color(0xFF94A3B8)
private val BrandBlue = Color(0xFF1877F2)
private val BrandBlueHover = Color(0xFF166FE5)
private val GoingGreen = Color(0xFF45BD62)
private val InterestedGold = Color(0xFFF7B928)
private val LocationRed = Color(0xFFF02849)

@Composable
fun EventsScreen(
    events: List<Event>,
    posts: List<Post> = emptyList(),
    currentUser: User? = null,
    isGuest: Boolean = false,
    onRequestAuth: () -> Unit = {},
    onToggleInterested: (Long) -> Unit = {},
    onToggleGoing: (Long) -> Unit = {},
    onRsvpEvent: ((Long, String, String) -> Unit)? = null,
    onLikePost: ((Long) -> Unit)? = null,
    onReactPost: ((Long, String) -> Unit)? = null,
    onAddComment: ((Long, String) -> Unit)? = null,
    onAddReply: ((Long, Long, String) -> Unit)? = null,
    onSharePost: ((Long) -> Unit)? = null,
    onOpenCreateEvent: () -> Unit,
    onProfileClick: ((Long) -> Unit)? = null,
    allUsers: List<User> = emptyList(),
    modifier: Modifier = Modifier
) {
    val viewerCountry = remember(currentUser?.location) { resolveUserCountry(currentUser?.location) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var selectedSort by remember { mutableStateOf("Date") } // "Date" or "Popular"
    var countryFilterMode by remember { mutableStateOf("country") } // "country" (Across user's country) or "worldwide"
    var previewEvent by remember { mutableStateOf<Event?>(null) }
    var activeDiscussionPost by remember { mutableStateOf<Post?>(null) }
    var activeReactionsPost by remember { mutableStateOf<Post?>(null) }
    var activeSharePost by remember { mutableStateOf<Post?>(null) }

    val filterOptions = listOf("All", "Upcoming", "Going", "Interested", "Tech & Summits", "Music & Beach")

    val filteredAndSortedEvents = remember(events, selectedFilter, selectedSort, searchQuery, countryFilterMode, viewerCountry) {
        var list = events

        // Filter by country location:
        // If "country", show events in user's country, or events where visibility is worldwide (ignoring country restriction)
        if (countryFilterMode == "country") {
            list = list.filter { ev ->
                ev.visibility == "worldwide" ||
                ev.groupId != null ||
                (currentUser != null && ev.organizer.id == currentUser.id) ||
                ev.location.contains(viewerCountry.name, ignoreCase = true) ||
                ev.organizer.location.contains(viewerCountry.name, ignoreCase = true) ||
                resolveUserCountry(ev.location).code == viewerCountry.code ||
                resolveUserCountry(ev.organizer.location).code == viewerCountry.code
            }
        }

        // Filter by category / tab
        list = when (selectedFilter) {
            "Going" -> list.filter { it.isGoing }
            "Interested" -> list.filter { it.isInterested }
            "Upcoming" -> list.filter { !it.date.lowercase().contains("past") }
            "Tech & Summits" -> list.filter {
                it.title.contains("Tech", ignoreCase = true) ||
                it.title.contains("Summit", ignoreCase = true) ||
                it.title.contains("Masterclass", ignoreCase = true)
            }
            "Music & Beach" -> list.filter {
                it.title.contains("Acoustic", ignoreCase = true) ||
                it.title.contains("Beach", ignoreCase = true) ||
                it.title.contains("Zanzibar", ignoreCase = true)
            }
            else -> list
        }

        // Filter by search query
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter {
                it.title.lowercase().contains(q) ||
                it.location.lowercase().contains(q) ||
                it.description.lowercase().contains(q)
            }
        }

        // Sort
        when (selectedSort) {
            "Popular" -> list.sortedByDescending { it.goingCount + it.interestedCount }
            else -> list
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .testTag("events_screen")
    ) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 96.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Top Header Bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Events",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = TextWhite
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Live Discovery Pulse Indicator
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(GoingGreen)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Live",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoingGreen
                            )
                        }
                        Text(
                            text = "Discover conferences, sessions & meetups",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }

                    Button(
                        onClick = onOpenCreateEvent,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("create_event_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create Event",
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Create",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }
                }
            }

            // 2. Search Input Field
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "Search events by title, venue, or city...",
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardBgDark,
                            unfocusedContainerColor = CardBgDark,
                            focusedBorderColor = BrandBlue,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            cursorColor = BrandBlue
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("events_search_bar")
                    )
                }
            }

            // 3. Country Location Scope & Category Filter Chips
            item {
                Column(modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)) {
                    // Country Location Scope: "Across [Country]" vs "Worldwide"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isCountrySelected = countryFilterMode == "country"
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCountrySelected) BrandBlue.copy(alpha = 0.2f) else CardBgDark,
                            border = BorderStroke(1.dp, if (isCountrySelected) BrandBlue else BorderDark),
                            modifier = Modifier
                                .clickable { countryFilterMode = "country" }
                                .testTag("events_country_scope_btn")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(text = viewerCountry.flag, fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Across ${viewerCountry.name}",
                                    fontSize = 12.sp,
                                    fontWeight = if (isCountrySelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isCountrySelected) Color(0xFF38BDF8) else TextMuted
                                )
                            }
                        }

                        val isWorldwideSelected = countryFilterMode == "worldwide"
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isWorldwideSelected) BrandBlue.copy(alpha = 0.2f) else CardBgDark,
                            border = BorderStroke(1.dp, if (isWorldwideSelected) BrandBlue else BorderDark),
                            modifier = Modifier
                                .clickable { countryFilterMode = "worldwide" }
                                .testTag("events_worldwide_scope_btn")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(text = "🌍", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Worldwide",
                                    fontSize = 12.sp,
                                    fontWeight = if (isWorldwideSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isWorldwideSelected) Color(0xFF38BDF8) else TextMuted
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filterOptions) { filter ->
                            val isSelected = filter == selectedFilter
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) BrandBlue else CardBgDark,
                                border = if (isSelected) null else BorderStroke(1.dp, BorderDark),
                                modifier = Modifier
                                    .clickable { selectedFilter = filter }
                                    .testTag("event_filter_$filter")
                            ) {
                                Text(
                                    text = filter,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else TextMuted,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    // Sort row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${filteredAndSortedEvents.size} events found",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextMuted
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Sort:",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                            listOf("Date", "Popular").forEach { sortOption ->
                                val isSortActive = selectedSort == sortOption
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSortActive) BrandBlue.copy(alpha = 0.2f) else Color.Transparent,
                                    modifier = Modifier.clickable { selectedSort = sortOption }
                                ) {
                                    Text(
                                        text = sortOption,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSortActive) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSortActive) BrandBlue else TextMuted,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Events Feed List
            if (filteredAndSortedEvents.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(CardBgDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventBusy,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No events found",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Try different keywords or filters, or be the first to host an event!",
                            fontSize = 13.sp,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = onOpenCreateEvent,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create Event")
                        }
                    }
                }
            } else {
                items(filteredAndSortedEvents, key = { it.id }) { event ->
                    val correspondingPost = posts.find { it.event?.id == event.id || (it.postType == "event" && it.id == event.id) }
                        ?: event.toPost()

                    EventFeedItemCard(
                        event = event,
                        post = correspondingPost,
                        currentUser = currentUser,
                        isGuest = isGuest,
                        onRequestAuth = onRequestAuth,
                        onRsvp = { id, target, prev ->
                            onRsvpEvent?.invoke(id, target, prev) ?: run {
                                if (target == "going") onToggleGoing(id) else if (target == "interested") onToggleInterested(id)
                            }
                        },
                        onLike = {
                            if (isGuest) onRequestAuth() else onLikePost?.invoke(correspondingPost.id)
                        },
                        onReact = { reaction ->
                            if (isGuest) onRequestAuth() else onReactPost?.invoke(correspondingPost.id, reaction)
                        },
                        onDiscuss = {
                            activeDiscussionPost = correspondingPost
                        },
                        onShare = {
                            if (isGuest) onRequestAuth() else onSharePost?.invoke(correspondingPost.id)
                        },
                        onOpenReactions = {
                            if (isGuest) onRequestAuth() else activeReactionsPost = correspondingPost
                        },
                        onCardClick = { previewEvent = event },
                        onProfileClick = onProfileClick,
                        allUsers = allUsers
                    )

                    // 8dp gap separator between event cards matching Feed
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(Color(0xFF050B18))
                    )
                }
            }
        }

        // 5. Full Detail Preview Modal
        if (previewEvent != null) {
            EventDetailModal(
                event = previewEvent!!,
                onDismiss = { previewEvent = null },
                onToggleInterested = {
                    onRsvpEvent?.invoke(previewEvent!!.id, if (previewEvent!!.isInterested) "" else "interested", previewEvent!!.userRsvpStatus)
                        ?: onToggleInterested(previewEvent!!.id)
                    previewEvent = previewEvent!!.copy(
                        isInterested = !previewEvent!!.isInterested,
                        interestedCount = if (!previewEvent!!.isInterested) previewEvent!!.interestedCount + 1 else (previewEvent!!.interestedCount - 1).coerceAtLeast(0)
                    )
                },
                onToggleGoing = {
                    onRsvpEvent?.invoke(previewEvent!!.id, if (previewEvent!!.isGoing) "" else "going", previewEvent!!.userRsvpStatus)
                        ?: onToggleGoing(previewEvent!!.id)
                    previewEvent = previewEvent!!.copy(
                        isGoing = !previewEvent!!.isGoing,
                        goingCount = if (!previewEvent!!.isGoing) previewEvent!!.goingCount + 1 else (previewEvent!!.goingCount - 1).coerceAtLeast(0)
                    )
                },
                onProfileClick = onProfileClick
            )
        }

        // Discussions & Comments Sheet Modal
        activeDiscussionPost?.let { curPost ->
            val livePost = posts.find { it.id == curPost.id } ?: curPost
            DiscussionsSheetDialog(
                post = livePost,
                currentUser = currentUser ?: User(id = 0, name = "Guest", username = "@guest"),
                isFollowing = livePost.author.isFollowing,
                allUsers = allUsers,
                onFollowToggle = {
                    if (isGuest) {
                        activeDiscussionPost = null
                        onRequestAuth()
                    }
                },
                onDismiss = { activeDiscussionPost = null },
                onAddComment = { commentText ->
                    if (isGuest) {
                        activeDiscussionPost = null
                        onRequestAuth()
                    } else {
                        onAddComment?.invoke(livePost.id, commentText)
                    }
                },
                onAddReply = { parentId, replyText ->
                    if (isGuest) {
                        activeDiscussionPost = null
                        onRequestAuth()
                    } else {
                        onAddReply?.invoke(livePost.id, parentId, replyText)
                    }
                },
                onLikeComment = { _ -> },
                onProfileClick = { user ->
                    activeDiscussionPost = null
                    onProfileClick?.invoke(user.id)
                }
            )
        }

        // Reactions Sheet Modal
        activeReactionsPost?.let { currentPost ->
            ReactionsSheetDialog(
                post = currentPost,
                allUsers = allUsers,
                onDismiss = { activeReactionsPost = null },
                onUserClick = { user ->
                    activeReactionsPost = null
                    onProfileClick?.invoke(user.id)
                }
            )
        }

        // Share Sheet Modal
        activeSharePost?.let { currentPost ->
            ShareBottomSheetDialog(
                post = currentPost,
                onDismiss = { activeSharePost = null },
                onShareToFeed = {
                    activeSharePost = null
                    if (isGuest) onRequestAuth() else onSharePost?.invoke(currentPost.id)
                },
                onShareToGroup = {
                    activeSharePost = null
                    if (isGuest) onRequestAuth() else onSharePost?.invoke(currentPost.id)
                },
                onSendMessage = {
                    activeSharePost = null
                    if (isGuest) onRequestAuth() else onSharePost?.invoke(currentPost.id)
                },
                onCopyLink = {
                    activeSharePost = null
                    if (isGuest) onRequestAuth() else onSharePost?.invoke(currentPost.id)
                }
            )
        }
    }
}

/**
 * EventFeedItemCard:
 * Exact event post card layout matching FeedScreen WebPostCard:
 * Full width (0 left, 0 right), organizer header, description,
 * EventPostCard with exact web RSVP buttons, engagement stats,
 * and action bar with FacebookReactionButton, DiscussButton, and ShareButton.
 */
@Composable
fun EventFeedItemCard(
    event: Event,
    post: Post,
    currentUser: User?,
    isGuest: Boolean,
    onRequestAuth: () -> Unit,
    onRsvp: (Long, String, String) -> Unit,
    onLike: () -> Unit,
    onReact: (String) -> Unit,
    onDiscuss: () -> Unit,
    onShare: () -> Unit,
    onOpenReactions: () -> Unit,
    onCardClick: () -> Unit,
    onProfileClick: ((Long) -> Unit)?,
    allUsers: List<User> = emptyList()
) {
    var showReactionDock by remember { mutableStateOf(false) }
    var isExpandedText by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("event_post_card_${event.id}"),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Post Header matching Feeds
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val realOrganizerName = remember(event.organizer, allUsers) {
                    val org = event.organizer
                    if (org.name.isNotBlank() && !org.name.equals("Organizer", ignoreCase = true) && !org.name.startsWith("User")) {
                        org.name
                    } else {
                        val matched = allUsers.find { it.id == org.id }
                            ?: allUsers.firstOrNull { it.name.isNotBlank() && !it.name.equals("Organizer", ignoreCase = true) }
                        matched?.name?.takeIf { it.isNotBlank() && !it.equals("Organizer", ignoreCase = true) }
                            ?: org.username.takeIf { it.isNotBlank() && !it.startsWith("@user") }?.removePrefix("@")
                            ?: "Alex Morgan"
                    }
                }
                val realOrganizerAvatar = remember(event.organizer, allUsers, realOrganizerName) {
                    if (event.organizer.profileImageUrl.isNotBlank() && !event.organizer.profileImageUrl.contains("name=Organizer")) {
                        event.organizer.profileImageUrl
                    } else {
                        val matched = allUsers.find { it.id == event.organizer.id }
                        matched?.profileImageUrl?.takeIf { it.isNotBlank() }
                            ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=800"
                    }
                }

                AsyncImage(
                    model = realOrganizerAvatar,
                    contentDescription = realOrganizerName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .border(1.dp, Color(0xFF1E293B), CircleShape)
                        .clickable { onProfileClick?.invoke(event.organizer.id) }
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = realOrganizerName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 21.sp,
                            color = Color(0xFFF8FAFC),
                            modifier = Modifier.clickable { onProfileClick?.invoke(event.organizer.id) }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        VerifiedBadge(size = 21.dp)
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Below name: REMOVED event date and time! Displays relative post timestamp and public globe icon like feed posts
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val relativeTime = PostsApiService.formatRelativeTime(post.timestamp)
                        val displayTime = if (relativeTime.isBlank() || relativeTime.equals("Sponsored", ignoreCase = true)) "Just now" else relativeTime
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
                }
            }

            // 2. Post Description (if any)
            if (event.description.isNotBlank() && !event.description.equals(event.title, ignoreCase = true)) {
                val words = remember(event.description) {
                    event.description.split(Regex("\\s+")).filter { it.isNotBlank() }
                }
                val hasMoreThan8Words = words.size > 8
                val truncatedContent = remember(event.description, words) {
                    if (words.size > 8) words.take(8).joinToString(" ") + "…" else event.description
                }

                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Text(
                        text = if (!hasMoreThan8Words || isExpandedText) event.description else truncatedContent,
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
            }

            // 3. Exact EventPostCard body
            EventPostCard(
                event = event,
                currentUser = currentUser,
                onRequestAuth = onRequestAuth,
                onRsvp = onRsvp,
                onEventClick = onCardClick
            )

            // 4. Engagement Stats Row (matching Web & Feed)
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
                        modifier = Modifier.clickable(onClick = onDiscuss)
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

            // 5. Action Buttons Row: React, Discuss, Share
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
                    FacebookReactionButton(
                        isLiked = post.isLiked,
                        myReaction = post.myReaction,
                        likesCount = post.likesCount,
                        onLike = onLike,
                        onReact = onReact,
                        showReactionDock = showReactionDock,
                        onToggleReactionDock = { showReactionDock = it },
                        modifier = Modifier.testTag("like_event_btn_${event.id}")
                    )

                    DiscussButton(
                        commentCount = post.commentsCount,
                        onClick = onDiscuss,
                        testTag = "discuss_event_btn_${event.id}"
                    )

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onShare)
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                            .testTag("share_event_btn_${event.id}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Share",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier
                                .size(19.dp)
                                .graphicsLayer(rotationZ = -35f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Share",
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Backward compatibility alias for EventCard
 */
@Composable
fun EventCard(
    event: Event,
    onCardClick: () -> Unit,
    onToggleInterested: () -> Unit,
    onToggleGoing: () -> Unit,
    onProfileClick: ((Long) -> Unit)? = null
) {
    EventFeedItemCard(
        event = event,
        post = event.toPost(),
        currentUser = null,
        isGuest = false,
        onRequestAuth = {},
        onRsvp = { _, target, _ ->
            if (target == "going") onToggleGoing() else if (target == "interested") onToggleInterested()
        },
        onLike = {},
        onReact = {},
        onDiscuss = onCardClick,
        onShare = {},
        onOpenReactions = {},
        onCardClick = onCardClick,
        onProfileClick = onProfileClick
    )
}

/**
 * Full Event Detail View Modal matching web Rd isPreview.
 */
@Composable
fun EventDetailModal(
    event: Event,
    onDismiss: () -> Unit,
    onToggleInterested: () -> Unit,
    onToggleGoing: () -> Unit,
    onProfileClick: ((Long) -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDark)
                .testTag("event_detail_modal")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Top Cover Box (40vh)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    AsyncImage(
                        model = event.imageUrl.ifBlank {
                            "https://images.unsplash.com/photo-1540575467063-178a50c2df87?w=800"
                        },
                        contentDescription = event.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.5f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.85f)
                                    )
                                )
                            )
                    )

                    // Close button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(12.dp)
                            .align(Alignment.TopEnd)
                            .size(38.dp)
                            .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Modal Details Container
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .offset(y = (-20).dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBgDark),
                        border = BorderStroke(1.dp, BorderDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            // Title
                            Text(
                                text = event.title,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = TextWhite
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Date & Time info box
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFF1E293B), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        tint = BrandBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = event.date,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = TextWhite
                                    )
                                    Text(
                                        text = if (event.time.isNotBlank()) event.time else "Scheduled time",
                                        fontSize = 12.sp,
                                        color = TextMuted
                                    )
                                }
                            }

                            // Location info box
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFF1E293B), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Place,
                                        contentDescription = null,
                                        tint = LocationRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Location",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = TextWhite
                                    )
                                    Text(
                                        text = event.location,
                                        fontSize = 12.sp,
                                        color = TextMuted
                                    )
                                }
                            }

                            // Host info box
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(vertical = 6.dp)
                                    .clickable { onProfileClick?.invoke(event.organizer.id) }
                            ) {
                                AsyncImage(
                                    model = event.organizer.profileImageUrl.ifBlank {
                                        "https://ui-avatars.com/api/?name=${event.organizer.name}&background=1877F2&color=fff&bold=true"
                                    },
                                    contentDescription = "Organizer",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1E293B))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Hosted by",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = TextWhite
                                    )
                                    Text(
                                        text = event.organizer.name,
                                        fontSize = 12.sp,
                                        color = BrandBlue,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = BorderDark, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(10.dp))

                            // Social Attendees counters
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Surface(
                                    color = GoingGreen.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = "${event.goingCount}",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp,
                                            color = GoingGreen
                                        )
                                        Text(
                                            text = "Attending",
                                            fontSize = 11.sp,
                                            color = TextMuted
                                        )
                                    }
                                }

                                Surface(
                                    color = InterestedGold.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = "${event.interestedCount}",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp,
                                            color = InterestedGold
                                        )
                                        Text(
                                            text = "Interested",
                                            fontSize = 11.sp,
                                            color = TextMuted
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Action RSVP buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = onToggleGoing,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (event.isGoing) GoingGreen else BrandBlue
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = if (event.isGoing) "✓ Going" else "Going",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }

                                Button(
                                    onClick = onToggleInterested,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (event.isInterested) InterestedGold else Color(0xFF1E293B)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = if (event.isInterested) "✓ Interested" else "Interested",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (event.isInterested) Color.Black else TextWhite
                                    )
                                }
                            }

                            if (event.description.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "About this event",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextWhite
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = event.description,
                                    fontSize = 13.sp,
                                    color = TextMuted,
                                    lineHeight = 19.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
