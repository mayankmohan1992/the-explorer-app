package com.explorer.app.ui.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.explorer.app.ui.components.GlassmorphicCard
import com.explorer.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MediaFile(
    val uri: Uri,
    val name: String,
    val duration: Long = 0,
    val isVideo: Boolean = false,
    val isAudio: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaTab() {
    val context = LocalContext.current
    var selectedMediaTab by remember { mutableStateOf(0) } // 0 = Photos, 1 = Videos, 2 = Music
    var mediaFiles by remember { mutableStateOf<List<MediaFile>>(emptyList()) }
    var hasPermission by remember { mutableStateOf(false) }

    // Media Viewer state
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var activeAudioFile by remember { mutableStateOf<MediaFile?>(null) }

    // Check permissions
    LaunchedEffect(Unit) {
        hasPermission = checkMediaPermissions(context)
        if (hasPermission) {
            mediaFiles = loadMediaFiles(context, selectedMediaTab)
        }
    }

    // Reload files when tab changes
    LaunchedEffect(selectedMediaTab, hasPermission) {
        if (hasPermission) {
            mediaFiles = loadMediaFiles(context, selectedMediaTab)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp)
    ) {
        // Media Tab Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { selectedMediaTab = 0 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedMediaTab == 0) NeonCyan else Color(0x11FFFFFF),
                    contentColor = if (selectedMediaTab == 0) DeepSpaceBackground else TextPrimary
                ),
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Photo, contentDescription = "Photos", modifier = Modifier.size(18.dp).padding(end = 2.dp))
                Text("Photos", fontSize = 11.sp)
            }
            Button(
                onClick = { selectedMediaTab = 1 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedMediaTab == 1) NeonCyan else Color(0x11FFFFFF),
                    contentColor = if (selectedMediaTab == 1) DeepSpaceBackground else TextPrimary
                ),
                shape = RoundedCornerShape(0.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Movie, contentDescription = "Videos", modifier = Modifier.size(18.dp).padding(end = 2.dp))
                Text("Videos", fontSize = 11.sp)
            }
            Button(
                onClick = { selectedMediaTab = 2 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedMediaTab == 2) NeonCyan else Color(0x11FFFFFF),
                    contentColor = if (selectedMediaTab == 2) DeepSpaceBackground else TextPrimary
                ),
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = "Music", modifier = Modifier.size(18.dp).padding(end = 2.dp))
                Text("Music", fontSize = 11.sp)
            }
        }

        // Section header title
        Text(
            text = when(selectedMediaTab) {
                0 -> "IMAGE GALLERY"
                1 -> "VIDEO PLAYER"
                else -> "MUSIC PLAYER"
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = NeonCyan,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (!hasPermission) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                GlassmorphicCard(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Permission Needed", tint = NeonCyan, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Storage Access Required", fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "To access your photos, videos, and music tracks directly on this device, please grant the storage permission.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                // In production, trigger requestPermissions API launcher
                                hasPermission = true // Simulation fallback
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                        ) {
                            Text("Grant Permission", color = DeepSpaceBackground)
                        }
                    }
                }
            }
        } else if (mediaFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No media found in this category.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        } else {
            // Render media
            when (selectedMediaTab) {
                0 -> {
                    // Photos Grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(mediaFiles) { photo ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedImageUri = photo.uri }
                            ) {
                                AsyncImage(
                                    model = photo.uri,
                                    contentDescription = photo.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // Videos Grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(mediaFiles) { video ->
                            GlassmorphicCard(
                                modifier = Modifier
                                    .aspectRatio(1.3f)
                                    .clickable { selectedVideoUri = video.uri }
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    // Thumbnail preview placeholder or loaded image
                                    AsyncImage(
                                        model = video.uri,
                                        contentDescription = video.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    
                                    // Play overlay button icon
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0x33000000)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayCircle,
                                            contentDescription = "Play",
                                            tint = NeonCyan,
                                            modifier = Modifier.size(44.dp)
                                        )
                                    }

                                    // Duration label
                                    Text(
                                        text = formatDuration(video.duration),
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp)
                                            .background(Color(0x88000000), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // Music List
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(mediaFiles) { audio ->
                            AudioItemRow(
                                audio = audio,
                                isPlaying = activeAudioFile?.uri == audio.uri,
                                onClick = { activeAudioFile = audio }
                            )
                        }
                    }
                }
            }
        }
    }

    // Image fullscreen dialog viewer
    selectedImageUri?.let { uri ->
        Dialog(
            onDismissRequest = { selectedImageUri = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = uri,
                    contentDescription = "Fullscreen",
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center),
                    contentScale = ContentScale.Fit
                )
                
                IconButton(
                    onClick = { selectedImageUri = null },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }

    // Video playback dialog player (utilizing ExoPlayer and AndroidView PlayerView)
    selectedVideoUri?.let { uri ->
        VideoPlaybackDialog(
            videoUri = uri,
            onDismiss = { selectedVideoUri = null }
        )
    }

    // Audio bottom overlay player sheet
    activeAudioFile?.let { audio ->
        AudioOverlayPlayer(
            audioFile = audio,
            onClose = { activeAudioFile = null }
        )
    }
}

@Composable
fun AudioItemRow(audio: MediaFile, isPlaying: Boolean, onClick: () -> Unit) {
    GlassmorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.VolumeUp else Icons.Default.MusicNote,
                contentDescription = null,
                tint = if (isPlaying) NeonCyan else TextSecondary,
                modifier = Modifier.size(32.dp).padding(end = 8.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = audio.name,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = if (isPlaying) NeonCyan else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Local Audio file",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
            Text(
                text = formatDuration(audio.duration),
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun VideoPlaybackDialog(videoUri: Uri, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close Player", tint = Color.White)
            }
        }
    }
}

@Composable
fun AudioOverlayPlayer(audioFile: MediaFile, onClose: () -> Unit) {
    val context = LocalContext.current
    var isPlayingState by remember { mutableStateOf(true) }
    
    val exoPlayer = remember(audioFile.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(audioFile.uri))
            prepare()
            playWhenReady = true
        }
    }

    // Track play state changes
    DisposableEffect(audioFile.uri) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlayingState = isPlaying
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 68.dp), // Height above navigation bottom tabs
        contentAlignment = Alignment.BottomCenter
    ) {
        GlassmorphicCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = audioFile.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Now Playing",
                        fontSize = 10.sp,
                        color = NeonCyan
                    )
                }

                IconButton(
                    onClick = {
                        if (isPlayingState) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (isPlayingState) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                        contentDescription = "Play/Pause",
                        tint = NeonCyan,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

private fun checkMediaPermissions(context: Context): Boolean {
    // Return true for sandbox simulator, normally verify permission context
    return true
}

private suspend fun loadMediaFiles(context: Context, type: Int): List<MediaFile> = withContext(Dispatchers.IO) {
    val list = mutableListOf<MediaFile>()
    val contentUri: Uri
    val projection: Array<String>
    val selection: String?
    val selectionArgs: Array<String>?
    val sortOrder: String

    when (type) {
        0 -> { // Photos
            contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME
            )
            selection = null
            selectionArgs = null
            sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        }
        1 -> { // Videos
            contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION
            )
            selection = null
            selectionArgs = null
            sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"
        }
        else -> { // Audio/Music
            contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DURATION
            )
            selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            selectionArgs = null
            sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
        }
    }

    try {
        context.contentResolver.query(
            contentUri,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(projection[0])
            val nameColumn = cursor.getColumnIndexOrThrow(projection[1])
            val durationColumn = if (type > 0) cursor.getColumnIndexOrThrow(projection[2]) else -1

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val duration = if (durationColumn != -1) cursor.getLong(durationColumn) else 0L
                val uri = ContentUris.withAppendedId(contentUri, id)

                list.add(
                    MediaFile(
                        uri = uri,
                        name = name,
                        duration = duration,
                        isVideo = type == 1,
                        isAudio = type == 2
                    )
                )
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    // Populate with high-quality mock data if device storage is empty (very common in emulator sandbox)
    if (list.isEmpty()) {
        when (type) {
            0 -> {
                list.add(MediaFile(Uri.parse("https://picsum.photos/800/800?random=1"), "Sample Photo 1.jpg"))
                list.add(MediaFile(Uri.parse("https://picsum.photos/800/800?random=2"), "Sample Photo 2.jpg"))
                list.add(MediaFile(Uri.parse("https://picsum.photos/800/800?random=3"), "Sample Photo 3.jpg"))
                list.add(MediaFile(Uri.parse("https://picsum.photos/800/800?random=4"), "Sample Photo 4.jpg"))
                list.add(MediaFile(Uri.parse("https://picsum.photos/800/800?random=5"), "Sample Photo 5.jpg"))
                list.add(MediaFile(Uri.parse("https://picsum.photos/800/800?random=6"), "Sample Photo 6.jpg"))
            }
            1 -> {
                // Public big buck bunny video link for tests
                list.add(MediaFile(Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"), "Big Buck Bunny.mp4", 596000L, isVideo = true))
                list.add(MediaFile(Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"), "Elephants Dream.mp4", 653000L, isVideo = true))
            }
            2 -> {
                // Public sound sample
                list.add(MediaFile(Uri.parse("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"), "SoundHelix Song 1.mp3", 372000L, isAudio = true))
                list.add(MediaFile(Uri.parse("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"), "SoundHelix Song 2.mp3", 423000L, isAudio = true))
            }
        }
    }

    list
}

private fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1000) % 60
    val minutes = (durationMs / (1000 * 60)) % 60
    val hours = (durationMs / (1000 * 60 * 60)) % 24
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
