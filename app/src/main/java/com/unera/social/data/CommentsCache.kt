package com.unera.social.data

import com.unera.social.model.Comment
import com.unera.social.model.Post
import com.unera.social.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * CommentsCache
 *
 * Implements the exact 5-step web comment preview and caching lifecycle:
 *
 * For each post card in the feed:
 * 1. Try post.comments[0] -> if present, use it as previewComment
 * 2. Else try getCachedComments(itemType, postId)[0] -> if present, use it
 * 3. Else, if commentCount > 0:
 *    GET {endpoint} -> take arr[0] as previewComment -> setCachedComments(itemType, postId, arr)
 * 4. Resolve previewAuthor from previewComment.user_id via users[] (with fallbacks)
 * 5. Render inline preview bubble iff !hideCommentPreview && previewComment && previewAuthor
 *
 * Endpoints per itemType:
 * - post (image, text, share, sponsored): GET /api/posts/{id}/comments?viewerId={id}
 * - group_post: GET /api/group-post-comments?post_id={id}&viewerId={id}
 * - product (marketplace): GET /api/products/{id}/reviews?viewerId={id}
 * - song / music: GET /api/songs/{id}/comments?viewerId={id}
 * - event: GET /api/events/{id}/comments?viewerId={id}
 * - story: GET /api/stories/{id}/comments?viewerId={id}
 * - reel / video: GET /api/posts/{id}/comments?viewerId={id}
 */
object CommentsCache {

    // Persistent in-memory cache: "itemType:id" -> List<Comment>
    // Survives pull-to-refresh so comment previews do not disappear
    private val memoryCache = ConcurrentHashMap<String, List<Comment>>()

    // In-flight deduplication to prevent duplicate HTTP calls for the same post preview
    private val inFlight = ConcurrentHashMap<String, Boolean>()

    private val _cacheVersion = MutableStateFlow(0)
    val cacheVersion: StateFlow<Int> = _cacheVersion.asStateFlow()

    fun cacheKey(itemType: String, id: Long): String {
        val cleanType = when (itemType.lowercase().trim()) {
            "group_post" -> "group_post"
            "product", "marketplace" -> "product"
            "song", "music" -> "song"
            "event" -> "event"
            "story" -> "story"
            "reel", "video" -> "reel"
            else -> "post" // image, text, share, sponsored, general post
        }
        return "$cleanType:$id"
    }

    fun resolveItemType(post: Post): String {
        return when {
            post.groupId != null || post.postType == "group_post" -> "group_post"
            post.song != null || post.postType == "music" || post.postType == "song" -> "song"
            post.product != null || post.postType == "product" || post.postType == "marketplace" -> "product"
            post.event != null || post.postType == "event" -> "event"
            post.story != null || post.postType == "story" -> "story"
            post.postType == "reel" || post.postType == "video" -> "reel"
            else -> "post"
        }
    }

    fun getCachedComments(itemType: String, id: Long): List<Comment>? {
        return memoryCache[cacheKey(itemType, id)]
    }

    fun setCachedComments(itemType: String, id: Long, comments: List<Comment>) {
        if (comments.isEmpty()) return
        memoryCache[cacheKey(itemType, id)] = comments
        _cacheVersion.value += 1
    }

    fun updateCachedComment(commentId: Long, transform: (Comment) -> Comment) {
        var changed = false
        for ((key, list) in memoryCache) {
            var listModified = false
            val updatedList = list.map { parent ->
                if (parent.id == commentId) {
                    listModified = true
                    transform(parent)
                } else if (parent.replies.any { it.id == commentId }) {
                    listModified = true
                    parent.copy(replies = parent.replies.map { r -> if (r.id == commentId) transform(r) else r })
                } else {
                    parent
                }
            }
            if (listModified) {
                memoryCache[key] = updatedList
                changed = true
            }
        }
        if (changed) {
            _cacheVersion.value += 1
        }
    }

    /**
     * Synchronously resolves the preview comment from post.comments or memory cache.
     */
    fun getPreviewCommentSync(post: Post): Comment? {
        val fromPost = post.comments.firstOrNull()
        if (fromPost != null) {
            val type = resolveItemType(post)
            // Seed cache with post.comments if not already present
            if (memoryCache[cacheKey(type, post.id)].isNullOrEmpty()) {
                memoryCache[cacheKey(type, post.id)] = post.comments
            }
            return fromPost
        }
        return getCachedComments(resolveItemType(post), post.id)?.firstOrNull()
    }

    /**
     * Resolves the preview comment, fetching from network if not in cache and commentCount > 0.
     */
    suspend fun ensurePreviewComment(
        post: Post,
        viewerId: Long
    ): Comment? = withContext(Dispatchers.IO) {
        // 1. Try post.comments[0]
        val fromPost = post.comments.firstOrNull()
        val itemType = resolveItemType(post)
        val key = cacheKey(itemType, post.id)

        if (fromPost != null) {
            if (memoryCache[key].isNullOrEmpty()) {
                memoryCache[key] = post.comments
            }
            return@withContext fromPost
        }

        // 2. Try getCachedComments(itemType, postId)[0]
        val fromCache = memoryCache[key]?.firstOrNull()
        if (fromCache != null) {
            return@withContext fromCache
        }

        // 3. Else, if commentCount > 0: fetch from endpoint
        if (post.commentsCount <= 0) {
            return@withContext null
        }

        if (inFlight.putIfAbsent(key, true) != null) {
            return@withContext null
        }

        try {
            val liveComments = FeedInteractionApiService.fetchComments(post, viewerId)
            if (liveComments.isNotEmpty()) {
                setCachedComments(itemType, post.id, liveComments)
                return@withContext liveComments.firstOrNull()
            }
        } catch (_: Exception) {
        } finally {
            inFlight.remove(key)
        }
        null
    }

    /**
     * 4. Resolve previewAuthor from previewComment.user_id via users[] (with fallbacks)
     * Ensures real name (never generic "User"), verified badge tick, and profile image.
     */
    fun resolvePreviewAuthor(comment: Comment, users: List<User>): User {
        val targetUserId = if (comment.userId > 0L) comment.userId else comment.author.id
        val matched = if (targetUserId > 0L) users.find { it.id == targetUserId } else null

        if (matched != null) {
            val realName = matched.name.ifBlank { matched.username.removePrefix("@") }
            val fallbackName = comment.author.name.takeIf { it.isNotBlank() && !it.equals("User", ignoreCase = true) }
            val finalName = if (realName.isNotBlank() && !realName.equals("User", ignoreCase = true)) realName else (fallbackName ?: matched.name)

            val realAvatar = matched.profileImageUrl.takeIf { it.isNotBlank() } ?: comment.author.profileImageUrl
            val isVerified = matched.isVerified || comment.author.isVerified

            return matched.copy(
                name = finalName.ifBlank { "Feathered User" },
                username = if (matched.username.isNotBlank()) matched.username else comment.author.username,
                profileImageUrl = realAvatar,
                isVerified = isVerified
            )
        }

        // Fallback: check if comment.author already has verified or valid profile
        val authorName = comment.author.name.takeIf { it.isNotBlank() && !it.equals("User", ignoreCase = true) }
            ?: comment.author.username.removePrefix("@").takeIf { it.isNotBlank() }
            ?: "Feathered User"

        return comment.author.copy(
            name = authorName,
            isVerified = comment.author.isVerified
        )
    }
}
