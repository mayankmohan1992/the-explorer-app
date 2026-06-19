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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.text.style.TextAlign
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
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.media3.common.PlaybackParameters
import com.explorer.app.data.db.AppDatabase
import com.explorer.app.data.db.Playlist
import com.explorer.app.data.db.PlaylistSong
import com.explorer.app.ui.components.GlassmorphicCard
import com.explorer.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MediaFile(
    val uri: Uri,
    val name: String,
    val duration: Long = 0,
    val isVideo: Boolean = false,
    val isAudio: Boolean = false,
    val folder: String = "Other",
    val dateAdded: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MediaTab() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val rssDao = db.rssDao()

    var selectedMediaTab by remember { mutableStateOf(0) } // 0 = Photos, 1 = Videos, 2 = Music
    var mediaFiles by remember { mutableStateOf<List<MediaFile>>(emptyList()) }
    var hasPermission by remember { mutableStateOf(false) }

    // Media sub-views
    var photoSubView by remember { mutableStateOf(0) } // 0 = Chronological, 1 = Folders, 2 = Camera Only
    var videoSubView by remember { mutableStateOf(0) } // 0 = All Videos, 1 = Folders
    var musicSubView by remember { mutableStateOf(0) } // 0 = A-Z, 1 = Recently Added, 2 = Playlists

    // Media Viewer states
    var activeFullscreenList by remember { mutableStateOf<List<MediaFile>>(emptyList()) }
    var activeFullscreenIndex by remember { mutableStateOf(-1) }
    var selectedVideoFile by remember { mutableStateOf<MediaFile?>(null) }
    var activeAudioFile by remember { mutableStateOf<MediaFile?>(null) }

    // Audio Player Queue States
    var activeAudioQueue by remember { mutableStateOf<List<MediaFile>>(emptyList()) }
    var activeAudioIndex by remember { mutableStateOf(-1) }
    var isAudioPlayerExpanded by remember { mutableStateOf(false) }
    var isShuffleEnabled by remember { mutableStateOf(false) }
    var repeatModeState by remember { mutableStateOf(Player.REPEAT_MODE_OFF) }

    // Folder Drilldown Dialog state
    var selectedFolderDrilldown by remember { mutableStateOf<String?>(null) }
    var drilldownFiles by remember { mutableStateOf<List<MediaFile>>(emptyList()) }

    // Playlists state
    var playlistsList by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var selectedPlaylistForViewing by remember { mutableStateOf<Playlist?>(null) }
    var playlistSongsList by remember { mutableStateOf<List<PlaylistSong>>(emptyList()) }
    var activeAddSongToPlaylist by remember { mutableStateOf<MediaFile?>(null) }

    // Check permissions
    LaunchedEffect(Unit) {
        hasPermission = checkMediaPermissions(context)
        if (hasPermission) {
            mediaFiles = loadMediaFiles(context, selectedMediaTab)
            playlistsList = withContext(Dispatchers.IO) { rssDao.getAllPlaylists() }
        }
    }

    val audioPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.release()
        }
    }

    DisposableEffect(audioPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    if (repeatModeState == Player.REPEAT_MODE_ONE) {
                        audioPlayer.seekTo(0)
                        audioPlayer.playWhenReady = true
                    } else {
                        val nextIndex = getNextAudioIndex(activeAudioIndex, activeAudioQueue, isShuffleEnabled, repeatModeState)
                        if (nextIndex != -1) {
                            activeAudioIndex = nextIndex
                        }
                    }
                }
            }
        }
        audioPlayer.addListener(listener)
        onDispose {
            audioPlayer.removeListener(listener)
        }
    }

    LaunchedEffect(activeAudioIndex, activeAudioQueue) {
        val audioFile = activeAudioQueue.getOrNull(activeAudioIndex)
        if (audioFile != null) {
            audioPlayer.stop()
            audioPlayer.setMediaItem(MediaItem.fromUri(audioFile.uri))
            audioPlayer.prepare()
            audioPlayer.playWhenReady = true
            activeAudioFile = audioFile
        } else {
            activeAudioFile = null
        }
    }

    // Reload files when main tab changes
    LaunchedEffect(selectedMediaTab, hasPermission) {
        if (hasPermission) {
            mediaFiles = loadMediaFiles(context, selectedMediaTab)
        }
    }

    // Fetch playlists when music tab loads
    LaunchedEffect(musicSubView) {
        if (hasPermission && selectedMediaTab == 2) {
            playlistsList = withContext(Dispatchers.IO) { rssDao.getAllPlaylists() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp)
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Media Tab Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { selectedMediaTab = 0 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedMediaTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (selectedMediaTab == 0) Color.White else MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Photo, contentDescription = "Photos", modifier = Modifier.size(16.dp).padding(end = 4.dp))
                Text("Photos", fontSize = 11.sp)
            }
            Button(
                onClick = { selectedMediaTab = 1 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedMediaTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (selectedMediaTab == 1) Color.White else MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(0.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Movie, contentDescription = "Videos", modifier = Modifier.size(16.dp).padding(end = 4.dp))
                Text("Videos", fontSize = 11.sp)
            }
            Button(
                onClick = { selectedMediaTab = 2 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedMediaTab == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (selectedMediaTab == 2) Color.White else MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = "Music", modifier = Modifier.size(16.dp).padding(end = 4.dp))
                Text("Music", fontSize = 11.sp)
            }
        }

        // Sub-Tab navigation bar (OxygenOS style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (selectedMediaTab) {
                0 -> { // Photos Subbar
                    TextButton(onClick = { photoSubView = 0 }) {
                        Text("All Photos", color = if (photoSubView == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontWeight = if (photoSubView == 0) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
                    }
                    TextButton(onClick = { photoSubView = 1 }) {
                        Text("Folders", color = if (photoSubView == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontWeight = if (photoSubView == 1) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
                    }
                    TextButton(onClick = { photoSubView = 2 }) {
                        Text("Camera Only", color = if (photoSubView == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontWeight = if (photoSubView == 2) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
                    }
                }
                1 -> { // Videos Subbar
                    TextButton(onClick = { videoSubView = 0 }) {
                        Text("All Videos", color = if (videoSubView == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontWeight = if (videoSubView == 0) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
                    }
                    TextButton(onClick = { videoSubView = 1 }) {
                        Text("Folders", color = if (videoSubView == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontWeight = if (videoSubView == 1) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
                    }
                }
                2 -> { // Music Subbar
                    TextButton(onClick = { musicSubView = 0 }) {
                        Text("A-Z Sorted", color = if (musicSubView == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontWeight = if (musicSubView == 0) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
                    }
                    TextButton(onClick = { musicSubView = 1 }) {
                        Text("Recently Added", color = if (musicSubView == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontWeight = if (musicSubView == 1) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
                    }
                    TextButton(onClick = { musicSubView = 2 }) {
                        Text("Playlists", color = if (musicSubView == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontWeight = if (musicSubView == 2) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
                    }
                }
            }
        }

        if (!hasPermission) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Permission Needed", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Storage Access Required", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "To access your photos, videos, and music tracks directly on this device, please grant the storage permission.",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { hasPermission = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Grant Permission", color = Color.White)
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
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    fontSize = 13.sp
                )
            }
        } else {
            // Render media based on active tab and subview selection
            when (selectedMediaTab) {
                0 -> { // Photos View Routing
                    when (photoSubView) {
                        0 -> { // Chronological
                            val groupedPhotos = remember(mediaFiles) {
                                mediaFiles.groupBy { file ->
                                    val date = java.util.Date(file.dateAdded)
                                    val sdf = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())
                                    sdf.format(date)
                                }
                            }
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(bottom = 80.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                groupedPhotos.forEach { (month, photos) ->
                                    item {
                                        Text(
                                            text = month.uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    }
                                    item {
                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(3),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(((photos.size + 2) / 3 * 110).dp), // dynamic height estimate
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                            userScrollEnabled = false
                                        ) {
                                            items(photos) { photo ->
                                                Box(
                                                    modifier = Modifier
                                                        .aspectRatio(1f)
                                                        .clip(RoundedCornerShape(2.dp))
                                                        .clickable { 
                                                            activeFullscreenList = mediaFiles
                                                            activeFullscreenIndex = mediaFiles.indexOf(photo)
                                                        }
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
                                }
                            }
                        }
                        1 -> { // Folders
                            val folderGroups = remember(mediaFiles) {
                                mediaFiles.groupBy { it.folder }
                            }
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(bottom = 80.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(folderGroups.keys.toList()) { folder ->
                                    val files = folderGroups[folder] ?: emptyList()
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1.1f)
                                            .clickable {
                                                selectedFolderDrilldown = folder
                                                drilldownFiles = files
                                            },
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .weight(1f)
                                            ) {
                                                if (files.isNotEmpty()) {
                                                    AsyncImage(
                                                        model = files[0].uri,
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(Color(0x33000000))
                                                )
                                            }
                                            PaddingValues(8.dp).let {
                                                Column(modifier = Modifier.padding(8.dp)) {
                                                    Text(
                                                        text = folder,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "${files.size} Photos",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        2 -> { // Camera Only
                            val cameraPhotos = remember(mediaFiles) {
                                mediaFiles.filter { it.folder.equals("Camera", ignoreCase = true) }
                            }
                            LazyVerticalGrid(
                                                columns = GridCells.Fixed(3),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .weight(1f),
                                                contentPadding = PaddingValues(bottom = 80.dp),
                                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                items(cameraPhotos) { photo ->
                                                    Box(
                                                        modifier = Modifier
                                                            .aspectRatio(1f)
                                                            .clip(RoundedCornerShape(2.dp))
                                                            .clickable { 
                                                                activeFullscreenList = cameraPhotos
                                                                activeFullscreenIndex = cameraPhotos.indexOf(photo)
                                                            }
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
                    }
                }
                1 -> { // Videos View Routing
                    when (videoSubView) {
                        0 -> { // All Videos
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(bottom = 80.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(mediaFiles) { video ->
                                    VideoCardItem(video = video, onClick = { selectedVideoFile = video })
                                }
                            }
                        }
                        1 -> { // Folders
                            val folderGroups = remember(mediaFiles) {
                                mediaFiles.groupBy { it.folder }
                            }
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(bottom = 80.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(folderGroups.keys.toList()) { folder ->
                                    val files = folderGroups[folder] ?: emptyList()
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1.1f)
                                            .clickable {
                                                selectedFolderDrilldown = folder
                                                drilldownFiles = files
                                            },
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .weight(1f)
                                            ) {
                                                if (files.isNotEmpty()) {
                                                    AsyncImage(
                                                        model = files[0].uri,
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(Color(0x33000000)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                                                }
                                            }
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Text(
                                                    text = folder,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${files.size} Videos",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> { // Music View Routing
                    when (musicSubView) {
                        0 -> { // Alphabetical
                            val alphabeticalMusic = remember(mediaFiles) {
                                mediaFiles.sortedBy { it.name.lowercase() }
                            }
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(bottom = 80.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(alphabeticalMusic) { audio ->
                                    AudioItemRow(
                                        audio = audio,
                                        isPlaying = activeAudioIndex != -1 && activeAudioQueue.getOrNull(activeAudioIndex)?.uri == audio.uri,
                                        onClick = {
                                            activeAudioQueue = alphabeticalMusic
                                            activeAudioIndex = alphabeticalMusic.indexOf(audio)
                                        },
                                        onAddToPlaylist = { activeAddSongToPlaylist = audio }
                                    )
                                }
                            }
                        }
                        1 -> { // Recently Added
                            val recentlyAddedMusic = remember(mediaFiles) {
                                mediaFiles.sortedByDescending { it.dateAdded }
                            }
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(bottom = 80.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(recentlyAddedMusic) { audio ->
                                    AudioItemRow(
                                        audio = audio,
                                        isPlaying = activeAudioIndex != -1 && activeAudioQueue.getOrNull(activeAudioIndex)?.uri == audio.uri,
                                        onClick = {
                                            activeAudioQueue = recentlyAddedMusic
                                            activeAudioIndex = recentlyAddedMusic.indexOf(audio)
                                        },
                                        onAddToPlaylist = { activeAddSongToPlaylist = audio }
                                    )
                                }
                            }
                        }
                        2 -> { // Playlists
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("PLAYLISTS COLLECTION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    TextButton(onClick = { showCreatePlaylistDialog = true }) {
                                        Icon(Icons.Default.Add, contentDescription = "Create Playlist", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("New Playlist")
                                    }
                                }

                                if (playlistsList.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("No playlists created yet.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        contentPadding = PaddingValues(bottom = 80.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(playlistsList) { playlist ->
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        selectedPlaylistForViewing = playlist
                                                    },
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(16.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(playlist.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                        Text("Custom Playlist Collection", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            coroutineScope.launch {
                                                                withContext(Dispatchers.IO) {
                                                                    rssDao.deletePlaylist(playlist)
                                                                }
                                                                playlistsList = withContext(Dispatchers.IO) { rssDao.getAllPlaylists() }
                                                            }
                                                        }
                                                    ) {
                                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
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
        }
    }

    // Video playback dialog player
    selectedVideoFile?.let { video ->
        VideoPlaybackDialog(
            videoUri = video.uri,
            videoTitle = video.name,
            onDismiss = { selectedVideoFile = null }
        )
    }

    // Image fullscreen viewer with HorizontalPager swiping
    if (activeFullscreenIndex >= 0 && activeFullscreenList.isNotEmpty()) {
        Dialog(
            onDismissRequest = { activeFullscreenIndex = -1; activeFullscreenList = emptyList() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                key(activeFullscreenIndex, activeFullscreenList.size) {
                    val pagerState = rememberPagerState(
                        initialPage = activeFullscreenIndex,
                        pageCount = { activeFullscreenList.size }
                    )
                    
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        pageSpacing = 16.dp
                    ) { pageIndex ->
                        val file = activeFullscreenList.getOrNull(pageIndex)
                        if (file != null) {
                            AsyncImage(
                                model = file.uri,
                                contentDescription = file.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
                
                IconButton(
                    onClick = { activeFullscreenIndex = -1; activeFullscreenList = emptyList() },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }

    // Folder Drilldown dialog viewer
    selectedFolderDrilldown?.let { folderName ->
        Dialog(
            onDismissRequest = { selectedFolderDrilldown = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectedFolderDrilldown = null }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = folderName.uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(drilldownFiles) { file ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(2.dp))
                                    .clickable {
                                        if (file.isVideo) {
                                            selectedVideoFile = file
                                        } else {
                                            activeFullscreenList = drilldownFiles.filter { !it.isVideo }
                                            activeFullscreenIndex = activeFullscreenList.indexOf(file)
                                        }
                                    }
                            ) {
                                if (file.isVideo) {
                                    VideoThumbnail(videoUri = file.uri, modifier = Modifier.fillMaxSize())
                                } else {
                                    AsyncImage(
                                        model = file.uri,
                                        contentDescription = file.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                if (file.isVideo) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0x33000000)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Playlist songs viewer drilldown
    selectedPlaylistForViewing?.let { playlist ->
        LaunchedEffect(playlist.id) {
            playlistSongsList = withContext(Dispatchers.IO) { rssDao.getSongsForPlaylist(playlist.id) }
        }

        Dialog(
            onDismissRequest = { selectedPlaylistForViewing = null }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.8f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Playlist: ${playlist.name}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (playlistSongsList.isEmpty()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No songs in this playlist yet.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(playlistSongsList) { pSong ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .clickable {
                                            val playlistQueue = playlistSongsList.map { song ->
                                                MediaFile(
                                                    uri = Uri.parse(song.songUri),
                                                    name = song.title,
                                                    isAudio = true
                                                )
                                            }
                                            activeAudioQueue = playlistQueue
                                            activeAudioIndex = playlistQueue.indexOfFirst { it.uri.toString() == pSong.songUri }
                                            selectedPlaylistForViewing = null
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = pSong.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                withContext(Dispatchers.IO) {
                                                    rssDao.deletePlaylistSong(pSong)
                                                }
                                                playlistSongsList = withContext(Dispatchers.IO) { rssDao.getSongsForPlaylist(playlist.id) }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = { selectedPlaylistForViewing = null },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }

    // Add Song to Playlist dialog
    activeAddSongToPlaylist?.let { audio ->
        Dialog(
            onDismissRequest = { activeAddSongToPlaylist = null }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Add to Playlist", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp))
                    
                    if (playlistsList.isEmpty()) {
                        Text("No playlists found. Please create a playlist first.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 16.dp))
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 200.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(playlistsList) { playlist ->
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            withContext(Dispatchers.IO) {
                                                rssDao.insertPlaylistSong(
                                                    PlaylistSong(
                                                        playlistId = playlist.id,
                                                        songUri = audio.uri.toString(),
                                                        title = audio.name
                                                    )
                                                )
                                            }
                                            activeAddSongToPlaylist = null
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(playlist.name, textAlign = TextAlign.Left, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = { activeAddSongToPlaylist = null },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }

    // Create Playlist dialog input
    if (showCreatePlaylistDialog) {
        Dialog(onDismissRequest = { showCreatePlaylistDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Create New Playlist", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Playlist Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showCreatePlaylistDialog = false }) { Text("Cancel") }
                        Button(
                            onClick = {
                                if (newPlaylistName.isNotEmpty()) {
                                    coroutineScope.launch {
                                        withContext(Dispatchers.IO) {
                                            rssDao.insertPlaylist(Playlist(name = newPlaylistName))
                                        }
                                        playlistsList = withContext(Dispatchers.IO) { rssDao.getAllPlaylists() }
                                        newPlaylistName = ""
                                        showCreatePlaylistDialog = false
                                    }
                                }
                            }
                        ) {
                            Text("Create")
                        }
                    }
                }
            }
        }
    }

    // Audio bottom overlay player sheet & expanded dialog
    if (activeAudioIndex != -1 && activeAudioQueue.isNotEmpty()) {
        val currentAudio = activeAudioQueue.getOrNull(activeAudioIndex)
        if (currentAudio != null) {
            AudioOverlayPlayer(
                audioFile = currentAudio,
                exoPlayer = audioPlayer,
                onExpand = { isAudioPlayerExpanded = true },
                onClose = {
                    audioPlayer.stop()
                    activeAudioIndex = -1
                    activeAudioQueue = emptyList()
                }
            )

            if (isAudioPlayerExpanded) {
                AudioExpandedPlayer(
                    audioFile = currentAudio,
                    exoPlayer = audioPlayer,
                    queue = activeAudioQueue,
                    activeIndex = activeAudioIndex,
                    isShuffleEnabled = isShuffleEnabled,
                    repeatMode = repeatModeState,
                    onIndexChanged = { activeAudioIndex = it },
                    onShuffleToggled = { isShuffleEnabled = it },
                    onRepeatModeChanged = { repeatModeState = it },
                    onCollapse = { isAudioPlayerExpanded = false },
                    onClose = {
                        audioPlayer.stop()
                        activeAudioIndex = -1
                        activeAudioQueue = emptyList()
                        isAudioPlayerExpanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun VideoThumbnail(videoUri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var thumbnail by remember(videoUri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(videoUri) {
        withContext(Dispatchers.IO) {
            try {
                if (videoUri.scheme == "http" || videoUri.scheme == "https") {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(videoUri.toString(), HashMap<String, String>())
                    thumbnail = retriever.frameAtTime
                    retriever.release()
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        thumbnail = context.contentResolver.loadThumbnail(videoUri, android.util.Size(320, 240), null)
                    } catch (e: Exception) {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(context, videoUri)
                        thumbnail = retriever.frameAtTime
                        retriever.release()
                    }
                } else {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, videoUri)
                    thumbnail = retriever.frameAtTime
                    retriever.release()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (thumbnail != null) {
        Image(
            bitmap = thumbnail!!.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Movie,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun VideoCardItem(video: MediaFile, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .aspectRatio(1.3f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            VideoThumbnail(
                videoUri = video.uri,
                modifier = Modifier.fillMaxSize()
            )
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x33000000)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

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

@Composable
fun AudioItemRow(audio: MediaFile, isPlaying: Boolean, onClick: () -> Unit, onAddToPlaylist: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
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
                tint = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(28.dp).padding(end = 8.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = audio.name,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Local Audio file",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Text(
                text = formatDuration(audio.duration),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            IconButton(onClick = onAddToPlaylist) {
                Icon(
                    imageVector = Icons.Default.PlaylistAdd,
                    contentDescription = "Add to Playlist",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun VideoPlaybackDialog(videoUri: Uri, videoTitle: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var isPlayingState by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableStateOf(0L) }
    var totalDuration by remember { mutableStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var volume by remember { mutableStateOf(1.0f) }
    var isLocked by remember { mutableStateOf(false) }

    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(exoPlayer, isPlayingState) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            totalDuration = exoPlayer.duration
            kotlinx.coroutines.delay(500)
        }
    }

    LaunchedEffect(showControls, isPlayingState) {
        if (showControls && isPlayingState) {
            kotlinx.coroutines.delay(3000)
            showControls = false
        }
    }

    DisposableEffect(videoUri) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlayingState = isPlaying
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    totalDuration = exoPlayer.duration
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
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
                .background(Color.Black)
                .clickable { if (!isLocked) showControls = !showControls }
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = showControls,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut()
            ) {
                if (isLocked) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        IconButton(
                            onClick = { isLocked = false },
                            modifier = Modifier
                                .padding(24.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Unlock Controls", tint = Color(0xFFFF8800))
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = videoTitle,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            
                            TextButton(
                                onClick = {
                                    playbackSpeed = when (playbackSpeed) {
                                        1.0f -> 1.5f
                                        1.5f -> 2.0f
                                        2.0f -> 0.5f
                                        else -> 1.0f
                                    }
                                    exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF8800))
                            ) {
                                Text("${playbackSpeed}x", fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(32.dp)
                        ) {
                            IconButton(
                                onClick = { exoPlayer.seekTo((currentPosition - 10000).coerceAtLeast(0L)) },
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(28.dp))
                            ) {
                                Icon(Icons.Default.FastRewind, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(32.dp))
                            }

                            IconButton(
                                onClick = { if (isPlayingState) exoPlayer.pause() else exoPlayer.play() },
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(Color(0xFFFF8800), RoundedCornerShape(36.dp))
                            ) {
                                Icon(
                                    imageVector = if (isPlayingState) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp)
                                )
                            }

                            IconButton(
                                onClick = { exoPlayer.seekTo((currentPosition + 10000).coerceAtMost(totalDuration)) },
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(28.dp))
                            ) {
                                Icon(Icons.Default.FastForward, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                        }

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                                .padding(16.dp)
                        ) {
                            Slider(
                                value = currentPosition.toFloat(),
                                onValueChange = { newPos ->
                                    currentPosition = newPos.toLong()
                                    exoPlayer.seekTo(currentPosition)
                                },
                                valueRange = 0f..totalDuration.toFloat().coerceAtLeast(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFFF8800),
                                    activeTrackColor = Color(0xFFFF8800),
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${formatDuration(currentPosition)} / ${formatDuration(totalDuration)}",
                                    color = Color.White,
                                    fontSize = 12.sp
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    IconButton(onClick = { isLocked = true }) {
                                        Icon(Icons.Default.LockOpen, contentDescription = "Lock Controls", tint = Color.White)
                                    }

                                    IconButton(
                                        onClick = {
                                            volume = if (volume > 0f) 0f else 1.0f
                                            exoPlayer.volume = volume
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (volume > 0f) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                            contentDescription = "Mute/Unmute",
                                            tint = Color.White
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

@Composable
fun AudioOverlayPlayer(
    audioFile: MediaFile,
    exoPlayer: ExoPlayer,
    onExpand: () -> Unit,
    onClose: () -> Unit
) {
    var isPlayingState by remember { mutableStateOf(exoPlayer.isPlaying) }
    var currentPosition by remember { mutableStateOf(exoPlayer.currentPosition) }
    var totalDuration by remember { mutableStateOf(exoPlayer.duration) }

    DisposableEffect(exoPlayer, audioFile.uri) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlayingState = isPlaying
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    totalDuration = exoPlayer.duration
                }
            }
        }
        exoPlayer.addListener(listener)
        isPlayingState = exoPlayer.isPlaying
        totalDuration = exoPlayer.duration
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    LaunchedEffect(exoPlayer, isPlayingState, audioFile.uri) {
        while (isPlayingState) {
            currentPosition = exoPlayer.currentPosition
            totalDuration = exoPlayer.duration
            kotlinx.coroutines.delay(500)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 76.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 8.dp)
                .clickable(onClick = onExpand),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                val progressPercent = if (totalDuration > 0) currentPosition.toFloat() / totalDuration.toFloat() else 0f
                LinearProgressIndicator(
                    progress = progressPercent.coerceIn(0f, 1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = Color(0xFFFF8800),
                    trackColor = Color.Transparent
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFF8800).copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color(0xFFFF8800),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = audioFile.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Now Playing",
                            fontSize = 10.sp,
                            color = Color(0xFFFF8800)
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
                            tint = Color(0xFFFF8800),
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AudioExpandedPlayer(
    audioFile: MediaFile,
    exoPlayer: ExoPlayer,
    queue: List<MediaFile>,
    activeIndex: Int,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    onIndexChanged: (Int) -> Unit,
    onShuffleToggled: (Boolean) -> Unit,
    onRepeatModeChanged: (Int) -> Unit,
    onCollapse: () -> Unit,
    onClose: () -> Unit
) {
    var isPlayingState by remember { mutableStateOf(exoPlayer.isPlaying) }
    var currentPosition by remember { mutableStateOf(exoPlayer.currentPosition) }
    var totalDuration by remember { mutableStateOf(exoPlayer.duration) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var volume by remember { mutableStateOf(exoPlayer.volume) }
    var showQueueList by remember { mutableStateOf(false) }

    DisposableEffect(exoPlayer, audioFile.uri) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlayingState = isPlaying
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    totalDuration = exoPlayer.duration
                }
            }
        }
        exoPlayer.addListener(listener)
        isPlayingState = exoPlayer.isPlaying
        totalDuration = exoPlayer.duration
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    LaunchedEffect(exoPlayer, isPlayingState, audioFile.uri) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            totalDuration = exoPlayer.duration
            kotlinx.coroutines.delay(250)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Dialog(
        onDismissRequest = onCollapse,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF121212)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCollapse) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Collapse",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    
                    Text(
                        text = "NOW PLAYING",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(onClick = { showQueueList = !showQueueList }) {
                        Icon(
                            imageVector = Icons.Default.QueueMusic,
                            contentDescription = "Play Queue",
                            tint = if (showQueueList) Color(0xFFFF8800) else Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                if (showQueueList) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    ) {
                        Text(
                            text = "Play Queue (${queue.size} songs)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(queue.size) { idx ->
                                val song = queue[idx]
                                val isActive = idx == activeIndex
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isActive) Color(0xFFFF8800).copy(alpha = 0.2f)
                                            else Color.White.copy(alpha = 0.05f)
                                        )
                                        .clickable { onIndexChanged(idx) }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isActive) Icons.Default.VolumeUp else Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = if (isActive) Color(0xFFFF8800) else Color.White.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = song.name,
                                        color = if (isActive) Color(0xFFFF8800) else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(240.dp)
                                .clip(RoundedCornerShape(120.dp))
                                .background(
                                    androidx.compose.ui.graphics.Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFFFF8800).copy(alpha = 0.2f),
                                            Color.Transparent
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(200.dp)
                                    .rotate(if (isPlayingState) rotation else 0f)
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(Color(0xFF1E1E1E))
                                    .border(4.dp, Color(0xFF333333), RoundedCornerShape(100.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(150.dp)
                                        .clip(RoundedCornerShape(75.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(75.dp))
                                )
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(RoundedCornerShape(50.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(50.dp))
                                )
                                
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(30.dp))
                                        .background(Color(0xFFFF8800)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = audioFile.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "VLC Media Stream • Local Audio",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = currentPosition.toFloat(),
                        onValueChange = { newPos ->
                            currentPosition = newPos.toLong()
                            exoPlayer.seekTo(currentPosition)
                        },
                        valueRange = 0f..totalDuration.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFF8800),
                            activeTrackColor = Color(0xFFFF8800),
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDuration(currentPosition),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                        Text(
                            text = formatDuration(totalDuration),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onShuffleToggled(!isShuffleEnabled) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (isShuffleEnabled) Color(0xFFFF8800) else Color.White.copy(alpha = 0.4f)
                        )
                    }

                    IconButton(
                        onClick = {
                            val prevIndex = getPreviousAudioIndex(activeIndex, queue, isShuffleEnabled, repeatMode)
                            if (prevIndex != -1) onIndexChanged(prevIndex)
                        },
                        enabled = isShuffleEnabled || activeIndex > 0 || repeatMode == Player.REPEAT_MODE_ALL
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = if (isShuffleEnabled || activeIndex > 0 || repeatMode == Player.REPEAT_MODE_ALL) Color.White else Color.White.copy(alpha = 0.2f)
                        )
                    }

                    IconButton(onClick = { exoPlayer.seekTo((currentPosition - 10000).coerceAtLeast(0L)) }) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = "Rewind 10s",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = {
                            if (isPlayingState) exoPlayer.pause() else exoPlayer.play()
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(36.dp))
                            .background(Color(0xFFFF8800))
                    ) {
                        Icon(
                            imageVector = if (isPlayingState) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    IconButton(onClick = { exoPlayer.seekTo((currentPosition + 10000).coerceAtMost(totalDuration)) }) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = "Fast Forward 10s",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = {
                            val nextIndex = getNextAudioIndex(activeIndex, queue, isShuffleEnabled, repeatMode)
                            if (nextIndex != -1) onIndexChanged(nextIndex)
                        },
                        enabled = isShuffleEnabled || activeIndex < queue.lastIndex || repeatMode == Player.REPEAT_MODE_ALL
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = if (isShuffleEnabled || activeIndex < queue.lastIndex || repeatMode == Player.REPEAT_MODE_ALL) Color.White else Color.White.copy(alpha = 0.2f)
                        )
                    }

                    IconButton(
                        onClick = {
                            val nextMode = when (repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                            onRepeatModeChanged(nextMode)
                        }
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Icon(
                                imageVector = Icons.Default.Repeat,
                                contentDescription = "Repeat",
                                tint = if (repeatMode != Player.REPEAT_MODE_OFF) Color(0xFFFF8800) else Color.White.copy(alpha = 0.4f)
                            )
                            if (repeatMode == Player.REPEAT_MODE_ONE) {
                                Text(
                                    text = "1",
                                    color = Color(0xFFFF8800),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(Color.Black, RoundedCornerShape(2.dp))
                                        .padding(horizontal = 2.dp)
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (volume > 0f) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = "Volume",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Slider(
                            value = volume,
                            onValueChange = {
                                volume = it
                                exoPlayer.volume = it
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White.copy(alpha = 0.6f),
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${(volume * 100).toInt()}%",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            modifier = Modifier.width(32.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Playback Speed:",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val speeds = listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f)
                            speeds.forEach { speed ->
                                val isSelected = playbackSpeed == speed
                                Text(
                                    text = "${speed}x",
                                    color = if (isSelected) Color(0xFFFF8800) else Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isSelected) Color(0xFFFF8800).copy(alpha = 0.15f) else Color.Transparent)
                                        .clickable {
                                            playbackSpeed = speed
                                            exoPlayer.playbackParameters = PlaybackParameters(speed)
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getNextAudioIndex(
    currentIndex: Int,
    queue: List<MediaFile>,
    shuffle: Boolean,
    repeatMode: Int
): Int {
    if (queue.isEmpty()) return -1
    if (shuffle) {
        return (queue.indices).random()
    }
    if (currentIndex < queue.lastIndex) {
        return currentIndex + 1
    }
    if (repeatMode == Player.REPEAT_MODE_ALL) {
        return 0
    }
    return -1
}

private fun getPreviousAudioIndex(
    currentIndex: Int,
    queue: List<MediaFile>,
    shuffle: Boolean,
    repeatMode: Int
): Int {
    if (queue.isEmpty()) return -1
    if (shuffle) {
        return (queue.indices).random()
    }
    if (currentIndex > 0) {
        return currentIndex - 1
    }
    if (repeatMode == Player.REPEAT_MODE_ALL) {
        return queue.lastIndex
    }
    return -1
}

private fun checkMediaPermissions(context: Context): Boolean {
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
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED
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
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Video.Media.DATE_ADDED
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
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATE_ADDED
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
            val bucketColumn = if (type < 2) cursor.getColumnIndexOrThrow("bucket_display_name") else -1
            val dateAddedColumn = cursor.getColumnIndexOrThrow("date_added")

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val duration = if (durationColumn != -1) cursor.getLong(durationColumn) else 0L
                val folder = if (bucketColumn != -1) cursor.getString(bucketColumn) ?: "Other" else "Other"
                val dateAdded = cursor.getLong(dateAddedColumn) * 1000L // convert seconds to ms
                val uri = ContentUris.withAppendedId(contentUri, id)

                list.add(
                    MediaFile(
                        uri = uri,
                        name = name,
                        duration = duration,
                        isVideo = type == 1,
                        isAudio = type == 2,
                        folder = folder,
                        dateAdded = dateAdded
                    )
                )
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    if (list.isEmpty()) {
        when (type) {
            0 -> {
                list.add(MediaFile(Uri.parse("https://picsum.photos/800/800?random=1"), "vacation_mountains.jpg", folder = "Camera", dateAdded = System.currentTimeMillis() - 86400000 * 2))
                list.add(MediaFile(Uri.parse("https://picsum.photos/800/800?random=2"), "beach_sunset.jpg", folder = "Camera", dateAdded = System.currentTimeMillis() - 86400000 * 2))
                list.add(MediaFile(Uri.parse("https://picsum.photos/800/800?random=3"), "pdf_screenshot.png", folder = "Screenshots", dateAdded = System.currentTimeMillis() - 86400000 * 5))
                list.add(MediaFile(Uri.parse("https://picsum.photos/800/800?random=4"), "wa_shared_meme.jpg", folder = "WhatsApp", dateAdded = System.currentTimeMillis() - 86400000 * 1))
                list.add(MediaFile(Uri.parse("https://picsum.photos/800/800?random=5"), "download_wallpaper.jpg", folder = "Downloads", dateAdded = System.currentTimeMillis() - 86400000 * 10))
                list.add(MediaFile(Uri.parse("https://picsum.photos/800/800?random=6"), "grad_portrait.jpg", folder = "Camera", dateAdded = System.currentTimeMillis() - 86400000 * 30))
            }
            1 -> {
                list.add(MediaFile(Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"), "bunny_family.mp4", 596000L, isVideo = true, folder = "Downloads", dateAdded = System.currentTimeMillis() - 86400000 * 3))
                list.add(MediaFile(Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"), "elephants_clip.mp4", 653000L, isVideo = true, folder = "Camera", dateAdded = System.currentTimeMillis() - 86400000 * 7))
            }
            2 -> {
                list.add(MediaFile(Uri.parse("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"), "Chiptune Beats.mp3", 372000L, isAudio = true, dateAdded = System.currentTimeMillis() - 86400000 * 12))
                list.add(MediaFile(Uri.parse("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"), "Synthwave Drive.mp3", 423000L, isAudio = true, dateAdded = System.currentTimeMillis() - 86400000 * 2))
                list.add(MediaFile(Uri.parse("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3"), "Acoustic Sunset.mp3", 302000L, isAudio = true, dateAdded = System.currentTimeMillis() - 86400000 * 45))
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
