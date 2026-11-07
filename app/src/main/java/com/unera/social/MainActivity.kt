package com.unera.social

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unera.social.data.MockData
import com.unera.social.model.*
import com.unera.social.ui.FeatheredViewModel
import com.unera.social.ui.components.FeatheredBottomNav
import com.unera.social.ui.components.FeatheredTopBar
import com.unera.social.ui.components.GlobalAudioPlayer
import com.unera.social.ui.components.MenuOverlay
import com.unera.social.ui.components.MusicCommentsSheet
import com.unera.social.ui.components.MusicShareSheet
import com.unera.social.ui.components.ProvideMarketplaceContext
import com.unera.social.ui.components.LocalMarketplaceContext
import com.unera.social.ui.components.ProvideScrollRestoration
import com.unera.social.ui.components.ScrollRestoration
import com.unera.social.ui.components.ScrollRestorationController
import com.unera.social.ui.components.ImageViewer
import com.unera.social.ui.components.ProfessionalLoader
import com.unera.social.ui.components.Spinner
import com.unera.social.ui.components.DiscussionsSheetDialog
import com.unera.social.ui.components.CallScreen
import com.unera.social.ui.components.CallMode
import com.unera.social.ui.components.CallPhase
import com.unera.social.ui.components.FacebookSplashScreen
import com.unera.social.ui.screens.*
import com.unera.social.ui.theme.FeatheredTheme
import android.net.Uri
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("FeatheredApp", "Uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
        enableEdgeToEdge()
        setContent {
            FeatheredTheme {
                FeatheredApp()
            }
        }
    }
}

@Composable
fun FeatheredApp(
    viewModel: FeatheredViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollRestorationController = remember { ScrollRestorationController() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var showSplash by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        ProvideScrollRestoration(controller = scrollRestorationController) {
        ScrollRestoration(
            currentRoute = uiState.currentScreen,
            controller = scrollRestorationController
        )

        val unreadNotificationsCount = uiState.notifications.count { !it.isRead }
        val unreadMessagesCount = uiState.conversations.sumOf { it.unreadCount }

    // System Back Handler
    BackHandler(enabled = uiState.currentScreen != Screen.FEED || uiState.showNotificationsOverlay || uiState.showMenuOverlay || uiState.showSearchDialog || uiState.fullScreenImageViewerUrl != null) {
        if (uiState.fullScreenImageViewerUrl != null) {
            viewModel.closeImageViewer()
        } else if (uiState.showMenuOverlay) {
            viewModel.setShowMenuOverlay(false)
        } else if (uiState.showNotificationsOverlay) {
            viewModel.setShowNotificationsOverlay(false)
        } else if (uiState.showSearchDialog) {
            viewModel.setShowSearchDialog(false)
            viewModel.setSearchQuery("")
        } else if (uiState.activeConversationId != null) {
            viewModel.closeConversation()
        } else {
            viewModel.setScreen(Screen.FEED)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            val hasCustomTopBar = uiState.showSearchDialog || uiState.currentScreen in listOf(
                Screen.SEARCH,
                Screen.TERMS,
                Screen.MARKETPLACE,
                Screen.DISCOVER_PEOPLE,
                Screen.BIRTHDAYS,
                Screen.MEMORIES,
                Screen.SAVED_POSTS,
                Screen.STORY_FEED,
                Screen.REELS,
                Screen.MUSIC,
                Screen.SETTINGS,
                Screen.ADS_MANAGER
            )
            if (!uiState.showNotificationsOverlay && uiState.activeConversationId == null && !hasCustomTopBar) {
                FeatheredTopBar(
                    currentUser = uiState.currentUser,
                    isGuest = uiState.isGuest || !uiState.isLoggedIn,
                    unreadNotificationsCount = unreadNotificationsCount,
                    marketplaceBadgeCount = uiState.products.size.coerceAtMost(99),
                    isMarketplaceActive = uiState.currentScreen == Screen.MARKETPLACE,
                    onHomeClick = { viewModel.setScreen(Screen.FEED) },
                    onMarketplaceClick = { viewModel.setScreen(Screen.MARKETPLACE) },
                    onSearchClick = { viewModel.setShowSearchDialog(true) },
                    onNotificationsClick = {
                        if (uiState.isGuest || !uiState.isLoggedIn) {
                            viewModel.openAuth("login")
                        } else {
                            viewModel.setShowNotificationsOverlay(true)
                        }
                    },
                    onProfileClick = {
                        if (uiState.isGuest || !uiState.isLoggedIn) {
                            viewModel.openAuth("login")
                        } else {
                            viewModel.setScreen(Screen.PROFILE)
                        }
                    },
                    onLoginClick = { viewModel.openAuth("login") },
                    onMenuClick = { viewModel.setShowMenuOverlay(true) }
                )
            }
        },
        bottomBar = {
            if (!uiState.showNotificationsOverlay && !uiState.showSearchDialog && uiState.currentScreen != Screen.SEARCH && uiState.currentScreen != Screen.TERMS && uiState.activeConversationId == null && uiState.currentScreen != Screen.REELS) {
                Column {
                    if (uiState.currentAudioTrack != null) {
                        GlobalAudioPlayer(
                            currentTrack = uiState.currentAudioTrack,
                            isPlaying = uiState.isPlayingAudio,
                            positionSeconds = uiState.audioPositionSeconds,
                            durationSeconds = uiState.audioDurationSeconds,
                            isExpanded = uiState.isPlayerExpanded,
                            isLiked = "music:${uiState.currentAudioTrack?.id}" in uiState.likedTrackKeys,
                            isShuffle = uiState.isShuffleEnabled,
                            isRepeat = uiState.isRepeatEnabled,
                            volume = uiState.audioVolume,
                            isDownloading = uiState.downloadingTrackId == uiState.currentAudioTrack?.id,
                            downloadProgress = uiState.downloadProgress,
                            downloadToast = uiState.downloadToast,
                            uploaderProfile = uiState.users.find { it.id == uiState.currentAudioTrack?.uploaderId },
                            totalPlays = uiState.trackPlays["music:${uiState.currentAudioTrack?.id}"] ?: 0L,
                            onTogglePlay = { viewModel.togglePlayAudio() },
                            onNext = { viewModel.nextTrack() },
                            onPrevious = { viewModel.prevTrack() },
                            onSeek = { viewModel.seekAudio(it) },
                            onStop = { viewModel.stopAudio() },
                            onClose = { viewModel.closeAudioPlayer() },
                            onExpand = { viewModel.setPlayerExpanded(true) },
                            onCollapse = { viewModel.setPlayerExpanded(false) },
                            onToggleLike = { uiState.currentAudioTrack?.let { viewModel.toggleLikeTrack(it.id) } },
                            onToggleShuffle = { viewModel.toggleShuffle() },
                            onToggleRepeat = { viewModel.toggleRepeat() },
                            onVolumeChange = { viewModel.setAudioVolume(it) },
                            onDownload = { uiState.currentAudioTrack?.let { viewModel.downloadTrack(it.id) } },
                            onOpenDiscussions = { uiState.currentAudioTrack?.let { viewModel.openSongDiscussions(it) } },
                            onOpenShare = { uiState.currentAudioTrack?.let { viewModel.openShareTrack(it) } },
                            onArtistClick = { artistId ->
                                val targetUser = uiState.users.find { it.id == artistId }
                                if (targetUser != null) viewModel.openUserProfile(targetUser)
                            }
                        )
                    }
                    FeatheredBottomNav(
                        currentScreen = uiState.currentScreen,
                        unreadMessagesCount = unreadMessagesCount,
                        marketplaceBadgeCount = uiState.products.size.coerceAtMost(99),
                        onTabSelected = { selectedScreen ->
                            if (uiState.currentScreen == selectedScreen) {
                                // Tapped active tab, scroll to top (like Facebook / Instagram native behavior)
                                coroutineScope.launch {
                                    scrollRestorationController.scrollToTop(selectedScreen.name)
                                }
                            } else {
                                viewModel.setScreen(selectedScreen)
                            }
                        },
                        onPostClick = {
                            if (uiState.isGuest) viewModel.openAuth("login") else viewModel.setShowCreatePostDialog(true)
                        },
                        onMenuClick = { viewModel.setShowMenuOverlay(true) }
                    )
                }
            }
        }
    ) { innerPadding ->
        ProvideMarketplaceContext(
            products = uiState.products,
            onViewProduct = { pid: Long ->
                viewModel.openProductById(pid)
            }
        ) {
            val screenModifier = Modifier.padding(innerPadding)

            if (uiState.showNotificationsOverlay) {
                NotificationsScreen(
                    notifications = uiState.notifications,
                    users = uiState.users,
                    currentUser = uiState.currentUser,
                    onBack = { viewModel.setShowNotificationsOverlay(false) },
                    onProfileClick = { userId ->
                        viewModel.setShowNotificationsOverlay(false)
                        viewModel.openUserProfileById(userId)
                    },
                    onMarkAllAsRead = { viewModel.markNotificationsAsRead() },
                    onDeleteNotification = { notifId -> viewModel.deleteNotification(notifId) },
                    onToggleRead = { notifId, isRead -> viewModel.markNotificationRead(notifId, isRead) },
                    onAcceptGroupInvite = { notifId, groupId -> viewModel.acceptGroupInvite(notifId, groupId) },
                    onDeclineGroupInvite = { notifId, groupId -> viewModel.declineGroupInvite(notifId, groupId) },
                    modifier = screenModifier
                )
            } else if (uiState.showSearchDialog || uiState.currentScreen == Screen.SEARCH) {
                SearchScreen(
                    onBack = {
                        viewModel.setShowSearchDialog(false)
                        viewModel.setSearchQuery("")
                        if (uiState.currentScreen == Screen.SEARCH) {
                            viewModel.setScreen(Screen.FEED)
                        }
                    },
                    users = uiState.users,
                    posts = uiState.posts,
                    reels = uiState.reels,
                    groups = uiState.groups,
                    marketplaceItems = uiState.products,
                    currentUser = uiState.currentUser,
                    onProfileClick = { userId ->
                        viewModel.setShowSearchDialog(false)
                        viewModel.openUserProfileById(userId)
                    },
                    onOpenGroup = { groupId ->
                        viewModel.setShowSearchDialog(false)
                        viewModel.setActiveGroupId(groupId)
                        viewModel.setScreen(Screen.GROUPS)
                    },
                    onFollow = { userId ->
                        viewModel.toggleFollowUser(userId)
                    },
                    onVideoClick = { reel ->
                        viewModel.setShowSearchDialog(false)
                        viewModel.setScreen(Screen.REELS)
                    },
                    onPostClick = { post ->
                        viewModel.setShowSearchDialog(false)
                        viewModel.setSelectedPostForComments(post)
                    },
                    onViewProduct = { productId ->
                        viewModel.setShowSearchDialog(false)
                        val prod = uiState.products.find { it.id == productId }
                        if (prod != null) {
                            viewModel.setSelectedProductForDetail(prod)
                        }
                    },
                    modifier = screenModifier
                )
            } else {
            when (uiState.currentScreen) {
                Screen.FEED -> {
                    FeedScreen(
                        isGuest = uiState.isGuest,
                        onRequestAuth = { viewModel.openAuth("login") },
                        currentUser = uiState.currentUser,
                        stories = uiState.stories,
                        posts = uiState.posts,
                        ads = uiState.sponsoredFeedAds,
                        products = uiState.products,
                        adCampaigns = uiState.adCampaigns,
                        onViewProduct = { productId ->
                            val prod = uiState.products.find { it.id == productId }
                            if (prod != null) {
                                viewModel.setSelectedProductForDetail(prod)
                            }
                        },
                        allUsers = uiState.users,
                        peopleYouMayKnow = uiState.peopleYouMayKnow,
                        onFollowFromPymk = { viewModel.toggleFollowUser(it) },
                        onFollowUser = { viewModel.toggleFollowUser(it) },
                        onDismissPymk = { viewModel.dismissPymk(it) },
                        pymkLoading = uiState.isPymkLoading,
                        groups = uiState.groups,
                        isFeedLoading = uiState.isFeedLoading,
                        onRefresh = { viewModel.refreshFeed() },
                        onOpenCreatePost = { viewModel.setShowCreatePostDialog(true) },
                        onPhotoClick = { viewModel.setShowCreatePostDialog(true, "photo") },
                        onVideoClick = { viewModel.setShowCreatePostDialog(true, "video") },
                        onStoryClick = { viewModel.setSelectedStory(it) },
                        onLikePost = { viewModel.toggleLikePost(it) },
                        onCommentPost = { viewModel.setSelectedPostForComments(it) },
                        onAddComment = { postId, text -> viewModel.addComment(postId, text) },
                        onAddReply = { postId, parentId, text -> viewModel.addReply(postId, parentId, text) },
                        onLikeComment = { postId, commentId -> viewModel.likeComment(postId, commentId) },
                        onDeleteComment = { postId, commentId -> viewModel.deleteComment(postId, commentId) },
                        onToggleHideComment = { postId, commentId, isHidden -> viewModel.hideComment(postId, commentId, isHidden) },
                        onSavePost = { viewModel.toggleSavePost(it) },
                        onUserClick = { viewModel.openUserProfile(it) },
                        onAddStoryClick = { viewModel.setShowCreateStoryDialog(true) },
                        onReactPost = { postId, reaction -> viewModel.reactToPost(postId, reaction) },
                        onSharePost = { postId -> viewModel.sharePost(postId) },
                        onOpenProduct = { product -> viewModel.setSelectedProductForDetail(product) },
                        onOpenEvent = { _ -> viewModel.setScreen(Screen.EVENTS) },
                        onOpenGroup = { groupId ->
                            viewModel.setActiveGroupId(groupId)
                            viewModel.setScreen(Screen.GROUPS)
                        },
                        onJoinGroup = { viewModel.joinGroup(it) },
                        onOpenVideos = { post ->
                            if (post != null) {
                                viewModel.openVideoReel(post)
                            } else {
                                viewModel.setScreen(Screen.REELS)
                            }
                        },
                        onDeletePost = { viewModel.deletePost(it) },
                        onEditPost = { id, text -> viewModel.editPost(id, text) },
                        onHidePost = { viewModel.hidePost(it) },
                        onPushMore = { viewModel.pushMore(it) },
                        pushedPosts = uiState.pushedPosts,
                        onPlayMusic = { song ->
                            val track = com.unera.social.model.AudioTrack(
                                id = song.id,
                                title = song.title,
                                artist = song.artist,
                                url = song.audioUrl,
                                cover = song.cover,
                                duration = song.durationSeconds,
                                uploaderId = song.uploaderId,
                                type = "music",
                                isVerified = song.isVerified,
                                likesCount = song.stats.likes
                            )
                            viewModel.playTrack(track)
                        },
                        uploadState = uiState.postUploadState,
                        onDismissUpload = { viewModel.dismissPostUpload() },
                        modifier = screenModifier
                    )
                }
                Screen.REELS -> {
                    ReelsScreen(
                        posts = if (uiState.videoPosts.isNotEmpty()) uiState.videoPosts else uiState.posts,
                        initialPostId = uiState.selectedVideoPostId,
                        ads = uiState.sponsoredFeedAds,
                        reels = uiState.reels,
                        currentUser = uiState.currentUser,
                        users = uiState.users,
                        stories = uiState.stories,
                        isLoading = uiState.isVideosLoading,
                        onReact = { post, type -> viewModel.reactToPost(post.id, type) },
                        onOpenComments = { post -> viewModel.setSelectedPostForComments(post) },
                        onShare = { postId, _ -> viewModel.sharePost(postId) },
                        onFollow = { userId -> viewModel.toggleFollowUser(userId) },
                        onProfileClick = { userId -> viewModel.openUserProfileById(userId) },
                        onStoryClick = { userId ->
                            val st = uiState.stories.find { it.author.id == userId }
                            if (st != null) {
                                viewModel.setSelectedStory(st)
                            } else {
                                viewModel.openUserProfileById(userId)
                            }
                        },
                        onLikeReel = { viewModel.toggleLikeReel(it) },
                        onSavePost = { viewModel.toggleSavePost(it) },
                        onBack = { viewModel.setScreen(Screen.FEED) },
                        onRefresh = { viewModel.refreshVideos() },
                        onNavigate = { destination -> viewModel.setScreen(destination) },
                        onOpenNotifications = { viewModel.setShowNotificationsOverlay(true) },
                        onOpenMenu = { viewModel.setShowMenuOverlay(true) },
                        unreadNotificationsCount = unreadNotificationsCount,
                        unreadMessagesCount = unreadMessagesCount,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Screen.MARKETPLACE -> {
                    MarketplaceScreen(
                        products = uiState.products,
                        selectedCategory = uiState.selectedCategory,
                        selectedCountry = uiState.selectedCountry,
                        marketMode = uiState.marketMode,
                        isLoading = uiState.isProductsLoading,
                        onCategorySelected = { viewModel.setSelectedCategory(it) },
                        onCountrySelected = { viewModel.setSelectedCountry(it) },
                        onMarketModeSelected = { viewModel.setMarketMode(it) },
                        onProductClick = { viewModel.setSelectedProductForDetail(it) },
                        onOpenCreateProduct = {
                            if (uiState.isGuest) viewModel.openAuth("login") else viewModel.setShowCreateProductDialog(true)
                        },
                        onToggleSaveProduct = { viewModel.toggleSaveProduct(it) },
                        onRefresh = { viewModel.refreshLiveProducts() },
                        onNavigateBack = { viewModel.setScreen(Screen.FEED) },
                        modifier = screenModifier
                    )
                }
                Screen.EVENTS -> {
                    EventsScreen(
                        events = uiState.events,
                        posts = uiState.posts,
                        currentUser = uiState.currentUser,
                        isGuest = uiState.isGuest,
                        onRequestAuth = { viewModel.openAuth("login") },
                        onToggleInterested = {
                            if (uiState.isGuest) viewModel.openAuth("login") else viewModel.toggleInterestedEvent(it)
                        },
                        onToggleGoing = {
                            if (uiState.isGuest) viewModel.openAuth("login") else viewModel.toggleGoingEvent(it)
                        },
                        onRsvpEvent = { id, target, prev ->
                            viewModel.rsvpEvent(id, target, prev)
                        },
                        onLikePost = { viewModel.toggleLikePost(it) },
                        onReactPost = { postId, reaction -> viewModel.reactToPost(postId, reaction) },
                        onAddComment = { postId, comment -> viewModel.addComment(postId, comment) },
                        onAddReply = { postId, parentId, reply -> viewModel.addReply(postId, parentId, reply) },
                        onSharePost = { viewModel.sharePost(it) },
                        onOpenCreateEvent = {
                            if (uiState.isGuest) viewModel.openAuth("login") else viewModel.setShowCreateEventDialog(true)
                        },
                        onProfileClick = { userId ->
                            viewModel.openUserProfileById(userId)
                        },
                        allUsers = uiState.users,
                        modifier = screenModifier
                    )
                }
                Screen.MESSAGES -> {
                    MessagesScreen(
                        conversations = uiState.conversations,
                        followingUsers = uiState.followingUsers,
                        activeConversationId = uiState.activeConversationId,
                        currentUser = uiState.currentUser,
                        isConversationsLoading = uiState.isConversationsLoading,
                        recipientOnline = uiState.recipientOnline,
                        recipientLastSeen = uiState.recipientLastSeen,
                        onOpenConversation = { viewModel.openConversation(it) },
                        onCloseConversation = { viewModel.closeConversation() },
                        onSendMessage = { convId, text, replyToId, atts ->
                            viewModel.sendMessage(convId, text, replyToId, atts)
                        },
                        onSendFile = { convId, uri, text ->
                            viewModel.uploadAndSendFile(convId, uri, text)
                        },
                        onSendVoiceNote = { convId, bytes, durationSec ->
                            viewModel.uploadAndSendVoiceNote(convId, bytes, durationSec)
                        },
                        onEditMessage = { convId, msgId, text -> viewModel.editChatMessage(convId, msgId, text) },
                        onDeleteMessage = { convId, msgId, deleteForEveryone -> viewModel.deleteChatMessage(convId, msgId, deleteForEveryone) },
                        onStartCall = { user, mode -> viewModel.startCall(user, mode) },
                        onStartNewChat = { user -> viewModel.startConversationWithUser(user) },
                        onRefresh = { viewModel.refreshConversations() },
                        onViewProduct = { pid ->
                            val product = uiState.products.find { it.id == pid }
                            if (product != null) viewModel.setSelectedProductForDetail(product)
                        },
                        modifier = screenModifier
                    )
                }
                Screen.PROFILE -> {
                    val activeUser = uiState.selectedUserProfile ?: uiState.currentUser
                    ProfileScreen(
                        isGuest = uiState.isGuest,
                        onRequestAuth = { viewModel.openAuth("login") },
                        user = activeUser,
                        currentUser = uiState.currentUser,
                        allUsers = uiState.users,
                        allPosts = uiState.userProfilePosts[activeUser.id] ?: uiState.posts.filter { it.author.id == activeUser.id },
                        allReels = uiState.reels,
                        allStories = uiState.stories,
                        allProducts = uiState.products,
                        onLikePost = { viewModel.toggleLikePost(it) },
                        onReactPost = { postId, reaction -> viewModel.reactToPost(postId, reaction) },
                        onCommentPost = { viewModel.setSelectedPostForComments(it) },
                        onSavePost = { viewModel.toggleSavePost(it) },
                        onSharePost = { viewModel.sharePost(it) },
                        onOpenProduct = { product -> viewModel.setSelectedProductForDetail(product) },
                        onUpdateUserDetails = { bio, work, edu, loc, web ->
                            viewModel.updateUserDetails(bio, work, edu, loc, web)
                        },
                        onUpdateCoverImage = { uri ->
                            viewModel.updateCoverImage(uri)
                        },
                        onFollowUser = { viewModel.toggleFollowUser(it) },
                        onProfileClick = { targetUser ->
                            viewModel.openUserProfile(targetUser)
                        },
                        onMessageUser = { targetUser ->
                            viewModel.startConversationWithUser(targetUser)
                        },
                        onAddStoryClick = {
                            viewModel.setShowCreateStoryDialog(true)
                        },
                        onVerifyUser = { viewModel.toggleVerifyUser(it) },
                        onSuspendUser = { userId, duration, reason ->
                            viewModel.suspendUser(userId, duration, reason)
                        },
                        onUnsuspendUser = { viewModel.unsuspendUser(it) },
                        onToggleModeratorRole = { viewModel.toggleModeratorRole(it) },
                        onDeletePost = { viewModel.deletePost(it) },
                        onEditPost = { id, text -> viewModel.editPost(id, text) },
                        onPushMore = { viewModel.pushMore(it) },
                        pushedPosts = uiState.pushedPosts,
                        onBack = if (activeUser.id != uiState.currentUser.id) {
                            { viewModel.openCurrentUserProfile() }
                        } else null,
                        modifier = screenModifier
                    )
                }
                Screen.DISCOVER_PEOPLE -> {
                    SuggestedProfilesPage(
                        currentUser = uiState.currentUser,
                        users = (uiState.peopleYouMayKnow + uiState.users).distinctBy { it.id },
                        onFollow = { viewModel.toggleFollowUser(it) },
                        onProfileClick = { userId ->
                            val targetUser = uiState.users.find { it.id == userId }
                                ?: uiState.peopleYouMayKnow.find { it.id == userId }
                            if (targetUser != null) {
                                viewModel.openUserProfile(targetUser)
                            }
                        },
                        onBack = { viewModel.setScreen(Screen.FEED) },
                        modifier = screenModifier
                    )
                }
                Screen.BIRTHDAYS -> {
                    BirthdaysPage(
                        currentUser = uiState.currentUser,
                        users = uiState.users,
                        onMessage = { userId ->
                            val targetUser = uiState.users.find { it.id == userId }
                            if (targetUser != null) {
                                viewModel.startConversationWithUser(targetUser)
                            }
                        },
                        onProfileClick = { userId ->
                            val targetUser = uiState.users.find { it.id == userId }
                            if (targetUser != null) {
                                viewModel.openUserProfile(targetUser)
                            }
                        },
                        onBack = { viewModel.setScreen(Screen.FEED) },
                        modifier = screenModifier
                    )
                }
                Screen.SAVED_POSTS -> {
                    SavedPostsPage(
                        currentUser = uiState.currentUser,
                        users = uiState.users,
                        posts = uiState.posts,
                        reels = uiState.reels,
                        onProfileClick = { userId ->
                            val targetUser = uiState.users.find { it.id == userId }
                            if (targetUser != null) {
                                viewModel.openUserProfile(targetUser)
                            }
                        },
                        onVideoClick = { reel ->
                            viewModel.setScreen(Screen.REELS)
                        },
                        onOpenComments = { post ->
                            viewModel.setSelectedPostForComments(post)
                        },
                        onLikePost = { viewModel.toggleLikePost(it) },
                        onCommentPost = { viewModel.setSelectedPostForComments(it) },
                        onSavePost = { viewModel.toggleSavePost(it) },
                        onSaveReel = { viewModel.toggleSaveReel(it) },
                        onShare = { viewModel.sharePost(it.id) },
                        onBack = { viewModel.setScreen(Screen.FEED) },
                        modifier = screenModifier
                    )
                }
                Screen.MEMORIES -> {
                    MemoriesPage(
                        currentUser = uiState.currentUser,
                        posts = uiState.posts,
                        onLikePost = { viewModel.toggleLikePost(it) },
                        onCommentPost = { viewModel.setSelectedPostForComments(it) },
                        onBack = { viewModel.setScreen(Screen.FEED) },
                        modifier = screenModifier
                    )
                }
                Screen.STORY_FEED -> {
                    StoryFeedPage(
                        currentUser = uiState.currentUser,
                        stories = uiState.stories,
                        onBack = { viewModel.setScreen(Screen.FEED) },
                        onAddStoryClick = { viewModel.setShowCreateStoryDialog(true) },
                        onStoryClick = { story -> viewModel.setSelectedStory(story) },
                        modifier = screenModifier
                    )
                }
                Screen.MUSIC -> {
                    MusicSystemScreen(
                        currentUser = uiState.currentUser,
                        users = uiState.users,
                        songs = uiState.songs,
                        currentTrack = uiState.currentAudioTrack,
                        isPlaying = uiState.isPlayingAudio,
                        likedTrackKeys = uiState.likedTrackKeys,
                        myTotalPlays = uiState.myTotalPlays,
                        trackPlays = uiState.trackPlays,
                        uploadProgress = uiState.songUploadProgress,
                        uploadStatus = uiState.songUploadStatus,
                        isLoading = uiState.isSongsLoading,
                        onRefresh = { viewModel.refreshLiveSongs() },
                        onPlayTrack = { viewModel.playTrack(it) },
                        onTogglePlay = { viewModel.togglePlayAudio() },
                        onToggleLikeTrack = { viewModel.toggleLikeTrack(it) },
                        onUploadSong = { title, artist, genre, album, cover, audio ->
                            if (uiState.isGuest) viewModel.openAuth("login")
                            else viewModel.uploadSong(title, artist, genre, album, cover, audio)
                        },
                        onPublishSingle = { title, artist, genre, album, coverUri, audioUri ->
                            viewModel.publishSingleSong(context, title, artist, genre, album, coverUri, audioUri)
                        },
                        onPublishAlbum = { albumTitle, artist, genre, coverUri, tracks ->
                            viewModel.publishAlbum(context, albumTitle, artist, genre, coverUri, tracks)
                        },
                        onDeleteUploadedSong = { viewModel.deleteUploadedSong(it) },
                        onProfileClick = { userId ->
                            val target = uiState.users.find { it.id == userId }
                            if (target != null) viewModel.openUserProfile(target)
                        },
                        onBack = { viewModel.setScreen(Screen.FEED) },
                        modifier = screenModifier
                    )
                }
                Screen.GROUPS -> {
                    val activeGid = uiState.activeGroupId
                    GroupsScreen(
                        currentUser = uiState.currentUser,
                        groups = uiState.groups,
                        groupPosts = uiState.groupPosts,
                        groupInvites = uiState.groupInvites,
                        allEvents = uiState.events,
                        allUsers = uiState.users,
                        initialGroupId = activeGid,
                        groupSuggestions = uiState.groupSuggestions,
                        groupMembers = if (activeGid != null) uiState.groupMembers[activeGid] ?: emptyList() else emptyList(),
                        groupEvents = if (activeGid != null) uiState.groupEvents[activeGid] ?: emptyList() else emptyList(),
                        isGroupsLoading = uiState.isGroupsLoading,
                        isGroupPostsLoading = uiState.isGroupPostsLoading,
                        onBack = { viewModel.setScreen(Screen.FEED) },
                        onSelectGroup = { gid -> viewModel.setActiveGroupId(gid) },
                        onCreateGroup = { name, desc, type, cat, prof, cov ->
                            if (uiState.isGuest) viewModel.openAuth("login")
                            else viewModel.createGroup(name, desc, type, cat, prof, cov)
                        },
                        onJoinGroup = {
                            if (uiState.isGuest) viewModel.openAuth("login")
                            else viewModel.joinGroup(it)
                        },
                        onLeaveGroup = { viewModel.leaveGroup(it) },
                        onDeleteGroup = { viewModel.deleteGroup(it) },
                        onUpdateGroupSettings = { gid, name, desc, postingAllowed ->
                            viewModel.updateGroupSettings(gid, name, desc, postingAllowed)
                        },
                        onPostToGroup = { gid, content, images, price, curr, cond, loc, stat, job, comp, jType, sal, appType, appVal ->
                            if (uiState.isGuest) viewModel.openAuth("login")
                            else viewModel.postToGroup(context, gid, content, images, price, curr, cond, loc, stat, job, comp, jType, sal, appType, appVal)
                        },
                        onToggleLikePost = { pid, r ->
                            if (uiState.isGuest) viewModel.openAuth("login")
                            else viewModel.toggleLikeGroupPost(pid, r)
                        },
                        onAddPostComment = { pid, text ->
                            if (uiState.isGuest) viewModel.openAuth("login")
                            else viewModel.addGroupPostComment(pid, text)
                        },
                        onDeletePost = { viewModel.deleteGroupPost(it) },
                        onSharePost = {
                            if (uiState.isGuest) viewModel.openAuth("login")
                            else viewModel.shareGroupPost(it)
                        },
                        onInviteUser = { gid, uid -> viewModel.inviteUserToGroup(gid, uid) },
                        onAcceptInvite = { invId, gid -> viewModel.acceptGroupInvite(invId, gid) },
                        onDeclineInvite = { viewModel.declineGroupInvite(it) },
                        onToggleMemberPosting = { gid, uid, disabled -> viewModel.toggleMemberPosting(gid, uid, disabled) },
                        onSetMemberRole = { gid, uid, role -> viewModel.setMemberRole(gid, uid, role) },
                        onRemoveMember = { gid, uid -> viewModel.removeGroupMember(gid, uid) },
                        onProfileClick = { userId ->
                            viewModel.openUserProfileById(userId)
                        },
                        uploadState = uiState.postUploadState,
                        onDismissUpload = { viewModel.dismissPostUpload() },
                        modifier = screenModifier
                    )
                }
                Screen.SETTINGS -> {
                    SettingsScreen(
                        currentUser = uiState.currentUser,
                        onClose = { viewModel.setScreen(Screen.FEED) },
                        onLogout = { viewModel.logoutUser() },
                        onUpdateUserDetails = { name, bio, work, edu, loc, web, gender, bday ->
                            viewModel.updateFullProfileDetails(name, bio, work, edu, loc, web, gender, bday)
                        },
                        onChangePassword = { curr, new ->
                            viewModel.changePassword(curr, new)
                        },
                        onDeleteAccount = {
                            viewModel.deleteAccount()
                        },
                        onOpenHelpSupport = {
                            viewModel.setScreen(Screen.HELP_SUPPORT)
                        },
                        onOpenTerms = {
                            viewModel.setScreen(Screen.TERMS)
                        },
                        modifier = screenModifier
                    )
                }
                Screen.ADS_MANAGER -> {
                    AdsManagerScreen(
                        campaigns = uiState.adCampaigns,
                        currentUser = uiState.currentUser,
                        userPosts = uiState.posts.filter { it.author.id == uiState.currentUser.id },
                        initialPost = uiState.activeAdCampaignToBoostPost,
                        onPauseCampaign = { viewModel.pauseAdCampaign(it) },
                        onResumeCampaign = { viewModel.resumeAdCampaign(it) },
                        onDeleteCampaign = { viewModel.deleteAdCampaign(it) },
                        onCreateCampaign = { postId, name, type, mediaUrl, mediaUrls, desc, link, phone, email, cta, loc, budget, days ->
                            viewModel.createAdCampaign(postId, name, type, mediaUrl, mediaUrls, desc, link, phone, email, cta, loc, budget, days)
                        },
                        onBack = {
                            viewModel.setAdCampaignToBoostPost(null)
                            viewModel.setScreen(Screen.FEED)
                        },
                        modifier = screenModifier
                    )
                }
                Screen.AUTH -> {
                    AuthScreen(
                        initialMode = uiState.authMode,
                        onClose = { viewModel.setScreen(Screen.FEED) },
                        onLogin = { email, pass -> viewModel.loginUser(email, pass) },
                        onSignup = { u, e, p, nat, loc, bday, gen -> viewModel.registerUser(u, e, p, nat, loc, bday, gen) },
                        onContinueAsGuest = { viewModel.continueAsGuest() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Screen.HELP_SUPPORT -> {
                    HelpSupportScreen(
                        currentUser = uiState.currentUser,
                        onBack = { viewModel.setScreen(Screen.FEED) },
                        modifier = screenModifier
                    )
                }
                Screen.SEARCH -> {
                    // Full page handled in outer branch
                }
                Screen.TERMS -> {
                    TermsOfServiceScreen(
                        onBack = { viewModel.setScreen(Screen.SETTINGS) },
                        onOpenPrivacy = { viewModel.setScreen(Screen.SETTINGS) },
                        modifier = screenModifier
                    )
                }
            }
        }
    }
}

    // Modal Dialogs
    if (uiState.showCreatePostDialog) {
        val context = androidx.compose.ui.platform.LocalContext.current
        CreatePostModal(
            currentUser = uiState.currentUser,
            allUsers = uiState.users,
            initialOpenPicker = uiState.createPostInitialPicker,
            onDismiss = { viewModel.setShowCreatePostDialog(false) },
            onSubmit = { content, mediaUrl, mediaUrls, feeling, location, background, taggedUsers, mediaType ->
                viewModel.createPost(
                    context = context,
                    content = content,
                    mediaUrl = mediaUrl,
                    feeling = feeling,
                    mediaUrls = mediaUrls,
                    location = location,
                    backgroundStyle = background,
                    taggedUsers = taggedUsers,
                    explicitMediaType = mediaType
                )
            },
            onCreateEventClick = {
                viewModel.setShowCreatePostDialog(false)
                viewModel.setShowCreateEventDialog(true)
            }
        )
    }

    if (uiState.selectedPostForComments != null) {
        val selectedPost = uiState.selectedPostForComments!!
        DiscussionsSheetDialog(
            post = selectedPost,
            currentUser = uiState.currentUser,
            isFollowing = selectedPost.author.isFollowing,
            allUsers = uiState.users,
            onFollowToggle = {
                viewModel.toggleFollowUser(selectedPost.author.id)
            },
            onDismiss = { viewModel.setSelectedPostForComments(null) },
            onAddComment = { commentText ->
                viewModel.addComment(selectedPost.id, commentText)
            },
            onAddReply = { parentId, replyText ->
                viewModel.addReply(selectedPost.id, parentId, replyText)
            },
            onLikeComment = { commentId ->
                viewModel.likeComment(selectedPost.id, commentId)
            },
            onDeleteComment = { commentId ->
                viewModel.deleteComment(selectedPost.id, commentId)
            },
            onToggleHideComment = { commentId, isHidden ->
                viewModel.hideComment(selectedPost.id, commentId, isHidden)
            },
            onProfileClick = { author ->
                viewModel.setSelectedPostForComments(null)
                viewModel.openUserProfile(author)
            },
            onLikePost = {
                viewModel.toggleLikePost(selectedPost.id)
            },
            onSharePost = {
                viewModel.sharePost(selectedPost.id)
            },
            onSavePost = {
                viewModel.toggleSavePost(selectedPost.id)
            }
        )
    }

    if (uiState.selectedProductForDetail != null) {
        ProductDetailDialog(
            product = uiState.selectedProductForDetail!!,
            onDismiss = { viewModel.setSelectedProductForDetail(null) },
            onContactSeller = { seller ->
                viewModel.startConversationWithUser(seller, uiState.selectedProductForDetail)
            }
        )
    }

    if (uiState.showCreateProductDialog) {
        val context = androidx.compose.ui.platform.LocalContext.current
        CreateProductDialog(
            currentUser = uiState.currentUser,
            onDismiss = { viewModel.setShowCreateProductDialog(false) },
            onSubmit = { title, price, discountPrice, category, location, country, description, imageUrl, images, phone ->
                viewModel.createProduct(
                    context = context,
                    title = title,
                    price = price,
                    discountPrice = discountPrice,
                    category = category,
                    location = location,
                    country = country,
                    description = description,
                    imageUrl = imageUrl,
                    images = images,
                    phone = phone
                )
            }
        )
    }

    if (uiState.showCreateEventDialog) {
        val context = androidx.compose.ui.platform.LocalContext.current
        CreateEventModal(
            currentUser = uiState.currentUser,
            onClose = { viewModel.setShowCreateEventDialog(false) },
            onCreate = { title, desc, date, time, location, visibility, coverUrl, gId, gName ->
                viewModel.createEvent(
                    context = context,
                    title = title,
                    date = if (time.isNotBlank()) "$date • $time" else date,
                    location = location,
                    description = desc,
                    imageUrl = coverUrl,
                    time = time,
                    visibility = visibility,
                    groupId = gId,
                    groupName = gName
                )
            }
        )
    }



    if (uiState.showMenuOverlay) {
        MenuOverlay(
            currentUser = uiState.currentUser,
            onClose = { viewModel.setShowMenuOverlay(false) },
            onNavigate = { destination ->
                viewModel.setShowMenuOverlay(false)
                when (destination) {
                    "marketplace" -> viewModel.setScreen(Screen.MARKETPLACE)
                    "events" -> viewModel.setScreen(Screen.EVENTS)
                    "reels" -> viewModel.setScreen(Screen.REELS)
                    "notifications" -> viewModel.setShowNotificationsOverlay(true)
                    "profile" -> viewModel.setScreen(Screen.PROFILE)
                    "profiles" -> viewModel.setScreen(Screen.DISCOVER_PEOPLE)
                    "birthdays" -> viewModel.setScreen(Screen.BIRTHDAYS)
                    "memories" -> viewModel.setScreen(Screen.MEMORIES)
                    "saved-posts", "saved" -> viewModel.setScreen(Screen.SAVED_POSTS)
                    "story-feed" -> viewModel.setScreen(Screen.STORY_FEED)
                    "music" -> viewModel.setScreen(Screen.MUSIC)
                    "groups" -> viewModel.setScreen(Screen.GROUPS)
                    "tools" -> viewModel.setShowSearchDialog(true)
                    "ads", "ad-manager", "ads-manager", "ad_manager", "adsmanager", "ad manager", "Ad Manager" -> {
                        viewModel.setAdCampaignToBoostPost(null)
                        viewModel.setScreen(Screen.ADS_MANAGER)
                    }
                    "help" -> viewModel.setScreen(Screen.HELP_SUPPORT)
                    "settings", "privacy" -> viewModel.setScreen(Screen.SETTINGS)
                    "terms" -> viewModel.setScreen(Screen.TERMS)
                    else -> {}
                }
            },
            onLogout = {
                viewModel.setShowMenuOverlay(false)
                viewModel.logoutUser()
            }
        )
    }

    // Story Viewer Dialog (matching Web StoryViewer in Storypage)
    uiState.selectedStory?.let { currentStory ->
        StoryViewerDialog(
            story = currentStory,
            allStories = uiState.stories,
            currentUser = uiState.currentUser,
            onClose = { viewModel.setSelectedStory(null) },
            onNextStory = {
                val stories = uiState.stories
                val idx = stories.indexOfFirst { it.id == currentStory.id }
                if (idx in 0 until stories.size - 1) {
                    viewModel.setSelectedStory(stories[idx + 1])
                } else {
                    viewModel.setSelectedStory(null)
                }
            },
            onPrevStory = {
                val stories = uiState.stories
                val idx = stories.indexOfFirst { it.id == currentStory.id }
                if (idx > 0) {
                    viewModel.setSelectedStory(stories[idx - 1])
                } else {
                    viewModel.setSelectedStory(null)
                }
            },
            onReact = { reaction ->
                viewModel.reactToStory(currentStory.id, reaction)
            },
            onDeleteStory = {
                viewModel.deleteStory(currentStory.id)
            },
            onToggleFollow = { authorId ->
                viewModel.toggleFollowUser(authorId)
            },
            onProfileClick = { authorId ->
                viewModel.setSelectedStory(null)
                val user = uiState.users.find { it.id == authorId }
                if (user != null) {
                    viewModel.openUserProfile(user)
                }
            }
        )
    }

    // Create Story Modal Dialog (matching Web CreateStoryModal in Storypage)
    if (uiState.showCreateStoryDialog) {
        val context = androidx.compose.ui.platform.LocalContext.current
        CreateStoryDialog(
            currentUser = uiState.currentUser,
            onDismiss = { viewModel.setShowCreateStoryDialog(false) },
            onSubmitStory = { mediaUri, isVideo, type, textContent, backgroundStyle, musicTitle, musicUrl ->
                viewModel.uploadStoryAndPost(
                    context = context,
                    mediaUri = mediaUri,
                    isVideo = isVideo,
                    type = type,
                    textContent = textContent,
                    backgroundStyle = backgroundStyle,
                    musicTitle = musicTitle,
                    musicUrl = musicUrl,
                    onSuccess = {
                        android.widget.Toast.makeText(context, "Story posted successfully!", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onError = { err ->
                        android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_LONG).show()
                    }
                )
            },
            isSubmitting = uiState.isStoriesLoading
        )
    }

    // Music Discussions Bottom Sheet Modal
    if (uiState.activeDiscussionTrack != null) {
        MusicCommentsSheet(
            track = uiState.activeDiscussionTrack,
            currentUser = uiState.currentUser,
            comments = uiState.songComments[uiState.activeDiscussionTrack!!.id] ?: emptyList(),
            onClose = { viewModel.closeSongDiscussions() },
            onAddComment = { text ->
                if (uiState.isGuest) viewModel.openAuth("login")
                else viewModel.addSongComment(uiState.activeDiscussionTrack!!.id, text)
            },
            onToggleHide = { commentId -> viewModel.toggleHideSongComment(uiState.activeDiscussionTrack!!.id, commentId) },
            onDeleteComment = { commentId -> viewModel.deleteSongComment(uiState.activeDiscussionTrack!!.id, commentId) },
            onProfileClick = { userId ->
                viewModel.closeSongDiscussions()
                viewModel.openUserProfileById(userId)
            }
        )
    }

    // Music Share Bottom Sheet Modal
    if (uiState.activeShareTrack != null) {
        MusicShareSheet(
            track = uiState.activeShareTrack,
            onClose = { viewModel.closeShareTrack() },
            onShareDestination = { _ ->
                viewModel.closeShareTrack()
            }
        )
    }

    // Auth Screen Modal Overlay (when user logs out or requests sign in)
    if (uiState.showAuthModal) {
        Dialog(
            onDismissRequest = {
                // If user is logged out, allow them to dismiss only if guest, or continue as guest
                viewModel.closeAuth()
            },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            AuthScreen(
                initialMode = uiState.authMode,
                onClose = { viewModel.closeAuth() },
                onLogin = { email, pass -> viewModel.loginUser(email, pass) },
                onSignup = { u, e, p, nat, loc, bday, gen -> viewModel.registerUser(u, e, p, nat, loc, bday, gen) },
                onContinueAsGuest = { viewModel.continueAsGuest() },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    // Active or Incoming Voice/Video Call Dialog
    uiState.activeCall?.let { call ->
        CallScreen(
            open = true,
            mode = CallMode.fromString(call.mode),
            phase = CallPhase.fromString(call.phase),
            peerName = call.peerName,
            peerAvatar = call.peerAvatar,
            onAccept = { viewModel.acceptIncomingCall() },
            onDecline = { viewModel.declineIncomingCall() },
            onHangup = { viewModel.hangupCall() },
            onToggleMic = { /* Audio toggle handled inside CallScreen state */ },
            subtitle = if (call.phase == "active") {
                val mins = call.seconds / 60
                val secs = call.seconds % 60
                String.format("%02d:%02d", mins, secs)
            } else null
        )
    }

    // Full screen image viewer matching web ImageViewer
    if (uiState.fullScreenImageViewerUrl != null) {
        ImageViewer(
            imageUrl = uiState.fullScreenImageViewerUrl,
            onClose = { viewModel.closeImageViewer() }
        )
    }

    // Professional full-screen loader matching web ProfessionalLoader
    if (uiState.isAppLoading) {
        ProfessionalLoader()
    }
        }

        // Facebook/Meta 1:1 launch splash screen
        AnimatedVisibility(
            visible = showSplash,
            enter = fadeIn(),
            exit = fadeOut(animationSpec = tween(durationMillis = 350))
        ) {
            FacebookSplashScreen(
                isLoading = uiState.isFeedLoading,
                onFinished = { showSplash = false }
            )
        }
    }
}

