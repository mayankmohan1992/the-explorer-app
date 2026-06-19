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

@OptIn(ExperimentalMaterial3Api::class)
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
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var activeAudioFile by remember { mutableStateOf<MediaFile?>(null) }

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
                                    VideoCardItem(video = video, onClick = { selectedVideoUri = video.uri })
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
                                        isPlaying = activeAudioFile?.uri == audio.uri,
                                        onClick = { activeAudioFile = audio },
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
                                        isPlaying = activeAudioFile?.uri == audio.uri,
                                        onClick = { activeAudioFile = audio },
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
    selectedVideoUri?.let { uri ->
        VideoPlaybackDialog(
            videoUri = uri,
            onDismiss = { selectedVideoUri = null }
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
                                            selectedVideoUri = file.uri
                                        } else {
                                            activeFullscreenList = drilldownFiles.filter { !it.isVideo }
                                            activeFullscreenIndex = activeFullscreenList.indexOf(file)
                                        }
                                    }
                            ) {
                                AsyncImage(
                                    model = file.uri,
                                    contentDescription = file.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
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
                                            activeAudioFile = MediaFile(
                                                uri = Uri.parse(pSong.songUri),
                                                name = pSong.title,
                                                isAudio = true
                                            )
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

    // Audio bottom overlay player sheet
    activeAudioFile?.let { audio ->
        AudioOverlayPlayer(
            audioFile = audio,
            onClose = { activeAudioFile = null }
        )
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
            AsyncImage(
                model = video.uri,
                contentDescription = video.name,
                contentScale = ContentScale.Crop,
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
            .padding(bottom = 76.dp), // Float above bottom bar
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
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
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Now Playing",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary
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
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
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
