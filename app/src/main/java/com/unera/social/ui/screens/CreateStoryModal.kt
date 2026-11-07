package com.unera.social.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.unera.social.data.StoriesApiService
import com.unera.social.model.Song
import com.unera.social.model.User
import java.io.File
import java.io.FileOutputStream

data class StoryGradient(
    val css: String,
    val colors: List<Color>
)

val STORY_COLORS = listOf(
    StoryGradient("linear-gradient(45deg, #1877F2, #0055FF)", listOf(Color(0xFF1877F2), Color(0xFF0055FF))),
    StoryGradient("linear-gradient(45deg, #F3425F, #E41E3F)", listOf(Color(0xFFF3425F), Color(0xFFE41E3F))),
    StoryGradient("linear-gradient(45deg, #45BD62, #31A24C)", listOf(Color(0xFF45BD62), Color(0xFF31A24C))),
    StoryGradient("linear-gradient(45deg, #F7B928, #E3A300)", listOf(Color(0xFFF7B928), Color(0xFFE3A300))),
    StoryGradient("linear-gradient(45deg, #A033FF, #7B1FA2)", listOf(Color(0xFFA033FF), Color(0xFF7B1FA2))),
    StoryGradient("linear-gradient(45deg, #FF7E5F, #FEB47B)", listOf(Color(0xFFFF7E5F), Color(0xFFFEB47B))),
    StoryGradient("linear-gradient(45deg, #00C6FF, #0072FF)", listOf(Color(0xFF00C6FF), Color(0xFF0072FF))),
    StoryGradient("linear-gradient(45deg, #2193b0, #6dd5ed)", listOf(Color(0xFF2193B0), Color(0xFF6DD5ED))),
    StoryGradient("linear-gradient(45deg, #ee9ca7, #ffdde1)", listOf(Color(0xFFEE9CA7), Color(0xFFFFDDE1))),
    StoryGradient("linear-gradient(45deg, #42275a, #734b6d)", listOf(Color(0xFF42275A), Color(0xFF734B6D))),
    StoryGradient("linear-gradient(45deg, #BDC3C7, #2C3E50)", listOf(Color(0xFFBDC3C7), Color(0xFF2C3E50))),
    StoryGradient("linear-gradient(45deg, #000000, #434343)", listOf(Color(0xFF000000), Color(0xFF434343)))
)

data class MediaPick(
    val uri: Uri
)

data class SelectedMusicInfo(
    val title: String,
    val artist: String,
    val url: String,
    val coverUrl: String? = null
)

/**
 * CreateStoryDialog
 * Native Android implementation matching web Story.tsx (CreateStoryModal):
 * - Discard / Create Story / Share top bar
 * - Text mode with 12 STORY_COLORS gradient backgrounds & centered text input
 * - Media mode with empty placeholder & multi-image picker (videos disabled)
 * - Media preview with uncropped true-to-aspect-ratio scaling and ambient backdrop
 * - Floating music pill with cancel button
 * - Bottom controls: Text mode color carousel, mode pill switcher (Text/Media), Camera icon, Plus media icon, Music icon
 * - Live Music Picker modal fetching /api/songs live, with audio file upload, audio preview, and Add button (no fake songs)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateStoryDialog(
    currentUser: User,
    onDismiss: () -> Unit,
    onSubmitStory: (
        mediaUri: Uri?,
        isVideo: Boolean,
        type: String,
        textContent: String,
        backgroundStyle: String,
        musicTitle: String?,
        musicUrl: String?
    ) -> Unit,
    isSubmitting: Boolean = false
) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf("media") } // "media" or "text"
    var textContent by remember { mutableStateOf("") }
    var selectedColorIndex by remember { mutableIntStateOf(0) }
    var picks by remember { mutableStateOf<List<MediaPick>>(emptyList()) }
    var activePickIndex by remember { mutableIntStateOf(0) }
    var selectedMusic by remember { mutableStateOf<SelectedMusicInfo?>(null) }
    var showMusicPicker by remember { mutableStateOf(false) }
    var localSubmitting by remember { mutableStateOf(false) }

    val activeGradient = STORY_COLORS[selectedColorIndex]

    // Launcher for taking a quick photo with camera
    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            try {
                val file = File(context.cacheDir, "story_cam_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
                val uri = Uri.fromFile(file)
                picks = picks + MediaPick(uri = uri)
                activePickIndex = picks.size - 1
                mode = "media"
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Launcher for selecting multiple photos from system gallery (ImageOnly)
    val pickMultipleMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(30)
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            val newPicks = uris.mapNotNull { uri ->
                val mime = try { context.contentResolver.getType(uri) ?: "" } catch (_: Exception) { "" }
                val isVid = mime.startsWith("video") || uri.toString().contains(".mp4", ignoreCase = true)
                if (isVid) {
                    Toast.makeText(context, "Story videos are disabled. Only photos are supported.", Toast.LENGTH_SHORT).show()
                    null
                } else {
                    MediaPick(uri = uri)
                }
            }
            if (newPicks.isNotEmpty()) {
                picks = (picks + newPicks).take(30)
                mode = "media"
            }
        }
    }

    val busy = isSubmitting || localSubmitting
    val canShare = (mode == "text" && textContent.trim().isNotBlank()) || (mode == "media" && picks.isNotEmpty())

    val handleShare = {
        if (canShare && !busy) {
            localSubmitting = true
            val backgroundCss = activeGradient.css
            val musicTitleStr = selectedMusic?.let { "${it.title} - ${it.artist}" }
            val musicUrlStr = selectedMusic?.url

            if (mode == "text") {
                onSubmitStory(
                    null,
                    false,
                    "text",
                    textContent.trim(),
                    backgroundCss,
                    musicTitleStr,
                    musicUrlStr
                )
            } else {
                val active = picks.getOrNull(activePickIndex) ?: picks.first()
                onSubmitStory(
                    active.uri,
                    false,
                    "image",
                    textContent.trim(),
                    backgroundCss,
                    musicTitleStr,
                    musicUrlStr
                )
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !busy,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF050B18)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Main Preview Canvas (center)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 56.dp, bottom = if (mode == "text") 150.dp else 100.dp)
                ) {
                    if (mode == "text") {
                        // Text Mode: Fullscreen gradient canvas with centered text input
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.linearGradient(activeGradient.colors))
                                .padding(horizontal = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicTextField(
                                value = textContent,
                                onValueChange = { textContent = it },
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                ),
                                cursorBrush = SolidColor(Color.White),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (textContent.isEmpty()) {
                                            Text(
                                                text = "Start typing...",
                                                color = Color.White.copy(alpha = 0.45f),
                                                fontSize = 32.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                            )
                        }
                    } else {
                        // Media Mode: Photos only (Videos disabled)
                        if (picks.isEmpty()) {
                            // Empty placeholder matching web
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF050B18))
                                    .clickable {
                                        pickMultipleMediaLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF0F172A))
                                            .border(1.dp, Color(0xFF1E293B), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AddPhotoAlternate,
                                            contentDescription = "Select Photos",
                                            tint = Color.White,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text(
                                        text = "Select Photos",
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 20.sp,
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = "Choose high quality photos for your story",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            // Media Preview with active pick - true aspect ratio with ambient backdrop
                            val activePick = picks.getOrNull(activePickIndex) ?: picks.first()
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF050B18)),
                                contentAlignment = Alignment.Center
                            ) {
                                // Subtle ambient background to eliminate harsh gaps
                                AsyncImage(
                                    model = activePick.uri,
                                    contentDescription = "Story preview ambient",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .alpha(0.25f)
                                )

                                // Crisp uncropped foreground image preserving full quality and aspect ratio
                                AsyncImage(
                                    model = activePick.uri,
                                    contentDescription = "Story preview",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Delete active pick button (top-left)
                                IconButton(
                                    onClick = {
                                        val newPicks = picks.toMutableList()
                                        newPicks.removeAt(activePickIndex)
                                        picks = newPicks
                                        if (activePickIndex >= newPicks.size) {
                                            activePickIndex = maxOf(0, newPicks.size - 1)
                                        }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(16.dp)
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.55f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete media",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Dot indicators if multiple items
                                if (picks.size > 1) {
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 74.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Color.Black.copy(alpha = 0.45f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        picks.indices.forEach { idx ->
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (idx == activePickIndex) Color.White else Color.White.copy(alpha = 0.4f)
                                                    )
                                            )
                                        }
                                    }

                                    // Thumbnail strip at bottom
                                    LazyRow(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        items(picks.indices.toList()) { idx ->
                                            val p = picks[idx]
                                            val isSelected = idx == activePickIndex
                                            Box(
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .border(
                                                        width = if (isSelected) 2.5.dp else 1.dp,
                                                        color = if (isSelected) Color(0xFF1877F2) else Color.White.copy(alpha = 0.2f),
                                                        shape = RoundedCornerShape(10.dp)
                                                    )
                                                    .clickable { activePickIndex = idx }
                                            ) {
                                                AsyncImage(
                                                    model = p.uri,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }

                                        // Plus button to add more media
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color.White.copy(alpha = 0.12f))
                                                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                                    .clickable {
                                                        pickMultipleMediaLauncher.launch(
                                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                        )
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "Add media",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Floating Music Badge (top of canvas)
                    selectedMusic?.let { music ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1877F2)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = music.title,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = music.artist,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { selectedMusic = null },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove music",
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Top Header Bar
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Color(0xFF0B1120))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = { if (!busy) onDismiss() },
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E293B),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(
                            text = "Discard",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Text(
                        text = "Create Story",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )

                    Button(
                        onClick = { handleShare() },
                        enabled = canShare && !busy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1877F2),
                            disabledContainerColor = Color(0xFF334155),
                            contentColor = Color.White,
                            disabledContentColor = Color.White.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Share",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // Bottom Dock
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color(0xFF0B1120))
                        .border(1.dp, Color(0xFF1E293B))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Color gradient picker (Text Mode only)
                    if (mode == "text") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            items(STORY_COLORS.indices.toList()) { idx ->
                                val grad = STORY_COLORS[idx]
                                val isSelected = selectedColorIndex == idx
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Brush.linearGradient(grad.colors))
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) Color.White else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColorIndex = idx }
                                )
                            }
                        }
                    }

                    // Mode Switcher & Action Icons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mode Switcher: Text / Media
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF070D1D))
                                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Button(
                                onClick = { mode = "text" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (mode == "text") Color(0xFF1877F2) else Color.Transparent
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TextFields,
                                    contentDescription = null,
                                    tint = if (mode == "text") Color.White else Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Text",
                                    color = if (mode == "text") Color.White else Color.White.copy(alpha = 0.6f),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            Button(
                                onClick = { mode = "media" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (mode == "media") Color(0xFF1877F2) else Color.Transparent
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = null,
                                    tint = if (mode == "media") Color.White else Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Media",
                                    color = if (mode == "media") Color.White else Color.White.copy(alpha = 0.6f),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        // Right Action Icons: Camera, Media Plus, Music
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Camera Button
                            IconButton(
                                onClick = { takePhotoLauncher.launch(null) },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.1f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = "Take Photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Add Media Button
                            IconButton(
                                onClick = {
                                    pickMultipleMediaLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.1f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Media",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Music Button (Opens Live Music Picker)
                            IconButton(
                                onClick = { showMusicPicker = true },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selectedMusic != null) Color(0xFF45BD62) else Color.White.copy(alpha = 0.1f)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = "Music",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Live Music Picker Modal
            if (showMusicPicker) {
                LiveMusicPickerModal(
                    onDismiss = { showMusicPicker = false },
                    onSelectSong = { song ->
                        selectedMusic = SelectedMusicInfo(
                            title = song.title,
                            artist = song.artist,
                            url = song.audioUrl,
                            coverUrl = song.cover
                        )
                        showMusicPicker = false
                    },
                    onSelectAudioFile = { uri, name ->
                        selectedMusic = SelectedMusicInfo(
                            title = name,
                            artist = "Local Upload",
                            url = uri.toString()
                        )
                        showMusicPicker = false
                    }
                )
            }
        }
    }
}

/**
 * Live Music Picker Modal matching web renderMusicPicker in Story.tsx:
 * - Upload Music button with system audio picker
 * - "F-MUSIC TRENDS" uppercase section
 * - Live fetch from /api/songs (no hardcoded fake tracks)
 * - Play/Pause audio preview
 * - Green "Add" button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveMusicPickerModal(
    onDismiss: () -> Unit,
    onSelectSong: (Song) -> Unit,
    onSelectAudioFile: (Uri, String) -> Unit
) {
    val context = LocalContext.current
    var liveSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var previewSongId by remember { mutableStateOf<String?>(null) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    // Audio file picker launcher
    val pickAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val fileName = try {
                var name = "Uploaded Audio"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0 && cursor.moveToFirst()) {
                        name = cursor.getString(nameIdx)
                    }
                }
                name
            } catch (_: Exception) { "Uploaded Audio" }
            onSelectAudioFile(uri, fileName.substringBeforeLast("."))
        }
    }

    // Fetch live songs from /api/songs
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val songs = StoriesApiService.fetchSongs()
            liveSongs = songs
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF050B18)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0B1120))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Close",
                            tint = Color(0xFFB0B3B8),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Close",
                            color = Color(0xFFB0B3B8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Text(
                        text = "Add Music",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    Spacer(modifier = Modifier.width(48.dp))
                }

                HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Upload Music button (uses native picker)
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFF1E293B)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pickAudioLauncher.launch("audio/*") }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1877F2)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = "Upload Music",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Upload Music",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "Choose a file from your device",
                                        color = Color(0xFFB0B3B8),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    // F-Music Trends Header
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "F-MUSIC TRENDS",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    if (isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF1877F2),
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Loading music...",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    } else if (liveSongs.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No tracks currently listed. Upload your own music above!",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        items(liveSongs, key = { it.id }) { song ->
                            val isThisPlaying = previewSongId == song.id
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    AsyncImage(
                                        model = song.cover.ifBlank { "https://media.featheredsocial.site/task_01kftb3024ed7bm84gy6j485fh_1769336848_img_0.webp" },
                                        contentDescription = song.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = song.title,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = song.artist,
                                            color = Color(0xFFB0B3B8),
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Play / Pause Preview Button
                                        IconButton(
                                            onClick = {
                                                if (isThisPlaying) {
                                                    exoPlayer.pause()
                                                    previewSongId = null
                                                } else {
                                                    exoPlayer.stop()
                                                    exoPlayer.clearMediaItems()
                                                    if (song.audioUrl.isNotBlank()) {
                                                        exoPlayer.setMediaItem(MediaItem.fromUri(song.audioUrl))
                                                        exoPlayer.prepare()
                                                        exoPlayer.play()
                                                        previewSongId = song.id
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF1877F2))
                                        ) {
                                            Icon(
                                                imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (isThisPlaying) "Pause" else "Play",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        // Green "Add" Button
                                        Button(
                                            onClick = {
                                                exoPlayer.stop()
                                                previewSongId = null
                                                onSelectSong(song)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF45BD62)),
                                            shape = RoundedCornerShape(20.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Text(
                                                text = "Add",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
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
}

/**
 * Backward-compatible overload for CreateStoryDialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateStoryDialog(
    currentUser: User,
    onDismiss: () -> Unit,
    onCreateStory: (mediaUrl: String, type: String, textContent: String, backgroundStyle: String, musicTitle: String?) -> Unit
) {
    CreateStoryDialog(
        currentUser = currentUser,
        onDismiss = onDismiss,
        onSubmitStory = { uri, isVideo, type, text, bg, music, _ ->
            onCreateStory(uri?.toString() ?: "", type, text, bg, music)
        }
    )
}
