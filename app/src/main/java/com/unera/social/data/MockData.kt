package com.unera.social.data

import com.unera.social.model.*

object MockData {
    val currentUser = User(
        id = 0L,
        name = "Guest",
        username = "guest",
        profileImageUrl = "",
        coverImageUrl = "",
        bio = "",
        location = "",
        followersCount = 0,
        followingCount = 0,
        isVerified = false,
        isOnline = false,
        role = "guest"
    )

    // No fake users - users list populated dynamically from database (UserApiService)
    val users = emptyList<User>()

    val stories = emptyList<Story>()

    val posts = emptyList<Post>()

    val reels = emptyList<Reel>()

    val products = emptyList<Product>()

    // Live events loaded exclusively from EventsApiService
    val events = emptyList<Event>()

    // Real conversations and notifications loaded dynamically
    val conversations = emptyList<Conversation>()

    val notifications = emptyList<Notification>()

    val songs = emptyList<Song>()


    val initialSongComments = emptyList<SongComment>()

    val groups = emptyList<Group>()
    val groupPosts = emptyList<GroupPost>()
    val groupInvites = emptyList<GroupInvite>()
}
