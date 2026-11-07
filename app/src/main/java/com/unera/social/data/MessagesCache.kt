package com.unera.social.data

import android.content.Context
import com.unera.social.model.ChatAttachment
import com.unera.social.model.ChatMessage
import com.unera.social.model.Conversation
import com.unera.social.model.User
import org.json.JSONArray
import org.json.JSONObject

/**
 * MessagesCache
 *
 * Implements client-side persistent storage matching Feathered web:
 * - unera_chat_msgs_v3_<userId>_<recipientId>
 * - unera_chat_cid_v3_<userId>_<recipientId>
 * - unera_cached_convos_v2_<userId>
 * - unera_cached_following_v2_<userId>
 */
object MessagesCache {

    private const val PREFS_NAME = "unera_messages_cache"

    fun getCachedConversationId(context: Context, userId: Long, recipientId: Long): Long {
        if (userId <= 0L || recipientId <= 0L) return 0L
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong("unera_chat_cid_v3_${userId}_$recipientId", 0L)
    }

    fun setCachedConversationId(context: Context, userId: Long, recipientId: Long, conversationId: Long) {
        if (userId <= 0L || recipientId <= 0L || conversationId <= 0L) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong("unera_chat_cid_v3_${userId}_$recipientId", conversationId).apply()
    }

    fun getCachedMessages(context: Context, userId: Long, recipientId: Long): List<ChatMessage> {
        if (userId <= 0L || recipientId <= 0L) return emptyList()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("unera_chat_msgs_v3_${userId}_$recipientId", null) ?: return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<ChatMessage>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                parseMessage(obj, userId)?.let { list.add(it) }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setCachedMessages(context: Context, userId: Long, recipientId: Long, messages: List<ChatMessage>) {
        if (userId <= 0L || recipientId <= 0L) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val toSave = messages.takeLast(200)
            val arr = JSONArray()
            for (msg in toSave) {
                val obj = JSONObject().apply {
                    put("id", msg.rawId.takeIf { it > 0 } ?: msg.id.toLongOrNull() ?: 0L)
                    put("sender_id", msg.senderId)
                    put("recipient_id", msg.recipientId.takeIf { it > 0 } ?: recipientId)
                    put("text_content", msg.text)
                    put("created_at", msg.createdAt.ifBlank { msg.timestamp })
                    put("read_at", msg.readAt)
                    put("delivered_at", msg.deliveredAt)
                    put("is_edited", msg.isEdited)
                    put("parent_message_id", msg.parentMessageId)
                    put("reply_to_text", msg.replyToText)
                    val atts = JSONArray()
                    for (att in msg.attachments) {
                        atts.put(JSONObject().apply {
                            put("url", att.url)
                            put("file_type", att.fileType)
                            put("mime_type", att.mimeType)
                            put("filename", att.filename)
                            put("size_bytes", att.sizeBytes)
                            put("duration_seconds", att.durationSeconds)
                        })
                    }
                    put("attachments", atts)
                }
                arr.put(obj)
            }
            prefs.edit().putString("unera_chat_msgs_v3_${userId}_$recipientId", arr.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getCachedConversations(context: Context, userId: Long): List<Conversation> {
        if (userId <= 0L) return emptyList()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("unera_cached_convos_v2_$userId", null) ?: return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<Conversation>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optLong("id", 0L)
                val otherId = obj.optLong("other_user_id", 0L)
                val otherName = obj.optString("other_name", "User")
                val otherAvatar = obj.optString("other_profile_image_url", "").takeIf { it.isNotBlank() && it != "null" } ?: ""
                val lastText = obj.optString("last_text_preview", "")
                val lastTime = obj.optString("last_message_at", "Just now")
                val unreadCount = obj.optInt("unread_count", 0)
                val isOnline = obj.optBoolean("is_online", false)

                val participant = User(
                    id = otherId,
                    name = otherName,
                    username = "@${otherName.lowercase().replace(" ", "")}",
                    profileImageUrl = otherAvatar,
                    isOnline = isOnline
                )
                list.add(
                    Conversation(
                        id = id.toString(),
                        participant = participant,
                        lastMessage = lastText,
                        lastTimestamp = formatTimeAgo(lastTime),
                        unreadCount = unreadCount,
                        messages = emptyList(),
                        rawConversationId = id
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setCachedConversations(context: Context, userId: Long, conversations: List<Conversation>) {
        if (userId <= 0L) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val arr = JSONArray()
            for (c in conversations) {
                arr.put(JSONObject().apply {
                    put("id", c.rawConversationId.takeIf { it > 0 } ?: c.id.toLongOrNull() ?: 0L)
                    put("other_user_id", c.participant.id)
                    put("other_name", c.participant.name)
                    put("other_profile_image_url", c.participant.profileImageUrl)
                    put("last_text_preview", c.lastMessage)
                    put("last_message_at", c.lastTimestamp)
                    put("unread_count", c.unreadCount)
                    put("is_online", c.participant.isOnline)
                })
            }
            prefs.edit().putString("unera_cached_convos_v2_$userId", arr.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getCachedFollowing(context: Context, userId: Long): List<User> {
        if (userId <= 0L) return emptyList()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("unera_cached_following_v2_$userId", null) ?: return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<User>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optLong("id", 0L)
                if (id <= 0L) continue
                val name = obj.optString("name", "User")
                val avatar = obj.optString("profile_image_url", "").takeIf { it.isNotBlank() && it != "null" } ?: ""
                val isOnline = obj.optInt("is_online", 0) == 1 || obj.optBoolean("is_online", false)
                list.add(
                    User(
                        id = id,
                        name = name,
                        username = "@${name.lowercase().replace(" ", "")}",
                        profileImageUrl = avatar,
                        isOnline = isOnline,
                        isFollowing = true
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setCachedFollowing(context: Context, userId: Long, users: List<User>) {
        if (userId <= 0L) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val arr = JSONArray()
            for (u in users) {
                arr.put(JSONObject().apply {
                    put("id", u.id)
                    put("name", u.name)
                    put("profile_image_url", u.profileImageUrl)
                    put("is_online", if (u.isOnline) 1 else 0)
                })
            }
            prefs.edit().putString("unera_cached_following_v2_$userId", arr.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun parseMessage(obj: JSONObject, currentUserId: Long): ChatMessage? {
        val id = obj.optLong("id", 0L)
        val senderId = obj.optLong("sender_id", 0L)
        val recipientId = obj.optLong("recipient_id", 0L)
        val text = obj.optString("text_content", "").let { if (it == "null") "" else it }
        val createdAt = obj.optString("created_at", "")
        val readAt = obj.optString("read_at").takeIf { it.isNotBlank() && it != "null" }
        val deliveredAt = obj.optString("delivered_at").takeIf { it.isNotBlank() && it != "null" }
        val editedAt = obj.optString("edited_at").takeIf { it.isNotBlank() && it != "null" }
        val parentMessageId = obj.optLong("parent_message_id", 0L).takeIf { it > 0 }?.toString()

        val attachments = mutableListOf<ChatAttachment>()
        val attsArr = obj.optJSONArray("attachments")
        if (attsArr != null) {
            for (i in 0 until attsArr.length()) {
                val a = attsArr.optJSONObject(i) ?: continue
                val url = a.optString("url", "")
                if (url.isNotBlank()) {
                    attachments.add(
                        ChatAttachment(
                            url = url,
                            fileType = a.optString("file_type", "image"),
                            mimeType = a.optString("mime_type", ""),
                            filename = a.optString("filename", "Attachment"),
                            sizeBytes = a.optLong("size_bytes", 0L).takeIf { it > 0 },
                            durationSeconds = a.optInt("duration_seconds", 0).takeIf { it > 0 }
                        )
                    )
                }
            }
        }

        val isCall = text.contains("Missed voice call") || text.contains("Missed video call") ||
                text.contains("Voice call") || text.contains("Video call")
        val isMissed = text.contains("Missed", ignoreCase = true)
        val callType = if (text.contains("video", ignoreCase = true)) "video" else "voice"

        val isMine = senderId == currentUserId

        return ChatMessage(
            id = id.toString(),
            rawId = id,
            senderId = senderId,
            recipientId = recipientId,
            text = text,
            timestamp = formatTimeAgo(createdAt),
            isMine = isMine,
            isSeen = readAt != null,
            isDelivered = deliveredAt != null || isMine,
            isEdited = editedAt != null || obj.optBoolean("is_edited", false),
            parentMessageId = parentMessageId,
            replyToText = obj.optString("reply_to_text").takeIf { it.isNotBlank() && it != "null" },
            attachments = attachments,
            isCallMessage = isCall,
            callType = callType,
            isMissedCall = isMissed,
            createdAt = createdAt,
            readAt = readAt,
            deliveredAt = deliveredAt
        )
    }

    private fun formatTimeAgo(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return "Just now"
        return try {
            val normalized = if (dateStr.endsWith("Z")) dateStr.replace("Z", "+0000") else dateStr
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", java.util.Locale.US)
            val date = sdf.parse(normalized) ?: return dateStr
            val diff = System.currentTimeMillis() - date.time
            when {
                diff < 60_000 -> "Just now"
                diff < 3600_000 -> "${diff / 60_000}m"
                diff < 86400_000 -> "${diff / 3600_000}h"
                diff < 604800_000 -> "${diff / 86400_000}d"
                else -> {
                    val outSdf = java.text.SimpleDateFormat("MMM d", java.util.Locale.US)
                    outSdf.format(date)
                }
            }
        } catch (e: Exception) {
            try {
                val sdf2 = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", java.util.Locale.US)
                val date = sdf2.parse(dateStr.replace("Z", "+0000")) ?: return dateStr
                val diff = System.currentTimeMillis() - date.time
                if (diff < 60_000) "Just now" else "${diff / 60_000}m"
            } catch (e2: Exception) {
                dateStr
            }
        }
    }
}
