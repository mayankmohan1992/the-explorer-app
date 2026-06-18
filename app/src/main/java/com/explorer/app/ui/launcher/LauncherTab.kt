package com.explorer.app.ui.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import com.explorer.app.ui.components.GlassmorphicCard
import com.explorer.app.ui.theme.NeonCyan
import com.explorer.app.ui.theme.TextPrimary
import com.explorer.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(
    val label: String,
    val packageName: String,
    val className: String,
    val iconBitmap: android.graphics.Bitmap?
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LauncherTab() {
    val context = LocalContext.current
    var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFolderApps by remember { mutableStateOf<List<AppInfo>?>(null) }
    var folderName by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    // Query installed apps in the background
    LaunchedEffect(Unit) {
        installedApps = getInstalledApps(context)
    }

    val filteredApps = installedApps.filter {
        it.label.contains(searchQuery, ignoreCase = true)
    }

    // Smart categorization folders logic
    val categorizedFolders = remember(filteredApps) {
        val categories = mutableMapOf<String, MutableList<AppInfo>>()
        
        filteredApps.forEach { app ->
            val pkg = app.packageName.lowercase()
            when {
                pkg.contains("mail") || pkg.contains("chat") || pkg.contains("social") || pkg.contains("whatsapp") || pkg.contains("telegram") || pkg.contains("discord") || pkg.contains("twitter") || pkg.contains("facebook") -> {
                    categories.getOrPut("Social") { mutableListOf() }.add(app)
                }
                pkg.contains("tool") || pkg.contains("calc") || pkg.contains("setting") || pkg.contains("file") || pkg.contains("camera") || pkg.contains("clock") || pkg.contains("compass") -> {
                    categories.getOrPut("Tools") { mutableListOf() }.add(app)
                }
                pkg.contains("play") || pkg.contains("game") || pkg.contains("arcade") || pkg.contains("puzzle") || pkg.contains("steam") || pkg.contains("nintendo") -> {
                    categories.getOrPut("Games") { mutableListOf() }.add(app)
                }
                pkg.contains("music") || pkg.contains("video") || pkg.contains("player") || pkg.contains("youtube") || pkg.contains("netflix") || pkg.contains("spotify") || pkg.contains("gallery") -> {
                    categories.getOrPut("Media") { mutableListOf() }.add(app)
                }
                pkg.contains("chrome") || pkg.contains("browser") || pkg.contains("firefox") || pkg.contains("opera") || pkg.contains("safari") -> {
                    categories.getOrPut("Browsing") { mutableListOf() }.add(app)
                }
                else -> {
                    categories.getOrPut("Other") { mutableListOf() }.add(app)
                }
            }
        }
        categories.filter { it.value.isNotEmpty() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Search widget
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            placeholder = { Text("Search apps & web...", color = TextSecondary) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = NeonCyan) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = TextSecondary.copy(alpha = 0.5f),
                focusedLabelColor = NeonCyan,
                cursorColor = NeonCyan,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                focusManager.clearFocus()
                // Web search fallback if not matching any local app
                val match = filteredApps.firstOrNull { it.label.equals(searchQuery, ignoreCase = true) }
                if (match != null) {
                    launchApp(context, match)
                } else if (searchQuery.isNotEmpty()) {
                    val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                        putExtra("query", searchQuery)
                    }
                    context.startActivity(searchIntent)
                }
            }),
            shape = RoundedCornerShape(24.dp)
        )

        // Title or Welcome message
        if (searchQuery.isEmpty()) {
            Text(
                text = "SYSTEM DASHBOARD",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = NeonCyan,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // App display layout (Folders first, then grid of other apps)
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Folders section
            if (searchQuery.isEmpty()) {
                categorizedFolders.forEach { (name, appsInFolder) ->
                    item {
                        FolderItem(
                            name = name,
                            count = appsInFolder.size,
                            onClick = {
                                selectedFolderApps = appsInFolder
                                folderName = name
                            }
                        )
                    }
                }
            }

            // Normal individual apps list (if searching, show all matches; if not, show raw list)
            val appsToShow = if (searchQuery.isNotEmpty()) filteredApps else installedApps
            items(appsToShow) { app ->
                AppIconItem(app = app, onClick = { launchApp(context, app) })
            }
        }
    }

    // Folder open dialog overlay
    selectedFolderApps?.let { apps ->
        Dialog(onDismissRequest = { selectedFolderApps = null }) {
            GlassmorphicCard(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.7f)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = folderName.uppercase(),
                        color = NeonCyan,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    )
                    
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(apps) { app ->
                            AppIconItem(app = app, onClick = {
                                launchApp(context, app)
                                selectedFolderApps = null
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FolderItem(name: String, count: Int, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = name,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = name,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "$count apps",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            fontSize = 9.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AppIconItem(app: AppInfo, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(52.dp),
            contentAlignment = Alignment.Center
        ) {
            if (app.iconBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = app.iconBitmap.asImageBitmap(),
                    contentDescription = app.label,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = app.label.take(1).uppercase(),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = app.label,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private suspend fun getInstalledApps(context: Context): List<AppInfo> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    
    val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(mainIntent, 0)
    
    // Sort and convert to list of AppInfo
    resolveInfos.mapNotNull { resolveInfo ->
        val label = resolveInfo.loadLabel(pm).toString()
        val packageName = resolveInfo.activityInfo.packageName
        
        // Exclude our own package to prevent loop launchers
        if (packageName == context.packageName) return@mapNotNull null
        
        val className = resolveInfo.activityInfo.name
        val icon = resolveInfo.loadIcon(pm)
        val bitmap = try {
            icon.toBitmap(128, 128, android.graphics.Bitmap.Config.ARGB_8888)
        } catch (e: Exception) {
            null
        }
        AppInfo(label, packageName, className, bitmap)
    }.sortedBy { it.label.lowercase() }
}

private fun launchApp(context: Context, app: AppInfo) {
    try {
        val intent = Intent().apply {
            setClassName(app.packageName, app.className)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback to launch intent
        val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
        if (launchIntent != null) {
            context.startActivity(launchIntent)
        }
    }
}
