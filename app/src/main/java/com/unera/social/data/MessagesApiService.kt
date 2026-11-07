package com.unera.social.data

import android.content.Context
import android.net.Uri
import com.unera.social.model.ChatAttachment
import com.unera.social.model.ChatMessage
import com.unera.social.model.Conversation
import com.unera.social.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class CallEvent(
    val seq: Long,
    val type: String,
    val payload: JSONObject?
)

data class IncomingCall(
    val id: String,
    val callerId: Long,
    val calleeId: Long,
    val callType: String, // "voice" | "video"
    val status: String,
    val callerName: String,
    val callerUsername: String,
    val callerAvatar: String?
)

data class PresenceStatus(
    val online: Boolean,
    val lastSeenAt: String?
)

/**
 * MessagesApiService
 *
 * Fully implements the complete Feathered Chat System REST & Signaling API
 * matching web Chat.tsx & ChatsList.tsx exactly:
 *
 * 1. Auth headers helper (Authorization: Bearer <token>, x-user-id: <currentUserId>, Content-Type: application/json)
 * 2. Send message (POST /api/messages/send)
 * 3. Fetch conversations list (GET /api/messages/conversations)
 * 4. Fetch message history for conversation (GET /api/messages/conversations/{cid})
 * 5. Mark messages as read (POST /api/messages/mark-read)
 * 6. Edit message (PUT /api/messages/{id})
 * 7. Delete message (DELETE /api/messages/{id})
 * 8. Presence heartbeat (POST /api/presence/heartbeat)
 * 9. Recipient presence status (GET /api/presence/status?user_id=)
 * 10. File & voice note upload (POST /api/upload)
 * 11. Fetch user profile (GET /api/users/{id})
 * 12. Following users for Active Now tray (GET /api/messages/following)
 * 13. Create call (POST /api/calls/create)
 * 14. Send call signal (POST /api/calls/signal)
 * 15. Poll call signals (GET /api/calls/poll?call_id=&after=)
 * 16. Poll incoming call (GET /api/calls/incoming)
 */
object MessagesApiService {

    private const val BASE_URL = "https://featheredsocial.site"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) Feathered/1.0"

    /**
     * Unified apiFetch helper matching web apiFetch
     */
    private suspend fun apiFetch(
        endpoint: String,
        method: String = "GET",
        body: String? = null,
        userId: Long? = null,
        token: String? = null,
        timeoutMs: Int = 10000
    ): String = withContext(Dispatchers.IO) {
        val fullUrl = if (endpoint.startsWith("http")) endpoint else "$BASE_URL$endpoint"
        val url = URL(fullUrl)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
            if (!token.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $token")
            }
            if (userId != null && userId > 0L) {
                setRequestProperty("x-user-id", userId.toString())
            }
            if (body != null) {
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                doOutput = true
            }
        }

        if (body != null) {
            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(body)
                it.flush()
            }
        }

        val code = conn.responseCode
        val stream: InputStream? = if (code in 200..299) conn.inputStream else conn.errorStream ?: conn.inputStream
        val response = stream?.let {
            BufferedReader(InputStreamReader(it)).use { reader -> reader.readText() }
        } ?: ""
        conn.disconnect()

        if (code !in 200..299) {
            val errorMsg = try {
                val j = JSONObject(response)
                j.optString("error", j.optString("message", "API Error ($code)"))
            } catch (e: Exception) {
                "API Error ($code)"
            }
            throw RuntimeException(errorMsg)
        }
        response
    }

    // ==========================================
    // 2. CONVERSATIONS & MESSAGES
    // ==========================================

    /**
     * GET /api/messages/conversations
     * Fetches all active conversations for the authenticated user.
     */
    suspend fun fetchConversations(userId: Long, token: String?): List<Conversation> = withContext(Dispatchers.IO) {
        if (userId <= 0L) return@withContext emptyList()
        try {
            val responseText = apiFetch(
                endpoint = "/api/messages/conversations",
                method = "GET",
                userId = userId,
                token = token
            )
            val list = mutableListOf<Conversation>()
            if (responseText.trim().startsWith("[")) {
                val arr = JSONArray(responseText)
                for (i in 0 until arr.length()) {
                    val c = arr.optJSONObject(i) ?: continue
                    val id = c.optLong("id", 0L)
                    val otherUserId = c.optLong("other_user_id", 0L)
                    val otherName = c.optString("other_name", "User").let { if (it == "null" || it.isBlank()) "User" else it }
                    val otherAvatar = c.optString("other_profile_image_url", "").let { if (it == "null") "" else it }
                    val lastText = c.optString("last_text_preview", "").let { if (it == "null") "" else it }
                    val lastTime = c.optString("last_message_at", "Just now")
                    val unreadCount = c.optInt("unread_count", 0)
                    val isOnline = c.optInt("is_online", 0) == 1 || c.optBoolean("is_online", false)

                    val participant = User(
                        id = otherUserId,
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
                            lastTimestamp = MessagesCache.parseMessage(JSONObject().apply {
                                put("id", 0L)
                                put("created_at", lastTime)
                            }, userId)?.timestamp ?: "Just now",
                            unreadCount = unreadCount,
                            messages = emptyList(),
                            rawConversationId = id
                        )
                    )
                }
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * GET /api/messages/conversations/{cid}
     * Fetches message history for a specific conversation.
     */
    suspend fun fetchMessageHistory(conversationId: Long, userId: Long, token: String?): List<ChatMessage> = withContext(Dispatchers.IO) {
        if (conversationId <= 0L || userId <= 0L) return@withContext emptyList()
        try {
            val responseText = apiFetch(
                endpoint = "/api/messages/conversations/$conversationId",
                method = "GET",
                userId = userId,
                token = token
            )
            val list = mutableListOf<ChatMessage>()
            if (responseText.trim().startsWith("[")) {
                val arr = JSONArray(responseText)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    MessagesCache.parseMessage(obj, userId)?.let { list.add(it) }
                }
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * POST /api/messages/send
     * Sends a new chat message (text, reply, and/or attachments).
     */
    suspend fun sendMessage(
        senderId: Long,
        recipientId: Long,
        textContent: String? = null,
        parentMessageId: Long? = null,
        attachments: List<ChatAttachment> = emptyList(),
        token: String? = null
    ): ChatMessage? = withContext(Dispatchers.IO) {
        if (senderId <= 0L || recipientId <= 0L) return@withContext null
        try {
            val payload = JSONObject().apply {
                put("sender_id", senderId)
                put("recipient_id", recipientId)
                if (textContent != null) put("text_content", textContent) else put("text_content", JSONObject.NULL)
                if (parentMessageId != null && parentMessageId > 0) put("parent_message_id", parentMessageId)

                if (attachments.isNotEmpty()) {
                    val atts = JSONArray()
                    for (att in attachments) {
                        atts.put(JSONObject().apply {
                            put("url", att.url)
                            put("file_type", att.fileType)
                            put("mime_type", att.mimeType)
                            put("filename", att.filename)
                            if (att.sizeBytes != null) put("size_bytes", att.sizeBytes)
                            put("metadata", JSONObject())
                        })
                    }
                    put("attachments", atts)
                }
            }

            val responseText = apiFetch(
                endpoint = "/api/messages/send",
                method = "POST",
                body = payload.toString(),
                userId = senderId,
                token = token
            )

            val root = JSONObject(responseText)
            val msgObj = root.optJSONObject("message") ?: root
            val parsed = MessagesCache.parseMessage(msgObj, senderId)
            if (parsed != null && attachments.isNotEmpty() && parsed.attachments.isEmpty()) {
                parsed.copy(attachments = attachments)
            } else {
                parsed
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * POST /api/messages/mark-read
     * Marks messages in the conversation as read.
     */
    suspend fun markRead(conversationId: Long, userId: Long, token: String?): Boolean = withContext(Dispatchers.IO) {
        if (conversationId <= 0L || userId <= 0L) return@withContext false
        try {
            val payload = JSONObject().apply {
                put("conversation_id", conversationId)
                put("user_id", userId)
            }
            apiFetch(
                endpoint = "/api/messages/mark-read",
                method = "POST",
                body = payload.toString(),
                userId = userId,
                token = token
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * PUT /api/messages/{id}
     * Edits message text.
     */
    suspend fun editMessage(messageId: Long, textContent: String, userId: Long, token: String?): ChatMessage? = withContext(Dispatchers.IO) {
        if (messageId <= 0L || userId <= 0L) return@withContext null
        try {
            val payload = JSONObject().apply {
                put("text_content", textContent)
                put("user_id", userId)
            }
            val res = apiFetch(
                endpoint = "/api/messages/$messageId",
                method = "PUT",
                body = payload.toString(),
                userId = userId,
                token = token
            )
            val root = JSONObject(res)
            val msgObj = root.optJSONObject("message") ?: root
            MessagesCache.parseMessage(msgObj, userId)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * DELETE /api/messages/{id}
     * Deletes message for self or everyone.
     */
    suspend fun deleteMessage(messageId: Long, deleteForEveryone: Boolean, userId: Long, token: String?): Boolean = withContext(Dispatchers.IO) {
        if (messageId <= 0L || userId <= 0L) return@withContext false
        try {
            val payload = JSONObject().apply {
                put("delete_for_everyone", deleteForEveryone)
                put("user_id", userId)
            }
            apiFetch(
                endpoint = "/api/messages/$messageId",
                method = "DELETE",
                body = payload.toString(),
                userId = userId,
                token = token
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ==========================================
    // 3. PRESENCE
    // ==========================================

    /**
     * POST /api/presence/heartbeat
     * Keeps user marked "online" on the server.
     */
    suspend fun sendHeartbeat(userId: Long, token: String?): Boolean = withContext(Dispatchers.IO) {
        if (userId <= 0L) return@withContext false
        try {
            val payload = JSONObject().apply { put("user_id", userId) }
            apiFetch(
                endpoint = "/api/presence/heartbeat",
                method = "POST",
                body = payload.toString(),
                userId = userId,
                token = token,
                timeoutMs = 6000
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * GET /api/presence/status?user_id=
     * Fetches current presence status and last seen timestamp of a recipient.
     */
    suspend fun fetchRecipientPresence(recipientId: Long, userId: Long, token: String?): PresenceStatus = withContext(Dispatchers.IO) {
        if (recipientId <= 0L || userId <= 0L) return@withContext PresenceStatus(online = false, lastSeenAt = null)
        try {
            val res = apiFetch(
                endpoint = "/api/presence/status?user_id=$recipientId",
                method = "GET",
                userId = userId,
                token = token,
                timeoutMs = 6000
            )
            val obj = JSONObject(res)
            val online = obj.optBoolean("online", false)
            val lastSeen = obj.optString("last_seen_at").takeIf { it.isNotBlank() && it != "null" }
            PresenceStatus(online = online, lastSeenAt = lastSeen)
        } catch (e: Exception) {
            PresenceStatus(online = false, lastSeenAt = null)
        }
    }

    // ==========================================
    // 4. FOLLOWING (For Active Now & Chat Picker)
    // ==========================================

    /**
     * GET /api/messages/following
     * Fetches followed users for the Active Now tray & chat picker.
     */
    suspend fun fetchFollowing(userId: Long, token: String?): List<User> = withContext(Dispatchers.IO) {
        if (userId <= 0L) return@withContext emptyList()
        try {
            val responseText = apiFetch(
                endpoint = "/api/messages/following",
                method = "GET",
                userId = userId,
                token = token
            )
            val list = mutableListOf<User>()
            if (responseText.trim().startsWith("[")) {
                val arr = JSONArray(responseText)
                for (i in 0 until arr.length()) {
                    val u = arr.optJSONObject(i) ?: continue
                    val id = u.optLong("id", 0L)
                    if (id <= 0L) continue
                    val name = u.optString("name", "User").let { if (it == "null" || it.isBlank()) "User" else it }
                    val avatar = (u.optString("profile_image_url").takeIf { it.isNotBlank() && it != "null" }
                        ?: u.optString("profile_image_url_").takeIf { it.isNotBlank() && it != "null" }
                        ?: "")
                    val isOnline = u.optInt("is_online", 0) == 1 || u.optBoolean("is_online", false)
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
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // ==========================================
    // 5. ATTACHMENT UPLOAD
    // ==========================================

    /**
     * POST /api/upload
     * Uploads media or voice note via multipart/form-data.
     */
    suspend fun uploadAttachment(
        context: Context,
        uri: Uri,
        folder: String = "chat",
        token: String? = null
    ): ChatAttachment? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
            val filename = "upload_${System.currentTimeMillis()}_" + (uri.lastPathSegment?.substringAfterLast('/') ?: "file")
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null

            uploadBytes(bytes, filename, mimeType, folder, token)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Uploads in-memory byte array (voice notes, generated images, etc.)
     */
    suspend fun uploadBytes(
        bytes: ByteArray,
        filename: String,
        mimeType: String,
        folder: String = "chat",
        token: String? = null,
        durationSeconds: Int? = null
    ): ChatAttachment? = withContext(Dispatchers.IO) {
        val boundary = "===Boundary_${UUID.randomUUID().toString().replace("-", "")}==="
        val lineEnd = "\r\n"
        val twoHyphens = "--"

        try {
            val url = URL("$BASE_URL/api/upload")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30000
                readTimeout = 30000
                doOutput = true
                doInput = true
                useCaches = false
                setRequestProperty("Connection", "Keep-Alive")
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                if (!token.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
            }

            DataOutputStream(conn.outputStream).use { dos ->
                // Param: filename
                dos.writeBytes("$twoHyphens$boundary$lineEnd")
                dos.writeBytes("Content-Disposition: form-data; name=\"filename\"$lineEnd$lineEnd")
                dos.writeBytes(filename)
                dos.writeBytes(lineEnd)

                // Param: type
                dos.writeBytes("$twoHyphens$boundary$lineEnd")
                dos.writeBytes("Content-Disposition: form-data; name=\"type\"$lineEnd$lineEnd")
                dos.writeBytes(mimeType)
                dos.writeBytes(lineEnd)

                // Param: folder
                dos.writeBytes("$twoHyphens$boundary$lineEnd")
                dos.writeBytes("Content-Disposition: form-data; name=\"folder\"$lineEnd$lineEnd")
                dos.writeBytes(folder)
                dos.writeBytes(lineEnd)

                // Param: timestamp
                dos.writeBytes("$twoHyphens$boundary$lineEnd")
                dos.writeBytes("Content-Disposition: form-data; name=\"timestamp\"$lineEnd$lineEnd")
                dos.writeBytes(System.currentTimeMillis().toString())
                dos.writeBytes(lineEnd)

                // File part
                dos.writeBytes("$twoHyphens$boundary$lineEnd")
                dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"$filename\"$lineEnd")
                dos.writeBytes("Content-Type: $mimeType$lineEnd$lineEnd")
                dos.write(bytes)
                dos.writeBytes(lineEnd)

                // End of multipart
                dos.writeBytes("$twoHyphens$boundary$twoHyphens$lineEnd")
                dos.flush()
            }

            val code = conn.responseCode
            val stream: InputStream? = if (code in 200..299) conn.inputStream else conn.errorStream ?: conn.inputStream
            val responseText = stream?.let {
                BufferedReader(InputStreamReader(it)).use { r -> r.readText() }
            } ?: ""
            conn.disconnect()

            if (code in 200..299) {
                val root = JSONObject(responseText)
                val fileUrl = root.optString("url", "")
                if (fileUrl.isNotBlank()) {
                    val fileType = when {
                        mimeType.startsWith("image/") -> "image"
                        mimeType.startsWith("video/") -> "video"
                        mimeType.startsWith("audio/") -> "audio"
                        filename.endsWith(".gif", ignoreCase = true) -> "gif"
                        else -> root.optString("file_type", "document")
                    }
                    return@withContext ChatAttachment(
                        url = fileUrl,
                        fileType = fileType,
                        mimeType = root.optString("mime_type", mimeType),
                        filename = root.optString("filename", filename),
                        sizeBytes = root.optLong("size_bytes", bytes.size.toLong()),
                        durationSeconds = durationSeconds
                    )
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ==========================================
    // 6. CALLS SIGNALING (VOICE & VIDEO)
    // ==========================================

    /**
     * POST /api/calls/create
     * Starts an outgoing voice or video call.
     */
    suspend fun createCall(calleeId: Long, callType: String, userId: Long, token: String?): String? = withContext(Dispatchers.IO) {
        if (calleeId <= 0L || userId <= 0L) return@withContext null
        try {
            val payload = JSONObject().apply {
                put("callee_id", calleeId)
                put("call_type", callType)
            }
            val res = apiFetch(
                endpoint = "/api/calls/create",
                method = "POST",
                body = payload.toString(),
                userId = userId,
                token = token
            )
            val root = JSONObject(res)
            root.optString("call_id").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * POST /api/calls/signal
     * Sends a signaling event (offer, answer, ice, accept, decline, hangup).
     */
    suspend fun sendSignal(
        callId: String,
        toUserId: Long,
        type: String,
        payload: JSONObject? = null,
        userId: Long,
        token: String?
    ): Boolean = withContext(Dispatchers.IO) {
        if (callId.isBlank() || toUserId <= 0L || userId <= 0L) return@withContext false
        try {
            val body = JSONObject().apply {
                put("call_id", callId)
                put("to_user_id", toUserId)
                put("type", type)
                if (payload != null) put("payload", payload) else put("payload", JSONObject.NULL)
            }
            apiFetch(
                endpoint = "/api/calls/signal",
                method = "POST",
                body = body.toString(),
                userId = userId,
                token = token,
                timeoutMs = 6000
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * GET /api/calls/poll?call_id=&after=
     * Polls for incoming signals for an active call.
     */
    suspend fun pollCallEvents(callId: String, after: Long, userId: Long, token: String?): Pair<List<CallEvent>, Long> = withContext(Dispatchers.IO) {
        if (callId.isBlank() || userId <= 0L) return@withContext Pair(emptyList(), after)
        try {
            val res = apiFetch(
                endpoint = "/api/calls/poll?call_id=${java.net.URLEncoder.encode(callId, "UTF-8")}&after=$after",
                method = "GET",
                userId = userId,
                token = token,
                timeoutMs = 6000
            )
            val root = JSONObject(res)
            val eventsArr = root.optJSONArray("events")
            val newCursor = root.optLong("cursor", after)

            val list = mutableListOf<CallEvent>()
            if (eventsArr != null) {
                for (i in 0 until eventsArr.length()) {
                    val ev = eventsArr.optJSONObject(i) ?: continue
                    list.add(
                        CallEvent(
                            seq = ev.optLong("seq", 0L),
                            type = ev.optString("type", ""),
                            payload = ev.optJSONObject("payload")
                        )
                    )
                }
            }
            Pair(list, newCursor)
        } catch (e: Exception) {
            Pair(emptyList(), after)
        }
    }

    /**
     * GET /api/calls/incoming
     * Checks if there is an incoming ringing call for the current user.
     */
    suspend fun pollIncomingCall(userId: Long, token: String?): IncomingCall? = withContext(Dispatchers.IO) {
        if (userId <= 0L) return@withContext null
        try {
            val res = apiFetch(
                endpoint = "/api/calls/incoming",
                method = "GET",
                userId = userId,
                token = token,
                timeoutMs = 4000
            )
            val root = JSONObject(res)
            val callObj = root.optJSONObject("call") ?: return@withContext null
            val id = callObj.optString("id", "")
            val status = callObj.optString("status", "")
            if (id.isBlank() || status != "ringing") return@withContext null

            val callerId = callObj.optLong("caller_id", 0L)
            val calleeId = callObj.optLong("callee_id", 0L)
            val callType = callObj.optString("call_type", "voice")
            val callerName = callObj.optString("caller_name", "User")
            val callerUsername = callObj.optString("caller_username", "")
            val callerAvatar = (callObj.optString("caller_avatar").takeIf { it.isNotBlank() && it != "null" }
                ?: callObj.optString("caller_profile_image_url").takeIf { it.isNotBlank() && it != "null" })

            IncomingCall(
                id = id,
                callerId = callerId,
                calleeId = calleeId,
                callType = callType,
                status = status,
                callerName = callerName,
                callerUsername = callerUsername,
                callerAvatar = callerAvatar
            )
        } catch (e: Exception) {
            null
        }
    }
}
