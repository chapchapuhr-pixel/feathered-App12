package com.unera.social.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.unera.social.model.ChatAttachment
import com.unera.social.model.ChatMessage
import com.unera.social.model.Conversation
import com.unera.social.model.User
import com.unera.social.ui.components.ChatWindow
import com.unera.social.ui.theme.*

/**
 * MessagesScreen - Android counterpart of ChatsList.tsx + Chat.tsx
 *
 * Implements:
 * - Real API synchronization via MessagesApiService and MessagesCache
 * - Active Now horizontal tray featuring followed online contacts (GET /api/messages/following)
 * - Compose / New Message modal with follower search to start instant chats
 * - Rich conversation items with avatar, online badge, delivery preview, and unread counts
 * - Full ChatWindow integration with real sending, file uploads, voice notes, and calls
 */
@Composable
fun MessagesScreen(
    conversations: List<Conversation>,
    followingUsers: List<User> = emptyList(),
    activeConversationId: String?,
    currentUser: User,
    isConversationsLoading: Boolean = false,
    recipientOnline: Boolean = false,
    recipientLastSeen: String? = null,
    onOpenConversation: (String) -> Unit,
    onCloseConversation: () -> Unit,
    onSendMessage: (String, String, Long?, List<ChatAttachment>) -> Unit,
    onSendFile: ((String, Uri, String) -> Unit)? = null,
    onSendVoiceNote: ((String, ByteArray, Int) -> Unit)? = null,
    onEditMessage: ((String, String, String) -> Unit)? = null,
    onDeleteMessage: ((String, String, Boolean) -> Unit)? = null,
    onStartCall: ((User, String) -> Unit)? = null,
    onStartNewChat: ((User) -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    onViewProduct: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showNewChatDialog by remember { mutableStateOf(false) }

    // If an active conversation is open, render ChatWindow
    if (activeConversationId != null) {
        val activeConv = conversations.find { it.id == activeConversationId }
            ?: Conversation(
                id = activeConversationId,
                participant = User(id = 0L, name = "Chat", username = "@chat", profileImageUrl = ""),
                lastMessage = "",
                lastTimestamp = "Just now"
            )

        ChatWindow(
            conversation = activeConv,
            currentUser = currentUser,
            isRecipientOnline = recipientOnline || activeConv.participant.isOnline,
            recipientLastSeen = recipientLastSeen,
            onBack = onCloseConversation,
            onSendMessage = { text, replyToId ->
                onSendMessage(activeConv.id, text, replyToId, emptyList())
            },
            onSendAttachment = { att, text ->
                onSendMessage(activeConv.id, text, null, listOf(att))
            },
            onSendFile = { uri, text ->
                onSendFile?.invoke(activeConv.id, uri, text)
            },
            onSendVoiceNote = { bytes, durationSec ->
                onSendVoiceNote?.invoke(activeConv.id, bytes, durationSec)
            },
            onEditMessage = { msgId, text ->
                onEditMessage?.invoke(activeConv.id, msgId, text)
            },
            onDeleteMessage = { msgId, deleteForEveryone ->
                onDeleteMessage?.invoke(activeConv.id, msgId, deleteForEveryone)
            },
            onStartCall = { user, mode ->
                onStartCall?.invoke(user, mode)
            },
            onViewProduct = onViewProduct,
            modifier = modifier
        )
        return
    }

    // ==========================================
    // CONVERSATIONS LIST VIEW (ChatsList.tsx)
    // ==========================================
    var query by remember { mutableStateOf("") }
    val filteredConversations = remember(conversations, query) {
        if (query.isBlank()) {
            conversations
        } else {
            conversations.filter {
                it.participant.name.contains(query, ignoreCase = true) ||
                it.participant.username.contains(query, ignoreCase = true) ||
                it.lastMessage.contains(query, ignoreCase = true)
            }
        }
    }

    // Followed users available for Active Now or New Chat
    val activeNowUsers = remember(followingUsers) {
        followingUsers.filter { it.id != currentUser.id }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .testTag("messages_screen")
    ) {
        // 1. Top Header: Title + Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Chats",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (isConversationsLoading) {
                    Spacer(modifier = Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Carrot500,
                        strokeWidth = 2.dp
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onRefresh != null) {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Compose / New Chat button
                IconButton(
                    onClick = { showNewChatDialog = true },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Carrot500)
                        .testTag("new_chat_btn")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "New Message",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 2. Search Conversations Bar
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search messages and friends…", color = Color(0xFF64748B), fontSize = 13.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1E293B),
                unfocusedContainerColor = Color(0xFF1E293B),
                focusedBorderColor = Carrot500,
                unfocusedBorderColor = Color(0xFF334155),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag("messages_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Active Now Horizontal Tray (GET /api/messages/following)
        if (activeNowUsers.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "ACTIVE NOW",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(activeNowUsers, key = { "active_${it.id}" }) { user ->
                        ActiveNowItem(
                            user = user,
                            onClick = {
                                onStartNewChat?.invoke(user) ?: onOpenConversation("conv_${user.id}")
                            }
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                    thickness = 0.5.dp,
                    color = Color(0xFF1E293B)
                )
            }
        }

        // 4. Conversations List
        if (filteredConversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = null,
                            tint = Carrot500,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (query.isNotBlank()) "No messages matching \"$query\"" else "No messages yet",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Connect and start chatting with friends and creators.",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showNewChatDialog = true },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Carrot500)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Start a Chat", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("conversations_list")
            ) {
                items(filteredConversations, key = { it.id }) { conv ->
                    ConversationItem(
                        conversation = conv,
                        onClick = { onOpenConversation(conv.id) }
                    )
                }
            }
        }
    }

    // ==========================================
    // 5. NEW CHAT / USER PICKER MODAL
    // ==========================================
    if (showNewChatDialog) {
        NewChatPickerModal(
            followingUsers = followingUsers,
            onSelectUser = { user ->
                showNewChatDialog = false
                onStartNewChat?.invoke(user) ?: onOpenConversation("conv_${user.id}")
            },
            onDismiss = { showNewChatDialog = false }
        )
    }
}

/**
 * Active Now Avatar Circle in the top tray
 */
@Composable
fun ActiveNowItem(
    user: User,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .width(58.dp)
            .testTag("active_now_user_${user.id}")
    ) {
        Box(modifier = Modifier.size(52.dp)) {
            AsyncImage(
                model = user.profileImageUrl.ifBlank { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200" },
                contentDescription = user.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .border(2.dp, if (user.isOnline) Color(0xFF10B981) else Color(0xFF334155), CircleShape)
            )
            if (user.isOnline) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                        .border(2.dp, Color(0xFF0F172A), CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = user.name.split(" ").firstOrNull() ?: user.name,
            fontSize = 11.sp,
            color = Color(0xFFE2E8F0),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Single Conversation Item Row
 */
@Composable
fun ConversationItem(
    conversation: Conversation,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("conversation_item_${conversation.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            AsyncImage(
                model = conversation.participant.profileImageUrl.ifBlank {
                    "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200"
                },
                contentDescription = conversation.participant.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color(0xFF334155), CircleShape)
            )
            if (conversation.participant.isOnline) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                        .border(2.dp, Color(0xFF0F172A), CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.participant.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
                Text(
                    text = conversation.lastTimestamp,
                    fontSize = 11.sp,
                    color = if (conversation.unreadCount > 0) Carrot500 else Color(0xFF94A3B8)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.lastMessage.ifBlank { "Started a conversation" },
                    fontSize = 13.sp,
                    color = if (conversation.unreadCount > 0) Color.White else Color(0xFF94A3B8),
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (conversation.unreadCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Badge(
                        containerColor = Carrot500,
                        contentColor = Color.White
                    ) {
                        Text(
                            text = conversation.unreadCount.toString(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * New Chat Picker Modal
 */
@Composable
fun NewChatPickerModal(
    followingUsers: List<User>,
    onSelectUser: (User) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredUsers = remember(followingUsers, searchQuery) {
        if (searchQuery.isBlank()) followingUsers else {
            followingUsers.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.username.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1E293B),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "New Message",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search friends…", color = Color(0xFF64748B), fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A),
                        focusedBorderColor = Carrot500,
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (filteredUsers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No friends found",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredUsers, key = { "picker_${it.id}" }) { user ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSelectUser(user) }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(42.dp)) {
                                    AsyncImage(
                                        model = user.profileImageUrl.ifBlank { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200" },
                                        contentDescription = user.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                    )
                                    if (user.isOnline) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF10B981))
                                                .border(1.5.dp, Color(0xFF1E293B), CircleShape)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = user.username,
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }

                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Message",
                                    tint = Carrot500,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
