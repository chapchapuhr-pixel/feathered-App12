package com.unera.social.data

import com.unera.social.model.Comment
import com.unera.social.model.Post
import com.unera.social.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class LiveReactResult(
    val success: Boolean,
    val myReaction: String?,
    val reactionsCount: Int,
    val sharesCount: Int? = null,
    val commentsCount: Int? = null
)

data class LiveShareResult(
    val success: Boolean,
    val sharesCount: Int,
    val newPost: Post? = null
)

/**
 * FeedInteractionApiService
 * Implements the verbatim API specification from web Feed.tsx for:
 * 1. React (Posts, Videos, Groups, Music, Products, Events, Stories)
 * 2. Discuss / Comments (Fetch, Add, Like, Hide, Delete)
 * 3. Share (Posts, Videos, Groups, Music, Products, Events, Stories)
 */
object FeedInteractionApiService {

    private const val BASE_URL = "https://featheredsocial.site"
    private const val USER_AGENT = "FeatheredAndroid/1.0 (Linux; Android)"

    // =========================================================================
    // 1. REACT
    // =========================================================================

    /**
     * Reacts to any feed item type matching Feed.tsx / App.tsx reactToFeedItem
     */
    suspend fun reactToFeedItem(
        post: Post,
        userId: Long,
        reactionType: String
    ): LiveReactResult = withContext(Dispatchers.IO) {
        val isGroup = post.groupId != null || post.postType == "group_post"
        val isMusic = post.song != null || post.postType == "music" || post.postType == "song"
        val isProduct = post.product != null || post.postType == "product" || post.postType == "marketplace"
        val isEvent = post.event != null || post.postType == "event"
        val isStory = post.story != null || post.postType == "story"

        val endpointUrl: String
        val payload = JSONObject()
        val extraHeaders = mutableMapOf<String, String>()

        when {
            isGroup -> {
                endpointUrl = "$BASE_URL/api/group-post-likes"
                val targetPostId = post.id
                payload.put("user_id", userId)
                payload.put("post_id", targetPostId)
                payload.put("type", reactionType)
            }
            isMusic -> {
                val songId = post.song?.id?.toLongOrNull() ?: post.id
                endpointUrl = "$BASE_URL/api/songs/$songId/react"
                payload.put("type", reactionType)
                payload.put("user_id", userId)
            }
            isProduct -> {
                val productId = post.product?.id ?: post.id
                endpointUrl = "$BASE_URL/api/products/$productId/react"
                payload.put("type", reactionType)
                payload.put("user_id", userId)
            }
            isEvent -> {
                val eventId = post.event?.id ?: post.id
                endpointUrl = "$BASE_URL/api/events/$eventId/react"
                payload.put("type", reactionType)
                payload.put("user_id", userId)
            }
            isStory -> {
                val storyId = post.story?.id ?: post.id
                endpointUrl = "$BASE_URL/api/stories/$storyId/react"
                extraHeaders["x-user-id"] = userId.toString()
                payload.put("user_id", userId)
                payload.put("type", reactionType)
            }
            else -> {
                // Default: Regular Post or Video Reel
                endpointUrl = "$BASE_URL/api/posts/${post.id}/react"
                payload.put("type", reactionType)
                payload.put("user_id", userId)
            }
        }

        try {
            val url = URL(endpointUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("x-user-id", userId.toString())
                extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(payload.toString())
                it.flush()
            }

            val code = conn.responseCode
            if (code in 200..299) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }.trim()
                conn.disconnect()
                if (responseText.startsWith("{")) {
                    val root = JSONObject(responseText)
                    val success = root.optBoolean("success", true)
                    val myReactionRaw = if (root.has("my_reaction") && !root.isNull("my_reaction")) {
                        root.optString("my_reaction")
                    } else if (reactionType == "unlike") null else reactionType

                    val finalMyReaction = if (myReactionRaw == "unlike" || myReactionRaw.isNullOrBlank()) null else myReactionRaw

                    val reactionsCount = when {
                        root.has("reactions_count") -> root.optInt("reactions_count")
                        root.has("likes_count") -> root.optInt("likes_count")
                        root.has("count") -> root.optInt("count")
                        else -> if (finalMyReaction != null) post.likesCount + 1 else (post.likesCount - 1).coerceAtLeast(0)
                    }

                    val sharesCount = if (root.has("shares_count")) root.optInt("shares_count")
                        else if (root.has("shares")) root.optInt("shares") else null

                    val commentsCount = if (root.has("comments_count")) root.optInt("comments_count") else null

                    return@withContext LiveReactResult(
                        success = success,
                        myReaction = finalMyReaction,
                        reactionsCount = reactionsCount,
                        sharesCount = sharesCount,
                        commentsCount = commentsCount
                    )
                }
            } else {
                conn.disconnect()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback optimistic calculation if network failure
        val isSame = post.myReaction == reactionType
        val newReaction = if (isSame || reactionType == "unlike") null else reactionType
        val prevHad = post.myReaction != null
        val newCount = when {
            !prevHad && newReaction != null -> post.likesCount + 1
            prevHad && newReaction == null -> (post.likesCount - 1).coerceAtLeast(0)
            else -> post.likesCount
        }
        LiveReactResult(success = false, myReaction = newReaction, reactionsCount = newCount)
    }

    /**
     * Fetch live reactions for any post/item matching web endpoints
     */
    suspend fun fetchReactions(post: Post, viewerId: Long): Pair<List<com.unera.social.model.UserReaction>, Int> = withContext(Dispatchers.IO) {
        val reactionsList = mutableListOf<com.unera.social.model.UserReaction>()
        var count = post.likesCount

        val isMusic = post.song != null || post.postType == "music" || post.postType == "song"
        val isProduct = post.product != null || post.postType == "product" || post.postType == "marketplace"
        val isEvent = post.event != null || post.postType == "event"
        val isStory = post.story != null || post.postType == "story"

        val endpointUrl = when {
            isMusic -> {
                val songId = post.song?.id?.toLongOrNull() ?: post.id
                "$BASE_URL/api/songs/$songId/reactions?viewerId=$viewerId"
            }
            isProduct -> {
                val productId = post.product?.id ?: post.id
                "$BASE_URL/api/products/$productId/reactions"
            }
            isEvent -> {
                val eventId = post.event?.id ?: post.id
                "$BASE_URL/api/events/$eventId/reactions?viewerId=$viewerId"
            }
            isStory -> {
                val storyId = post.story?.id ?: post.id
                "$BASE_URL/api/stories/$storyId/reactions?viewerId=$viewerId"
            }
            else -> "$BASE_URL/api/posts/${post.id}/reactions?viewerId=$viewerId"
        }

        try {
            val url = URL(endpointUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("x-user-id", viewerId.toString())
            }

            if (conn.responseCode in 200..299) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }.trim()
                if (responseText.startsWith("{")) {
                    val root = JSONObject(responseText)
                    if (root.has("reactions_count")) count = root.optInt("reactions_count")
                    else if (root.has("count")) count = root.optInt("count")

                    val arr = root.optJSONArray("reactions")
                        ?: root.optJSONArray("data")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            reactionsList.add(parseUserReaction(obj))
                        }
                    }
                } else if (responseText.startsWith("[")) {
                    val arr = JSONArray(responseText)
                    count = arr.length()
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        reactionsList.add(parseUserReaction(obj))
                    }
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        Pair(reactionsList, count)
    }

    // =========================================================================
    // 2. DISCUSS / COMMENTS
    // =========================================================================

    /**
     * Fetch live comments from the server matching getFeedCommentFetchEndpoint
     */
    suspend fun fetchComments(post: Post, viewerId: Long): List<Comment> = withContext(Dispatchers.IO) {
        val commentsList = mutableListOf<Comment>()
        val isGroup = post.groupId != null || post.postType == "group_post"
        val isMusic = post.song != null || post.postType == "music" || post.postType == "song"
        val isProduct = post.product != null || post.postType == "product" || post.postType == "marketplace"
        val isEvent = post.event != null || post.postType == "event"
        val isStory = post.story != null || post.postType == "story"

        val endpointUrl = when {
            isGroup -> "$BASE_URL/api/group-post-comments?post_id=${post.id}&viewerId=$viewerId"
            isMusic -> {
                val songId = post.song?.id?.toLongOrNull() ?: post.id
                "$BASE_URL/api/songs/$songId/comments?viewerId=$viewerId"
            }
            isProduct -> {
                val productId = post.product?.id ?: post.id
                "$BASE_URL/api/products/$productId/reviews?viewerId=$viewerId"
            }
            isEvent -> {
                val eventId = post.event?.id ?: post.id
                "$BASE_URL/api/events/$eventId/comments?viewerId=$viewerId"
            }
            isStory -> {
                val storyId = post.story?.id ?: post.id
                "$BASE_URL/api/stories/$storyId/comments?viewerId=$viewerId"
            }
            else -> "$BASE_URL/api/posts/${post.id}/comments?viewerId=$viewerId"
        }

        try {
            val url = URL(endpointUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("x-user-id", viewerId.toString())
            }

            if (conn.responseCode in 200..299) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }.trim()
                if (responseText.startsWith("{")) {
                    val root = JSONObject(responseText)
                    val arr = root.optJSONArray("comments")
                        ?: root.optJSONArray("reviews")
                        ?: root.optJSONArray("data")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            parseComment(obj, viewerId)?.let { commentsList.add(it) }
                        }
                    }
                } else if (responseText.startsWith("[")) {
                    val arr = JSONArray(responseText)
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        parseComment(obj, viewerId)?.let { commentsList.add(it) }
                    }
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val organizedComments = organizeCommentsIntoThreads(commentsList)
        if (organizedComments.isNotEmpty()) {
            CommentsCache.setCachedComments(CommentsCache.resolveItemType(post), post.id, organizedComments)
        }
        organizedComments
    }

    /**
     * Organizes flat or nested comments into top-level comments with threaded replies in chronological order.
     * Oldest comments are at the top, and newer comments appear down below.
     * Replies are strictly placed in their parent's replies list and never appear as top-level comments.
     */
    fun organizeCommentsIntoThreads(comments: List<Comment>): List<Comment> {
        // Collect all reply IDs that are already nested inside any comment's replies
        val nestedReplyIds = comments.flatMap { it.replies.map { r -> r.id } }.toSet()

        // Top-level comments are those without parentCommentId and not nested inside another's replies
        val topLevel = comments.filter {
            (it.parentCommentId == null || it.parentCommentId == 0L) && !nestedReplyIds.contains(it.id)
        }.distinctBy { it.id }.toMutableList()

        // Group all replies that specify a parent comment
        val flatReplies = comments.filter {
            it.parentCommentId != null && it.parentCommentId != 0L
        }
        val repliesMap = flatReplies.groupBy { it.parentCommentId!! }

        val result = topLevel.map { parent ->
            val directReplies = parent.replies
            val extraReplies = repliesMap[parent.id] ?: emptyList()
            val allReplies = (directReplies + extraReplies).distinctBy { it.id }.sortedBy { it.id }
            parent.copy(replies = allReplies)
        }.sortedBy { it.id } // Chronological: oldest at top, newest at bottom

        return result
    }

data class AddCommentResult(
    val comment: Comment,
    val commentsCount: Int? = null
)

    /**
     * Submit a new live comment matching App.tsx createComment
     */
    suspend fun addComment(
        post: Post,
        userId: Long,
        text: String,
        parentCommentId: Long? = null,
        imageUrl: String? = null
    ): AddCommentResult? = withContext(Dispatchers.IO) {
        val isGroup = post.groupId != null || post.postType == "group_post"
        val isMusic = post.song != null || post.postType == "music" || post.postType == "song"
        val isProduct = post.product != null || post.postType == "product" || post.postType == "marketplace"
        val isEvent = post.event != null || post.postType == "event"
        val isStory = post.story != null || post.postType == "story"

        val endpointUrl: String
        val payload = JSONObject().apply {
            put("user_id", userId)
        }

        when {
            isGroup -> {
                endpointUrl = "$BASE_URL/api/group-post-comments"
                payload.put("post_id", post.id)
                payload.put("text", text)
                if (parentCommentId != null && parentCommentId > 0) payload.put("parent_comment_id", parentCommentId)
                if (!imageUrl.isNullOrBlank()) payload.put("image_url", imageUrl)
            }
            isMusic -> {
                val songId = post.song?.id?.toLongOrNull() ?: post.id
                endpointUrl = "$BASE_URL/api/songs/$songId/comment"
                payload.put("text", text)
                if (parentCommentId != null && parentCommentId > 0) payload.put("parent_comment_id", parentCommentId)
                if (!imageUrl.isNullOrBlank()) payload.put("image_url", imageUrl)
            }
            isProduct -> {
                val productId = post.product?.id ?: post.id
                endpointUrl = "$BASE_URL/api/products/$productId/review"
                payload.put("rating", JSONObject.NULL)
                payload.put("text", text)
                if (parentCommentId != null && parentCommentId > 0) payload.put("parent_comment_id", parentCommentId)
                if (!imageUrl.isNullOrBlank()) payload.put("image_url", imageUrl)
            }
            isEvent -> {
                val eventId = post.event?.id ?: post.id
                endpointUrl = "$BASE_URL/api/events/$eventId/comment"
                payload.put("text", text)
                if (parentCommentId != null && parentCommentId > 0) payload.put("parent_comment_id", parentCommentId)
                if (!imageUrl.isNullOrBlank()) payload.put("image_url", imageUrl)
            }
            isStory -> {
                val storyId = post.story?.id ?: post.id
                endpointUrl = "$BASE_URL/api/stories/$storyId/comments"
                payload.put("content", text)
                if (parentCommentId != null && parentCommentId > 0) payload.put("parent_id", parentCommentId)
                if (!imageUrl.isNullOrBlank()) payload.put("image_url", imageUrl)
            }
            else -> {
                endpointUrl = "$BASE_URL/api/posts/${post.id}/comments"
                payload.put("text", text)
                if (parentCommentId != null && parentCommentId > 0) payload.put("parent_comment_id", parentCommentId)
                if (!imageUrl.isNullOrBlank()) payload.put("image_url", imageUrl)
            }
        }

        try {
            val url = URL(endpointUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("x-user-id", userId.toString())
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(payload.toString())
                it.flush()
            }

            if (conn.responseCode in 200..299) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }.trim()
                conn.disconnect()
                if (responseText.startsWith("{")) {
                    val root = JSONObject(responseText)
                    val commentObj = root.optJSONObject("comment")
                        ?: root.optJSONObject("review")
                        ?: root.optJSONObject("data")
                        ?: root

                    val serverCommentsCount = if (root.has("comments_count") && !root.isNull("comments_count")) {
                        root.optInt("comments_count")
                    } else if (commentObj.has("comments_count") && !commentObj.isNull("comments_count")) {
                        commentObj.optInt("comments_count")
                    } else null

                    val serverId = commentObj.optLong("id", System.currentTimeMillis())
                    val serverCreatedAt = commentObj.optString("created_at").takeIf { it.isNotBlank() && it != "null" }
                    val serverText = (commentObj.optString("text").takeIf { it.isNotBlank() && it != "null" }
                        ?: commentObj.optString("content").takeIf { it.isNotBlank() && it != "null" }
                        ?: text).trim()
                    val serverImage = commentObj.optString("image_url").takeIf { it.isNotBlank() && it != "null" } ?: imageUrl
                    val serverParentId = if (commentObj.has("parent_comment_id") && !commentObj.isNull("parent_comment_id")) {
                        commentObj.optLong("parent_comment_id")
                    } else if (commentObj.has("parent_id") && !commentObj.isNull("parent_id")) {
                        commentObj.optLong("parent_id")
                    } else parentCommentId

                    val parsed = parseComment(commentObj, userId)
                    val currentUser = UserSessionManager.getCurrentUser()
                    val finalAuthor = if (parsed != null && parsed.author.name.isNotBlank() && parsed.author.id > 0) {
                        parsed.author.copy(
                            name = parsed.author.name.ifBlank { currentUser?.name ?: "You" },
                            profileImageUrl = parsed.author.profileImageUrl.ifBlank { currentUser?.profileImageUrl ?: "" },
                            isVerified = parsed.author.isVerified || (currentUser?.isVerified == true && (parsed.author.id == currentUser.id || parsed.author.id == userId))
                        )
                    } else {
                        currentUser ?: User(id = userId, name = "You", username = "", profileImageUrl = "", isVerified = false)
                    }

                    val finalComment = Comment(
                        id = serverId,
                        author = finalAuthor,
                        text = serverText,
                        timestamp = if (serverCreatedAt != null) PostsApiService.formatRelativeTime(serverCreatedAt) else "Just now",
                        likesCount = parsed?.likesCount ?: 0,
                        isLiked = parsed?.isLiked ?: false,
                        imageUrl = serverImage,
                        parentCommentId = serverParentId,
                        isHidden = parsed?.isHidden ?: false
                    )

                    val itemType = CommentsCache.resolveItemType(post)
                    val existing = CommentsCache.getCachedComments(itemType, post.id) ?: emptyList()
                    CommentsCache.setCachedComments(itemType, post.id, listOf(finalComment) + existing.filter { it.id != finalComment.id })

                    return@withContext AddCommentResult(
                        comment = finalComment,
                        commentsCount = serverCommentsCount
                    )
                }
            } else {
                conn.disconnect()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    /**
     * Like a comment matching getCommentLikeEndpoint
     */
    suspend fun likeComment(
        itemType: String,
        commentId: Long,
        userId: Long
    ): Boolean = withContext(Dispatchers.IO) {
        val endpointUrl = when (itemType) {
            "group_post" -> "$BASE_URL/api/group-post-comment-likes"
            "song", "music" -> "$BASE_URL/api/song-comments/$commentId/like"
            "product" -> "$BASE_URL/api/product-reviews/$commentId/like"
            "event" -> "$BASE_URL/api/event-comments/$commentId/like"
            else -> "$BASE_URL/api/post-comments/$commentId/like"
        }

        try {
            val url = URL(endpointUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("x-user-id", userId.toString())
            }

            val payload = JSONObject().apply {
                put("user_id", userId)
                if (itemType == "group_post") {
                    put("comment_id", commentId)
                }
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(payload.toString())
                it.flush()
            }

            val code = conn.responseCode
            conn.disconnect()
            val success = code in 200..299
            if (success) {
                CommentsCache.updateCachedComment(commentId) { c ->
                    val newLiked = !c.isLiked
                    val newCount = if (newLiked) c.likesCount + 1 else (c.likesCount - 1).coerceAtLeast(0)
                    c.copy(isLiked = newLiked, likesCount = newCount)
                }
            }
            return@withContext success
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    /**
     * Delete a comment matching CommentsSheet.handleDelete
     */
    suspend fun deleteComment(
        itemType: String,
        commentId: Long,
        userId: Long,
        parentId: Long
    ): Boolean = withContext(Dispatchers.IO) {
        val endpointUrl = when (itemType) {
            "song", "music" -> "$BASE_URL/api/songs/$parentId/comments?comment_id=$commentId&user_id=$userId"
            "product" -> "$BASE_URL/api/products/$parentId/reviews?comment_id=$commentId&user_id=$userId"
            "story" -> "$BASE_URL/api/stories/$parentId/comments?comment_id=$commentId&user_id=$userId"
            else -> "$BASE_URL/api/post-comments/$commentId/delete?user_id=$userId"
        }

        try {
            val url = URL(endpointUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("x-user-id", userId.toString())
            }
            val code = conn.responseCode
            conn.disconnect()
            return@withContext code in 200..299
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    /**
     * Hide or unhide a comment matching CommentsSheet.handleToggleHide
     */
    suspend fun hideComment(
        itemType: String,
        commentId: Long,
        userId: Long,
        parentId: Long,
        action: String // "hide" | "unhide"
    ): Boolean = withContext(Dispatchers.IO) {
        val isPatch = itemType in listOf("song", "music", "product", "story")
        val endpointUrl = when (itemType) {
            "song", "music" -> "$BASE_URL/api/songs/$parentId/comments"
            "product" -> "$BASE_URL/api/products/$parentId/reviews"
            "story" -> "$BASE_URL/api/stories/$parentId/comments"
            else -> "$BASE_URL/api/post-comments/$commentId/hide"
        }

        try {
            val url = URL(endpointUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = if (isPatch) "PATCH" else "POST"
                doOutput = true
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("x-user-id", userId.toString())
            }

            val payload = JSONObject().apply {
                put("user_id", userId)
                put("action", action)
                if (isPatch) {
                    put("comment_id", commentId)
                }
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(payload.toString())
                it.flush()
            }

            val code = conn.responseCode
            conn.disconnect()
            return@withContext code in 200..299
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    // =========================================================================
    // 3. SHARE
    // =========================================================================

    /**
     * Share feed item matching ShareBottomSheet.getShareEndpoint
     */
    suspend fun shareItem(
        post: Post,
        userId: Long,
        destination: String = "feed",
        message: String = ""
    ): LiveShareResult = withContext(Dispatchers.IO) {
        val isGroup = post.groupId != null || post.postType == "group_post"
        val isMusic = post.song != null || post.postType == "music" || post.postType == "song"
        val isProduct = post.product != null || post.postType == "product" || post.postType == "marketplace"
        val isEvent = post.event != null || post.postType == "event"
        val isStory = post.story != null || post.postType == "story"

        val endpointUrl: String
        val payload = JSONObject()
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val isoDate = isoFormat.format(Date())

        when {
            isGroup -> {
                endpointUrl = "$BASE_URL/api/groups/posts/share"
                payload.put("user_id", userId)
                payload.put("post_id", post.id)
                payload.put("group_id", post.groupId ?: 0L)
                payload.put("destination", destination)
                payload.put("source", "feed")
                if (message.isNotBlank()) payload.put("message", message)
            }
            isMusic -> {
                val songId = post.song?.id?.toLongOrNull() ?: post.id
                endpointUrl = "$BASE_URL/api/songs/$songId/share"
                payload.put("user_id", userId)
                payload.put("destination", destination)
                payload.put("shared_at", isoDate)
                payload.put("item_type", "music")
                payload.put("post_id", post.id)
                payload.put("song_id", songId)
                if (message.isNotBlank()) payload.put("message", message)
            }
            isProduct -> {
                val productId = post.product?.id ?: post.id
                endpointUrl = "$BASE_URL/api/products/$productId/share"
                payload.put("user_id", userId)
                payload.put("destination", destination)
                payload.put("shared_at", isoDate)
                payload.put("item_type", "product")
                payload.put("post_id", post.id)
                payload.put("product_id", productId)
                if (message.isNotBlank()) payload.put("message", message)
            }
            isEvent -> {
                val eventId = post.event?.id ?: post.id
                endpointUrl = "$BASE_URL/api/events/$eventId/share"
                payload.put("user_id", userId)
                payload.put("destination", destination)
                payload.put("shared_at", isoDate)
                payload.put("item_type", "event")
                payload.put("post_id", post.id)
                payload.put("event_id", eventId)
                if (message.isNotBlank()) payload.put("message", message)
            }
            isStory -> {
                val storyId = post.story?.id ?: post.id
                endpointUrl = "$BASE_URL/api/stories/$storyId/share"
                payload.put("user_id", userId)
                payload.put("destination", destination)
                if (message.isNotBlank()) payload.put("message", message)
            }
            else -> {
                endpointUrl = "$BASE_URL/api/posts/${post.id}/share"
                payload.put("user_id", userId)
                payload.put("destination", destination)
                payload.put("shared_at", isoDate)
                payload.put("item_type", "post")
                payload.put("post_id", post.id)
                if (message.isNotBlank()) payload.put("message", message)
                payload.put("audience", "Public")
            }
        }

        try {
            val url = URL(endpointUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 12000
                readTimeout = 12000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("x-user-id", userId.toString())
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(payload.toString())
                it.flush()
            }

            val code = conn.responseCode
            if (code in 200..299) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }.trim()
                conn.disconnect()
                if (responseText.startsWith("{")) {
                    val root = JSONObject(responseText)
                    val success = root.optBoolean("success", true)
                    val sharesCount = when {
                        root.has("shares_count") -> root.optInt("shares_count")
                        root.has("shares") -> root.optInt("shares")
                        else -> post.sharesCount + 1
                    }
                    val postObj = root.optJSONObject("post")
                    val newPost = if (postObj != null) {
                        PostsApiService.parseFeedItemToPostExternal(postObj)
                    } else null

                    return@withContext LiveShareResult(
                        success = success,
                        sharesCount = sharesCount,
                        newPost = newPost
                    )
                }
            } else {
                conn.disconnect()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        LiveShareResult(success = false, sharesCount = post.sharesCount + 1)
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun parseUserReaction(obj: JSONObject): com.unera.social.model.UserReaction {
        val userObj = obj.optJSONObject("user") ?: obj.optJSONObject("author")
        val uid = obj.optLong("user_id", obj.optLong("id", userObj?.optLong("id", 0L) ?: 0L))

        val rawName = (userObj?.optString("name") ?: "").takeIf { it.isNotBlank() && it != "null" }
            ?: (userObj?.optString("display_name") ?: "").takeIf { it.isNotBlank() && it != "null" }
            ?: obj.optString("user_name", "").takeIf { it.isNotBlank() && it != "null" }
            ?: obj.optString("name", "").takeIf { it.isNotBlank() && it != "null" }
            ?: (userObj?.optString("username") ?: "").takeIf { it.isNotBlank() && it != "null" }
            ?: obj.optString("username", "")

        val rawUsername = (userObj?.optString("username") ?: "").takeIf { it.isNotBlank() && it != "null" }
            ?: obj.optString("username", "").takeIf { it.isNotBlank() && it != "null" }
            ?: obj.optString("user_username", "").takeIf { it.isNotBlank() && it != "null" }
            ?: ""

        val rawAvatar = (userObj?.optString("profile_image_url") ?: "").takeIf { it.isNotBlank() && it != "null" }
            ?: (userObj?.optString("avatar_url") ?: "").takeIf { it.isNotBlank() && it != "null" }
            ?: (userObj?.optString("avatar") ?: "").takeIf { it.isNotBlank() && it != "null" }
            ?: obj.optString("user_avatar", "").takeIf { it.isNotBlank() && it != "null" }
            ?: obj.optString("profile_image_url", "").takeIf { it.isNotBlank() && it != "null" }
            ?: obj.optString("avatar", "").takeIf { it.isNotBlank() && it != "null" }
            ?: ""

        val type = obj.optString("type", obj.optString("reaction", "like"))

        val isVerified = (userObj?.optBoolean("is_verified") ?: obj.optBoolean("is_verified", false)) ||
            (userObj?.optInt("is_verified", 0) == 1) ||
            (obj.optInt("is_verified", 0) == 1) ||
            (userObj?.optBoolean("isVerified") ?: obj.optBoolean("isVerified", false)) ||
            (userObj?.optBoolean("verified") ?: obj.optBoolean("verified", false))

        val resolvedName = when {
            rawName.isNotBlank() && !rawName.equals("User", ignoreCase = true) && !rawName.startsWith("User ", ignoreCase = true) -> rawName
            rawUsername.isNotBlank() -> rawUsername.removePrefix("@")
            rawName.isNotBlank() -> rawName
            else -> "Member"
        }

        return com.unera.social.model.UserReaction(
            userId = uid,
            userName = resolvedName,
            userAvatar = rawAvatar,
            type = type,
            username = rawUsername,
            isVerified = isVerified
        )
    }

    private fun parseComment(obj: JSONObject, viewerId: Long): Comment? {
        val id = obj.optLong("id", obj.optLong("comment_id", System.currentTimeMillis()))
        val text = obj.optString("text").takeIf { it != "null" }
            ?: obj.optString("content").takeIf { it != "null" }
            ?: ""
        val userId = obj.optLong("user_id", obj.optLong("userId", 0L))
        val userObj = obj.optJSONObject("user") ?: obj.optJSONObject("author")

        val authorName = userObj?.optString("name")?.takeIf { it.isNotBlank() && it != "null" }
            ?: userObj?.optString("display_name")?.takeIf { it.isNotBlank() && it != "null" }
            ?: userObj?.optString("fullName")?.takeIf { it.isNotBlank() && it != "null" }
            ?: obj.optString("author_name").takeIf { it.isNotBlank() && it != "null" }
            ?: obj.optString("user_name").takeIf { it.isNotBlank() && it != "null" }
            ?: obj.optString("user_fullname").takeIf { it.isNotBlank() && it != "null" }
            ?: userObj?.optString("username")?.takeIf { it.isNotBlank() && it != "null" }
            ?: obj.optString("username").takeIf { it.isNotBlank() && it != "null" }
            ?: ""

        val username = userObj?.optString("username")?.takeIf { it.isNotBlank() && it != "null" }
            ?: obj.optString("username").takeIf { it.isNotBlank() && it != "null" }
            ?: if (authorName.isNotBlank()) "@${authorName.lowercase().replace(" ", "")}" else ""

        val avatar = userObj?.optString("profile_image_url")?.takeIf { it.isNotBlank() && it != "null" }
            ?: userObj?.optString("avatar_url")?.takeIf { it.isNotBlank() && it != "null" }
            ?: userObj?.optString("avatar")?.takeIf { it.isNotBlank() && it != "null" }
            ?: obj.optString("author_avatar").takeIf { it.isNotBlank() && it != "null" }
            ?: obj.optString("profile_image_url").takeIf { it.isNotBlank() && it != "null" }
            ?: obj.optString("user_avatar").takeIf { it.isNotBlank() && it != "null" }
            ?: obj.optString("user_avatar_url").takeIf { it.isNotBlank() && it != "null" }
            ?: obj.optString("avatar").takeIf { it.isNotBlank() && it != "null" }
            ?: ""

        val isVerified = (userObj?.optBoolean("is_verified") ?: obj.optBoolean("is_verified", false)) ||
            (userObj?.optInt("is_verified", 0) == 1) ||
            (obj.optInt("is_verified", 0) == 1) ||
            (userObj?.optBoolean("isVerified") ?: obj.optBoolean("isVerified", false)) ||
            (userObj?.optBoolean("verified") ?: obj.optBoolean("verified", false)) ||
            (obj.optBoolean("verified", false))

        val loggedInUser = UserSessionManager.getCurrentUser()
        val isMe = (loggedInUser != null && (userId == loggedInUser.id || (userId <= 0 && viewerId == loggedInUser.id))) ||
                   (viewerId > 0 && userId == viewerId)

        val resolvedName = if (authorName.isNotBlank()) authorName else if (isMe && loggedInUser != null) loggedInUser.name else username.removePrefix("@").ifBlank { "User" }
        val resolvedUsername = if (username.isNotBlank()) username else if (isMe && loggedInUser != null) loggedInUser.username else if (resolvedName.isNotBlank()) "@${resolvedName.lowercase().replace(" ", "")}" else ""
        val resolvedAvatar = if (avatar.isNotBlank()) avatar else if (isMe && loggedInUser != null) loggedInUser.profileImageUrl else ""
        val resolvedVerified = isVerified || (isMe && loggedInUser?.isVerified == true)
        val resolvedUserId = if (userId > 0) userId else if (isMe && loggedInUser != null) loggedInUser.id else viewerId

        val author = User(
            id = resolvedUserId,
            name = resolvedName,
            username = resolvedUsername,
            profileImageUrl = resolvedAvatar,
            isVerified = resolvedVerified
        )

        val rawDate = obj.optString("created_at").takeIf { it.isNotBlank() && it != "null" }
        val timestamp = PostsApiService.formatRelativeTime(rawDate)
        val likesCount = obj.optInt("likes_count", obj.optInt("likes", 0))
        val isLiked = obj.optBoolean("is_liked", false) || obj.optInt("liked_by_me", 0) == 1
        val imageUrl = obj.optString("image_url").takeIf { it.isNotBlank() && it != "null" }
        val parentCommentId = if (obj.has("parent_comment_id") && !obj.isNull("parent_comment_id")) {
            obj.optLong("parent_comment_id")
        } else if (obj.has("parent_id") && !obj.isNull("parent_id")) {
            obj.optLong("parent_id")
        } else null
        val isHidden = obj.optBoolean("is_hidden", false) || obj.optInt("hidden", 0) == 1 || obj.optBoolean("hidden", false)
        val replyToAuthorName = obj.optString("reply_to_name").takeIf { it.isNotBlank() && it != "null" }
            ?: obj.optString("reply_to_author_name").takeIf { it.isNotBlank() && it != "null" }
            ?: obj.optString("reply_to_user").takeIf { it.isNotBlank() && it != "null" }
            ?: obj.optJSONObject("reply_to_user")?.optString("name")?.takeIf { it.isNotBlank() && it != "null" }

        val nestedReplies = mutableListOf<Comment>()
        val repliesArr = obj.optJSONArray("replies")
        if (repliesArr != null) {
            for (r in 0 until repliesArr.length()) {
                val rObj = repliesArr.optJSONObject(r) ?: continue
                parseComment(rObj, viewerId)?.let { nestedReplies.add(it) }
            }
        }

        return Comment(
            id = id,
            author = author,
            text = text,
            timestamp = timestamp,
            likesCount = likesCount,
            isLiked = isLiked,
            imageUrl = imageUrl,
            parentCommentId = parentCommentId,
            replies = nestedReplies,
            isHidden = isHidden,
            userId = resolvedUserId,
            replyToAuthorName = replyToAuthorName
        )
    }
}
