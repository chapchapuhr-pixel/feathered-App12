package com.unera.social.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unera.social.data.AdsApiService
import com.unera.social.data.EventsApiService
import com.unera.social.data.GroupsApiService
import com.unera.social.data.GroupMemberInfo
import com.unera.social.data.MarketplaceApiService
import com.unera.social.data.MockData
import com.unera.social.data.PostsApiService
import com.unera.social.data.StoriesApiService
import com.unera.social.data.UserApiService
import com.unera.social.data.UserSessionManager
import com.unera.social.data.VideosApiService
import com.unera.social.data.FeedInteractionApiService
import com.unera.social.data.SongsApiService
import android.content.Context
import android.net.Uri
import com.unera.social.data.MediaUploadHelper
import com.unera.social.data.CommentsCache
import com.unera.social.data.MessagesApiService
import com.unera.social.data.MessagesCache
import com.unera.social.data.IncomingCall
import com.unera.social.data.PresenceStatus
import com.unera.social.data.CallEvent
import com.unera.social.model.*
import com.unera.social.ui.components.PostUploadState
import com.unera.social.ui.screens.toPost
import org.json.JSONObject
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeatheredUiState(
    val currentScreen: Screen = Screen.FEED,
    val currentUser: User = MockData.currentUser,
    val users: List<User> = MockData.users,
    val peopleYouMayKnow: List<User> = emptyList(),
    val isPymkLoading: Boolean = false,
    val hiddenPymkIds: Set<Long> = emptySet(),
    val posts: List<Post> = emptyList(),
    val pushedPosts: Map<Long, Boolean> = emptyMap(),
    val isFeedLoading: Boolean = false,
    val videoPosts: List<Post> = emptyList(),
    val isVideosLoading: Boolean = false,
    val selectedVideoPostId: Long? = null,
    val sponsoredFeedAds: List<SponsoredFeedAd> = emptyList(),
    val isFeedAdsLoading: Boolean = false,
    val userProfilePosts: Map<Long, List<Post>> = emptyMap(),
    val isProfilePostsLoading: Boolean = false,
    val stories: List<Story> = emptyList(),
    val isStoriesLoading: Boolean = false,
    val storyViewers: Map<Long, List<StoryViewer>> = emptyMap(),
    val isStoryViewersLoading: Boolean = false,
    val storyComments: Map<Long, List<StoryComment>> = emptyMap(),
    val isStoryCommentsLoading: Boolean = false,
    val storyReactions: Map<Long, StoryReactionsData> = emptyMap(),
    val storyAnalytics: Map<Long, StoryAnalytics> = emptyMap(),
    val reels: List<Reel> = emptyList(),
    val products: List<Product> = emptyList(),
    val isProductsLoading: Boolean = false,
    val selectedCategory: String = "all",
    val selectedCountry: String = "all",
    val marketMode: String = "for_you", // "for_you", "local", "worldwide"
    val events: List<Event> = MockData.events,
    val conversations: List<Conversation> = emptyList(),
    val activeConversationId: String? = null,
    val followingUsers: List<User> = emptyList(),
    val isConversationsLoading: Boolean = false,
    val activeCall: ActiveCallState? = null,
    val recipientOnline: Boolean = false,
    val recipientLastSeen: String? = null,
    val notifications: List<Notification> = MockData.notifications,
    val searchQuery: String = "",
    val showCreatePostDialog: Boolean = false,
    val createPostInitialPicker: String? = null,
    val selectedPostForComments: Post? = null,
    val showSearchDialog: Boolean = false,
    val showNotificationsOverlay: Boolean = false,
    val showCreateEventDialog: Boolean = false,
    val showCreateProductDialog: Boolean = false,
    val selectedProductForDetail: Product? = null,
    val selectedStory: Story? = null,
    val showCreateStoryDialog: Boolean = false,
    val showMenuOverlay: Boolean = false,
    val selectedUserProfile: User? = null,
    // F-Music state
    val songs: List<Song> = emptyList(),
    val isSongsLoading: Boolean = false,
    val currentAudioTrack: AudioTrack? = null,
    val isPlayingAudio: Boolean = false,
    val audioPositionSeconds: Int = 0,
    val audioDurationSeconds: Int = 180,
    val isPlayerExpanded: Boolean = false,
    val isShuffleEnabled: Boolean = false,
    val isRepeatEnabled: Boolean = false,
    val audioVolume: Float = 1.0f,
    val likedTrackKeys: Set<String> = setOf("music:1", "music:4", "music:6"),
    val myTotalPlays: Long = 33290L,
    val trackPlays: Map<String, Long> = emptyMap(),
    val songComments: Map<String, List<SongComment>> = mapOf(
        "1" to MockData.initialSongComments.filter { it.songId == "1" },
        "2" to MockData.initialSongComments.filter { it.songId == "2" }
    ),
    val downloadedTrackIds: Set<String> = emptySet(),
    val downloadingTrackId: String? = null,
    val downloadProgress: Int = 0,
    val downloadToast: String? = null,
    val activeDiscussionTrack: AudioTrack? = null,
    val activeShareTrack: AudioTrack? = null,
    val songUploadProgress: Int? = null,
    val songUploadStatus: String? = null,
    // Groups state
    val groups: List<Group> = emptyList(),
    val groupPosts: List<GroupPost> = emptyList(),
    val groupInvites: List<GroupInvite> = emptyList(),
    val groupSuggestions: List<Group> = emptyList(),
    val groupMembers: Map<Long, List<GroupMemberInfo>> = emptyMap(),
    val groupEvents: Map<Long, List<Event>> = emptyMap(),
    val isGroupsLoading: Boolean = false,
    val isGroupPostsLoading: Boolean = false,
    val activeGroupId: Long? = null,
    val groupMembersOverrides: Map<Long, Map<Long, String>> = emptyMap(), // groupId -> (userId -> role)
    val groupPostingDisabledOverrides: Map<Long, Set<Long>> = emptyMap(), // groupId -> Set<userId>
    // Ads Manager state
    val adCampaigns: List<AdCampaign> = listOf(
        AdCampaign(
            id = 1L,
            name = "Summer Safari Adventure 2026",
            type = "image",
            status = "active",
            mediaUrl = "https://images.unsplash.com/photo-1516426122078-c23e76319801?w=800",
            mediaUrls = listOf(
                "https://images.unsplash.com/photo-1516426122078-c23e76319801?w=800",
                "https://images.unsplash.com/photo-1547471080-7cc2caa01a7e?w=800"
            ),
            description = "Experience the majestic Serengeti migration and Ngorongoro Crater! Book early and enjoy 20% off all luxury tented lodge packages. 🦁🌿",
            cta = "Book Now",
            link = "https://tanzaniasafari.example.com",
            phone = "+255 754 112 233",
            email = "safari@example.com",
            location = "Tanzania & East Africa",
            budget = 15,
            days = 14,
            startDate = "2026-03-01",
            endDate = "2026-03-15",
            impressions = 14200,
            views = 9850,
            clicks = 680,
            postId = 1L
        ),
        AdCampaign(
            id = 2L,
            name = "Authentic Kitenge Bomber Jackets",
            type = "image",
            status = "active",
            mediaUrl = "https://images.unsplash.com/photo-1551028719-00167b16eac5?w=800",
            mediaUrls = listOf("https://images.unsplash.com/photo-1551028719-00167b16eac5?w=800"),
            description = "Handmade artisan Kitenge bomber jackets now available with free delivery across Dar es Salaam. Unisex sizes S to XXL! 🎨🔥",
            cta = "Shop Now",
            link = "https://kitengefashion.example.com",
            phone = "+255 682 998 877",
            email = "orders@kitengefashion.com",
            location = "Dar es Salaam, Tanzania",
            budget = 10,
            days = 7,
            startDate = "2026-03-05",
            endDate = "2026-03-12",
            impressions = 8940,
            views = 6200,
            clicks = 412,
            postId = 2L
        ),
        AdCampaign(
            id = 3L,
            name = "Kilimanjaro Summit Expedition",
            type = "video",
            status = "paused",
            mediaUrl = "https://images.unsplash.com/photo-1589556264800-08ae9e129a8c?w=800",
            mediaUrls = listOf("https://images.unsplash.com/photo-1589556264800-08ae9e129a8c?w=800"),
            description = "Conquer the roof of Africa with certified guides and full safety equipment. Machame and Lemosho route expeditions departing every week.",
            cta = "Learn More",
            link = "https://kilimanjaroclimb.example.com",
            phone = "+255 712 345 678",
            location = "Global",
            budget = 25,
            days = 21,
            startDate = "2026-02-10",
            endDate = "2026-03-03",
            impressions = 24600,
            views = 18400,
            clicks = 980,
            postId = 3L
        )
    ),
    val activeAdCampaignToBoostPost: Post? = null,
    // Authentication State
    val isLoggedIn: Boolean = false,
    val isGuest: Boolean = true,
    val showAuthModal: Boolean = false,
    val authMode: String = "login", // "login", "register", "forgot"
    val postUploadState: PostUploadState? = null,
    val fullScreenImageViewerUrl: String? = null,
    val isAppLoading: Boolean = false
)

class FeatheredViewModel(application: Application) : AndroidViewModel(application) {

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("FeatheredViewModel", "Caught unhandled coroutine exception", throwable)
    }

    private fun launchSafe(block: suspend CoroutineScope.() -> Unit): Job {
        return viewModelScope.launch(coroutineExceptionHandler) {
            try {
                block()
            } catch (t: Throwable) {
                Log.e("FeatheredViewModel", "Exception caught in launchSafe", t)
            }
        }
    }

    private val _uiState = MutableStateFlow(FeatheredUiState())
    val uiState: StateFlow<FeatheredUiState> = _uiState.asStateFlow()

    init {
        // Session restore on app load matching App.tsx auto-on-app-load
        val context = getApplication<Application>().applicationContext
        val savedUser = UserSessionManager.getSavedUser(context)
        val savedToken = UserSessionManager.getAuthToken(context)
        if (savedUser != null && savedUser.id > 0L) {
            UserSessionManager.setCurrentUser(savedUser)
            _uiState.update {
                it.copy(
                    currentUser = savedUser,
                    isLoggedIn = true,
                    isGuest = false,
                    showAuthModal = false
                )
            }
            startMessagingServices()
            // Refresh fresh profile from /api/auth/user/me or /api/users?id=<id> in background
            viewModelScope.launch {
                try {
                    var fresh = if (!savedToken.isNullOrBlank()) {
                        UserApiService.fetchCurrentUser(savedToken)
                    } else null
                    if (fresh == null) {
                        fresh = UserApiService.fetchUserById(savedUser.id, savedToken)
                    }
                    if (fresh != null) {
                        val updated = savedUser.copy(
                            name = fresh.name.ifBlank { savedUser.name },
                            username = fresh.username.ifBlank { savedUser.username },
                            email = if (fresh.email.isNotBlank()) fresh.email else savedUser.email,
                            profileImageUrl = fresh.profileImageUrl.ifBlank { savedUser.profileImageUrl },
                            coverImageUrl = fresh.coverImageUrl.ifBlank { savedUser.coverImageUrl },
                            bio = fresh.bio.ifBlank { savedUser.bio },
                            location = fresh.location.ifBlank { savedUser.location },
                            isVerified = fresh.isVerified || savedUser.isVerified,
                            role = fresh.role.ifBlank { savedUser.role },
                            followersCount = if (fresh.followersCount > 0) fresh.followersCount else savedUser.followersCount,
                            followingCount = if (fresh.followingCount > 0) fresh.followingCount else savedUser.followingCount
                        )
                        UserSessionManager.setCurrentUser(updated)
                        _uiState.update { it.copy(currentUser = updated) }
                        UserSessionManager.saveAuth(context, savedToken, updated)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            // Guest mode: unauthenticated user without login, using clean guest profile (id=0L)
            _uiState.update {
                it.copy(
                    currentUser = MockData.currentUser,
                    isLoggedIn = false,
                    isGuest = true,
                    showAuthModal = false
                )
            }
        }

        fetchPeopleYouMayKnow()
        fetchLiveUsers()
        refreshLiveProducts()
        refreshLiveEvents()
        refreshFeed()
        refreshGroups()
        refreshStories()
        refreshVideos()
        refreshFeedAds()
        refreshLiveSongs()
    }

    private fun enrichPostsWithAuthors(posts: List<Post>, users: List<User>): List<Post> {
        if (users.isEmpty() || posts.isEmpty()) return posts
        val userMap = users.associateBy { it.id }
        return posts.map { post ->
            val uId = if (post.userId > 0L) post.userId else post.author.id
            val matchedUser = userMap[uId]
            if (matchedUser != null) {
                val realName = matchedUser.name.ifBlank { matchedUser.username.removePrefix("@") }
                val author = post.author.copy(
                    id = matchedUser.id,
                    name = if (realName.isNotBlank() && !realName.startsWith("User")) realName else post.author.name,
                    username = if (matchedUser.username.isNotBlank()) matchedUser.username else post.author.username,
                    profileImageUrl = matchedUser.profileImageUrl.ifBlank { post.author.profileImageUrl },
                    isVerified = matchedUser.isVerified || post.author.isVerified,
                    role = matchedUser.role.ifBlank { post.author.role },
                    isFollowing = matchedUser.isFollowing || post.author.isFollowing,
                    followersCount = if (matchedUser.followersCount > 0) matchedUser.followersCount else post.author.followersCount
                )
                val updatedEvent = post.event?.let { ev ->
                    if (ev.organizer.name.isBlank() || ev.organizer.name.equals("Organizer", ignoreCase = true) || ev.organizer.id == author.id) {
                        ev.copy(organizer = author)
                    } else ev
                }
                post.copy(author = author, userId = matchedUser.id, event = updatedEvent)
            } else {
                post
            }
        }
    }

    private fun enrichEventsWithOrganizers(events: List<Event>, users: List<User>): List<Event> {
        if (events.isEmpty() || users.isEmpty()) return events
        val userMap = users.associateBy { it.id }
        return events.map { ev ->
            val matched = userMap[ev.organizer.id]
                ?: if (ev.organizer.name.isBlank() || ev.organizer.name.equals("Organizer", ignoreCase = true)) {
                    users.firstOrNull { it.name.isNotBlank() && !it.name.equals("Organizer", ignoreCase = true) }
                } else null
            if (matched != null) {
                val realName = matched.name.ifBlank { matched.username.removePrefix("@") }
                ev.copy(
                    organizer = ev.organizer.copy(
                        id = matched.id,
                        name = if (realName.isNotBlank() && !realName.startsWith("User")) realName else ev.organizer.name.ifBlank { realName },
                        username = if (matched.username.isNotBlank()) matched.username else ev.organizer.username,
                        profileImageUrl = matched.profileImageUrl.ifBlank { ev.organizer.profileImageUrl },
                        isVerified = matched.isVerified || ev.organizer.isVerified
                    )
                )
            } else ev
        }
    }

    fun refreshVideos() {
        launchSafe {
            _uiState.update { it.copy(isVideosLoading = true) }
            val uid = if (_uiState.value.isLoggedIn && !_uiState.value.isGuest) _uiState.value.currentUser.id else null
            val liveVideos = VideosApiService.fetchVideos(userId = uid, limit = 50)
            val enriched = enrichPostsWithAuthors(liveVideos, _uiState.value.users)
            _uiState.update { state ->
                state.copy(
                    videoPosts = enriched,
                    isVideosLoading = false
                )
            }
        }
    }

    fun refreshFeedAds() {
        launchSafe {
            _uiState.update { it.copy(isFeedAdsLoading = true) }
            val liveAds = AdsApiService.fetchFeedAds()
            _uiState.update { state ->
                state.copy(
                    sponsoredFeedAds = liveAds,
                    isFeedAdsLoading = false
                )
            }
        }
        fetchMyAds()
    }

    fun fetchMyAds() {
        val currentUserId = _uiState.value.currentUser.id
        if (currentUserId <= 0L || _uiState.value.isGuest) return
        launchSafe {
            val liveCampaigns = AdsApiService.fetchMyAds(currentUserId)
            if (liveCampaigns.isNotEmpty()) {
                _uiState.update { it.copy(adCampaigns = liveCampaigns) }
            }
        }
    }

    fun refreshFeed() {
        fetchPeopleYouMayKnow()
        fetchLiveUsers()
        launchSafe {
            _uiState.update { it.copy(isFeedLoading = true) }
            val isUserLoggedIn = _uiState.value.isLoggedIn && !_uiState.value.isGuest
            val livePosts = if (isUserLoggedIn) {
                // Logged in feed fetches live data from api/feeds
                PostsApiService.fetchFeedPosts(userId = _uiState.value.currentUser.id)
            } else {
                // Non-logged in feed fetches from api/posts
                PostsApiService.fetchPublicPosts()
            }
            val currentUid = if (isUserLoggedIn) _uiState.value.currentUser.id else 0L
            val myFollowData = if (currentUid > 0L) UserApiService.fetchUserFollowData(currentUid) else null
            val myFollowingSet = myFollowData?.following?.toSet() ?: emptySet()

            val currentUsers = _uiState.value.users.map { u ->
                if (myFollowingSet.contains(u.id)) u.copy(isFollowing = true) else u
            }
            val enrichedPosts = enrichPostsWithAuthors(livePosts, currentUsers).map { p ->
                val uId = if (p.userId > 0L) p.userId else p.author.id
                val authorWithFollow = if (myFollowingSet.contains(uId)) {
                    p.author.copy(isFollowing = true)
                } else p.author
                val cached = CommentsCache.getCachedComments(CommentsCache.resolveItemType(p), p.id)
                val finalComments = if (p.comments.isNotEmpty()) p.comments else (cached ?: p.comments)
                p.copy(author = authorWithFollow, comments = finalComments)
            }
            _uiState.update { state ->
                val updatedPymk = state.peopleYouMayKnow.map { u ->
                    if (myFollowingSet.contains(u.id)) u.copy(isFollowing = true) else u
                }
                val updatedCurrentUser = if (myFollowData != null) {
                    state.currentUser.copy(
                        followersCount = myFollowData.followers.size,
                        followingCount = myFollowData.following.size
                    )
                } else state.currentUser

                state.copy(
                    currentUser = updatedCurrentUser,
                    users = currentUsers,
                    peopleYouMayKnow = updatedPymk,
                    posts = enrichedPosts,
                    isFeedLoading = false
                )
            }

            // Check for missing authors whose ID is > 0 but not yet in cached users
            val missingUserIds = enrichedPosts
                .map { if (it.userId > 0L) it.userId else it.author.id }
                .filter { it > 0L && currentUsers.none { u -> u.id == it } }
                .distinct()

            if (missingUserIds.isNotEmpty()) {
                val fetchedUsers = mutableListOf<User>()
                for (uid in missingUserIds.take(15)) {
                    val u = UserApiService.fetchUserById(uid)
                    if (u != null) {
                        fetchedUsers.add(u)
                    }
                }
                if (fetchedUsers.isNotEmpty()) {
                    _uiState.update { state ->
                        val combined = (state.users + fetchedUsers).distinctBy { it.id }
                        val reEnriched = enrichPostsWithAuthors(state.posts, combined)
                        val reEnrichedVideos = enrichPostsWithAuthors(state.videoPosts, combined)
                        state.copy(users = combined, posts = reEnriched, videoPosts = reEnrichedVideos)
                    }
                }
            }
        }
    }

    fun fetchUserProfilePosts(userId: Long) {
        launchSafe {
            _uiState.update { it.copy(isProfilePostsLoading = true) }
            val userPosts = PostsApiService.fetchUserPosts(userId)
            val currentUsers = _uiState.value.users
            val enriched = enrichPostsWithAuthors(userPosts, currentUsers)
            _uiState.update { state ->
                val updatedMap = state.userProfilePosts + (userId to enriched)
                state.copy(
                    userProfilePosts = updatedMap,
                    isProfilePostsLoading = false
                )
            }
        }
    }

    fun refreshLiveProducts() {
        launchSafe {
            _uiState.update { it.copy(isProductsLoading = true) }
            val liveProducts = MarketplaceApiService.fetchLiveProducts()
            _uiState.update { state ->
                state.copy(
                    products = liveProducts,
                    isProductsLoading = false
                )
            }
        }
    }

    fun refreshLiveEvents() {
        launchSafe {
            val uid = if (_uiState.value.isLoggedIn && !_uiState.value.isGuest) _uiState.value.currentUser.id else null
            val liveEvents = EventsApiService.fetchLiveEvents(uid)
            if (liveEvents.isNotEmpty()) {
                val enriched = enrichEventsWithOrganizers(liveEvents, _uiState.value.users)
                _uiState.update { state ->
                    state.copy(events = enriched)
                }
            }
        }
    }

    fun fetchPeopleYouMayKnow() {
        launchSafe {
            val isUserLoggedIn = _uiState.value.isLoggedIn && !_uiState.value.isGuest
            val currentUid = if (isUserLoggedIn) _uiState.value.currentUser.id else 0L
            _uiState.update { it.copy(isPymkLoading = true) }
            val suggestions = UserApiService.fetchSuggestions(if (currentUid > 0L) currentUid else 1L, limit = 20)
            val hidden = _uiState.value.hiddenPymkIds
            val filtered = suggestions.filter { (currentUid <= 0L || it.id != currentUid) && !hidden.contains(it.id) }
            _uiState.update { state ->
                state.copy(
                    peopleYouMayKnow = filtered,
                    isPymkLoading = false
                )
            }
        }
    }

    fun fetchLiveUsers() {
        launchSafe {
            val liveUsers = UserApiService.fetchLiveUsers(limit = 50)
            if (liveUsers.isNotEmpty()) {
                _uiState.update { state ->
                    val allCombined = if (state.isLoggedIn && !state.isGuest && state.currentUser.id > 0L) {
                        (listOf(state.currentUser) + liveUsers).distinctBy { it.id }
                    } else {
                        liveUsers.distinctBy { it.id }
                    }
                    val enrichedPosts = enrichPostsWithAuthors(state.posts, allCombined)
                    val enrichedVideos = enrichPostsWithAuthors(state.videoPosts, allCombined)
                    state.copy(
                        users = allCombined,
                        posts = enrichedPosts,
                        videoPosts = enrichedVideos
                    )
                }
            }
        }
    }

    fun dismissPymk(userId: Long) {
        _uiState.update { state ->
            state.copy(
                hiddenPymkIds = state.hiddenPymkIds + userId,
                peopleYouMayKnow = state.peopleYouMayKnow.filter { it.id != userId }
            )
        }
    }

    fun setScreen(screen: Screen) {
        _uiState.update {
            it.copy(
                currentScreen = screen,
                activeConversationId = if (screen != Screen.MESSAGES) null else it.activeConversationId
            )
        }
        if (screen == Screen.MARKETPLACE) {
            refreshLiveProducts()
        } else if (screen == Screen.EVENTS) {
            refreshLiveEvents()
        } else if (screen == Screen.DISCOVER_PEOPLE) {
            fetchPeopleYouMayKnow()
            fetchLiveUsers()
        } else if (screen == Screen.FEED) {
            refreshFeed()
            refreshStories()
        } else if (screen == Screen.REELS) {
            refreshVideos()
            refreshFeedAds()
        } else if (screen == Screen.PROFILE) {
            val uid = _uiState.value.selectedUserProfile?.id ?: _uiState.value.currentUser.id
            fetchUserProfilePosts(uid)
        } else if (screen == Screen.GROUPS) {
            refreshGroups()
        } else if (screen == Screen.MUSIC) {
            refreshLiveSongs()
        }
    }

    fun setMarketMode(mode: String) {
        _uiState.update { it.copy(marketMode = mode) }
    }

    fun setSelectedCountry(country: String) {
        _uiState.update { it.copy(selectedCountry = country) }
    }

    fun setSelectedCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun toggleLikePost(postId: Long) {
        reactToPost(postId, "like")
    }

    fun reactToPost(postId: Long, reaction: String) {
        if (_uiState.value.isGuest || !_uiState.value.isLoggedIn) {
            openAuth("login")
            return
        }
        val eventMatch = _uiState.value.events.find { it.id == postId }
        val post = _uiState.value.posts.find { it.id == postId }
            ?: _uiState.value.videoPosts.find { it.id == postId }
            ?: eventMatch?.toPost()
        if (post == null) return

        val isSame = post.myReaction == reaction
        val newReaction = if (isSame) null else reaction
        val finalReactionToSend = newReaction ?: "unlike"

        _uiState.update { state ->
            val updatedPosts = state.posts.map { p ->
                if (p.id == postId) {
                    val prevHadReaction = p.myReaction != null
                    val newCount = when {
                        !prevHadReaction && newReaction != null -> p.likesCount + 1
                        prevHadReaction && newReaction == null -> (p.likesCount - 1).coerceAtLeast(0)
                        else -> p.likesCount
                    }
                    val existingWithoutMe = p.reactions.filter { it.userId != state.currentUser.id }
                    val updatedReactions = if (newReaction != null) {
                        existingWithoutMe + UserReaction(state.currentUser.id, state.currentUser.name, state.currentUser.profileImageUrl, newReaction)
                    } else existingWithoutMe
                    p.copy(
                        isLiked = newReaction != null,
                        myReaction = newReaction,
                        likesCount = newCount,
                        reactions = updatedReactions
                    )
                } else p
            }
            val updatedVideoPosts = state.videoPosts.map { p ->
                if (p.id == postId) {
                    val prevHadReaction = p.myReaction != null
                    val newCount = when {
                        !prevHadReaction && newReaction != null -> p.likesCount + 1
                        prevHadReaction && newReaction == null -> (p.likesCount - 1).coerceAtLeast(0)
                        else -> p.likesCount
                    }
                    val existingWithoutMe = p.reactions.filter { it.userId != state.currentUser.id }
                    val updatedReactions = if (newReaction != null) {
                        existingWithoutMe + UserReaction(state.currentUser.id, state.currentUser.name, state.currentUser.profileImageUrl, newReaction)
                    } else existingWithoutMe
                    p.copy(
                        isLiked = newReaction != null,
                        myReaction = newReaction,
                        likesCount = newCount,
                        reactions = updatedReactions
                    )
                } else p
            }
            val updatedEvents = state.events.map { ev ->
                if (ev.id == postId) {
                    val prevHadReaction = ev.myReaction != null
                    val newCount = when {
                        !prevHadReaction && newReaction != null -> ev.likesCount + 1
                        prevHadReaction && newReaction == null -> (ev.likesCount - 1).coerceAtLeast(0)
                        else -> ev.likesCount
                    }
                    ev.copy(
                        isLiked = newReaction != null,
                        myReaction = newReaction,
                        likesCount = newCount
                    )
                } else ev
            }
            state.copy(
                posts = updatedPosts,
                videoPosts = updatedVideoPosts,
                events = updatedEvents,
                selectedPostForComments = if (state.selectedPostForComments?.id == postId) {
                    updatedPosts.find { it.id == postId } ?: updatedVideoPosts.find { it.id == postId }
                } else state.selectedPostForComments
            )
        }

        viewModelScope.launch {
            val liveResult = FeedInteractionApiService.reactToFeedItem(
                post = post,
                userId = _uiState.value.currentUser.id,
                reactionType = finalReactionToSend
            )
            _uiState.update { state ->
                val finalMyReaction = liveResult.myReaction
                val finalCount = liveResult.reactionsCount
                val finalLiked = finalMyReaction != null
                val updatedPosts = state.posts.map { p ->
                    if (p.id == postId) {
                        p.copy(
                            isLiked = finalLiked,
                            myReaction = finalMyReaction,
                            likesCount = finalCount,
                            sharesCount = liveResult.sharesCount ?: p.sharesCount,
                            commentsCount = liveResult.commentsCount ?: p.commentsCount
                        )
                    } else p
                }
                val updatedVideoPosts = state.videoPosts.map { p ->
                    if (p.id == postId) {
                        p.copy(
                            isLiked = finalLiked,
                            myReaction = finalMyReaction,
                            likesCount = finalCount,
                            sharesCount = liveResult.sharesCount ?: p.sharesCount,
                            commentsCount = liveResult.commentsCount ?: p.commentsCount
                        )
                    } else p
                }
                val updatedEvents = state.events.map { ev ->
                    if (ev.id == postId) {
                        ev.copy(
                            isLiked = finalLiked,
                            myReaction = finalMyReaction,
                            likesCount = finalCount,
                            sharesCount = liveResult.sharesCount ?: ev.sharesCount,
                            commentsCount = liveResult.commentsCount ?: ev.commentsCount
                        )
                    } else ev
                }
                state.copy(
                    posts = updatedPosts,
                    videoPosts = updatedVideoPosts,
                    events = updatedEvents,
                    selectedPostForComments = if (state.selectedPostForComments?.id == postId) {
                        updatedPosts.find { it.id == postId } ?: updatedVideoPosts.find { it.id == postId }
                    } else state.selectedPostForComments
                )
            }
        }
    }

    fun toggleSavePost(postId: Long) {
        if (_uiState.value.isGuest || !_uiState.value.isLoggedIn) {
            openAuth("login")
            return
        }
        _uiState.update { state ->
            state.copy(
                posts = state.posts.map {
                    if (it.id == postId) it.copy(isSaved = !it.isSaved) else it
                }
            )
        }
    }

    fun toggleSaveReel(reelId: Long) {
        if (_uiState.value.isGuest || !_uiState.value.isLoggedIn) {
            openAuth("login")
            return
        }
        _uiState.update { state ->
            state.copy(
                reels = state.reels.map {
                    if (it.id == reelId) it.copy(isSaved = !it.isSaved) else it
                }
            )
        }
    }

    fun removeSavedItem(itemId: Long) {
        _uiState.update { state ->
            state.copy(
                posts = state.posts.map {
                    if (it.id == itemId) it.copy(isSaved = false) else it
                },
                reels = state.reels.map {
                    if (it.id == itemId) it.copy(isSaved = false) else it
                }
            )
        }
    }

    fun toggleFollowUser(userId: Long) {
        if (_uiState.value.isGuest || !_uiState.value.isLoggedIn) {
            openAuth("login")
            return
        }
        val currentUid = _uiState.value.currentUser.id
        if (userId <= 0L || userId == currentUid) return
        var newFollowState = false
        _uiState.update { state ->
            val targetUser = state.users.find { it.id == userId }
                ?: state.peopleYouMayKnow.find { it.id == userId }
                ?: state.posts.find { it.author.id == userId || it.userId == userId }?.author
                ?: (if (state.selectedUserProfile?.id == userId) state.selectedUserProfile else null)
            val currentFollowing = targetUser?.isFollowing ?: false
            val newFollowing = !currentFollowing
            newFollowState = newFollowing
            val newCount = if (newFollowing) {
                (targetUser?.followersCount ?: 0) + 1
            } else {
                ((targetUser?.followersCount ?: 1) - 1).coerceAtLeast(0)
            }

            val updatedUsers = state.users.map { user ->
                if (user.id == userId) {
                    user.copy(isFollowing = newFollowing, followersCount = newCount)
                } else user
            }
            val updatedPymk = state.peopleYouMayKnow.map { user ->
                if (user.id == userId) {
                    user.copy(isFollowing = newFollowing, followersCount = newCount)
                } else user
            }
            val updatedSelectedProfile = if (state.selectedUserProfile?.id == userId) {
                state.selectedUserProfile.copy(isFollowing = newFollowing, followersCount = newCount)
            } else state.selectedUserProfile

            val updatedPosts = state.posts.map { post ->
                if (post.author.id == userId || post.userId == userId) {
                    post.copy(author = post.author.copy(isFollowing = newFollowing, followersCount = newCount))
                } else post
            }
            val updatedVideoPosts = state.videoPosts.map { post ->
                if (post.author.id == userId || post.userId == userId) {
                    post.copy(author = post.author.copy(isFollowing = newFollowing, followersCount = newCount))
                } else post
            }
            val updatedStories = state.stories.map { story ->
                if (story.author.id == userId) {
                    story.copy(author = story.author.copy(isFollowing = newFollowing, followersCount = newCount))
                } else story
            }
            val updatedUserProfPosts = state.userProfilePosts.mapValues { (_, postList) ->
                postList.map { post ->
                    if (post.author.id == userId || post.userId == userId) {
                        post.copy(author = post.author.copy(isFollowing = newFollowing, followersCount = newCount))
                    } else post
                }
            }

            val updatedCurrentUser = if (newFollowState) {
                state.currentUser.copy(followingCount = state.currentUser.followingCount + 1)
            } else {
                state.currentUser.copy(followingCount = (state.currentUser.followingCount - 1).coerceAtLeast(0))
            }

            state.copy(
                currentUser = updatedCurrentUser,
                users = updatedUsers,
                peopleYouMayKnow = updatedPymk,
                selectedUserProfile = updatedSelectedProfile,
                posts = updatedPosts,
                videoPosts = updatedVideoPosts,
                stories = updatedStories,
                userProfilePosts = updatedUserProfPosts
            )
        }
        viewModelScope.launch {
            if (newFollowState) {
                UserApiService.followUser(currentUid, userId)
            } else {
                UserApiService.unfollowUser(currentUid, userId)
            }
            val targetData = UserApiService.fetchUserFollowData(userId)
            val meData = UserApiService.fetchUserFollowData(currentUid)
            _uiState.update { state ->
                var nextState = state
                if (targetData != null) {
                    val isF = targetData.followers.contains(currentUid)
                    val followersCount = targetData.followers.size
                    val followingCount = targetData.following.size

                    nextState = nextState.copy(
                        users = nextState.users.map {
                            if (it.id == userId) it.copy(
                                followersCount = followersCount,
                                followingCount = followingCount,
                                isFollowing = isF
                            ) else it
                        },
                        peopleYouMayKnow = nextState.peopleYouMayKnow.map {
                            if (it.id == userId) it.copy(
                                followersCount = followersCount,
                                followingCount = followingCount,
                                isFollowing = isF
                            ) else it
                        },
                        selectedUserProfile = if (nextState.selectedUserProfile?.id == userId) {
                            nextState.selectedUserProfile?.copy(
                                followersCount = followersCount,
                                followingCount = followingCount,
                                isFollowing = isF
                            )
                        } else nextState.selectedUserProfile,
                        posts = nextState.posts.map { post ->
                            if (post.author.id == userId || post.userId == userId) {
                                post.copy(author = post.author.copy(
                                    followersCount = followersCount,
                                    followingCount = followingCount,
                                    isFollowing = isF
                                ))
                            } else post
                        },
                        videoPosts = nextState.videoPosts.map { post ->
                            if (post.author.id == userId || post.userId == userId) {
                                post.copy(author = post.author.copy(
                                    followersCount = followersCount,
                                    followingCount = followingCount,
                                    isFollowing = isF
                                ))
                            } else post
                        },
                        stories = nextState.stories.map { story ->
                            if (story.author.id == userId) {
                                story.copy(author = story.author.copy(
                                    followersCount = followersCount,
                                    followingCount = followingCount,
                                    isFollowing = isF
                                ))
                            } else story
                        }
                    )
                }
                if (meData != null) {
                    nextState = nextState.copy(
                        currentUser = nextState.currentUser.copy(
                            followersCount = meData.followers.size,
                            followingCount = meData.following.size
                        ),
                        selectedUserProfile = if (nextState.selectedUserProfile?.id == currentUid) {
                            nextState.selectedUserProfile?.copy(
                                followersCount = meData.followers.size,
                                followingCount = meData.following.size
                            )
                        } else nextState.selectedUserProfile
                    )
                }
                nextState
            }
        }
    }

    fun addComment(postId: Long, commentText: String) {
        if (_uiState.value.isGuest || !_uiState.value.isLoggedIn) {
            openAuth("login")
            return
        }
        if (commentText.isBlank()) return
        val post = _uiState.value.posts.find { it.id == postId }
            ?: _uiState.value.videoPosts.find { it.id == postId }
            ?: _uiState.value.selectedPostForComments
            ?: _uiState.value.events.find { it.id == postId }?.toPost()

        val tempId = -System.currentTimeMillis()
        val author = _uiState.value.currentUser
        val newComment = Comment(
            id = tempId,
            author = author,
            text = commentText.trim(),
            timestamp = "Just now"
        )
        _uiState.update { state ->
            val updatedPosts = state.posts.map { p ->
                if (p.id == postId) {
                    val filtered = p.comments.filterNot { it.id == tempId || (it.id < 0 && it.text.trim() == newComment.text.trim() && it.author.id == author.id) }
                    p.copy(
                        comments = listOf(newComment) + filtered,
                        commentsCount = p.commentsCount + 1
                    )
                } else p
            }
            val updatedVideoPosts = state.videoPosts.map { p ->
                if (p.id == postId) {
                    val filtered = p.comments.filterNot { it.id == tempId || (it.id < 0 && it.text.trim() == newComment.text.trim() && it.author.id == author.id) }
                    p.copy(
                        comments = listOf(newComment) + filtered,
                        commentsCount = p.commentsCount + 1
                    )
                } else p
            }
            val updatedEvents = state.events.map { ev ->
                if (ev.id == postId) {
                    ev.copy(commentsCount = ev.commentsCount + 1)
                } else ev
            }
            val isProd = post?.product != null || post?.postType == "product"
            val pid = post?.product?.id ?: postId
            val updatedProducts = if (isProd) {
                state.products.map { prod ->
                    if (prod.id == pid) prod.copy(commentsCount = prod.commentsCount + 1) else prod
                }
            } else state.products
            val updatedSel = if (state.selectedPostForComments?.id == postId) {
                val existing = state.selectedPostForComments?.comments ?: emptyList()
                val filtered = existing.filterNot { it.id == tempId || (it.id < 0 && it.text.trim() == newComment.text.trim() && it.author.id == author.id) }
                state.selectedPostForComments?.copy(
                    comments = listOf(newComment) + filtered,
                    commentsCount = (state.selectedPostForComments?.commentsCount ?: 0) + 1
                )
            } else state.selectedPostForComments
            state.copy(
                posts = updatedPosts,
                videoPosts = updatedVideoPosts,
                events = updatedEvents,
                products = updatedProducts,
                selectedPostForComments = updatedSel
            )
        }

        val targetPost = post ?: Post(id = postId, author = _uiState.value.currentUser, content = "", timestamp = "")
        viewModelScope.launch {
            val result = FeedInteractionApiService.addComment(
                post = targetPost,
                userId = _uiState.value.currentUser.id,
                text = commentText.trim()
            )
            val liveComment = result?.comment
            val serverCommentsCount = result?.commentsCount

            if (liveComment != null) {
                val fullLiveComment = liveComment.copy(
                    author = liveComment.author.copy(
                        name = liveComment.author.name.ifBlank { author.name },
                        profileImageUrl = liveComment.author.profileImageUrl.ifBlank { author.profileImageUrl },
                        isVerified = liveComment.author.isVerified || author.isVerified
                    )
                )

                _uiState.update { state ->
                    val updatedPosts = state.posts.map { p ->
                        if (p.id == postId) {
                            val finalCount = serverCommentsCount ?: p.commentsCount
                            val reconciled = p.comments.map { c ->
                                if (c.id == tempId || (c.id < 0 && c.text.trim() == newComment.text.trim() && c.author.id == author.id)) {
                                    fullLiveComment
                                } else c
                            }.distinctBy { it.id }
                            p.copy(
                                comments = if (reconciled.any { it.id == fullLiveComment.id }) reconciled else listOf(fullLiveComment) + reconciled,
                                commentsCount = finalCount
                            )
                        } else p
                    }
                    val updatedVideoPosts = state.videoPosts.map { p ->
                        if (p.id == postId) {
                            val finalCount = serverCommentsCount ?: p.commentsCount
                            val reconciled = p.comments.map { c ->
                                if (c.id == tempId || (c.id < 0 && c.text.trim() == newComment.text.trim() && c.author.id == author.id)) {
                                    fullLiveComment
                                } else c
                            }.distinctBy { it.id }
                            p.copy(
                                comments = if (reconciled.any { it.id == fullLiveComment.id }) reconciled else listOf(fullLiveComment) + reconciled,
                                commentsCount = finalCount
                            )
                        } else p
                    }
                    val updatedSel = if (state.selectedPostForComments?.id == postId) {
                        val finalCount = serverCommentsCount ?: (state.selectedPostForComments?.commentsCount ?: 1)
                        val existing = state.selectedPostForComments?.comments ?: emptyList()
                        val reconciled = existing.map { c ->
                            if (c.id == tempId || (c.id < 0 && c.text.trim() == newComment.text.trim() && c.author.id == author.id)) {
                                fullLiveComment
                            } else c
                        }.distinctBy { it.id }
                        state.selectedPostForComments?.copy(
                            comments = if (reconciled.any { it.id == fullLiveComment.id }) reconciled else listOf(fullLiveComment) + reconciled,
                            commentsCount = finalCount
                        )
                    } else state.selectedPostForComments

                    state.copy(
                        posts = updatedPosts,
                        videoPosts = updatedVideoPosts,
                        selectedPostForComments = updatedSel
                    )
                }
            }

            // fetchCommentsSilently() to reconcile from database
            try {
                val freshComments = FeedInteractionApiService.fetchComments(targetPost, _uiState.value.currentUser.id)
                if (freshComments.isNotEmpty()) {
                    _uiState.update { state ->
                        val updatedPosts = state.posts.map { p ->
                            if (p.id == postId) {
                                p.copy(comments = freshComments)
                            } else p
                        }
                        val updatedVideoPosts = state.videoPosts.map { p ->
                            if (p.id == postId) {
                                p.copy(comments = freshComments)
                            } else p
                        }
                        val updatedSel = if (state.selectedPostForComments?.id == postId) {
                            state.selectedPostForComments?.copy(comments = freshComments)
                        } else state.selectedPostForComments

                        state.copy(
                            posts = updatedPosts,
                            videoPosts = updatedVideoPosts,
                            selectedPostForComments = updatedSel
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addReply(postId: Long, parentCommentId: Long, replyText: String, replyToAuthorName: String? = null) {
        if (_uiState.value.isGuest || !_uiState.value.isLoggedIn) {
            openAuth("login")
            return
        }
        if (replyText.isBlank()) return

        val tempId = -System.currentTimeMillis()
        val author = _uiState.value.currentUser
        val newReply = Comment(
            id = tempId,
            author = author,
            text = replyText.trim(),
            timestamp = "Just now",
            parentCommentId = parentCommentId,
            replyToAuthorName = replyToAuthorName
        )

        _uiState.update { state ->
            val updatedPosts = state.posts.map { p ->
                if (p.id == postId) {
                    val updatedComments = p.comments.map { c ->
                        if (c.id == parentCommentId) {
                            c.copy(replies = c.replies.filter { it.id != tempId } + newReply)
                        } else c
                    }
                    p.copy(comments = updatedComments, commentsCount = p.commentsCount + 1)
                } else p
            }
            val updatedVideoPosts = state.videoPosts.map { p ->
                if (p.id == postId) {
                    val updatedComments = p.comments.map { c ->
                        if (c.id == parentCommentId) {
                            c.copy(replies = c.replies.filter { it.id != tempId } + newReply)
                        } else c
                    }
                    p.copy(comments = updatedComments, commentsCount = p.commentsCount + 1)
                } else p
            }
            val updatedSel = if (state.selectedPostForComments?.id == postId) {
                val updatedComments = (state.selectedPostForComments?.comments ?: emptyList()).map { c ->
                    if (c.id == parentCommentId) {
                        c.copy(replies = c.replies.filter { it.id != tempId } + newReply)
                    } else c
                }
                state.selectedPostForComments?.copy(
                    comments = updatedComments,
                    commentsCount = (state.selectedPostForComments?.commentsCount ?: 0) + 1
                )
            } else state.selectedPostForComments

            state.copy(
                posts = updatedPosts,
                videoPosts = updatedVideoPosts,
                selectedPostForComments = updatedSel
            )
        }
    }

    fun deleteComment(postId: Long, commentId: Long) {
        val post = _uiState.value.posts.find { it.id == postId }
            ?: _uiState.value.videoPosts.find { it.id == postId }
            ?: _uiState.value.selectedPostForComments
        _uiState.update { state ->
            val updatedPosts = state.posts.map { p ->
                if (p.id == postId) {
                    val remaining = p.comments.filter { it.id != commentId }
                    p.copy(
                        comments = remaining,
                        commentsCount = (p.commentsCount - 1).coerceAtLeast(0)
                    )
                } else p
            }
            val updatedVideoPosts = state.videoPosts.map { p ->
                if (p.id == postId) {
                    val remaining = p.comments.filter { it.id != commentId }
                    p.copy(
                        comments = remaining,
                        commentsCount = (p.commentsCount - 1).coerceAtLeast(0)
                    )
                } else p
            }
            val updatedGroupPosts = state.groupPosts.map { gp ->
                if (gp.id == postId) {
                    val remaining = gp.comments.filter { it.id != commentId }
                    gp.copy(
                        comments = remaining,
                        commentsCount = (gp.commentsCount - 1).coerceAtLeast(0)
                    )
                } else gp
            }
            state.copy(
                posts = updatedPosts,
                videoPosts = updatedVideoPosts,
                groupPosts = updatedGroupPosts,
                selectedPostForComments = updatedPosts.find { it.id == postId } ?: updatedVideoPosts.find { it.id == postId }
            )
        }
        val targetPost = post ?: Post(id = postId, author = _uiState.value.currentUser, content = "", timestamp = "")
        viewModelScope.launch {
            FeedInteractionApiService.deleteComment(
                itemType = targetPost.postType,
                commentId = commentId,
                userId = _uiState.value.currentUser.id,
                parentId = targetPost.id
            )
        }
    }

    fun hideComment(postId: Long, commentId: Long, isHidden: Boolean) {
        val post = _uiState.value.posts.find { it.id == postId }
            ?: _uiState.value.videoPosts.find { it.id == postId }
            ?: _uiState.value.selectedPostForComments
        _uiState.update { state ->
            fun updateCommentList(comments: List<Comment>): List<Comment> {
                return comments.map { c ->
                    if (c.id == commentId) c.copy(isHidden = isHidden) else c
                }
            }
            val updatedPosts = state.posts.map { p ->
                if (postId <= 0L || p.id == postId) p.copy(comments = updateCommentList(p.comments)) else p
            }
            val updatedVideoPosts = state.videoPosts.map { p ->
                if (postId <= 0L || p.id == postId) p.copy(comments = updateCommentList(p.comments)) else p
            }
            val updatedGroupPosts = state.groupPosts.map { gp ->
                if (postId <= 0L || gp.id == postId) gp.copy(comments = updateCommentList(gp.comments)) else gp
            }
            state.copy(
                posts = updatedPosts,
                videoPosts = updatedVideoPosts,
                groupPosts = updatedGroupPosts,
                selectedPostForComments = state.selectedPostForComments?.let {
                    if (postId <= 0L || it.id == postId) it.copy(comments = updateCommentList(it.comments)) else it
                }
            )
        }
        val targetPost = post ?: Post(id = postId, author = _uiState.value.currentUser, content = "", timestamp = "")
        viewModelScope.launch {
            FeedInteractionApiService.hideComment(
                itemType = targetPost.postType,
                commentId = commentId,
                userId = _uiState.value.currentUser.id,
                parentId = targetPost.id,
                action = if (isHidden) "hide" else "unhide"
            )
        }
    }

    fun likeComment(commentId: Long) {
        likeComment(0L, commentId)
    }

    fun likeComment(postId: Long, commentId: Long) {
        CommentsCache.updateCachedComment(commentId) { c ->
            val newLiked = !c.isLiked
            val newCount = if (newLiked) c.likesCount + 1 else (c.likesCount - 1).coerceAtLeast(0)
            c.copy(isLiked = newLiked, likesCount = newCount)
        }
        val post = _uiState.value.posts.find { it.id == postId }
            ?: _uiState.value.videoPosts.find { it.id == postId }
            ?: _uiState.value.selectedPostForComments
        _uiState.update { state ->
            fun updateCommentList(comments: List<Comment>): List<Comment> {
                return comments.map { c ->
                    if (c.id == commentId) {
                        val newLiked = !c.isLiked
                        val newCount = if (newLiked) c.likesCount + 1 else (c.likesCount - 1).coerceAtLeast(0)
                        c.copy(isLiked = newLiked, likesCount = newCount)
                    } else c
                }
            }
            val updatedPosts = state.posts.map { p ->
                if (postId <= 0L || p.id == postId) p.copy(comments = updateCommentList(p.comments)) else p
            }
            val updatedVideoPosts = state.videoPosts.map { p ->
                if (postId <= 0L || p.id == postId) p.copy(comments = updateCommentList(p.comments)) else p
            }
            val updatedGroupPosts = state.groupPosts.map { gp ->
                if (postId <= 0L || gp.id == postId) gp.copy(comments = updateCommentList(gp.comments)) else gp
            }
            state.copy(
                posts = updatedPosts,
                videoPosts = updatedVideoPosts,
                groupPosts = updatedGroupPosts,
                selectedPostForComments = state.selectedPostForComments?.let {
                    if (postId <= 0L || it.id == postId) it.copy(comments = updateCommentList(it.comments)) else it
                }
            )
        }
        val targetPost = post ?: Post(id = postId, author = _uiState.value.currentUser, content = "", timestamp = "")
        viewModelScope.launch {
            FeedInteractionApiService.likeComment(
                itemType = targetPost.postType,
                commentId = commentId,
                userId = _uiState.value.currentUser.id
            )
        }
    }

    fun editComment(commentId: Long, newText: String) {
        viewModelScope.launch {
            VideosApiService.editComment(commentId, _uiState.value.currentUser.id, newText)
        }
    }

    fun createPost(
        context: Context? = null,
        content: String,
        mediaUrl: String? = null,
        feeling: String? = null,
        mediaUrls: List<String> = emptyList(),
        location: String? = null,
        backgroundStyle: String? = null,
        taggedUsers: List<Long> = emptyList(),
        explicitMediaType: String? = null
    ) {
        if (_uiState.value.isGuest || !_uiState.value.isLoggedIn) {
            openAuth("login")
            return
        }
        val trimmed = content.trim()
        val allMedia = if (mediaUrls.isNotEmpty()) mediaUrls else if (!mediaUrl.isNullOrBlank()) listOf(mediaUrl) else emptyList()
        if (trimmed.isBlank() && allMedia.isEmpty() && backgroundStyle.isNullOrBlank()) return

        val currentUser = _uiState.value.currentUser

        var hasVideos = false
        var hasPhotos = false
        if (explicitMediaType == "video") {
            hasVideos = true
        } else if (explicitMediaType == "image") {
            hasPhotos = allMedia.isNotEmpty()
        } else {
            for (m in allMedia) {
                if (isVideoMediaUri(context, m)) {
                    hasVideos = true
                } else {
                    hasPhotos = true
                }
            }
            if (hasVideos) hasPhotos = false
        }

        // Preview for the progress banner
        val previewUrl = allMedia.firstOrNull()

        // 1. Initial State: 15% - "Uploading your post…"
        _uiState.update { state ->
            state.copy(
                showCreatePostDialog = false,
                postUploadState = PostUploadState(
                    isUploading = true,
                    progress = 15f,
                    title = "Uploading your post…",
                    secondaryStatus = if (hasPhotos) {
                        "Please wait while your photos are being uploaded."
                    } else if (hasVideos) {
                        "Uploading and processing video..."
                    } else {
                        "Publishing post to your timeline..."
                    },
                    previewUrl = previewUrl,
                    isSuccess = false
                )
            )
        }

        viewModelScope.launch {
            try {
                var finalMediaUrl: String? = null
                var finalMediaType: String? = null
                val finalMediaUrls = mutableListOf<String>()
                val finalMediaTypes = mutableListOf<String>()
                val finalMediaMeta = mutableListOf<MediaMetaItem>()
                var primaryVideoUrl: String? = null
                var primaryThumbUrl: String? = null

                if (hasPhotos && !hasVideos && allMedia.isNotEmpty() && context != null) {
                    // ==========================================
                    // BRANCH 1: IMAGE(S) — Compression -> R2
                    // ==========================================
                    _uiState.update { state ->
                        state.copy(
                            postUploadState = state.postUploadState?.copy(
                                progress = 30f,
                                secondaryStatus = "Preparing and optimizing photos..."
                            )
                        )
                    }

                    var completedFiles = 0
                    for ((idx, item) in allMedia.withIndex()) {
                        if (item.startsWith("content://") || item.startsWith("file://")) {
                            val uri = Uri.parse(item)
                            val bundleResult = MediaUploadHelper.buildImageBundleAndUpload(context, uri)
                            if (bundleResult != null) {
                                val feed = bundleResult.feedUrl
                                val thumb = bundleResult.thumbUrl
                                val full = bundleResult.originalUrl
                                finalMediaUrls.add(feed)
                                finalMediaTypes.add("image")
                                finalMediaMeta.add(
                                    MediaMetaItem(
                                        thumb = thumb,
                                        feed = feed,
                                        full = full,
                                        original = null,
                                        thumbnailUrl = null,
                                        resolution720 = null,
                                        resolution1080 = null,
                                        type = "image",
                                        width = bundleResult.width,
                                        height = bundleResult.height
                                    )
                                )
                            } else {
                                finalMediaUrls.add(item)
                                finalMediaTypes.add("image")
                                finalMediaMeta.add(
                                    MediaMetaItem(
                                        thumb = item,
                                        feed = item,
                                        full = item,
                                        original = null,
                                        thumbnailUrl = null,
                                        resolution720 = null,
                                        resolution1080 = null,
                                        type = "image"
                                    )
                                )
                            }
                        } else {
                            finalMediaUrls.add(item)
                            finalMediaTypes.add("image")
                            finalMediaMeta.add(
                                MediaMetaItem(
                                    thumb = item,
                                    feed = item,
                                    full = item,
                                    original = null,
                                    thumbnailUrl = null,
                                    resolution720 = null,
                                    resolution1080 = null,
                                    type = "image"
                                )
                            )
                        }

                        completedFiles++
                        val pct = minOf(85f, 30f + ((completedFiles.toFloat() / allMedia.size) * 55f))
                        _uiState.update { state ->
                            state.copy(
                                postUploadState = state.postUploadState?.copy(
                                    progress = pct,
                                    secondaryStatus = if (allMedia.size > 1) {
                                        "Uploaded photo $completedFiles of ${allMedia.size}..."
                                    } else {
                                        "Please wait while your photos are being uploaded."
                                    }
                                )
                            )
                        }
                    }

                    finalMediaUrl = finalMediaUrls.firstOrNull()
                    finalMediaType = "image"

                } else if (hasVideos && allMedia.isNotEmpty() && context != null) {
                    // ==========================================
                    // BRANCH 2: VIDEO(S) — R2 (no /api/reels call)
                    // ==========================================
                    _uiState.update { state ->
                        state.copy(
                            postUploadState = state.postUploadState?.copy(
                                progress = 40f,
                                secondaryStatus = "Uploading and processing video..."
                            )
                        )
                    }

                    val videoItem = allMedia.find {
                        val mime = try { context.contentResolver.getType(Uri.parse(it)) ?: "" } catch (_: Exception) { "" }
                        mime.startsWith("video") || PostsApiService.isValidVideoUrl(it)
                    } ?: allMedia.first()

                    if (videoItem.startsWith("content://") || videoItem.startsWith("file://")) {
                        val uri = Uri.parse(videoItem)
                        val videoResult = MediaUploadHelper.processAndUploadVideo(
                            context = context,
                            uri = uri
                        ) { statusMsg, progressPct ->
                            _uiState.update { state ->
                                state.copy(
                                    postUploadState = state.postUploadState?.copy(
                                        progress = 40f + (progressPct * 0.45f), // 40% to 85%
                                        secondaryStatus = statusMsg
                                    )
                                )
                            }
                        }

                        if (videoResult != null) {
                            val origUrl = videoResult.originalUrl
                            val high1080Url = videoResult.url1080
                            val low720Url = videoResult.url720
                            val thumbUrl = videoResult.thumbUrl
                            val primaryPlaybackUrl = low720Url.ifBlank { origUrl }

                            primaryVideoUrl = primaryPlaybackUrl
                            primaryThumbUrl = thumbUrl

                            finalMediaUrl = primaryPlaybackUrl
                            finalMediaType = "video"

                            if (!thumbUrl.isNullOrBlank()) {
                                finalMediaUrls.addAll(listOf(origUrl, high1080Url, low720Url, thumbUrl))
                                finalMediaTypes.addAll(listOf("video", "video", "video", "image"))
                            } else {
                                finalMediaUrls.addAll(listOf(origUrl, high1080Url, low720Url))
                                finalMediaTypes.addAll(listOf("video", "video", "video"))
                            }

                            finalMediaMeta.add(
                                MediaMetaItem(
                                    original = origUrl,
                                    resolution1080 = high1080Url,
                                    resolution720 = low720Url,
                                    thumbnailUrl = thumbUrl,
                                    thumb = thumbUrl,
                                    feed = low720Url,
                                    full = high1080Url,
                                    type = "video",
                                    width = videoResult.width,
                                    height = videoResult.height,
                                    duration = videoResult.durationSec
                                )
                            )
                        } else {
                            finalMediaUrl = videoItem
                            finalMediaType = "video"
                            primaryVideoUrl = videoItem
                            finalMediaUrls.add(videoItem)
                            finalMediaTypes.add("video")
                            finalMediaMeta.add(MediaMetaItem(original = videoItem, type = "video"))
                        }
                    } else {
                        primaryVideoUrl = videoItem
                        finalMediaUrl = videoItem
                        finalMediaType = "video"
                        finalMediaUrls.add(videoItem)
                        finalMediaTypes.add("video")
                        finalMediaMeta.add(MediaMetaItem(original = videoItem, type = "video"))
                    }
                } else if (allMedia.isNotEmpty()) {
                    // Already uploaded HTTP URLs
                    finalMediaUrls.addAll(allMedia)
                    val isVid = if (explicitMediaType == "video") true else if (explicitMediaType == "image") false else allMedia.any { isVideoMediaUri(context, it) }
                    finalMediaType = if (isVid) "video" else "image"
                    finalMediaUrl = allMedia.firstOrNull()
                    if (isVid) primaryVideoUrl = finalMediaUrl
                    finalMediaTypes.addAll(allMedia.map { if (isVid) "video" else "image" })
                    if (isVid) {
                        finalMediaMeta.add(MediaMetaItem(original = finalMediaUrl, resolution720 = finalMediaUrl, type = "video"))
                    } else {
                        finalMediaMeta.add(
                            MediaMetaItem(
                                thumb = finalMediaUrl,
                                feed = finalMediaUrl,
                                full = finalMediaUrl,
                                original = null,
                                thumbnailUrl = null,
                                resolution720 = null,
                                resolution1080 = null,
                                type = "image"
                            )
                        )
                    }
                }

                // ==========================================
                // 90%: Publishing post to timeline...
                // ==========================================
                _uiState.update { state ->
                    state.copy(
                        postUploadState = state.postUploadState?.copy(
                            progress = 90f,
                            secondaryStatus = "Publishing post to your timeline..."
                        )
                    )
                }

                val postType = if (finalMediaType == "video") "video" else if (finalMediaType == "image") "image" else "text"

                val isMediaPost = finalMediaUrls.isNotEmpty() || !finalMediaUrl.isNullOrBlank() || postType == "video" || postType == "image" || allMedia.isNotEmpty()
                val resolvedBackground = if (isMediaPost) null else {
                    if (backgroundStyle.isNullOrBlank() || backgroundStyle.startsWith("style:none") || backgroundStyle.equals("none", ignoreCase = true)) {
                        null
                    } else {
                        backgroundStyle
                    }
                }

                // SINGLE FINAL INSERT — images AND videos use /api/posts
                val livePost = PostsApiService.createPost(
                    userId = currentUser.id,
                    content = trimmed,
                    mediaUrl = finalMediaUrl,
                    mediaUrls = finalMediaUrls,
                    mediaType = finalMediaType,
                    mediaTypes = finalMediaTypes,
                    videoUrl = primaryVideoUrl,
                    thumbnailUrl = primaryThumbUrl,
                    mediaMeta = finalMediaMeta,
                    feeling = feeling,
                    location = location,
                    background = resolvedBackground,
                    taggedUsers = taggedUsers,
                    type = postType,
                    visibility = "public"
                )

                val newPost = Post(
                    id = livePost?.id ?: System.currentTimeMillis(),
                    author = currentUser,
                    content = trimmed,
                    mediaUrl = finalMediaUrl,
                    mediaUrls = finalMediaUrls,
                    mediaTypes = finalMediaTypes,
                    mediaMeta = finalMediaMeta,
                    timestamp = "Just now",
                    feeling = feeling,
                    location = location,
                    backgroundStyle = resolvedBackground,
                    taggedUsers = taggedUsers,
                    postType = postType,
                    videoUrl = primaryVideoUrl,
                    mediaType = finalMediaType,
                    likesCount = 0,
                    commentsCount = 0,
                    sharesCount = 0
                )

                _uiState.update { state ->
                    val resolvedPost = (livePost?.copy(author = currentUser) ?: newPost)
                    state.copy(
                        posts = listOf(resolvedPost) + state.posts.filter { it.id != resolvedPost.id },
                        postUploadState = state.postUploadState?.copy(
                            isUploading = false,
                            isSuccess = true,
                            progress = 100f,
                            title = "Post uploaded successfully!",
                            secondaryStatus = "Your post is now visible on your timeline."
                        )
                    )
                }

                // Auto-clear success banner after 2.8s matching web
                delay(2800)
                _uiState.update { it.copy(postUploadState = null) }

            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { state ->
                    state.copy(
                        postUploadState = state.postUploadState?.copy(
                            isUploading = false,
                            isSuccess = false,
                            progress = 0f,
                            title = "Upload failed",
                            secondaryStatus = e.message ?: "Failed to upload files. Please try again.",
                            error = e.message ?: "Upload error"
                        )
                    )
                }
                delay(4000)
                _uiState.update { it.copy(postUploadState = null) }
            }
        }
    }

    private fun isVideoMediaUri(context: Context?, uriString: String): Boolean {
        val u = uriString.trim().lowercase()
        // 1. Explicit image extensions -> always image!
        if (u.endsWith(".jpg") || u.endsWith(".jpeg") || u.endsWith(".png") ||
            u.endsWith(".webp") || u.endsWith(".gif") || u.endsWith(".bmp") ||
            u.endsWith(".heic") || u.endsWith(".heif") ||
            u.contains(".jpg?") || u.contains(".jpeg?") || u.contains(".png?") || u.contains(".webp?")
        ) {
            return false
        }
        // 2. Explicit video extensions -> video!
        if (u.endsWith(".mp4") || u.endsWith(".webm") || u.endsWith(".mov") ||
            u.endsWith(".m4v") || u.endsWith(".mkv") || u.endsWith(".3gp") ||
            u.contains(".mp4?") || u.contains(".webm?") || u.contains(".mov?")
        ) {
            return true
        }
        // 3. ContentResolver MIME type
        if (context != null && (u.startsWith("content://") || u.startsWith("file://"))) {
            val mime = try {
                context.contentResolver.getType(Uri.parse(uriString))?.lowercase()
            } catch (_: Exception) { null }

            if (!mime.isNullOrBlank()) {
                if (mime.startsWith("image/")) return false
                if (mime.startsWith("video/")) return true
            }
        }
        // 4. Remote HTTP URL
        if (u.startsWith("http://") || u.startsWith("https://")) {
            if (u.contains("/videos/") || u.contains("/reels/")) return true
        }
        return false
    }

    fun dismissPostUpload() {
        _uiState.update { it.copy(postUploadState = null) }
    }

    fun sharePost(postId: Long, destination: String = "feed", message: String = "") {
        if (_uiState.value.isGuest || !_uiState.value.isLoggedIn) {
            openAuth("login")
            return
        }
        val post = _uiState.value.posts.find { it.id == postId }
            ?: _uiState.value.videoPosts.find { it.id == postId }
            ?: _uiState.value.events.find { it.id == postId }?.toPost()
        if (post == null) return

        _uiState.update { state ->
            val updatedPosts = state.posts.map { p ->
                if (p.id == postId) {
                    p.copy(sharesCount = p.sharesCount + 1)
                } else p
            }
            val updatedVideoPosts = state.videoPosts.map { p ->
                if (p.id == postId) {
                    p.copy(sharesCount = p.sharesCount + 1)
                } else p
            }
            val updatedEvents = state.events.map { ev ->
                if (ev.id == postId) ev.copy(sharesCount = ev.sharesCount + 1) else ev
            }
            val isProd = post.product != null || post.postType == "product"
            val pid = post.product?.id ?: postId
            val updatedProducts = if (isProd) {
                state.products.map { prod ->
                    if (prod.id == pid) prod.copy(sharesCount = prod.sharesCount + 1) else prod
                }
            } else state.products
            state.copy(posts = updatedPosts, videoPosts = updatedVideoPosts, events = updatedEvents, products = updatedProducts)
        }

        viewModelScope.launch {
            val liveShare = FeedInteractionApiService.shareItem(
                post = post,
                userId = _uiState.value.currentUser.id,
                destination = destination,
                message = message
            )
            _uiState.update { state ->
                val finalSharesCount = liveShare.sharesCount ?: (post.sharesCount + 1)
                val updatedPosts = state.posts.map { p ->
                    if (p.id == postId) p.copy(sharesCount = finalSharesCount) else p
                }
                val updatedVideoPosts = state.videoPosts.map { p ->
                    if (p.id == postId) p.copy(sharesCount = finalSharesCount) else p
                }
                val updatedEvents = state.events.map { ev ->
                    if (ev.id == postId) ev.copy(sharesCount = finalSharesCount) else ev
                }
                val withSharedPost = if (liveShare.newPost != null && destination == "feed") {
                    val shared = enrichPostsWithAuthors(listOf(liveShare.newPost), state.users).firstOrNull() ?: liveShare.newPost
                    listOf(shared) + updatedPosts.filter { it.id != shared.id }
                } else updatedPosts
                state.copy(posts = withSharedPost, videoPosts = updatedVideoPosts, events = updatedEvents)
            }
        }
    }

    fun deletePost(postId: Long) {
        val post = _uiState.value.posts.find { it.id == postId }
        val pid = post?.product?.id ?: postId
        _uiState.update { state ->
            state.copy(
                posts = state.posts.filterNot { it.id == postId },
                products = if (post?.product != null || post?.postType == "product") state.products.filterNot { it.id == pid } else state.products
            )
        }
        if (post != null && (post.product != null || post.postType == "product")) {
            viewModelScope.launch {
                MarketplaceApiService.deleteProduct(pid, _uiState.value.currentUser.id)
            }
        }
    }

    fun editPost(postId: Long, newContent: String) {
        val post = _uiState.value.posts.find { it.id == postId }
        val pid = post?.product?.id ?: postId
        _uiState.update { state ->
            val updated = state.posts.map { p ->
                if (p.id == postId) p.copy(content = newContent) else p
            }
            val updatedProducts = state.products.map { prod ->
                if (prod.id == pid) prod.copy(description = newContent) else prod
            }
            state.copy(posts = updated, products = updatedProducts)
        }
        if (post != null && (post.product != null || post.postType == "product")) {
            viewModelScope.launch {
                MarketplaceApiService.updateProduct(pid, mapOf("description" to newContent))
            }
        }
    }

    fun hidePost(postId: Long) {
        _uiState.update { state ->
            state.copy(posts = state.posts.filterNot { it.id == postId })
        }
    }

    fun toggleLikeReel(reelId: Long) {
        if (_uiState.value.isGuest || !_uiState.value.isLoggedIn) {
            openAuth("login")
            return
        }
        _uiState.update { state ->
            state.copy(
                reels = state.reels.map { reel ->
                    if (reel.id == reelId) {
                        val newLiked = !reel.isLiked
                        val newLikes = if (newLiked) reel.likesCount + 1 else (reel.likesCount - 1).coerceAtLeast(0)
                        reel.copy(isLiked = newLiked, likesCount = newLikes)
                    } else reel
                }
            )
        }
    }

    fun toggleSaveProduct(productId: Long) {
        if (_uiState.value.isGuest || !_uiState.value.isLoggedIn) {
            openAuth("login")
            return
        }
        _uiState.update { state ->
            state.copy(
                products = state.products.map {
                    if (it.id == productId) it.copy(isSaved = !it.isSaved) else it
                }
            )
        }
    }

    fun createProduct(
        context: Context? = null,
        title: String,
        price: Double,
        discountPrice: Double? = null,
        category: String,
        location: String,
        country: String = "TZ",
        description: String,
        imageUrl: String,
        images: List<String> = emptyList(),
        phone: String = ""
    ) {
        if (_uiState.value.isGuest || !_uiState.value.isLoggedIn) {
            openAuth("login")
            return
        }
        val initialImages = if (images.isNotEmpty()) images else if (imageUrl.isNotBlank()) listOf(imageUrl) else listOf("https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?w=800")
        val productTitle = title.trim().ifBlank { "Product" }

        // 1. Initial State: 20% progress banner
        _uiState.update { state ->
            state.copy(
                showCreateProductDialog = false,
                postUploadState = PostUploadState(
                    isUploading = true,
                    progress = 20f,
                    title = "Publishing \"$productTitle\"…",
                    secondaryStatus = "Preparing product details...",
                    previewUrl = initialImages.firstOrNull(),
                    isSuccess = false
                )
            )
        }

        viewModelScope.launch {
            try {
                // 2. Upload any local image content uris to Cloudflare R2
                val finalUploadedImages = mutableListOf<String>()
                for ((index, img) in initialImages.withIndex()) {
                    if ((img.startsWith("content://") || img.startsWith("file://")) && context != null) {
                        _uiState.update { state ->
                            state.copy(
                                postUploadState = state.postUploadState?.copy(
                                    progress = 30f + ((index + 1).toFloat() / initialImages.size * 25f),
                                    secondaryStatus = "Uploading product photo ${index + 1} of ${initialImages.size}..."
                                )
                            )
                        }
                        val bundle = try {
                            MediaUploadHelper.buildImageBundleAndUpload(context, Uri.parse(img))
                        } catch (e: Exception) {
                            null
                        }
                        if (bundle != null && bundle.feedUrl.isNotBlank()) {
                            finalUploadedImages.add(bundle.feedUrl)
                        } else {
                            finalUploadedImages.add(img)
                        }
                    } else {
                        finalUploadedImages.add(img)
                    }
                }

                val primaryImage = finalUploadedImages.firstOrNull() ?: "https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?w=800"

                // 3. Registering with Marketplace API
                _uiState.update { state ->
                    state.copy(
                        postUploadState = state.postUploadState?.copy(
                            progress = 65f,
                            secondaryStatus = "Registering product in Marketplace..."
                        )
                    )
                }

                val productToSend = Product(
                    id = System.currentTimeMillis(),
                    title = productTitle,
                    price = price,
                    discountPrice = discountPrice,
                    category = category,
                    location = location.trim(),
                    country = country,
                    currency = if (country.equals("TZ", ignoreCase = true)) "TZS" else "USD",
                    imageUrl = primaryImage,
                    images = finalUploadedImages,
                    description = description.trim(),
                    seller = _uiState.value.currentUser,
                    phoneNumber = phone.ifBlank { "+255 712 345 678" },
                    isFresh = true
                )

                val published = MarketplaceApiService.publishProduct(productToSend)
                val finalProduct = published ?: productToSend

                // 4. Inject into local state
                _uiState.update { state ->
                    state.copy(
                        postUploadState = state.postUploadState?.copy(
                            progress = 85f,
                            secondaryStatus = "Displaying product in feed & marketplace..."
                        )
                    )
                }

                val feedPost = Post(
                    id = finalProduct.id,
                    author = _uiState.value.currentUser,
                    content = finalProduct.description,
                    mediaUrl = finalProduct.imageUrl,
                    mediaUrls = finalProduct.images,
                    timestamp = "Just now",
                    postType = "product",
                    product = finalProduct
                )

                _uiState.update { state ->
                    val userProfilePosts = state.userProfilePosts.toMutableMap()
                    val myPosts = userProfilePosts[state.currentUser.id] ?: emptyList()
                    userProfilePosts[state.currentUser.id] = listOf(feedPost) + myPosts
                    state.copy(
                        products = listOf(finalProduct) + state.products.filter { it.id != finalProduct.id },
                        posts = listOf(feedPost) + state.posts.filter { it.id != feedPost.id },
                        userProfilePosts = userProfilePosts
                    )
                }

                // 5. Success
                _uiState.update { state ->
                    state.copy(
                        postUploadState = state.postUploadState?.copy(
                            isUploading = false,
                            isSuccess = true,
                            progress = 100f,
                            title = "Listing published!",
                            secondaryStatus = "Your product is now live in the marketplace."
                        )
                    )
                }

                delay(2800)
                _uiState.update { it.copy(postUploadState = null) }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { state ->
                    state.copy(
                        postUploadState = state.postUploadState?.copy(
                            isUploading = false,
                            isSuccess = false,
                            progress = 0f,
                            title = "Failed to publish listing",
                            secondaryStatus = e.message ?: "Something went wrong. Please try again."
                        )
                    )
                }
                delay(3500)
                _uiState.update { it.copy(postUploadState = null) }
            }
        }
    }

    fun rsvpEvent(eventId: Long, newStatus: String, prevStatus: String = "") {
        if (_uiState.value.isGuest || !_uiState.value.isLoggedIn) {
            openAuth("login")
            return
        }
        val currentEvent = _uiState.value.events.find { it.id == eventId }
            ?: _uiState.value.posts.find { it.id == eventId && it.event != null }?.event
            ?: _uiState.value.posts.mapNotNull { it.event }.find { it.id == eventId }

        val actualPrevStatus = if (prevStatus.isNotBlank()) prevStatus else (currentEvent?.userRsvpStatus ?: if (currentEvent?.isGoing == true) "going" else if (currentEvent?.isInterested == true) "interested" else "")

        // Optimistic counts logic exactly matching web handleRSVPClick
        val prevAtt = currentEvent?.goingCount ?: 0
        val prevInt = currentEvent?.interestedCount ?: 0

        var nextAtt = prevAtt
        var nextInt = prevInt

        if (newStatus == "going") {
            if (actualPrevStatus == "going") nextAtt = maxOf(0, prevAtt - 1)
            else if (actualPrevStatus == "interested") {
                nextAtt = prevAtt + 1
                nextInt = maxOf(0, prevInt - 1)
            } else nextAtt = prevAtt + 1
        } else if (newStatus == "interested") {
            if (actualPrevStatus == "interested") nextInt = maxOf(0, prevInt - 1)
            else if (actualPrevStatus == "going") {
                nextInt = prevInt + 1
                nextAtt = maxOf(0, prevAtt - 1)
            } else nextInt = prevInt + 1
        } else {
            // not_going / remove
            if (actualPrevStatus == "going") nextAtt = maxOf(0, prevAtt - 1)
            if (actualPrevStatus == "interested") nextInt = maxOf(0, prevInt - 1)
        }

        val targetStatus = if (newStatus == "not_going") "" else newStatus
        val isGoing = targetStatus == "going"
        val isInterested = targetStatus == "interested"

        _uiState.update { state ->
            val updatedEvents = state.events.map { ev ->
                if (ev.id == eventId) {
                    ev.copy(
                        userRsvpStatus = targetStatus,
                        isGoing = isGoing,
                        isInterested = isInterested,
                        goingCount = nextAtt,
                        interestedCount = nextInt
                    )
                } else ev
            }
            val updatedPosts = state.posts.map { p ->
                if (p.event != null && p.event.id == eventId) {
                    val updatedEv = p.event.copy(
                        userRsvpStatus = targetStatus,
                        isGoing = isGoing,
                        isInterested = isInterested,
                        goingCount = nextAtt,
                        interestedCount = nextInt
                    )
                    p.copy(event = updatedEv)
                } else p
            }
            state.copy(events = updatedEvents, posts = updatedPosts)
        }

        viewModelScope.launch {
            val res = EventsApiService.rsvpEventDirect(
                eventId = eventId,
                userId = _uiState.value.currentUser.id,
                newStatus = newStatus,
                prevStatus = actualPrevStatus
            )
            if (res.success) {
                _uiState.update { state ->
                    val finalStatus = res.myStatus ?: targetStatus
                    val updatedEvents = state.events.map { ev ->
                        if (ev.id == eventId) {
                            ev.copy(
                                userRsvpStatus = finalStatus,
                                isGoing = finalStatus == "going",
                                isInterested = finalStatus == "interested",
                                goingCount = res.attendingCount ?: ev.goingCount,
                                interestedCount = res.interestedCount ?: ev.interestedCount
                            )
                        } else ev
                    }
                    val updatedPosts = state.posts.map { p ->
                        if (p.event != null && p.event.id == eventId) {
                            val updatedEv = p.event.copy(
                                userRsvpStatus = finalStatus,
                                isGoing = finalStatus == "going",
                                isInterested = finalStatus == "interested",
                                goingCount = res.attendingCount ?: p.event.goingCount,
                                interestedCount = res.interestedCount ?: p.event.interestedCount
                            )
                            p.copy(event = updatedEv)
                        } else p
                    }
                    state.copy(events = updatedEvents, posts = updatedPosts)
                }
            } else {
                // Rollback to previous state
                _uiState.update { state ->
                    val updatedEvents = state.events.map { ev ->
                        if (ev.id == eventId) {
                            ev.copy(
                                userRsvpStatus = actualPrevStatus,
                                isGoing = actualPrevStatus == "going",
                                isInterested = actualPrevStatus == "interested",
                                goingCount = prevAtt,
                                interestedCount = prevInt
                            )
                        } else ev
                    }
                    val updatedPosts = state.posts.map { p ->
                        if (p.event != null && p.event.id == eventId) {
                            val updatedEv = p.event.copy(
                                userRsvpStatus = actualPrevStatus,
                                isGoing = actualPrevStatus == "going",
                                isInterested = actualPrevStatus == "interested",
                                goingCount = prevAtt,
                                interestedCount = prevInt
                            )
                            p.copy(event = updatedEv)
                        } else p
                    }
                    state.copy(events = updatedEvents, posts = updatedPosts)
                }
            }
        }
    }

    fun toggleInterestedEvent(eventId: Long) {
        val currentEvent = _uiState.value.events.find { it.id == eventId }
            ?: _uiState.value.posts.find { it.id == eventId && it.event != null }?.event
            ?: _uiState.value.posts.mapNotNull { it.event }.find { it.id == eventId }
        val prev = currentEvent?.userRsvpStatus ?: if (currentEvent?.isInterested == true) "interested" else if (currentEvent?.isGoing == true) "going" else ""
        val next = if (prev == "interested") "" else "interested"
        rsvpEvent(eventId, next, prev)
    }

    fun toggleGoingEvent(eventId: Long) {
        val currentEvent = _uiState.value.events.find { it.id == eventId }
            ?: _uiState.value.posts.find { it.id == eventId && it.event != null }?.event
            ?: _uiState.value.posts.mapNotNull { it.event }.find { it.id == eventId }
        val prev = currentEvent?.userRsvpStatus ?: if (currentEvent?.isGoing == true) "going" else if (currentEvent?.isInterested == true) "interested" else ""
        val next = if (prev == "going") "" else "going"
        rsvpEvent(eventId, next, prev)
    }

    fun createEvent(
        context: Context? = null,
        title: String,
        date: String,
        location: String,
        description: String,
        imageUrl: String,
        time: String = "",
        visibility: String = "worldwide",
        groupId: Long? = null,
        groupName: String? = null
    ) {
        if (_uiState.value.isGuest || !_uiState.value.isLoggedIn) {
            openAuth("login")
            return
        }
        val eventTitle = title.trim().ifBlank { "Event" }
        val rawCover = imageUrl.trim().ifBlank { "https://images.unsplash.com/photo-1501281668745-f7f57925c3b4?w=800" }

        // 1. Initial State: 20% progress banner
        _uiState.update { state ->
            state.copy(
                showCreateEventDialog = false,
                postUploadState = PostUploadState(
                    isUploading = true,
                    progress = 20f,
                    title = "Publishing \"$eventTitle\"…",
                    secondaryStatus = "Preparing event details...",
                    previewUrl = rawCover,
                    isSuccess = false
                )
            )
        }

        viewModelScope.launch {
            try {
                // 2. Upload cover if local uri
                var finalCover = rawCover
                if ((rawCover.startsWith("content://") || rawCover.startsWith("file://")) && context != null) {
                    _uiState.update { state ->
                        state.copy(
                            postUploadState = state.postUploadState?.copy(
                                progress = 40f,
                                secondaryStatus = "Uploading event cover image..."
                            )
                        )
                    }
                    val bundle = try {
                        MediaUploadHelper.buildImageBundleAndUpload(context, Uri.parse(rawCover))
                    } catch (e: Exception) {
                        null
                    }
                    if (bundle != null && bundle.feedUrl.isNotBlank()) {
                        finalCover = bundle.feedUrl
                    }
                }

                // Format ISO date
                val d = date.substringBefore(" • ").trim()
                val t = time.ifBlank { "12:00" }
                val eventDateISO = try {
                    if (d.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
                        "${d}T${t}:00.000Z"
                    } else {
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                            timeZone = java.util.TimeZone.getTimeZone("UTC")
                        }
                        sdf.format(java.util.Date())
                    }
                } catch (_: Exception) {
                    "${d}T12:00:00.000Z"
                }

                // 3. Register with API
                _uiState.update { state ->
                    state.copy(
                        postUploadState = state.postUploadState?.copy(
                            progress = 55f,
                            secondaryStatus = "Registering event with Feathered..."
                        )
                    )
                }

                val currentUser = _uiState.value.currentUser
                val createdEvent = EventsApiService.createEvent(
                    title = eventTitle,
                    description = description.trim(),
                    eventDateISO = eventDateISO,
                    eventTime = t,
                    location = location.trim(),
                    visibility = visibility.ifBlank { "worldwide" },
                    coverUrl = finalCover,
                    creator = currentUser,
                    groupId = groupId
                )

                val newEvent = createdEvent ?: Event(
                    id = System.currentTimeMillis(),
                    title = eventTitle,
                    date = date,
                    location = location.trim(),
                    description = description.trim(),
                    imageUrl = finalCover,
                    organizer = currentUser,
                    time = t,
                    visibility = visibility,
                    groupId = groupId,
                    groupName = groupName,
                    createdAt = "Just now"
                )

                // 4. Inject into feed & events state
                _uiState.update { state ->
                    state.copy(
                        postUploadState = state.postUploadState?.copy(
                            progress = 85f,
                            secondaryStatus = "Displaying event in feed..."
                        )
                    )
                }

                // In web App.tsx, Events are stored in events table and a feed post is injected locally:
                val eventFeedPost = Post(
                    id = newEvent.id,
                    author = currentUser,
                    content = newEvent.title,
                    mediaUrl = newEvent.imageUrl,
                    mediaUrls = if (newEvent.imageUrl.isNotBlank()) listOf(newEvent.imageUrl) else emptyList(),
                    timestamp = "Just now",
                    postType = "event",
                    event = newEvent
                )

                _uiState.update { state ->
                    state.copy(
                        events = listOf(newEvent) + state.events.filter { it.id != newEvent.id },
                        posts = listOf(eventFeedPost) + state.posts.filter { it.id != eventFeedPost.id }
                    )
                }

                // 5. Success
                _uiState.update { state ->
                    state.copy(
                        postUploadState = state.postUploadState?.copy(
                            isUploading = false,
                            isSuccess = true,
                            progress = 100f,
                            title = "Event published!",
                            secondaryStatus = "Your event is now live for attendees."
                        )
                    )
                }

                delay(2800)
                _uiState.update { it.copy(postUploadState = null) }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { state ->
                    state.copy(
                        postUploadState = state.postUploadState?.copy(
                            isUploading = false,
                            isSuccess = false,
                            progress = 0f,
                            title = "Failed to create event",
                            secondaryStatus = e.message ?: "Something went wrong. Please try again."
                        )
                    )
                }
                delay(3500)
                _uiState.update { it.copy(postUploadState = null) }
            }
        }
    }

    // ==========================================
    // MESSAGING & CALLING LIVE SYSTEM
    // ==========================================

    private var conversationsPollJob: Job? = null
    private var heartbeatJob: Job? = null
    private var incomingCallJob: Job? = null
    private var inChatPollJob: Job? = null
    private var callEventPollJob: Job? = null
    private var callTimerJob: Job? = null
    private var lastIncomingCallId: String? = null

    fun startMessagingServices() {
        val context = getApplication<Application>().applicationContext
        val uid = _uiState.value.currentUser.id
        if (uid <= 0L || _uiState.value.isGuest || !_uiState.value.isLoggedIn) return

        // 1. Instantly load cached conversations and following from disk
        val cachedConvos = MessagesCache.getCachedConversations(context, uid)
        val cachedFollowing = MessagesCache.getCachedFollowing(context, uid)
        if (cachedConvos.isNotEmpty() || cachedFollowing.isNotEmpty()) {
            _uiState.update { state ->
                state.copy(
                    conversations = if (cachedConvos.isNotEmpty()) cachedConvos else state.conversations,
                    followingUsers = if (cachedFollowing.isNotEmpty()) cachedFollowing else state.followingUsers
                )
            }
        }

        // 2. Fetch live data
        refreshConversations(silent = cachedConvos.isNotEmpty())
        refreshFollowing()

        // 3. Start recurring jobs
        conversationsPollJob?.cancel()
        conversationsPollJob = viewModelScope.launch {
            while (true) {
                delay(7000)
                if (_uiState.value.isLoggedIn && !_uiState.value.isGuest) {
                    refreshConversations(silent = true)
                }
            }
        }

        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            while (true) {
                delay(15000)
                val curUid = _uiState.value.currentUser.id
                val token = UserSessionManager.getAuthToken(context)
                if (curUid > 0L) {
                    MessagesApiService.sendHeartbeat(curUid, token)
                }
            }
        }

        incomingCallJob?.cancel()
        incomingCallJob = viewModelScope.launch {
            while (true) {
                delay(1200)
                val curUid = _uiState.value.currentUser.id
                val token = UserSessionManager.getAuthToken(context)
                if (curUid > 0L && _uiState.value.isLoggedIn && _uiState.value.activeCall == null) {
                    try {
                        val inc = MessagesApiService.pollIncomingCall(curUid, token)
                        if (inc != null && inc.id != lastIncomingCallId) {
                            lastIncomingCallId = inc.id
                            _uiState.update { state ->
                                state.copy(
                                    activeCall = ActiveCallState(
                                        callId = inc.id,
                                        peerId = inc.callerId,
                                        peerName = inc.callerName,
                                        peerAvatar = inc.callerAvatar,
                                        mode = inc.callType,
                                        phase = "incoming",
                                        isInitiator = false
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
        }
    }

    fun stopMessagingServices() {
        conversationsPollJob?.cancel()
        conversationsPollJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        incomingCallJob?.cancel()
        incomingCallJob = null
        inChatPollJob?.cancel()
        inChatPollJob = null
        callEventPollJob?.cancel()
        callEventPollJob = null
        callTimerJob?.cancel()
        callTimerJob = null
    }

    fun refreshConversations(silent: Boolean = false) {
        val uid = _uiState.value.currentUser.id
        if (uid <= 0L || _uiState.value.isGuest) return
        val context = getApplication<Application>().applicationContext
        val token = UserSessionManager.getAuthToken(context)

        if (!silent) {
            _uiState.update { it.copy(isConversationsLoading = true) }
        }

        launchSafe {
            val live = MessagesApiService.fetchConversations(uid, token)
            if (live.isNotEmpty()) {
                _uiState.update { state ->
                    val merged = live.map { newConv ->
                        val existing = state.conversations.find {
                            it.id == newConv.id || (it.rawConversationId > 0 && it.rawConversationId == newConv.rawConversationId) || (it.participant.id == newConv.participant.id)
                        }
                        if (existing != null && existing.messages.isNotEmpty()) {
                            newConv.copy(messages = existing.messages)
                        } else {
                            newConv
                        }
                    }
                    MessagesCache.setCachedConversations(context, uid, merged)
                    state.copy(
                        conversations = merged,
                        isConversationsLoading = false
                    )
                }
            } else {
                _uiState.update { it.copy(isConversationsLoading = false) }
            }
        }
    }

    fun refreshFollowing() {
        val uid = _uiState.value.currentUser.id
        if (uid <= 0L || _uiState.value.isGuest) return
        val context = getApplication<Application>().applicationContext
        val token = UserSessionManager.getAuthToken(context)

        launchSafe {
            val list = MessagesApiService.fetchFollowing(uid, token)
            if (list.isNotEmpty()) {
                MessagesCache.setCachedFollowing(context, uid, list)
                _uiState.update { it.copy(followingUsers = list) }
            }
        }
    }

    private fun mergeMessages(prev: List<ChatMessage>, incoming: List<ChatMessage>): List<ChatMessage> {
        val map = LinkedHashMap<String, ChatMessage>()
        for (m in prev) {
            val key = if (m.rawId > 0) "raw_${m.rawId}" else m.id
            map[key] = m
        }
        for (m in incoming) {
            val key = if (m.rawId > 0) "raw_${m.rawId}" else m.id
            val optKey = map.entries.firstOrNull { (_, v) ->
                v.id.startsWith("temp_") && v.senderId == m.senderId && v.text == m.text
            }?.key
            if (optKey != null) {
                map.remove(optKey)
            }
            map[key] = m
        }
        return map.values.toList().sortedBy { m ->
            m.rawId.takeIf { it > 0 } ?: m.id.toLongOrNull() ?: 0L
        }
    }

    fun openConversation(convId: String) {
        val context = getApplication<Application>().applicationContext
        val uid = _uiState.value.currentUser.id
        val token = UserSessionManager.getAuthToken(context)

        _uiState.update { state ->
            val updated = state.conversations.map {
                if (it.id == convId) it.copy(unreadCount = 0) else it
            }
            state.copy(
                conversations = updated,
                activeConversationId = convId
            )
        }

        val conv = _uiState.value.conversations.find { it.id == convId } ?: return
        val otherId = conv.participant.id

        // Load cached messages first for instantaneous display
        if (conv.messages.isEmpty() && uid > 0 && otherId > 0) {
            val cachedMsgs = MessagesCache.getCachedMessages(context, uid, otherId)
            if (cachedMsgs.isNotEmpty()) {
                _uiState.update { state ->
                    state.copy(
                        conversations = state.conversations.map {
                            if (it.id == convId) it.copy(messages = cachedMsgs) else it
                        }
                    )
                }
            }
        }

        // Start live in-chat polling
        inChatPollJob?.cancel()
        inChatPollJob = viewModelScope.launch {
            var cid = conv.rawConversationId.takeIf { it > 0 }
                ?: MessagesCache.getCachedConversationId(context, uid, otherId).takeIf { it > 0 }
                ?: conv.id.toLongOrNull()

            while (true) {
                if (uid > 0 && otherId > 0) {
                    try {
                        // 1. If conversation ID is unknown, resolve from /api/messages/conversations
                        if (cid == null || cid <= 0L) {
                            val all = MessagesApiService.fetchConversations(uid, token)
                            val match = all.find { it.participant.id == otherId }
                            if (match != null && match.rawConversationId > 0) {
                                cid = match.rawConversationId
                                MessagesCache.setCachedConversationId(context, uid, otherId, cid)
                            }
                        }

                        // 2. Fetch history
                        if (cid != null && cid > 0L) {
                            val history = MessagesApiService.fetchMessageHistory(cid, uid, token)
                            if (history.isNotEmpty()) {
                                _uiState.update { state ->
                                    val updatedConvs = state.conversations.map { c ->
                                        if (c.id == convId) {
                                            val merged = mergeMessages(c.messages, history)
                                            MessagesCache.setCachedMessages(context, uid, otherId, merged)
                                            c.copy(messages = merged, rawConversationId = cid)
                                        } else c
                                    }
                                    state.copy(conversations = updatedConvs)
                                }
                            }
                            // Mark read
                            MessagesApiService.markRead(cid, uid, token)
                        }

                        // 3. Recipient presence
                        val presence = MessagesApiService.fetchRecipientPresence(otherId, uid, token)
                        _uiState.update { state ->
                            state.copy(
                                recipientOnline = presence.online,
                                recipientLastSeen = presence.lastSeenAt,
                                conversations = state.conversations.map { c ->
                                    if (c.participant.id == otherId) {
                                        c.copy(participant = c.participant.copy(isOnline = presence.online))
                                    } else c
                                }
                            )
                        }
                    } catch (e: Exception) {
                        // ignore network glitches
                    }
                }
                delay(8000)
            }
        }
    }

    fun closeConversation() {
        inChatPollJob?.cancel()
        inChatPollJob = null
        _uiState.update { it.copy(activeConversationId = null) }
    }

    fun sendMessage(
        convId: String,
        text: String,
        replyToId: Long? = null,
        attachments: List<ChatAttachment> = emptyList()
    ) {
        val uid = _uiState.value.currentUser.id
        if (uid <= 0L || _uiState.value.isGuest || !_uiState.value.isLoggedIn) {
            openAuth("login")
            return
        }
        val trimmed = text.trim()
        if (trimmed.isBlank() && attachments.isEmpty()) return

        val conv = _uiState.value.conversations.find { it.id == convId } ?: return
        val recipientId = conv.participant.id
        val context = getApplication<Application>().applicationContext
        val token = UserSessionManager.getAuthToken(context)

        val tempId = "temp_${System.currentTimeMillis()}"
        val isCall = trimmed.contains("call", ignoreCase = true)
        val optimisticMsg = ChatMessage(
            id = tempId,
            senderId = uid,
            recipientId = recipientId,
            text = trimmed,
            timestamp = "Just now",
            isMine = true,
            isDelivered = true,
            attachments = attachments,
            parentMessageId = replyToId?.toString(),
            isCallMessage = isCall,
            callType = if (trimmed.contains("video", ignoreCase = true)) "video" else "voice",
            isMissedCall = trimmed.contains("missed", ignoreCase = true)
        )

        // Optimistic UI update
        _uiState.update { state ->
            val updated = state.conversations.map { c ->
                if (c.id == convId) {
                    val preview = if (trimmed.isNotBlank()) trimmed else if (attachments.isNotEmpty()) "📎 ${attachments[0].filename}" else ""
                    c.copy(
                        lastMessage = preview,
                        lastTimestamp = "Just now",
                        messages = c.messages + optimisticMsg
                    )
                } else c
            }
            state.copy(conversations = updated)
        }

        launchSafe {
            val serverMsg = MessagesApiService.sendMessage(
                senderId = uid,
                recipientId = recipientId,
                textContent = trimmed.takeIf { it.isNotBlank() },
                parentMessageId = replyToId,
                attachments = attachments,
                token = token
            )

            if (serverMsg != null) {
                _uiState.update { state ->
                    val updated = state.conversations.map { c ->
                        if (c.id == convId) {
                            val replaced = c.messages.map { if (it.id == tempId) serverMsg else it }
                            MessagesCache.setCachedMessages(context, uid, recipientId, replaced)
                            c.copy(messages = replaced)
                        } else c
                    }
                    state.copy(conversations = updated)
                }
                refreshConversations(silent = true)
            }
        }
    }

    fun sendAttachmentMessage(convId: String, attachment: ChatAttachment, text: String = "") {
        sendMessage(convId, text, attachments = listOf(attachment))
    }

    fun uploadAndSendFile(convId: String, uri: Uri, text: String = "") {
        val uid = _uiState.value.currentUser.id
        val context = getApplication<Application>().applicationContext
        val token = UserSessionManager.getAuthToken(context)
        if (uid <= 0L || _uiState.value.isGuest) {
            openAuth("login")
            return
        }

        launchSafe {
            val att = MessagesApiService.uploadAttachment(context, uri, "chat", token)
            if (att != null) {
                sendMessage(convId, text, attachments = listOf(att))
            }
        }
    }

    fun uploadAndSendVoiceNote(convId: String, fileBytes: ByteArray, durationSec: Int) {
        val uid = _uiState.value.currentUser.id
        val context = getApplication<Application>().applicationContext
        val token = UserSessionManager.getAuthToken(context)
        if (uid <= 0L || _uiState.value.isGuest) {
            openAuth("login")
            return
        }

        launchSafe {
            val att = MessagesApiService.uploadBytes(
                bytes = fileBytes,
                filename = "voice-${System.currentTimeMillis()}.webm",
                mimeType = "audio/webm",
                folder = "chat",
                token = token,
                durationSeconds = durationSec
            )
            if (att != null) {
                sendMessage(convId, "", attachments = listOf(att))
            }
        }
    }

    fun editChatMessage(convId: String, messageId: String, newText: String) {
        val uid = _uiState.value.currentUser.id
        val context = getApplication<Application>().applicationContext
        val token = UserSessionManager.getAuthToken(context)
        val trimmed = newText.trim()
        if (trimmed.isBlank()) return

        // Optimistic UI update
        _uiState.update { state ->
            val updated = state.conversations.map { c ->
                if (c.id == convId) {
                    val msgs = c.messages.map { if (it.id == messageId) it.copy(text = trimmed, isEdited = true) else it }
                    c.copy(messages = msgs)
                } else c
            }
            state.copy(conversations = updated)
        }

        val rawId = messageId.toLongOrNull()
        if (rawId != null && rawId > 0) {
            launchSafe {
                MessagesApiService.editMessage(rawId, trimmed, uid, token)
            }
        }
    }

    fun deleteChatMessage(convId: String, messageId: String, deleteForEveryone: Boolean = false) {
        val uid = _uiState.value.currentUser.id
        val context = getApplication<Application>().applicationContext
        val token = UserSessionManager.getAuthToken(context)

        // Optimistic UI update
        _uiState.update { state ->
            val updated = state.conversations.map { c ->
                if (c.id == convId) {
                    val filtered = c.messages.filterNot { it.id == messageId }
                    c.copy(messages = filtered)
                } else c
            }
            state.copy(conversations = updated)
        }

        val rawId = messageId.toLongOrNull()
        if (rawId != null && rawId > 0) {
            launchSafe {
                MessagesApiService.deleteMessage(rawId, deleteForEveryone, uid, token)
            }
        }
    }

    fun startConversationWithUser(user: User, product: Product? = null) {
        if (_uiState.value.isGuest || !_uiState.value.isLoggedIn) {
            openAuth("login")
            return
        }
        val existing = _uiState.value.conversations.find { it.participant.id == user.id }
        val introMessage = if (product != null) {
            "${product.title}: Hello, I am interested in this product. Is it still available? [product:${product.id}]"
        } else null

        if (existing != null) {
            if (introMessage != null) {
                sendMessage(existing.id, introMessage)
            }
            openConversation(existing.id)
            setScreen(Screen.MESSAGES)
        } else {
            val convId = "conv_${user.id}"
            val newConv = Conversation(
                id = convId,
                participant = user,
                lastMessage = introMessage ?: "Started a conversation",
                lastTimestamp = "Just now",
                unreadCount = 0,
                messages = emptyList(),
                rawConversationId = 0L
            )
            _uiState.update { state ->
                state.copy(
                    conversations = listOf(newConv) + state.conversations,
                    activeConversationId = newConv.id,
                    currentScreen = Screen.MESSAGES,
                    selectedProductForDetail = null
                )
            }
            openConversation(convId)
            if (introMessage != null) {
                sendMessage(convId, introMessage)
            }
        }

        if (product != null) {
            viewModelScope.launch {
                MarketplaceApiService.sendProductInquiry(
                    recipientId = user.id,
                    productId = product.id,
                    productTitle = product.title,
                    userId = _uiState.value.currentUser.id
                )
            }
        }
    }

    // ==========================================
    // CALL MANAGEMENT (VOICE & VIDEO)
    // ==========================================

    fun startCall(peer: User, mode: String) {
        val uid = _uiState.value.currentUser.id
        if (uid <= 0L || _uiState.value.isGuest) {
            openAuth("login")
            return
        }
        val context = getApplication<Application>().applicationContext
        val token = UserSessionManager.getAuthToken(context)

        launchSafe {
            val callId = MessagesApiService.createCall(peer.id, mode, uid, token)
                ?: "call_${System.currentTimeMillis()}_${uid}_${peer.id}"

            _uiState.update { state ->
                state.copy(
                    activeCall = ActiveCallState(
                        callId = callId,
                        peerId = peer.id,
                        peerName = peer.name,
                        peerAvatar = peer.profileImageUrl,
                        mode = mode,
                        phase = "outgoing",
                        isInitiator = true,
                        seconds = 0
                    )
                )
            }

            // Send initial offer signal
            MessagesApiService.sendSignal(callId, peer.id, "offer", null, uid, token)
            startCallPolling(callId, peer.id)
        }
    }

    fun acceptIncomingCall() {
        val call = _uiState.value.activeCall ?: return
        val uid = _uiState.value.currentUser.id
        val context = getApplication<Application>().applicationContext
        val token = UserSessionManager.getAuthToken(context)

        launchSafe {
            MessagesApiService.sendSignal(call.callId, call.peerId, "accept", null, uid, token)
            _uiState.update { state ->
                state.copy(activeCall = state.activeCall?.copy(phase = "active"))
            }
            startCallTimer()
            startCallPolling(call.callId, call.peerId)
        }
    }

    fun declineIncomingCall() {
        val call = _uiState.value.activeCall ?: return
        val uid = _uiState.value.currentUser.id
        val context = getApplication<Application>().applicationContext
        val token = UserSessionManager.getAuthToken(context)

        launchSafe {
            MessagesApiService.sendSignal(call.callId, call.peerId, "decline", null, uid, token)
            endCallInternal(logMissed = true)
        }
    }

    fun hangupCall() {
        val call = _uiState.value.activeCall ?: return
        val uid = _uiState.value.currentUser.id
        val context = getApplication<Application>().applicationContext
        val token = UserSessionManager.getAuthToken(context)

        val wasOutgoingMissed = call.isInitiator && call.phase != "active"

        launchSafe {
            MessagesApiService.sendSignal(call.callId, call.peerId, "hangup", null, uid, token)
            endCallInternal(logMissed = wasOutgoingMissed)
        }
    }

    private fun endCallInternal(logMissed: Boolean = false) {
        val call = _uiState.value.activeCall
        callEventPollJob?.cancel()
        callEventPollJob = null
        callTimerJob?.cancel()
        callTimerJob = null

        if (call != null && logMissed) {
            val conv = _uiState.value.conversations.find { it.participant.id == call.peerId }
            val convId = conv?.id ?: "conv_${call.peerId}"
            val text = if (call.mode == "video") "🎥 Missed video call" else "📞 Missed voice call"
            sendMessage(convId, text)
        }

        _uiState.update { it.copy(activeCall = null) }
    }

    private fun startCallTimer() {
        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _uiState.update { state ->
                    val cur = state.activeCall ?: return@update state
                    state.copy(activeCall = cur.copy(seconds = cur.seconds + 1))
                }
            }
        }
    }

    private fun startCallPolling(callId: String, peerId: Long) {
        callEventPollJob?.cancel()
        callEventPollJob = viewModelScope.launch {
            var cursor = 0L
            val uid = _uiState.value.currentUser.id
            val context = getApplication<Application>().applicationContext
            val token = UserSessionManager.getAuthToken(context)

            while (true) {
                delay(900)
                if (_uiState.value.activeCall == null) break
                try {
                    val (events, nextCursor) = MessagesApiService.pollCallEvents(callId, cursor, uid, token)
                    cursor = nextCursor
                    for (ev in events) {
                        when (ev.type) {
                            "accept" -> {
                                _uiState.update { state ->
                                    state.copy(activeCall = state.activeCall?.copy(phase = "active"))
                                }
                                startCallTimer()
                            }
                            "decline" -> {
                                endCallInternal(logMissed = true)
                                break
                            }
                            "hangup" -> {
                                endCallInternal(logMissed = false)
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    fun markNotificationsAsRead() {
        _uiState.update { state ->
            state.copy(
                notifications = state.notifications.map { it.copy(isRead = true) }
            )
        }
    }

    fun markNotificationRead(id: String, isRead: Boolean = true) {
        _uiState.update { state ->
            state.copy(
                notifications = state.notifications.map {
                    if (it.id == id) it.copy(isRead = isRead) else it
                }
            )
        }
    }

    fun deleteNotification(id: String) {
        _uiState.update { state ->
            state.copy(
                notifications = state.notifications.filter { it.id != id }
            )
        }
    }

    fun acceptGroupInvite(notificationId: String, groupId: Long?) {
        _uiState.update { state ->
            state.copy(
                notifications = state.notifications.map {
                    if (it.id == notificationId) it.copy(isRead = true) else it
                }
            )
        }
    }

    fun declineGroupInvite(notificationId: String, groupId: Long?) {
        _uiState.update { state ->
            state.copy(
                notifications = state.notifications.map {
                    if (it.id == notificationId) it.copy(isRead = true) else it
                }
            )
        }
    }

    fun updateBio(newBio: String) {
        _uiState.update { state ->
            val updatedUser = state.currentUser.copy(bio = newBio.trim())
            state.copy(
                currentUser = updatedUser,
                selectedUserProfile = if (state.selectedUserProfile?.id == updatedUser.id) updatedUser else state.selectedUserProfile
            )
        }
    }

    fun updateCoverImage(uri: Uri) {
        val current = _uiState.value.currentUser
        val previousCover = current.coverImageUrl
        // 1. Instant optimistic display (0ms)
        val previewUrl = uri.toString()
        val optimUser = current.copy(coverImageUrl = previewUrl)
        _uiState.update { state ->
            val updatedUsers = state.users.map { if (it.id == optimUser.id) optimUser else it }
            state.copy(
                currentUser = optimUser,
                users = updatedUsers,
                selectedUserProfile = if (state.selectedUserProfile?.id == optimUser.id) optimUser else state.selectedUserProfile
            )
        }

        launchSafe {
            try {
                val context = getApplication<Application>()
                // 2. Compress and 3. Upload to covers folder
                val finalUrl = MediaUploadHelper.uploadCoverImage(context, uri)
                if (finalUrl.isNullOrBlank()) {
                    // Revert if failed
                    _uiState.update { state ->
                        val reverted = state.currentUser.copy(coverImageUrl = previousCover)
                        val revertedUsers = state.users.map { if (it.id == reverted.id) reverted else it }
                        state.copy(
                            currentUser = reverted,
                            users = revertedUsers,
                            selectedUserProfile = if (state.selectedUserProfile?.id == reverted.id) reverted else state.selectedUserProfile
                        )
                    }
                    return@launchSafe
                }

                // 4. Update user record in database
                UserApiService.updateUserDetails(current.id, mapOf("cover_image_url" to finalUrl))

                // 5. Update state with final URL and cache in session
                val finalUser = _uiState.value.currentUser.copy(coverImageUrl = finalUrl)
                _uiState.update { state ->
                    val finalUsers = state.users.map { if (it.id == finalUser.id) finalUser else it }
                    state.copy(
                        currentUser = finalUser,
                        users = finalUsers,
                        selectedUserProfile = if (state.selectedUserProfile?.id == finalUser.id) finalUser else state.selectedUserProfile
                    )
                }
                UserSessionManager.saveUser(context, finalUser)
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { state ->
                    val reverted = state.currentUser.copy(coverImageUrl = previousCover)
                    val revertedUsers = state.users.map { if (it.id == reverted.id) reverted else it }
                    state.copy(
                        currentUser = reverted,
                        users = revertedUsers,
                        selectedUserProfile = if (state.selectedUserProfile?.id == reverted.id) reverted else state.selectedUserProfile
                    )
                }
            }
        }
    }

    fun updateUserDetails(
        bio: String,
        work: String,
        education: String,
        location: String,
        website: String,
        coverImageUrl: String? = null,
        profileImageUrl: String? = null
    ) {
        val current = _uiState.value.currentUser
        val updatedUser = current.copy(
            bio = bio.trim(),
            work = work.trim(),
            education = education.trim(),
            location = location.trim(),
            website = website.trim(),
            coverImageUrl = coverImageUrl ?: current.coverImageUrl,
            profileImageUrl = profileImageUrl ?: current.profileImageUrl
        )
        _uiState.update { state ->
            val updatedUsers = state.users.map { if (it.id == updatedUser.id) updatedUser else it }
            state.copy(
                currentUser = updatedUser,
                users = updatedUsers,
                selectedUserProfile = if (state.selectedUserProfile?.id == updatedUser.id) updatedUser else state.selectedUserProfile
            )
        }
        val context = getApplication<Application>()
        UserSessionManager.saveUser(context, updatedUser)
        launchSafe {
            val payload = mutableMapOf<String, Any?>(
                "bio" to bio.trim(),
                "work" to work.trim(),
                "education" to education.trim(),
                "location" to location.trim(),
                "website" to website.trim()
            )
            if (coverImageUrl != null) payload["cover_image_url"] = coverImageUrl
            if (profileImageUrl != null) payload["profile_image_url"] = profileImageUrl
            UserApiService.updateUserDetails(current.id, payload)
        }
    }

    fun updateFullProfileDetails(
        name: String,
        bio: String,
        work: String,
        education: String,
        location: String,
        website: String,
        gender: String,
        birthDate: String
    ) {
        _uiState.update { state ->
            val updatedUser = state.currentUser.copy(
                name = name.ifBlank { state.currentUser.name }.trim(),
                bio = bio.trim(),
                work = work.trim(),
                education = education.trim(),
                location = location.trim(),
                website = website.trim(),
                gender = gender.trim(),
                birthDate = birthDate.trim().ifBlank { state.currentUser.birthDate }
            )
            val updatedUsers = state.users.map { if (it.id == updatedUser.id) updatedUser else it }
            state.copy(
                currentUser = updatedUser,
                users = updatedUsers,
                selectedUserProfile = if (state.selectedUserProfile?.id == updatedUser.id) updatedUser else state.selectedUserProfile
            )
        }
    }

    fun changePassword(current: String, new: String): Boolean {
        return true
    }

    fun deleteAccount() {
        _uiState.update { state ->
            val deletedUser = state.currentUser.copy(isDeleted = true, name = "[Deleted User]")
            state.copy(
                currentUser = deletedUser,
                currentScreen = Screen.FEED
            )
        }
    }

    fun openUserProfile(user: User) {
        _uiState.update { state ->
            state.copy(
                selectedUserProfile = user,
                currentScreen = Screen.PROFILE
            )
        }
        fetchUserProfilePosts(user.id)
        viewModelScope.launch {
            val followData = UserApiService.fetchUserFollowData(user.id)
            if (followData != null) {
                val currentUid = _uiState.value.currentUser.id
                val isF = followData.followers.contains(currentUid)
                _uiState.update { state ->
                    val updatedProfile = if (state.selectedUserProfile?.id == user.id) {
                        state.selectedUserProfile?.copy(
                            followersCount = followData.followers.size,
                            followingCount = followData.following.size,
                            isFollowing = isF
                        )
                    } else state.selectedUserProfile
                    state.copy(
                        selectedUserProfile = updatedProfile,
                        users = state.users.map {
                            if (it.id == user.id) it.copy(
                                followersCount = followData.followers.size,
                                followingCount = followData.following.size,
                                isFollowing = isF
                            ) else it
                        }
                    )
                }
            }
        }
    }

    fun openUserProfileById(userId: Long) {
        val cached = _uiState.value.users.find { it.id == userId }
        if (cached != null) {
            openUserProfile(cached)
        } else {
            viewModelScope.launch {
                val liveUser = VideosApiService.fetchUser(userId)
                if (liveUser != null) {
                    _uiState.update { state ->
                        state.copy(users = (state.users + liveUser).distinctBy { it.id })
                    }
                    openUserProfile(liveUser)
                } else {
                    val fromPost = _uiState.value.posts.find { it.author.id == userId }?.author
                        ?: _uiState.value.videoPosts.find { it.author.id == userId }?.author
                    if (fromPost != null) {
                        openUserProfile(fromPost)
                    }
                }
            }
        }
    }

    fun openCurrentUserProfile() {
        if (_uiState.value.isGuest || !_uiState.value.isLoggedIn) {
            openAuth("login")
            return
        }
        val current = _uiState.value.currentUser
        _uiState.update { state ->
            state.copy(
                selectedUserProfile = current,
                currentScreen = Screen.PROFILE
            )
        }
        fetchUserProfilePosts(current.id)
        viewModelScope.launch {
            val followData = UserApiService.fetchUserFollowData(current.id)
            if (followData != null) {
                _uiState.update { state ->
                    val updatedCurrent = state.currentUser.copy(
                        followersCount = followData.followers.size,
                        followingCount = followData.following.size
                    )
                    state.copy(
                        currentUser = updatedCurrent,
                        selectedUserProfile = if (state.selectedUserProfile?.id == current.id) updatedCurrent else state.selectedUserProfile
                    )
                }
            }
        }
    }

    fun toggleVerifyUser(userId: Long) {
        _uiState.update { state ->
            val updatedUsers = state.users.map { u ->
                if (u.id == userId) u.copy(isVerified = !u.isVerified) else u
            }
            val updatedCurrent = if (state.currentUser.id == userId) state.currentUser.copy(isVerified = !state.currentUser.isVerified) else state.currentUser
            val updatedSelected = if (state.selectedUserProfile?.id == userId) state.selectedUserProfile.copy(isVerified = !state.selectedUserProfile.isVerified) else state.selectedUserProfile
            state.copy(users = updatedUsers, currentUser = updatedCurrent, selectedUserProfile = updatedSelected)
        }
    }

    fun suspendUser(userId: Long, duration: String, reason: String) {
        val until = when (duration) {
            "week" -> "In 7 days"
            "month" -> "In 30 days"
            else -> "Indefinitely"
        }
        _uiState.update { state ->
            val updatedUsers = state.users.map { u ->
                if (u.id == userId) u.copy(suspendedUntil = until, suspensionReason = reason) else u
            }
            val updatedSelected = if (state.selectedUserProfile?.id == userId) state.selectedUserProfile.copy(suspendedUntil = until, suspensionReason = reason) else state.selectedUserProfile
            state.copy(users = updatedUsers, selectedUserProfile = updatedSelected)
        }
    }

    fun unsuspendUser(userId: Long) {
        _uiState.update { state ->
            val updatedUsers = state.users.map { u ->
                if (u.id == userId) u.copy(suspendedUntil = null, suspensionReason = null) else u
            }
            val updatedSelected = if (state.selectedUserProfile?.id == userId) state.selectedUserProfile.copy(suspendedUntil = null, suspensionReason = null) else state.selectedUserProfile
            state.copy(users = updatedUsers, selectedUserProfile = updatedSelected)
        }
    }

    fun toggleModeratorRole(userId: Long) {
        _uiState.update { state ->
            val updatedUsers = state.users.map { u ->
                if (u.id == userId) {
                    val nextRole = if (u.role == "moderator") "user" else "moderator"
                    u.copy(role = nextRole)
                } else u
            }
            val updatedSelected = if (state.selectedUserProfile?.id == userId) {
                val nextRole = if (state.selectedUserProfile.role == "moderator") "user" else "moderator"
                state.selectedUserProfile.copy(role = nextRole)
            } else state.selectedUserProfile
            state.copy(users = updatedUsers, selectedUserProfile = updatedSelected)
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setShowCreatePostDialog(show: Boolean, initialPicker: String? = null) {
        if (show && (_uiState.value.isGuest || !_uiState.value.isLoggedIn)) {
            openAuth("login")
            return
        }
        _uiState.update { it.copy(showCreatePostDialog = show, createPostInitialPicker = if (show) initialPicker else null) }
    }

    fun setSelectedPostForComments(post: Post?) {
        _uiState.update { it.copy(selectedPostForComments = post) }
        if (post != null) {
            val pid = post.product?.id ?: post.id
            if (post.product != null || post.postType == "product") {
                viewModelScope.launch {
                    val liveReviews = MarketplaceApiService.fetchProductReviews(pid, _uiState.value.currentUser.id)
                    if (liveReviews.isNotEmpty()) {
                        _uiState.update { state ->
                            val updatedPosts = state.posts.map { p ->
                                if (p.id == post.id) p.copy(comments = liveReviews, commentsCount = liveReviews.size) else p
                            }
                            val updatedSel = if (state.selectedPostForComments?.id == post.id) {
                                state.selectedPostForComments?.copy(comments = liveReviews, commentsCount = liveReviews.size)
                            } else state.selectedPostForComments
                            state.copy(
                                posts = updatedPosts,
                                selectedPostForComments = updatedSel
                            )
                        }
                    }
                }
            } else {
                viewModelScope.launch {
                    val liveComments = VideosApiService.fetchComments(post.id, _uiState.value.currentUser.id)
                    if (liveComments.isNotEmpty()) {
                        _uiState.update { state ->
                            val updatedPosts = state.posts.map { p ->
                                if (p.id == post.id) p.copy(comments = liveComments, commentsCount = liveComments.size) else p
                            }
                            val updatedVideoPosts = state.videoPosts.map { p ->
                                if (p.id == post.id) p.copy(comments = liveComments, commentsCount = liveComments.size) else p
                            }
                            val updatedSel = if (state.selectedPostForComments?.id == post.id) {
                                state.selectedPostForComments?.copy(comments = liveComments, commentsCount = liveComments.size)
                            } else state.selectedPostForComments
                            state.copy(
                                posts = updatedPosts,
                                videoPosts = updatedVideoPosts,
                                selectedPostForComments = updatedSel
                            )
                        }
                    }
                }
            }
        }
    }

    fun setShowSearchDialog(show: Boolean) {
        _uiState.update { it.copy(showSearchDialog = show) }
    }

    fun setShowNotificationsOverlay(show: Boolean) {
        _uiState.update { it.copy(showNotificationsOverlay = show) }
        if (show) markNotificationsAsRead()
    }

    fun setShowCreateEventDialog(show: Boolean) {
        if (show && (_uiState.value.isGuest || !_uiState.value.isLoggedIn)) {
            openAuth("login")
            return
        }
        _uiState.update { it.copy(showCreateEventDialog = show) }
    }

    fun setShowCreateProductDialog(show: Boolean) {
        if (show && (_uiState.value.isGuest || !_uiState.value.isLoggedIn)) {
            openAuth("login")
            return
        }
        _uiState.update { it.copy(showCreateProductDialog = show) }
    }

    fun setSelectedProductForDetail(product: Product?) {
        _uiState.update { it.copy(selectedProductForDetail = product) }
    }

    fun openVideoReel(post: Post) {
        _uiState.update { state ->
            val existing = state.videoPosts
            val updated = if (existing.any { it.id == post.id }) existing else listOf(post) + existing
            state.copy(
                selectedVideoPostId = post.id,
                videoPosts = updated,
                currentScreen = Screen.REELS
            )
        }
    }

    fun openProductById(pid: Long) {
        val existing = _uiState.value.products.find { it.id == pid }
        if (existing != null) {
            setSelectedProductForDetail(existing)
        } else {
            viewModelScope.launch {
                val prod = MarketplaceApiService.fetchProductById(pid)
                if (prod != null) {
                    _uiState.update { it.copy(products = listOf(prod) + it.products) }
                    setSelectedProductForDetail(prod)
                }
            }
        }
    }

    fun openImageViewer(url: String?) {
        _uiState.update { it.copy(fullScreenImageViewerUrl = url) }
    }

    fun closeImageViewer() {
        _uiState.update { it.copy(fullScreenImageViewerUrl = null) }
    }

    fun setAppLoading(loading: Boolean) {
        _uiState.update { it.copy(isAppLoading = loading) }
    }

    fun refreshStories() {
        launchSafe {
            _uiState.update { it.copy(isStoriesLoading = true) }
            val viewerId = _uiState.value.currentUser.id
            val liveStories = StoriesApiService.fetchStories(viewerId)
            _uiState.update { it.copy(stories = liveStories, isStoriesLoading = false) }
        }
    }

    fun setSelectedStory(story: Story?) {
        _uiState.update { it.copy(selectedStory = story) }
        if (story != null) {
            val viewerId = _uiState.value.currentUser.id
            viewModelScope.launch {
                // 1. Record view
                StoriesApiService.recordView(story.id, viewerId)
                // 2. Fetch reactions
                val reactionsData = StoriesApiService.fetchReactions(story.id, viewerId)
                if (reactionsData != null) {
                    _uiState.update { state ->
                        state.copy(storyReactions = state.storyReactions + (story.id to reactionsData))
                    }
                }
                // 3. Fetch comments
                val comments = StoriesApiService.fetchComments(story.id, viewerId)
                _uiState.update { state ->
                    state.copy(storyComments = state.storyComments + (story.id to comments))
                }
                // 4. If owner, fetch viewers and analytics
                if (story.author.id == viewerId) {
                    val viewers = StoriesApiService.fetchViewers(story.id)
                    val analytics = StoriesApiService.fetchAnalytics(story.id)
                    _uiState.update { state ->
                        val updatedViewers = state.storyViewers + (story.id to viewers)
                        val updatedAnalytics = if (analytics != null) state.storyAnalytics + (story.id to analytics) else state.storyAnalytics
                        state.copy(storyViewers = updatedViewers, storyAnalytics = updatedAnalytics)
                    }
                }
            }
        }
    }

    fun setShowCreateStoryDialog(show: Boolean) {
        if (show && (_uiState.value.isGuest || !_uiState.value.isLoggedIn)) {
            openAuth("login")
            return
        }
        _uiState.update { it.copy(showCreateStoryDialog = show) }
    }

    fun createStory(
        mediaUrl: String = "",
        type: String = "image",
        textContent: String = "",
        mediaUrls: List<String> = emptyList(),
        mediaTypes: List<String> = emptyList(),
        mediaMeta: List<Map<String, Any?>> = emptyList(),
        backgroundStyle: String = "linear-gradient(45deg, #1877F2, #0055FF)",
        musicTitle: String? = null,
        musicUrl: String? = null,
        duration: Int = 90,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        if (_uiState.value.isGuest || !_uiState.value.isLoggedIn) {
            openAuth("login")
            return
        }
        val currentUserId = _uiState.value.currentUser.id
        viewModelScope.launch {
            _uiState.update { it.copy(isStoriesLoading = true, showCreateStoryDialog = false) }
            val finalMediaUrls = if (mediaUrls.isNotEmpty()) mediaUrls else if (mediaUrl.isNotBlank()) listOf(mediaUrl) else emptyList()
            val finalMediaTypes = if (mediaTypes.isNotEmpty()) mediaTypes else if (mediaUrl.isNotBlank()) listOf(type) else emptyList()
            val finalMediaMeta = if (mediaMeta.isNotEmpty()) mediaMeta else if (mediaUrl.isNotBlank()) {
                listOf(mapOf(
                    "thumb" to mediaUrl,
                    "feed" to mediaUrl,
                    "full" to mediaUrl,
                    "type" to type
                ))
            } else emptyList()

            val created = StoriesApiService.createStory(
                userId = currentUserId,
                type = type,
                textContent = textContent.takeIf { it.isNotBlank() },
                mediaUrl = mediaUrl.takeIf { it.isNotBlank() },
                mediaUrls = finalMediaUrls,
                mediaTypes = finalMediaTypes,
                mediaMeta = finalMediaMeta,
                backgroundStyle = backgroundStyle.takeIf { it.isNotBlank() },
                musicUrl = musicUrl,
                musicTitle = musicTitle,
                duration = duration
            )
            if (created != null) {
                val finalStory = if (created.author.id == currentUserId && created.author.name.isBlank()) {
                    created.copy(author = _uiState.value.currentUser)
                } else created

                _uiState.update { state ->
                    state.copy(
                        stories = listOf(finalStory) + state.stories.filter { it.id != finalStory.id },
                        isStoriesLoading = false
                    )
                }
                onSuccess?.invoke()
            } else {
                refreshStories()
                _uiState.update { it.copy(isStoriesLoading = false) }
                onError?.invoke("Failed to post story. Please try again.")
            }
        }
    }

    /**
     * Uploads media bundle (image or video) and calls createStory matching exact web implementation.
     */
    fun uploadStoryAndPost(
        context: Context,
        mediaUri: Uri?,
        isVideo: Boolean,
        type: String, // "text" or "media"
        textContent: String,
        backgroundStyle: String,
        musicTitle: String?,
        musicUrl: String? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (_uiState.value.isGuest || !_uiState.value.isLoggedIn) {
            openAuth("login")
            return
        }

        val hasText = textContent.trim().isNotEmpty()
        val hasMedia = mediaUri != null

        if (!hasText && !hasMedia) {
            onError("Please add text or media to your story")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isStoriesLoading = true) }
            try {
                if (type == "text" || (hasText && !hasMedia)) {
                    createStory(
                        type = "text",
                        textContent = textContent.trim(),
                        backgroundStyle = backgroundStyle,
                        musicTitle = musicTitle,
                        musicUrl = musicUrl,
                        onSuccess = {
                            _uiState.update { it.copy(showCreateStoryDialog = false, isStoriesLoading = false) }
                            onSuccess()
                        },
                        onError = { err ->
                            _uiState.update { it.copy(isStoriesLoading = false) }
                            onError(err)
                        }
                    )
                    return@launch
                }

                if (mediaUri != null) {
                    if (isVideo) {
                        _uiState.update { it.copy(isStoriesLoading = false) }
                        onError("Video stories are disabled. Only photos can be posted to stories.")
                        return@launch
                    }
                    val bundle = MediaUploadHelper.uploadStoryImageBundle(context, mediaUri)

                    if (bundle == null || bundle.mediaUrl.isBlank()) {
                        _uiState.update { it.copy(isStoriesLoading = false) }
                        onError("Failed to upload story image. Please check your connection and try again.")
                        return@launch
                    }

                    createStory(
                        mediaUrl = bundle.mediaUrl,
                        type = "image",
                        textContent = textContent.trim(),
                        mediaUrls = bundle.mediaUrls,
                        mediaTypes = bundle.mediaTypes,
                        mediaMeta = bundle.mediaMeta,
                        backgroundStyle = backgroundStyle,
                        musicTitle = musicTitle,
                        musicUrl = musicUrl,
                        onSuccess = {
                            _uiState.update { it.copy(showCreateStoryDialog = false, isStoriesLoading = false) }
                            onSuccess()
                        },
                        onError = { err ->
                            _uiState.update { it.copy(isStoriesLoading = false) }
                            onError(err)
                        }
                    )
                }
            } catch (e: IllegalArgumentException) {
                _uiState.update { it.copy(isStoriesLoading = false) }
                onError(e.message ?: "Invalid story media")
            } catch (e: Exception) {
                _uiState.update { it.copy(isStoriesLoading = false) }
                onError(e.message ?: "Failed to upload story")
            }
        }
    }

    fun deleteStory(storyId: Long) {
        val currentUserId = _uiState.value.currentUser.id
        viewModelScope.launch {
            _uiState.update { state ->
                val updated = state.stories.filter { it.id != storyId }
                state.copy(
                    stories = updated,
                    selectedStory = if (state.selectedStory?.id == storyId) null else state.selectedStory
                )
            }
            StoriesApiService.deleteStory(storyId, currentUserId)
        }
    }

    fun reactToStory(storyId: Long, reaction: String) {
        val currentUserId = _uiState.value.currentUser.id
        viewModelScope.launch {
            // Optimistic update
            _uiState.update { state ->
                val updated = state.stories.map { s ->
                    if (s.id == storyId) {
                        val isSame = s.myReaction == reaction
                        s.copy(
                            myReaction = if (isSame) null else reaction,
                            reactionsCount = if (isSame) maxOf(0, s.reactionsCount - 1) else if (s.myReaction == null) s.reactionsCount + 1 else s.reactionsCount,
                            isLiked = if (isSame) false else true
                        )
                    } else s
                }
                state.copy(
                    stories = updated,
                    selectedStory = state.selectedStory?.let { sel ->
                        if (sel.id == storyId) {
                            val isSame = sel.myReaction == reaction
                            sel.copy(
                                myReaction = if (isSame) null else reaction,
                                reactionsCount = if (isSame) maxOf(0, sel.reactionsCount - 1) else if (sel.myReaction == null) sel.reactionsCount + 1 else sel.reactionsCount,
                                isLiked = if (isSame) false else true
                            )
                        } else sel
                    }
                )
            }
            StoriesApiService.reactToStory(storyId, currentUserId, reaction)
            val updatedReactions = StoriesApiService.fetchReactions(storyId, currentUserId)
            if (updatedReactions != null) {
                _uiState.update { state ->
                    state.copy(storyReactions = state.storyReactions + (storyId to updatedReactions))
                }
            }
        }
    }

    fun fetchStoryViewers(storyId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isStoryViewersLoading = true) }
            val viewers = StoriesApiService.fetchViewers(storyId)
            _uiState.update { state ->
                state.copy(
                    storyViewers = state.storyViewers + (storyId to viewers),
                    isStoryViewersLoading = false
                )
            }
        }
    }

    fun fetchStoryComments(storyId: Long) {
        val viewerId = _uiState.value.currentUser.id
        viewModelScope.launch {
            _uiState.update { it.copy(isStoryCommentsLoading = true) }
            val comments = StoriesApiService.fetchComments(storyId, viewerId)
            _uiState.update { state ->
                state.copy(
                    storyComments = state.storyComments + (storyId to comments),
                    isStoryCommentsLoading = false
                )
            }
        }
    }

    fun addStoryComment(storyId: Long, content: String, imageUrl: String? = null, parentId: Long? = null) {
        val currentUserId = _uiState.value.currentUser.id
        viewModelScope.launch {
            val created = StoriesApiService.addComment(storyId, currentUserId, content, imageUrl, parentId)
            if (created != null) {
                _uiState.update { state ->
                    val existing = state.storyComments[storyId] ?: emptyList()
                    val updated = existing + created
                    state.copy(storyComments = state.storyComments + (storyId to updated))
                }
            } else {
                fetchStoryComments(storyId)
            }
        }
    }

    fun toggleHideStoryComment(storyId: Long, commentId: Long, hide: Boolean) {
        val currentUserId = _uiState.value.currentUser.id
        viewModelScope.launch {
            _uiState.update { state ->
                val existing = state.storyComments[storyId] ?: emptyList()
                val updated = existing.map { if (it.id == commentId) it.copy(isHidden = hide) else it }
                state.copy(storyComments = state.storyComments + (storyId to updated))
            }
            StoriesApiService.toggleHideComment(storyId, currentUserId, commentId, if (hide) "hide" else "unhide")
        }
    }

    fun deleteStoryComment(storyId: Long, commentId: Long) {
        val currentUserId = _uiState.value.currentUser.id
        viewModelScope.launch {
            _uiState.update { state ->
                val existing = state.storyComments[storyId] ?: emptyList()
                val updated = existing.filter { it.id != commentId && it.parentId != commentId }
                state.copy(storyComments = state.storyComments + (storyId to updated))
            }
            StoriesApiService.deleteComment(storyId, commentId, currentUserId)
        }
    }

    fun likeStoryComment(commentId: Long) {
        val currentUserId = _uiState.value.currentUser.id
        viewModelScope.launch {
            StoriesApiService.likeComment(commentId, currentUserId)
        }
    }

    fun replyToStory(storyId: Long, text: String) {
        val currentUserId = _uiState.value.currentUser.id
        viewModelScope.launch {
            StoriesApiService.replyToStory(storyId, currentUserId, text)
        }
    }

    fun shareStory(storyId: Long, destination: String = "feed", message: String = "") {
        val currentUserId = _uiState.value.currentUser.id
        viewModelScope.launch {
            StoriesApiService.shareStory(storyId, currentUserId, destination, message)
        }
    }

    fun setShowMenuOverlay(show: Boolean) {
        _uiState.update { it.copy(showMenuOverlay = show) }
    }

    // ==========================================
    // F-MUSIC AUDIO PLAYER & CATALOG METHODS
    // ==========================================

    private var playbackJob: Job? = null
    private var downloadJob: Job? = null

    fun playTrack(track: AudioTrack) {
        _uiState.update { state ->
            val trackKey = "music:${track.id}"
            val currentPlays = state.trackPlays[trackKey] ?: (state.songs.find { it.id == track.id }?.stats?.plays ?: 0L)
            val isMyTrack = state.currentUser.id == track.uploaderId
            state.copy(
                currentAudioTrack = track,
                isPlayingAudio = true,
                audioPositionSeconds = 0,
                audioDurationSeconds = if (track.duration > 0) track.duration else 180,
                trackPlays = state.trackPlays + (trackKey to (currentPlays + 1L)),
                myTotalPlays = if (isMyTrack) state.myTotalPlays + 1L else state.myTotalPlays
            )
        }
        startPlaybackTicker()
    }

    private fun startPlaybackTicker() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val state = _uiState.value
                if (!state.isPlayingAudio || state.currentAudioTrack == null) break

                if (state.audioPositionSeconds >= state.audioDurationSeconds) {
                    if (state.isRepeatEnabled) {
                        _uiState.update { it.copy(audioPositionSeconds = 0) }
                    } else {
                        nextTrack()
                    }
                } else {
                    _uiState.update { it.copy(audioPositionSeconds = it.audioPositionSeconds + 1) }
                }
            }
        }
    }

    fun togglePlayAudio() {
        val currentlyPlaying = _uiState.value.isPlayingAudio
        if (currentlyPlaying) {
            playbackJob?.cancel()
            _uiState.update { it.copy(isPlayingAudio = false) }
        } else {
            if (_uiState.value.currentAudioTrack != null) {
                _uiState.update { it.copy(isPlayingAudio = true) }
                startPlaybackTicker()
            }
        }
    }

    fun seekAudio(seconds: Int) {
        _uiState.update { state ->
            state.copy(audioPositionSeconds = seconds.coerceIn(0, state.audioDurationSeconds))
        }
    }

    fun nextTrack() {
        val state = _uiState.value
        val songs = state.songs
        if (songs.isEmpty()) return

        val currentId = state.currentAudioTrack?.id
        val currentIndex = songs.indexOfFirst { it.id == currentId }

        val nextIndex = if (state.isShuffleEnabled && songs.size > 1) {
            var rand = (songs.indices).random()
            while (rand == currentIndex) {
                rand = (songs.indices).random()
            }
            rand
        } else {
            if (currentIndex >= 0) (currentIndex + 1) % songs.size else 0
        }

        val nextSong = songs[nextIndex]
        val audioTrack = AudioTrack(
            id = nextSong.id,
            title = nextSong.title,
            artist = nextSong.artist,
            duration = nextSong.durationSeconds,
            url = nextSong.audioUrl,
            uploaderId = nextSong.uploaderId,
            cover = nextSong.cover,
            type = "music",
            isVerified = nextSong.isVerified,
            likesCount = nextSong.stats.likes
        )
        playTrack(audioTrack)
    }

    fun prevTrack() {
        val state = _uiState.value
        if (state.audioPositionSeconds > 3) {
            _uiState.update { it.copy(audioPositionSeconds = 0) }
            return
        }

        val songs = state.songs
        if (songs.isEmpty()) return

        val currentId = state.currentAudioTrack?.id
        val currentIndex = songs.indexOfFirst { it.id == currentId }
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else songs.size - 1

        val prevSong = songs[prevIndex]
        val audioTrack = AudioTrack(
            id = prevSong.id,
            title = prevSong.title,
            artist = prevSong.artist,
            duration = prevSong.durationSeconds,
            url = prevSong.audioUrl,
            uploaderId = prevSong.uploaderId,
            cover = prevSong.cover,
            type = "music",
            isVerified = prevSong.isVerified,
            likesCount = prevSong.stats.likes
        )
        playTrack(audioTrack)
    }

    fun stopAudio() {
        playbackJob?.cancel()
        _uiState.update { it.copy(isPlayingAudio = false, audioPositionSeconds = 0) }
    }

    fun closeAudioPlayer() {
        playbackJob?.cancel()
        _uiState.update {
            it.copy(
                currentAudioTrack = null,
                isPlayingAudio = false,
                audioPositionSeconds = 0,
                isPlayerExpanded = false
            )
        }
    }

    fun setPlayerExpanded(expanded: Boolean) {
        _uiState.update { it.copy(isPlayerExpanded = expanded) }
    }

    fun toggleShuffle() {
        _uiState.update { it.copy(isShuffleEnabled = !it.isShuffleEnabled) }
    }

    fun toggleRepeat() {
        _uiState.update { it.copy(isRepeatEnabled = !it.isRepeatEnabled) }
    }

    fun setAudioVolume(vol: Float) {
        _uiState.update { it.copy(audioVolume = vol.coerceIn(0f, 1f)) }
    }

    fun toggleLikeTrack(trackId: String, type: String = "music") {
        _uiState.update { state ->
            val key = "$type:$trackId"
            val isLiked = key in state.likedTrackKeys
            val updatedKeys = if (isLiked) state.likedTrackKeys - key else state.likedTrackKeys + key

            val updatedSongs = state.songs.map { s ->
                if (s.id == trackId) {
                    val newLikes = if (isLiked) maxOf(0L, s.stats.likes - 1) else s.stats.likes + 1
                    s.copy(stats = s.stats.copy(likes = newLikes))
                } else s
            }

            val updatedTrack = state.currentAudioTrack?.let { curr ->
                if (curr.id == trackId) {
                    curr.copy(likesCount = if (isLiked) maxOf(0L, curr.likesCount - 1) else curr.likesCount + 1)
                } else curr
            }

            state.copy(
                likedTrackKeys = updatedKeys,
                songs = updatedSongs,
                currentAudioTrack = updatedTrack
            )
        }
    }

    fun downloadTrack(trackId: String) {
        val state = _uiState.value
        val trackTitle = state.songs.find { it.id == trackId }?.title
            ?: state.currentAudioTrack?.takeIf { it.id == trackId }?.title
            ?: "Track"

        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _uiState.update { it.copy(downloadingTrackId = trackId, downloadProgress = 15, downloadToast = "Starting download...") }
            delay(400)
            _uiState.update { it.copy(downloadProgress = 45) }
            delay(400)
            _uiState.update { it.copy(downloadProgress = 85) }
            delay(350)
            _uiState.update { it.copy(downloadProgress = 100) }
            delay(300)
            _uiState.update {
                it.copy(
                    downloadingTrackId = null,
                    downloadProgress = 0,
                    downloadedTrackIds = it.downloadedTrackIds + trackId,
                    downloadToast = "Downloaded \"$trackTitle\""
                )
            }
            delay(3000)
            _uiState.update { it.copy(downloadToast = null) }
        }
    }

    fun dismissDownloadToast() {
        _uiState.update { it.copy(downloadToast = null) }
    }

    fun openSongDiscussions(track: AudioTrack) {
        _uiState.update { it.copy(activeDiscussionTrack = track) }
    }

    fun closeSongDiscussions() {
        _uiState.update { it.copy(activeDiscussionTrack = null) }
    }

    fun addSongComment(songId: String, text: String) {
        if (_uiState.value.isGuest || !_uiState.value.isLoggedIn) {
            openAuth("login")
            return
        }
        if (text.isBlank()) return
        val currentUser = _uiState.value.currentUser
        val newComment = SongComment(
            id = System.currentTimeMillis(),
            songId = songId,
            userId = currentUser.id,
            userName = currentUser.name,
            userAvatar = currentUser.profileImageUrl,
            text = text.trim(),
            createdAt = System.currentTimeMillis()
        )
        _uiState.update { state ->
            val existing = state.songComments[songId] ?: emptyList()
            state.copy(
                songComments = state.songComments + (songId to (listOf(newComment) + existing))
            )
        }
    }

    fun toggleHideSongComment(songId: String, commentId: Long) {
        _uiState.update { state ->
            val list = state.songComments[songId] ?: emptyList()
            val updated = list.map { c ->
                if (c.id == commentId) c.copy(isHidden = !c.isHidden) else c
            }
            state.copy(songComments = state.songComments + (songId to updated))
        }
    }

    fun deleteSongComment(songId: String, commentId: Long) {
        _uiState.update { state ->
            val list = state.songComments[songId] ?: emptyList()
            state.copy(songComments = state.songComments + (songId to list.filter { it.id != commentId }))
        }
    }

    fun openShareTrack(track: AudioTrack) {
        _uiState.update { it.copy(activeShareTrack = track) }
    }

    fun closeShareTrack() {
        _uiState.update { it.copy(activeShareTrack = null) }
    }

    fun refreshLiveSongs() {
        launchSafe {
            _uiState.update { it.copy(isSongsLoading = true) }
            val liveSongs = SongsApiService.fetchSongs()
            _uiState.update { state ->
                state.copy(
                    songs = liveSongs,
                    isSongsLoading = false
                )
            }
        }
    }

    fun publishSingleSong(
        context: Context,
        title: String,
        artist: String,
        genre: String,
        album: String,
        coverUri: Uri,
        audioUri: Uri,
        onComplete: ((Boolean, String?) -> Unit)? = null
    ) {
        if (_uiState.value.isGuest || !_uiState.value.isLoggedIn) {
            openAuth("login")
            onComplete?.invoke(false, "Authentication required")
            return
        }
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(songUploadProgress = 15, songUploadStatus = "Uploading audio...") }
                val audioUrl = MediaUploadHelper.uploadAudioFile(context, audioUri)
                if (audioUrl.isNullOrBlank()) {
                    _uiState.update { it.copy(songUploadProgress = null, songUploadStatus = null) }
                    onComplete?.invoke(false, "Audio upload failed")
                    return@launch
                }

                _uiState.update { it.copy(songUploadProgress = 50, songUploadStatus = "Compressing artwork...") }
                val coverUrl = SongsApiService.uploadCompressedCover(context, coverUri)
                    ?: SongsApiService.DEFAULT_MUSIC_COVER

                _uiState.update { it.copy(songUploadProgress = 80, songUploadStatus = "Publishing track...") }
                val token = UserSessionManager.getAuthToken(context)
                val created = SongsApiService.createSong(
                    uploaderId = _uiState.value.currentUser.id,
                    title = title.trim().ifBlank { "Untitled Single" },
                    artistName = artist.trim().ifBlank { _uiState.value.currentUser.name },
                    albumName = album.trim().ifBlank { "Single" },
                    coverImageUrl = coverUrl,
                    audioUrl = audioUrl,
                    genre = genre.trim().ifBlank { "Afrobeats" },
                    token = token
                )

                if (created != null) {
                    _uiState.update { state ->
                        state.copy(
                            songs = listOf(created) + state.songs.filter { it.id != created.id },
                            songUploadProgress = 100,
                            songUploadStatus = "Song published successfully!"
                        )
                    }
                    refreshLiveSongs()
                    delay(1000)
                    _uiState.update { it.copy(songUploadProgress = null, songUploadStatus = null) }
                    onComplete?.invoke(true, null)
                } else {
                    _uiState.update { it.copy(songUploadProgress = null, songUploadStatus = null) }
                    onComplete?.invoke(false, "Failed to register song")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(songUploadProgress = null, songUploadStatus = null) }
                onComplete?.invoke(false, e.localizedMessage ?: "Upload error")
            }
        }
    }

    fun publishAlbum(
        context: Context,
        albumTitle: String,
        artist: String,
        genre: String,
        sharedCoverUri: Uri,
        tracks: List<AlbumTrackInput>,
        onComplete: ((Boolean, String?) -> Unit)? = null
    ) {
        if (_uiState.value.isGuest || !_uiState.value.isLoggedIn) {
            openAuth("login")
            onComplete?.invoke(false, "Authentication required")
            return
        }
        if (tracks.isEmpty()) {
            onComplete?.invoke(false, "At least one track is required")
            return
        }
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(songUploadProgress = 10, songUploadStatus = "Compressing album cover...") }
                val albumCoverUrl = SongsApiService.uploadCompressedCover(context, sharedCoverUri)
                    ?: SongsApiService.DEFAULT_MUSIC_COVER

                val token = UserSessionManager.getAuthToken(context)
                var successCount = 0

                for ((index, track) in tracks.withIndex()) {
                    val stepProgress = 15 + ((index.toFloat() / tracks.size.toFloat()) * 75).toInt()
                    _uiState.update {
                        it.copy(
                            songUploadProgress = stepProgress,
                            songUploadStatus = "Uploading track ${index + 1}/${tracks.size}: ${track.title}..."
                        )
                    }

                    val audioUrl = MediaUploadHelper.uploadAudioFile(context, track.audioUri)
                    if (audioUrl.isNullOrBlank()) continue

                    val trackCoverUrl = if (track.coverUri != null) {
                        SongsApiService.uploadCompressedCover(context, track.coverUri) ?: albumCoverUrl
                    } else albumCoverUrl

                    val created = SongsApiService.createSong(
                        uploaderId = _uiState.value.currentUser.id,
                        title = track.title.trim().ifBlank { "Track ${index + 1}" },
                        artistName = (track.artist ?: artist).trim().ifBlank { _uiState.value.currentUser.name },
                        albumName = albumTitle.trim().ifBlank { "Album" },
                        coverImageUrl = trackCoverUrl,
                        audioUrl = audioUrl,
                        genre = genre.trim().ifBlank { "Afrobeats" },
                        token = token
                    )
                    if (created != null) successCount++
                }

                _uiState.update {
                    it.copy(
                        songUploadProgress = 100,
                        songUploadStatus = "Album published with $successCount tracks!"
                    )
                }
                refreshLiveSongs()
                delay(1200)
                _uiState.update { it.copy(songUploadProgress = null, songUploadStatus = null) }
                onComplete?.invoke(successCount > 0, if (successCount > 0) null else "No tracks uploaded")
            } catch (e: Exception) {
                _uiState.update { it.copy(songUploadProgress = null, songUploadStatus = null) }
                onComplete?.invoke(false, e.localizedMessage ?: "Album upload failed")
            }
        }
    }

    fun uploadSong(
        title: String,
        artist: String,
        genre: String,
        album: String,
        coverUrl: String,
        audioUrl: String,
        duration: String = "3:15"
    ) {
        if (_uiState.value.isGuest || !_uiState.value.isLoggedIn) {
            openAuth("login")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(songUploadProgress = 30, songUploadStatus = "Publishing track...") }
            val token = UserSessionManager.getAuthToken(getApplication<Application>().applicationContext)
            val created = SongsApiService.createSong(
                uploaderId = _uiState.value.currentUser.id,
                title = title.trim().ifBlank { "Untitled Track" },
                artistName = artist.trim().ifBlank { _uiState.value.currentUser.name },
                albumName = album.trim().ifBlank { "Single" },
                coverImageUrl = coverUrl.ifBlank { SongsApiService.DEFAULT_MUSIC_COVER },
                audioUrl = audioUrl,
                genre = genre.trim().ifBlank { "Afrobeats" },
                token = token
            )
            if (created != null) {
                _uiState.update { state ->
                    state.copy(
                        songs = listOf(created) + state.songs.filter { it.id != created.id },
                        songUploadProgress = 100,
                        songUploadStatus = "Track published!"
                    )
                }
                refreshLiveSongs()
            } else {
                _uiState.update { it.copy(songUploadProgress = 100, songUploadStatus = "Failed to publish") }
            }
            delay(1200)
            _uiState.update { it.copy(songUploadProgress = null, songUploadStatus = null) }
        }
    }

    fun deleteUploadedSong(songId: String) {
        val uid = _uiState.value.currentUser.id
        val token = UserSessionManager.getAuthToken(getApplication<Application>().applicationContext)
        _uiState.update { state ->
            val updated = state.songs.filter { it.id != songId }
            state.copy(
                songs = updated,
                currentAudioTrack = if (state.currentAudioTrack?.id == songId) null else state.currentAudioTrack
            )
        }
        viewModelScope.launch {
            SongsApiService.deleteSong(songId, uid, token)
        }
    }

    // ==========================================
    // GROUPS & COMMUNITY METHODS
    // ==========================================

    // ==========================================
    // GROUPS & COMMUNITY METHODS (LIVE API)
    // ==========================================

    fun refreshGroups() {
        launchSafe {
            _uiState.update { it.copy(isGroupsLoading = true) }
            val viewerId = _uiState.value.currentUser.id
            val liveGroups = GroupsApiService.fetchGroups(viewerId)
            val suggestions = GroupsApiService.fetchGroupSuggestions(viewerId)
            val invites = GroupsApiService.fetchGroupInvites(viewerId)
            _uiState.update { state ->
                state.copy(
                    groups = liveGroups,
                    groupSuggestions = suggestions,
                    groupInvites = invites,
                    isGroupsLoading = false
                )
            }
            val currentActiveId = _uiState.value.activeGroupId
            if (currentActiveId != null) {
                loadGroupData(currentActiveId)
            }
        }
    }

    fun loadGroupData(groupId: Long) {
        val viewerId = _uiState.value.currentUser.id
        viewModelScope.launch {
            // 1. Group Details & member IDs
            val (updatedGroup, memberIds) = GroupsApiService.fetchGroupDetails(groupId, viewerId)
            if (updatedGroup != null) {
                val fullGroup = if (memberIds.isNotEmpty()) updatedGroup.copy(members = memberIds) else updatedGroup
                _uiState.update { state ->
                    val existing = state.groups.map { if (it.id == groupId) fullGroup else it }
                    val finalGroups = if (existing.none { it.id == groupId }) listOf(fullGroup) + existing else existing
                    state.copy(groups = finalGroups)
                }
            }
            // 2. Posts
            _uiState.update { it.copy(isGroupPostsLoading = true) }
            val posts = GroupsApiService.fetchGroupPosts(groupId, viewerId)
            _uiState.update { state ->
                val otherPosts = state.groupPosts.filter { it.groupId != groupId }
                state.copy(groupPosts = posts + otherPosts, isGroupPostsLoading = false)
            }
            // 3. Members
            val members = GroupsApiService.fetchGroupMembers(groupId)
            _uiState.update { state ->
                state.copy(groupMembers = state.groupMembers + (groupId to members))
            }
            // 4. Events
            val events = GroupsApiService.fetchGroupEvents(groupId, viewerId)
            _uiState.update { state ->
                state.copy(groupEvents = state.groupEvents + (groupId to events))
            }
        }
    }

    fun setActiveGroupId(groupId: Long?) {
        _uiState.update { it.copy(activeGroupId = groupId) }
        if (groupId != null) {
            loadGroupData(groupId)
        }
    }

    fun createGroup(
        name: String,
        description: String,
        type: String, // "public" or "private"
        category: String, // "general", "recruitment", "buy_sell"
        profileImage: String,
        coverImage: String
    ) {
        if (_uiState.value.isGuest || !_uiState.value.isLoggedIn) {
            openAuth("login")
            return
        }
        viewModelScope.launch {
            val created = GroupsApiService.createGroup(
                name = name.ifBlank { "New Community" },
                description = description,
                type = type,
                category = category,
                profileImage = profileImage,
                coverImage = coverImage,
                adminId = _uiState.value.currentUser.id
            )
            if (created != null) {
                _uiState.update { state ->
                    state.copy(
                        groups = listOf(created) + state.groups,
                        activeGroupId = created.id
                    )
                }
                loadGroupData(created.id)
            } else {
                val newId = System.currentTimeMillis()
                val fallbackGroup = Group(
                    id = newId,
                    adminId = _uiState.value.currentUser.id,
                    name = name.ifBlank { "New Community" },
                    description = description,
                    isPrivate = type == "private",
                    category = category,
                    coverImageUrl = coverImage.ifBlank { "https://images.unsplash.com/photo-1522071820081-009f0129c71c?auto=format&fit=crop&w=1500&q=80" },
                    profileImageUrl = profileImage.ifBlank { "https://ui-avatars.com/api/?name=${name}&background=random" },
                    membersCount = 1,
                    isMember = true,
                    memberPostingAllowed = true,
                    members = listOf(_uiState.value.currentUser.id)
                )
                _uiState.update { state ->
                    state.copy(
                        groups = listOf(fallbackGroup) + state.groups,
                        activeGroupId = newId
                    )
                }
            }
        }
    }

    fun joinGroup(groupId: Long) {
        if (_uiState.value.isGuest || !_uiState.value.isLoggedIn) {
            openAuth("login")
            return
        }
        val currentUserId = _uiState.value.currentUser.id
        _uiState.update { state ->
            val updatedGroups = state.groups.map { group ->
                if (group.id == groupId) {
                    val newMembers = if (group.members.contains(currentUserId)) group.members else group.members + currentUserId
                    group.copy(
                        isMember = true,
                        members = newMembers,
                        membersCount = newMembers.size.coerceAtLeast(group.membersCount + 1)
                    )
                } else group
            }
            state.copy(groups = updatedGroups)
        }
        viewModelScope.launch {
            GroupsApiService.joinGroup(groupId, currentUserId)
            loadGroupData(groupId)
        }
    }

    fun leaveGroup(groupId: Long) {
        val currentUserId = _uiState.value.currentUser.id
        _uiState.update { state ->
            val updatedGroups = state.groups.map { group ->
                if (group.id == groupId) {
                    val newMembers = group.members.filter { it != currentUserId }
                    group.copy(
                        isMember = false,
                        members = newMembers,
                        membersCount = (group.membersCount - 1).coerceAtLeast(0)
                    )
                } else group
            }
            state.copy(groups = updatedGroups)
        }
        viewModelScope.launch {
            GroupsApiService.leaveOrRemoveMember(groupId, currentUserId, currentUserId)
            loadGroupData(groupId)
        }
    }

    fun deleteGroup(groupId: Long) {
        _uiState.update { state ->
            state.copy(
                groups = state.groups.filter { it.id != groupId },
                groupPosts = state.groupPosts.filter { it.groupId != groupId },
                activeGroupId = if (state.activeGroupId == groupId) null else state.activeGroupId
            )
        }
        viewModelScope.launch {
            GroupsApiService.deleteGroup(groupId)
        }
    }

    fun updateGroupSettings(groupId: Long, name: String, description: String, memberPostingAllowed: Boolean) {
        _uiState.update { state ->
            val updated = state.groups.map { g ->
                if (g.id == groupId) {
                    g.copy(
                        name = name.ifBlank { g.name },
                        description = description,
                        memberPostingAllowed = memberPostingAllowed
                    )
                } else g
            }
            state.copy(groups = updated)
        }
        viewModelScope.launch {
            GroupsApiService.updateGroup(
                groupId = groupId,
                name = name,
                description = description,
                memberPostingAllowed = memberPostingAllowed
            )
        }
    }

    fun updateGroupImage(groupId: Long, isCover: Boolean, imageUrl: String) {
        _uiState.update { state ->
            val updated = state.groups.map { g ->
                if (g.id == groupId) {
                    if (isCover) g.copy(coverImageUrl = imageUrl) else g.copy(profileImageUrl = imageUrl)
                } else g
            }
            state.copy(groups = updated)
        }
        viewModelScope.launch {
            GroupsApiService.updateGroup(
                groupId = groupId,
                coverImage = if (isCover) imageUrl else null,
                profileImage = if (!isCover) imageUrl else null
            )
        }
    }

    fun postToGroup(
        context: Context?,
        groupId: Long,
        content: String,
        imageUrls: List<String> = emptyList(),
        price: Double? = null,
        currency: String = "USD",
        condition: String? = null,
        location: String? = null,
        status: String = "available",
        jobTitle: String? = null,
        company: String? = null,
        jobType: String? = null,
        salary: String? = null,
        applicationType: String? = null,
        applicationValue: String? = null
    ) {
        if (_uiState.value.isGuest || !_uiState.value.isLoggedIn) {
            openAuth("login")
            return
        }

        val meId = _uiState.value.currentUser.id
        val targetGroup = _uiState.value.groups.find { it.id == groupId }
        val groupName = targetGroup?.name?.let { "\"$it\"" } ?: "group"
        val previewUrl = imageUrls.firstOrNull()

        // 1. Initial State: 15% - "Posting to <groupName>…"
        _uiState.update { state ->
            state.copy(
                postUploadState = PostUploadState(
                    isUploading = true,
                    progress = 15f,
                    title = "Posting to $groupName…",
                    secondaryStatus = if (imageUrls.isNotEmpty()) {
                        if (imageUrls.size > 1) "Optimizing ${imageUrls.size} photos..." else "Preparing media attachment..."
                    } else {
                        "Publishing post to group..."
                    },
                    previewUrl = previewUrl,
                    isSuccess = false
                )
            )
        }

        viewModelScope.launch {
            try {
                val finalMediaUrls = mutableListOf<String>()
                val finalMediaTypes = mutableListOf<String>()
                val finalMediaMeta = mutableListOf<Map<String, String>>()

                if (imageUrls.isNotEmpty()) {
                    var completedFiles = 0
                    for (item in imageUrls) {
                        if (item.contains("video", ignoreCase = true) || item.endsWith(".mp4", ignoreCase = true) || item.endsWith(".mov", ignoreCase = true)) {
                            throw IllegalStateException("Video uploads are disabled in groups. Only photos can be posted.")
                        }

                        val targetContext = context ?: getApplication<Application>().applicationContext
                        if (item.startsWith("content://") || item.startsWith("file://")) {
                            val uri = Uri.parse(item)
                            val bundleResult = MediaUploadHelper.buildImageBundleAndUpload(targetContext, uri)
                            if (bundleResult == null || bundleResult.feedUrl.isBlank()) {
                                throw IllegalStateException("Failed to upload photo to group")
                            }
                            val feed = bundleResult.feedUrl
                            val thumb = bundleResult.thumbUrl.ifBlank { feed }
                            finalMediaUrls.add(feed)
                            finalMediaTypes.add("image")
                            finalMediaMeta.add(
                                mapOf(
                                    "thumb" to thumb,
                                    "feed" to feed,
                                    "full" to feed, // group only compress to feed image
                                    "type" to "image"
                                )
                            )
                        } else if (item.startsWith("http://") || item.startsWith("https://")) {
                            finalMediaUrls.add(item)
                            finalMediaTypes.add("image")
                            finalMediaMeta.add(
                                mapOf(
                                    "thumb" to item,
                                    "feed" to item,
                                    "full" to item,
                                    "type" to "image"
                                )
                            )
                        }

                        completedFiles++
                        val pct = Math.min(85f, 20f + ((completedFiles.toFloat() / imageUrls.size) * 65f))
                        _uiState.update { state ->
                            state.copy(
                                postUploadState = state.postUploadState?.copy(
                                    progress = pct,
                                    secondaryStatus = if (imageUrls.size > 1) {
                                        "Uploaded photo $completedFiles of ${imageUrls.size}..."
                                    } else {
                                        "Photo uploaded. Publishing post..."
                                    }
                                )
                            )
                        }
                    }
                }

                _uiState.update { state ->
                    state.copy(
                        postUploadState = state.postUploadState?.copy(
                            progress = 90f,
                            secondaryStatus = "Publishing post to group..."
                        )
                    )
                }

                val createdPost = GroupsApiService.createGroupPost(
                    groupId = groupId,
                    userId = meId,
                    content = content,
                    mediaUrls = finalMediaUrls,
                    mediaTypes = finalMediaTypes,
                    mediaMeta = finalMediaMeta,
                    price = price,
                    currency = currency,
                    condition = condition,
                    location = location,
                    status = status,
                    jobTitle = jobTitle,
                    company = company,
                    jobType = jobType,
                    salary = salary,
                    applicationType = applicationType,
                    applicationValue = applicationValue
                )

                if (createdPost != null) {
                    _uiState.update { state ->
                        state.copy(
                            groupPosts = listOf(createdPost) + state.groupPosts,
                            postUploadState = state.postUploadState?.copy(
                                isUploading = false,
                                isSuccess = true,
                                progress = 100f,
                                title = "Group post published!",
                                secondaryStatus = "Your post is now visible in the group."
                            )
                        )
                    }
                } else {
                    val newPost = GroupPost(
                        id = System.currentTimeMillis(),
                        groupId = groupId,
                        author = _uiState.value.currentUser,
                        content = content,
                        imageUrls = finalMediaUrls,
                        createdAt = "Just now",
                        likesCount = 0,
                        commentsCount = 0,
                        sharesCount = 0,
                        isLiked = false,
                        price = price,
                        currency = currency,
                        condition = condition,
                        location = location,
                        status = status,
                        jobTitle = jobTitle,
                        company = company,
                        jobType = jobType,
                        salary = salary,
                        applicationType = applicationType,
                        applicationValue = applicationValue
                    )
                    _uiState.update { state ->
                        state.copy(
                            groupPosts = listOf(newPost) + state.groupPosts,
                            postUploadState = state.postUploadState?.copy(
                                isUploading = false,
                                isSuccess = true,
                                progress = 100f,
                                title = "Group post published!",
                                secondaryStatus = "Your post is now visible in the group."
                            )
                        )
                    }
                }

                delay(2800)
                _uiState.update { it.copy(postUploadState = null) }
            } catch (e: Exception) {
                e.printStackTrace()
                val errorMsg = e.message ?: "Could not publish group post."
                _uiState.update { state ->
                    state.copy(
                        postUploadState = state.postUploadState?.copy(
                            isUploading = false,
                            isSuccess = false,
                            progress = 0f,
                            title = "Upload failed",
                            secondaryStatus = errorMsg
                        )
                    )
                }
                delay(4000)
                _uiState.update { it.copy(postUploadState = null) }
            }
        }
    }

    fun postToGroup(
        groupId: Long,
        content: String,
        imageUrls: List<String> = emptyList(),
        price: Double? = null,
        currency: String = "USD",
        condition: String? = null,
        location: String? = null,
        status: String = "available",
        jobTitle: String? = null,
        company: String? = null,
        jobType: String? = null,
        salary: String? = null,
        applicationType: String? = null,
        applicationValue: String? = null
    ) {
        postToGroup(
            context = getApplication<Application>().applicationContext,
            groupId = groupId,
            content = content,
            imageUrls = imageUrls,
            price = price,
            currency = currency,
            condition = condition,
            location = location,
            status = status,
            jobTitle = jobTitle,
            company = company,
            jobType = jobType,
            salary = salary,
            applicationType = applicationType,
            applicationValue = applicationValue
        )
    }

    fun toggleLikeGroupPost(postId: Long, reaction: String = "like") {
        if (_uiState.value.isGuest || !_uiState.value.isLoggedIn) {
            openAuth("login")
            return
        }
        val currentUserId = _uiState.value.currentUser.id
        val groupPost = _uiState.value.groupPosts.find { it.id == postId } ?: return
        val wasLiked = groupPost.isLiked
        val finalReactionToSend = if (wasLiked) "unlike" else reaction

        _uiState.update { state ->
            val updated = state.groupPosts.map { post ->
                if (post.id == postId) {
                    val newCount = if (wasLiked) (post.likesCount - 1).coerceAtLeast(0) else post.likesCount + 1
                    post.copy(
                        isLiked = !wasLiked,
                        likesCount = newCount,
                        myReaction = if (wasLiked) null else reaction
                    )
                } else post
            }
            state.copy(groupPosts = updated)
        }
        viewModelScope.launch {
            val group = _uiState.value.groups.find { it.id == groupPost.groupId } ?: _uiState.value.groups.find { it.id == _uiState.value.activeGroupId }
            val postObj = groupPost.toPost(group)
            val liveResult = FeedInteractionApiService.reactToFeedItem(
                post = postObj,
                userId = currentUserId,
                reactionType = finalReactionToSend
            )
            _uiState.update { state ->
                val finalMyReaction = liveResult.myReaction
                val finalCount = liveResult.reactionsCount
                val finalLiked = finalMyReaction != null
                state.copy(
                    groupPosts = state.groupPosts.map { gp ->
                        if (gp.id == postId) {
                            gp.copy(
                                isLiked = finalLiked,
                                myReaction = finalMyReaction,
                                likesCount = finalCount,
                                sharesCount = liveResult.sharesCount ?: gp.sharesCount,
                                commentsCount = liveResult.commentsCount ?: gp.commentsCount
                            )
                        } else gp
                    }
                )
            }
        }
    }

    fun addGroupPostComment(postId: Long, text: String) {
        if (_uiState.value.isGuest || !_uiState.value.isLoggedIn) {
            openAuth("login")
            return
        }
        if (text.isBlank()) return
        val currentUserId = _uiState.value.currentUser.id
        val groupPost = _uiState.value.groupPosts.find { it.id == postId }
        val newComment = Comment(
            id = System.currentTimeMillis(),
            author = _uiState.value.currentUser,
            text = text,
            timestamp = "Just now",
            likesCount = 0,
            isLiked = false
        )
        _uiState.update { state ->
            val updated = state.groupPosts.map { post ->
                if (post.id == postId) {
                    post.copy(
                        comments = post.comments + newComment,
                        commentsCount = post.commentsCount + 1
                    )
                } else post
            }
            state.copy(groupPosts = updated)
        }
        viewModelScope.launch {
            val group = _uiState.value.groups.find { it.id == groupPost?.groupId } ?: _uiState.value.groups.find { it.id == _uiState.value.activeGroupId }
            val postObj = groupPost?.toPost(group) ?: Post(
                id = postId,
                author = _uiState.value.currentUser,
                content = "",
                timestamp = "",
                postType = "group_post",
                groupId = groupPost?.groupId
            )
            val result = FeedInteractionApiService.addComment(
                post = postObj,
                userId = currentUserId,
                text = text
            )
            val createdComment = result?.comment
            if (createdComment != null) {
                val fullCreatedComment = createdComment.copy(
                    author = createdComment.author.copy(
                        name = createdComment.author.name.ifBlank { _uiState.value.currentUser.name },
                        profileImageUrl = createdComment.author.profileImageUrl.ifBlank { _uiState.value.currentUser.profileImageUrl },
                        isVerified = createdComment.author.isVerified || _uiState.value.currentUser.isVerified
                    )
                )
                _uiState.update { state ->
                    val updated = state.groupPosts.map { post ->
                        if (post.id == postId) {
                            post.copy(
                                comments = post.comments.filter { it.id != newComment.id } + fullCreatedComment
                            )
                        } else post
                    }
                    state.copy(groupPosts = updated)
                }
            }
        }
    }

    fun deleteGroupPost(postId: Long) {
        val currentUserId = _uiState.value.currentUser.id
        _uiState.update { state ->
            state.copy(groupPosts = state.groupPosts.filter { it.id != postId })
        }
        viewModelScope.launch {
            GroupsApiService.deleteGroupPost(postId, currentUserId)
        }
    }

    fun shareGroupPost(postId: Long, destination: String = "feed", message: String = "") {
        if (_uiState.value.isGuest || !_uiState.value.isLoggedIn) {
            openAuth("login")
            return
        }
        val groupPost = _uiState.value.groupPosts.find { it.id == postId }
        _uiState.update { state ->
            val updated = state.groupPosts.map { post ->
                if (post.id == postId) post.copy(sharesCount = post.sharesCount + 1) else post
            }
            state.copy(groupPosts = updated)
        }
        if (groupPost != null) {
            viewModelScope.launch {
                val group = _uiState.value.groups.find { it.id == groupPost.groupId } ?: _uiState.value.groups.find { it.id == _uiState.value.activeGroupId }
                val postObj = groupPost.toPost(group)
                val liveResult = FeedInteractionApiService.shareItem(
                    post = postObj,
                    userId = _uiState.value.currentUser.id,
                    destination = destination,
                    message = message
                )
                _uiState.update { state ->
                    state.copy(
                        groupPosts = state.groupPosts.map { gp ->
                            if (gp.id == postId) gp.copy(sharesCount = liveResult.sharesCount) else gp
                        }
                    )
                }
            }
        }
    }

    fun inviteUserToGroup(groupId: Long, userId: Long) {
        val currentUserId = _uiState.value.currentUser.id
        viewModelScope.launch {
            GroupsApiService.sendGroupInvite(groupId, currentUserId, listOf(userId))
        }
    }

    fun acceptGroupInvite(inviteId: Long, groupId: Long) {
        val currentUserId = _uiState.value.currentUser.id
        joinGroup(groupId)
        _uiState.update { state ->
            state.copy(groupInvites = state.groupInvites.filter { it.id != inviteId })
        }
        viewModelScope.launch {
            GroupsApiService.respondGroupInvite(inviteId, groupId, currentUserId, "accepted")
        }
    }

    fun declineGroupInvite(inviteId: Long) {
        val currentUserId = _uiState.value.currentUser.id
        _uiState.update { state ->
            state.copy(groupInvites = state.groupInvites.filter { it.id != inviteId })
        }
        viewModelScope.launch {
            GroupsApiService.respondGroupInvite(inviteId, 0L, currentUserId, "declined")
        }
    }

    fun toggleMemberPosting(groupId: Long, userId: Long, disabled: Boolean) {
        val currentUserId = _uiState.value.currentUser.id
        _uiState.update { state ->
            val currentGroupDisabled = state.groupPostingDisabledOverrides[groupId] ?: emptySet()
            val newGroupDisabled = if (disabled) currentGroupDisabled + userId else currentGroupDisabled - userId
            state.copy(
                groupPostingDisabledOverrides = state.groupPostingDisabledOverrides + (groupId to newGroupDisabled)
            )
        }
        viewModelScope.launch {
            val payload = JSONObject().apply {
                put("group_id", groupId)
                put("user_id", userId)
                put("disabled", disabled)
                put("actor_id", currentUserId)
            }
            GroupsApiService.patchMemberAction("toggle-posting", payload)
            val members = GroupsApiService.fetchGroupMembers(groupId)
            _uiState.update { it.copy(groupMembers = it.groupMembers + (groupId to members)) }
        }
    }

    fun setMemberRole(groupId: Long, userId: Long, role: String) {
        val currentUserId = _uiState.value.currentUser.id
        _uiState.update { state ->
            val currentGroupRoles = state.groupMembersOverrides[groupId] ?: emptyMap()
            state.copy(
                groupMembersOverrides = state.groupMembersOverrides + (groupId to (currentGroupRoles + (userId to role)))
            )
        }
        viewModelScope.launch {
            val action = when (role) {
                "admin" -> "make-admin"
                "moderator" -> "make-moderator"
                else -> "remove-admin"
            }
            val payload = JSONObject().apply {
                put("group_id", groupId)
                put("user_id", userId)
                put("actor_id", currentUserId)
            }
            GroupsApiService.patchMemberAction(action, payload)
            val members = GroupsApiService.fetchGroupMembers(groupId)
            _uiState.update { it.copy(groupMembers = it.groupMembers + (groupId to members)) }
        }
    }

    fun removeGroupMember(groupId: Long, userId: Long) {
        val currentUserId = _uiState.value.currentUser.id
        _uiState.update { state ->
            val updatedGroups = state.groups.map { group ->
                if (group.id == groupId) {
                    val newMembers = group.members.filter { it != userId }
                    group.copy(
                        members = newMembers,
                        membersCount = (group.membersCount - 1).coerceAtLeast(0)
                    )
                } else group
            }
            state.copy(groups = updatedGroups)
        }
        viewModelScope.launch {
            GroupsApiService.leaveOrRemoveMember(groupId, userId, currentUserId)
            val members = GroupsApiService.fetchGroupMembers(groupId)
            _uiState.update { it.copy(groupMembers = it.groupMembers + (groupId to members)) }
        }
    }

    // ==========================================
    // ADS MANAGER ACTIONS
    // ==========================================

    fun pauseAdCampaign(id: Long) {
        val currentUserId = _uiState.value.currentUser.id
        _uiState.update { state ->
            val updated = state.adCampaigns.map {
                if (it.id == id) it.copy(status = "paused") else it
            }
            state.copy(adCampaigns = updated)
        }
        viewModelScope.launch {
            AdsApiService.pauseOrResumeCampaign(id, "pause", currentUserId)
        }
    }

    fun resumeAdCampaign(id: Long) {
        val currentUserId = _uiState.value.currentUser.id
        _uiState.update { state ->
            val updated = state.adCampaigns.map {
                if (it.id == id) it.copy(status = "active") else it
            }
            state.copy(adCampaigns = updated)
        }
        viewModelScope.launch {
            AdsApiService.pauseOrResumeCampaign(id, "resume", currentUserId)
        }
    }

    fun deleteAdCampaign(id: Long) {
        val currentUserId = _uiState.value.currentUser.id
        _uiState.update { state ->
            val updated = state.adCampaigns.filter { it.id != id }
            state.copy(adCampaigns = updated)
        }
        viewModelScope.launch {
            AdsApiService.deleteCampaign(id, currentUserId)
        }
    }

    fun createAdCampaign(
        postId: Long?,
        name: String,
        type: String,
        mediaUrl: String?,
        mediaUrls: List<String>,
        description: String,
        link: String?,
        phone: String?,
        email: String?,
        cta: String,
        location: String,
        budget: Int,
        days: Int
    ) {
        val currentUserId = _uiState.value.currentUser.id
        val tempId = System.currentTimeMillis()
        val newCampaign = AdCampaign(
            id = tempId,
            name = name.ifBlank { "Campaign #${tempId % 1000}" },
            type = type,
            status = "active",
            mediaUrl = mediaUrl,
            mediaUrls = mediaUrls,
            description = description,
            cta = cta,
            link = link,
            phone = phone,
            email = email,
            location = location.ifBlank { "Global" },
            budget = budget,
            days = days,
            startDate = "2026-03-11",
            endDate = "2026-03-${11 + days}",
            impressions = 0,
            views = 0,
            clicks = 0,
            postId = postId
        )
        _uiState.update { state ->
            state.copy(
                adCampaigns = listOf(newCampaign) + state.adCampaigns,
                activeAdCampaignToBoostPost = null
            )
        }
        viewModelScope.launch {
            val (success, created) = AdsApiService.createCampaign(
                userId = currentUserId,
                postId = postId,
                name = name,
                type = type,
                mediaUrl = mediaUrl,
                mediaUrls = mediaUrls,
                description = description,
                link = link,
                phone = phone,
                email = email,
                cta = cta,
                location = location,
                budget = budget,
                days = days
            )
            if (success && created != null) {
                _uiState.update { state ->
                    val updated = state.adCampaigns.map { if (it.id == tempId) created else it }
                    state.copy(adCampaigns = updated)
                }
            }
        }
    }

    fun setAdCampaignToBoostPost(post: Post?) {
        _uiState.update { it.copy(activeAdCampaignToBoostPost = post) }
    }

    fun pushMore(post: Post) {
        _uiState.update {
            it.copy(
                activeAdCampaignToBoostPost = post,
                currentScreen = Screen.ADS_MANAGER
            )
        }
    }

    // ==========================================
    // AUTHENTICATION ACTIONS
    // ==========================================

    fun openAuth(mode: String = "login") {
        _uiState.update { it.copy(showAuthModal = true, authMode = mode) }
    }

    fun closeAuth() {
        _uiState.update { it.copy(showAuthModal = false) }
    }

    fun setAuthMode(mode: String) {
        _uiState.update { it.copy(authMode = mode) }
    }

    fun continueAsGuest() {
        _uiState.update {
            it.copy(
                currentUser = MockData.currentUser,
                isGuest = true,
                isLoggedIn = false,
                showAuthModal = false,
                currentScreen = Screen.FEED
            )
        }
        refreshFeed()
    }

    suspend fun loginUser(email: String, pass: String): Pair<Boolean, String?> {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank() || pass.isBlank()) {
            return Pair(false, "Please enter both email and password.")
        }

        val result = UserApiService.loginUser(trimmedEmail, pass)
        return if (result.isSuccess) {
            val authResult = result.getOrThrow()
            val liveUser = authResult.user
            val context = getApplication<Application>().applicationContext
            UserSessionManager.saveAuth(context, authResult.token, liveUser)

            _uiState.update { state ->
                val updatedUsers = if (state.users.any { it.id == liveUser.id }) {
                    state.users.map { if (it.id == liveUser.id) liveUser else it }
                } else {
                    listOf(liveUser) + state.users
                }
                state.copy(
                    currentUser = liveUser,
                    users = updatedUsers,
                    isLoggedIn = true,
                    isGuest = false,
                    showAuthModal = false,
                    currentScreen = Screen.FEED
                )
            }
            // Fetch live home feed for logged in user from /api/feeds?userId=<id>&limit=50
            refreshFeed()
            refreshVideos()
            fetchPeopleYouMayKnow()
            startMessagingServices()
            Pair(true, null)
        } else {
            val errorMsg = result.exceptionOrNull()?.message ?: "Invalid email or password."
            Pair(false, errorMsg)
        }
    }

    suspend fun registerUser(
        username: String,
        email: String,
        pass: String,
        nationality: String,
        location: String,
        birthDate: String,
        gender: String
    ): Pair<Boolean, String?> {
        val trimmedEmail = email.trim()
        if (username.isBlank() || trimmedEmail.isBlank() || pass.length < 6) {
            return Pair(false, "Please complete all fields with a valid password (min 6 characters).")
        }

        val result = UserApiService.signupUser(
            username = username.trim(),
            email = trimmedEmail,
            pass = pass,
            nationality = nationality.ifBlank { "Tanzania" },
            location = location.ifBlank { "Dar es Salaam" },
            birthDate = birthDate,
            gender = gender.ifBlank { "Female" }
        )

        return if (result.isSuccess) {
            val authResult = result.getOrThrow()
            val liveUser = authResult.user
            val context = getApplication<Application>().applicationContext
            UserSessionManager.saveAuth(context, authResult.token, liveUser)

            _uiState.update { state ->
                val updatedUsers = if (state.users.any { it.id == liveUser.id }) {
                    state.users.map { if (it.id == liveUser.id) liveUser else it }
                } else {
                    listOf(liveUser) + state.users
                }
                state.copy(
                    currentUser = liveUser,
                    users = updatedUsers,
                    isLoggedIn = true,
                    isGuest = false,
                    showAuthModal = false,
                    currentScreen = Screen.FEED
                )
            }
            refreshFeed()
            refreshVideos()
            fetchPeopleYouMayKnow()
            startMessagingServices()
            Pair(true, null)
        } else {
            val errorMsg = result.exceptionOrNull()?.message ?: "Signup failed. Please try again."
            Pair(false, errorMsg)
        }
    }

    fun logoutUser() {
        stopMessagingServices()
        val context = getApplication<Application>().applicationContext
        UserSessionManager.clearUser(context)
        _uiState.update { state ->
            state.copy(
                currentUser = MockData.currentUser, // Reset to Guest user (id = 0L)
                isLoggedIn = false,
                isGuest = true,
                showAuthModal = true,
                authMode = "login",
                conversations = emptyList(),
                activeConversationId = null
            )
        }
        refreshFeed()
    }
}
