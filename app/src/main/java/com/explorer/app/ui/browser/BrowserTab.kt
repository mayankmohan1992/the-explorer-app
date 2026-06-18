package com.explorer.app.ui.browser

import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.explorer.app.data.db.AppDatabase
import com.explorer.app.data.db.Bookmark
import com.explorer.app.ui.components.GlassmorphicCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BrowserTabState(
    val id: String = java.util.UUID.randomUUID().toString(),
    val url: String = "https://www.google.com",
    val title: String = "New Tab"
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserTab() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val rssDao = db.rssDao()

    // Multi-tabs state
    var tabsList by remember { mutableStateOf(listOf(BrowserTabState())) }
    var activeTabIndex by remember { mutableStateOf(0) }
    val activeTab = tabsList.getOrNull(activeTabIndex) ?: BrowserTabState()

    var urlInput by remember(activeTab.id) { mutableStateOf(activeTab.url) }
    var webView: WebView? by remember { mutableStateOf(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    val focusManager = LocalFocusManager.current

    // Bookmarks and script states
    var bookmarksList by remember { mutableStateOf<List<Bookmark>>(emptyList()) }
    var isBookmarkedState by remember { mutableStateOf(false) }
    var userScripts by remember { mutableStateOf(listOf<String>()) } // List of scripts to inject
    
    // Dialog control states
    var showTabsDialog by remember { mutableStateOf(false) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var showScriptsDialog by remember { mutableStateOf(false) }
    
    // Fetch bookmarks initially
    LaunchedEffect(Unit) {
        bookmarksList = withContext(Dispatchers.IO) { rssDao.getAllBookmarks() }
    }

    // Check if active URL is bookmarked
    LaunchedEffect(activeTab.url, bookmarksList) {
        isBookmarkedState = withContext(Dispatchers.IO) {
            rssDao.isBookmarked(activeTab.url)
        }
    }

    // Handle back press
    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

    // Load active url in webView when active tab changes
    LaunchedEffect(activeTabIndex) {
        webView?.loadUrl(activeTab.url)
        urlInput = activeTab.url
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp)
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Address bar and controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bookmarks Toggle Button
            IconButton(
                onClick = {
                    coroutineScope.launch {
                        if (isBookmarkedState) {
                            val bookmarkToDelete = bookmarksList.firstOrNull { it.url == activeTab.url }
                            if (bookmarkToDelete != null) {
                                withContext(Dispatchers.IO) { rssDao.deleteBookmark(bookmarkToDelete) }
                            } else {
                                withContext(Dispatchers.IO) { rssDao.deleteBookmark(Bookmark(activeTab.url, activeTab.title)) }
                            }
                        } else {
                            val newBookmark = Bookmark(activeTab.url, activeTab.title)
                            withContext(Dispatchers.IO) { rssDao.insertBookmark(newBookmark) }
                        }
                        bookmarksList = withContext(Dispatchers.IO) { rssDao.getAllBookmarks() }
                    }
                }
            ) {
                Icon(
                    imageVector = if (isBookmarkedState) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = "Bookmark Page",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                singleLine = true,
                placeholder = { Text("Search or type URL", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = {
                    focusManager.clearFocus()
                    val input = urlInput.trim()
                    if (input.isNotEmpty()) {
                        val formattedUrl = if (input.contains(".") && !input.contains(" ")) {
                            if (input.startsWith("http://") || input.startsWith("https://")) input else "https://$input"
                        } else {
                            "https://www.google.com/search?q=${Uri.encode(input)}"
                        }
                        // Update active tab URL
                        tabsList = tabsList.mapIndexed { idx, tab ->
                            if (idx == activeTabIndex) tab.copy(url = formattedUrl) else tab
                        }
                        urlInput = formattedUrl
                        webView?.loadUrl(formattedUrl)
                    }
                }),
                shape = RoundedCornerShape(24.dp)
            )

            // Tabs manager button (displays open tabs count, e.g. [ 2 ])
            IconButton(
                onClick = { showTabsDialog = true }
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tabsList.size.toString(),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            IconButton(
                onClick = {
                    if (isLoading) webView?.stopLoading() else webView?.reload()
                }
            ) {
                Icon(
                    imageVector = if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
                    contentDescription = if (isLoading) "Stop" else "Reload",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Progress bar for page loads
        if (isLoading) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent
            )
        }

        // WebView view holder
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                isLoading = true
                                url?.let {
                                    urlInput = it
                                    // Update active tab URL
                                    tabsList = tabsList.mapIndexed { idx, tab ->
                                        if (idx == activeTabIndex) tab.copy(url = it) else tab
                                    }
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                canGoBack = view?.canGoBack() ?: false
                                canGoForward = view?.canGoForward() ?: false
                                url?.let {
                                    val title = view?.title ?: "Browser Tab"
                                    tabsList = tabsList.mapIndexed { idx, tab ->
                                        if (idx == activeTabIndex) tab.copy(url = it, title = title) else tab
                                    }
                                }
                                
                                // Inject all scripts
                                userScripts.forEach { script ->
                                    view?.evaluateJavascript(script, null)
                                }
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                            }
                        }
                        loadUrl(activeTab.url)
                    }
                },
                update = { view ->
                    webView = view
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Bottom Browser navigation controls bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { webView?.goBack() },
                    enabled = canGoBack
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = if (canGoBack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
                }

                IconButton(
                    onClick = { showBookmarksDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmarks,
                        contentDescription = "Bookmarks",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = {
                        val homeUrl = "https://www.google.com"
                        tabsList = tabsList.mapIndexed { idx, tab ->
                            if (idx == activeTabIndex) tab.copy(url = homeUrl, title = "Google") else tab
                        }
                        urlInput = homeUrl
                        webView?.loadUrl(homeUrl)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Home",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = { showScriptsDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Extension,
                        contentDescription = "UserScripts Extensions",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = { webView?.goForward() },
                    enabled = canGoForward
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Forward",
                        tint = if (canGoForward) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }

    // Tabs List Dialog
    if (showTabsDialog) {
        Dialog(onDismissRequest = { showTabsDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.7f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tabs Manager",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = {
                                tabsList = tabsList + BrowserTabState()
                                activeTabIndex = tabsList.lastIndex
                                showTabsDialog = false
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add New Tab", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(tabsList.size) { index ->
                            val tab = tabsList[index]
                            val isActive = index == activeTabIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                    .clickable {
                                        activeTabIndex = index
                                        showTabsDialog = false
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tab.title,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = tab.url,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                                if (tabsList.size > 1) {
                                    IconButton(
                                        onClick = {
                                            tabsList = tabsList.filterIndexed { i, _ -> i != index }
                                            if (activeTabIndex >= tabsList.size) {
                                                activeTabIndex = tabsList.lastIndex
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Close Tab", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = { showTabsDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }

    // Bookmarks List Dialog
    if (showBookmarksDialog) {
        Dialog(onDismissRequest = { showBookmarksDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.7f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Bookmarks",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (bookmarksList.isEmpty()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No bookmarks saved yet.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(bookmarksList) { bookmark ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .clickable {
                                        tabsList = tabsList.mapIndexed { idx, tab ->
                                            if (idx == activeTabIndex) tab.copy(url = bookmark.url, title = bookmark.title) else tab
                                        }
                                        urlInput = bookmark.url
                                        webView?.loadUrl(bookmark.url)
                                        showBookmarksDialog = false
                                    }
                                    .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bookmark,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = bookmark.title,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = bookmark.url,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                withContext(Dispatchers.IO) { rssDao.deleteBookmark(bookmark) }
                                                bookmarksList = withContext(Dispatchers.IO) { rssDao.getAllBookmarks() }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Bookmark", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = { showBookmarksDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }

    // Scripts/Extensions Dialog
    if (showScriptsDialog) {
        var newScriptText by remember { mutableStateOf("") }
        var showAddScriptView by remember { mutableStateOf(false) }
        
        Dialog(onDismissRequest = { showScriptsDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.7f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "UserScripts Extensions",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = { showAddScriptView = !showAddScriptView }
                        ) {
                            Icon(
                                imageVector = if (showAddScriptView) Icons.Default.ArrowBack else Icons.Default.Add,
                                contentDescription = "Toggle Add Script",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (showAddScriptView) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text("Add Javascript to inject on page load:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = newScriptText,
                                onValueChange = { newScriptText = it },
                                placeholder = { Text("e.g. document.body.style.backgroundColor = 'red';") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                maxLines = 8
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (newScriptText.isNotEmpty()) {
                                        userScripts = userScripts + newScriptText
                                        newScriptText = ""
                                        showAddScriptView = false
                                    }
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Add Script")
                            }
                        }
                    } else {
                        if (userScripts.isEmpty()) {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No user scripts registered. Click + to add.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(userScripts.size) { index ->
                                    val script = userScripts[index]
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Code,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = script,
                                            fontSize = 12.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        IconButton(
                                            onClick = {
                                                userScripts = userScripts.filterIndexed { i, _ -> i != index }
                                            }
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Script", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }

                        TextButton(
                            onClick = { showScriptsDialog = false },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}
