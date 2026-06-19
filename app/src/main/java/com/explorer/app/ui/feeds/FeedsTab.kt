package com.explorer.app.ui.feeds

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.explorer.app.data.db.AppDatabase
import com.explorer.app.data.db.RssArticle
import com.explorer.app.data.db.RssSource
import com.explorer.app.data.mail.EmailClient
import com.explorer.app.data.mail.EmailMessage
import com.explorer.app.data.parser.RssParser
import com.explorer.app.ui.components.GlassmorphicCard
import com.explorer.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import androidx.compose.ui.layout.ContentScale

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.explorer.app.data.parser.OpmlParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedsTab() {
    var selectedSubTab by remember { mutableStateOf(0) } // 0 = RSS, 1 = Email
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val rssDao = db.rssDao()

    // RSS Feed States
    var rssArticles by remember { mutableStateOf<List<RssArticle>>(emptyList()) }
    var rssSources by remember { mutableStateOf<List<RssSource>>(emptyList()) }
    var activeCategoryFilter by remember { mutableStateOf<String?>(null) }
    var activeSourceFilter by remember { mutableStateOf<String?>(null) }
    var showFoldersBottomSheet by remember { mutableStateOf(false) }
    var showAddFeedDialog by remember { mutableStateOf(false) }
    var selectedArticle by remember { mutableStateOf<RssArticle?>(null) }
    var isRefreshingFeeds by remember { mutableStateOf(false) }

    // Import Launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                try {
                    val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().use { it.readText() }
                    }
                    if (content != null) {
                        val imported = OpmlParser.parseOpml(content)
                        if (imported.isNotEmpty()) {
                            withContext(Dispatchers.IO) {
                                imported.forEach { source ->
                                    rssDao.insertSource(source)
                                }
                                // Auto sync
                                val sources = rssDao.getAllSources()
                                sources.forEach { source ->
                                    try {
                                        val articles = RssParser.fetchFeed(source.url)
                                        rssDao.insertArticles(articles)
                                    } catch (e: Exception) {}
                                }
                                rssSources = sources
                            }
                            rssArticles = rssDao.getAllArticles()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Export Launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/xml")
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                try {
                    val sources = withContext(Dispatchers.IO) { rssDao.getAllSources() }
                    val opmlContent = OpmlParser.generateOpml(sources)
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            stream.bufferedWriter().use { it.write(opmlContent) }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Email States
    var emails by remember { mutableStateOf(EmailClient.mockEmails) }
    var imapHost by remember { mutableStateOf("") }
    var imapPort by remember { mutableStateOf("993") }
    var smtpHost by remember { mutableStateOf("") }
    var smtpPort by remember { mutableStateOf("587") }
    var emailUser by remember { mutableStateOf("") }
    var emailPass by remember { mutableStateOf("") }
    var isConfiguringMail by remember { mutableStateOf(false) }
    var selectedEmail by remember { mutableStateOf<EmailMessage?>(null) }
    var showComposeDialog by remember { mutableStateOf(false) }
    var isMailSyncing by remember { mutableStateOf(false) }
    var mailStatusMessage by remember { mutableStateOf("") }

    // Initial feed cache load and seed
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            var sources = rssDao.getAllSources()
            if (sources.isEmpty()) {
                // Seed default feeds with categories
                rssDao.insertSource(RssSource("https://news.ycombinator.com/rss", "Hacker News", "News"))
                rssDao.insertSource(RssSource("https://feeds.feedburner.com/TechCrunch/", "TechCrunch", "Tech"))
                sources = rssDao.getAllSources()
            }
            rssSources = sources
            rssArticles = rssDao.getAllArticles()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp)
    ) {
        // Tab Header selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { selectedSubTab = 0 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedSubTab == 0) NeonCyan else Color(0x11FFFFFF),
                    contentColor = if (selectedSubTab == 0) DeepSpaceBackground else TextPrimary
                ),
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.RssFeed, contentDescription = "Feeds", modifier = Modifier.padding(end = 4.dp))
                Text("Feeds")
            }
            Button(
                onClick = { selectedSubTab = 1 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedSubTab == 1) NeonPink else Color(0x11FFFFFF),
                    contentColor = if (selectedSubTab == 1) Color.White else TextPrimary
                ),
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Inbox, contentDescription = "Email", modifier = Modifier.padding(end = 4.dp))
                Text("Email")
            }
        }

        // Sub Tab views with crossfade transition
        AnimatedContent(
            targetState = selectedSubTab,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "SubTabs"
        ) { subTab ->
            when (subTab) {
                0 -> {
                    // RSS Section
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "LATEST NEWS",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { showFoldersBottomSheet = true }) {
                                Icon(Icons.Default.Folder, contentDescription = "Folders", tint = NeonCyan)
                            }
                            IconButton(onClick = { showAddFeedDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = "Add Feed", tint = NeonCyan)
                            }
                            IconButton(onClick = { importLauncher.launch("*/*") }) {
                                Icon(Icons.Default.FileDownload, contentDescription = "Import OPML", tint = NeonCyan)
                            }
                            IconButton(onClick = { exportLauncher.launch("feeds_export.opml") }) {
                                Icon(Icons.Default.FileUpload, contentDescription = "Export OPML", tint = NeonCyan)
                            }
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        isRefreshingFeeds = true
                                        val sources = rssDao.getAllSources()
                                        sources.forEach { source ->
                                            try {
                                                val articles = RssParser.fetchFeed(source.url)
                                                rssDao.insertArticles(articles)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                        rssArticles = rssDao.getAllArticles()
                                        isRefreshingFeeds = false
                                    }
                                },
                                enabled = !isRefreshingFeeds
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Sync Feeds",
                                    tint = if (isRefreshingFeeds) TextSecondary else NeonCyan
                                )
                            }
                        }

                        if (isRefreshingFeeds) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                color = NeonCyan
                            )
                        }

                        // Filter the RSS articles list
                        val filteredArticles = remember(rssArticles, activeCategoryFilter, activeSourceFilter, rssSources) {
                            when {
                                activeSourceFilter != null -> {
                                    rssArticles.filter { it.sourceUrl == activeSourceFilter }
                                }
                                activeCategoryFilter != null -> {
                                    val urls = rssSources.filter { it.category == activeCategoryFilter }.map { it.url }.toSet()
                                    rssArticles.filter { it.sourceUrl in urls }
                                }
                                else -> rssArticles
                            }
                        }

                        // Active Filter indicator
                        if (activeCategoryFilter != null || activeSourceFilter != null) {
                            val displayText = when {
                                activeCategoryFilter != null -> "Folder: $activeCategoryFilter"
                                activeSourceFilter != null -> {
                                    val title = rssSources.find { it.url == activeSourceFilter }?.title ?: "Feed"
                                    "Feed: $title"
                                }
                                else -> ""
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                InputChip(
                                    selected = true,
                                    onClick = {
                                        activeCategoryFilter = null
                                        activeSourceFilter = null
                                    },
                                    label = { Text(displayText) },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear filter",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    colors = InputChipDefaults.inputChipColors(
                                        selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                        selectedLabelColor = NeonCyan,
                                        selectedTrailingIconColor = NeonCyan
                                    )
                                )
                            }
                        }

                        if (filteredArticles.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                val emptyText = if (activeCategoryFilter != null || activeSourceFilter != null) {
                                    "No articles match this filter."
                                } else {
                                    "No articles cached. Tap refresh to sync."
                                }
                                Text(emptyText, color = TextSecondary)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(bottom = 80.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(filteredArticles) { article ->
                                    RssArticleCard(
                                        article = article,
                                        onClick = {
                                            selectedArticle = article
                                            coroutineScope.launch {
                                                rssDao.markAsRead(article.id)
                                                rssArticles = rssDao.getAllArticles()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Email Section
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "SECURE MAILBOX",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonPink,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { isConfiguringMail = !isConfiguringMail }) {
                                Icon(Icons.Default.Settings, contentDescription = "Mail Settings", tint = NeonPink)
                            }
                            IconButton(
                                onClick = {
                                    if (emailUser.isNotEmpty() && emailPass.isNotEmpty() && imapHost.isNotEmpty()) {
                                        coroutineScope.launch {
                                            isMailSyncing = true
                                            mailStatusMessage = "Syncing mail folders..."
                                            try {
                                                val fetched = EmailClient.fetchEmails(
                                                    host = imapHost,
                                                    port = imapPort,
                                                    user = emailUser,
                                                    pass = emailPass
                                                )
                                                emails = fetched
                                                mailStatusMessage = "Sync completed successfully."
                                            } catch (e: Exception) {
                                                mailStatusMessage = "Sync failed: ${e.localizedMessage}"
                                            } finally {
                                                isMailSyncing = false
                                            }
                                        }
                                    } else {
                                        mailStatusMessage = "Please configure IMAP settings first."
                                    }
                                },
                                enabled = !isMailSyncing
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Sync Email",
                                    tint = if (isMailSyncing) TextSecondary else NeonPink
                                )
                            }
                        }

                        if (mailStatusMessage.isNotEmpty()) {
                            Text(
                                text = mailStatusMessage,
                                color = if (mailStatusMessage.contains("failed", ignoreCase = true)) NeonPink else NeonCyan,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        // Collapsible settings configurations
                        if (isConfiguringMail) {
                            GlassmorphicCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                borderColors = listOf(GlassBorderPink, GlassBorderPurple)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("IMAP/SMTP SETTINGS", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NeonPink)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = emailUser,
                                        onValueChange = { emailUser = it },
                                        label = { Text("Email Address") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = emailPass,
                                        onValueChange = { emailPass = it },
                                        label = { Text("Password / App Token") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        OutlinedTextField(
                                            value = imapHost,
                                            onValueChange = { imapHost = it },
                                            label = { Text("IMAP Server") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f).padding(end = 4.dp)
                                        )
                                        OutlinedTextField(
                                            value = smtpHost,
                                            onValueChange = { smtpHost = it },
                                            label = { Text("SMTP Server") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f).padding(start = 4.dp)
                                        )
                                    }
                                    Button(
                                        onClick = { isConfiguringMail = false },
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonPink),
                                        modifier = Modifier.align(Alignment.End).padding(top = 8.dp)
                                    ) {
                                        Text("Save Settings")
                                    }
                                }
                            }
                        }

                        // Mail message listing
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(emails) { email ->
                                EmailCard(email = email, onClick = { selectedEmail = email })
                            }
                        }

                        // Floating compose FAB
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            FloatingActionButton(
                                onClick = { showComposeDialog = true },
                                containerColor = NeonPink,
                                modifier = Modifier.padding(bottom = 16.dp, end = 8.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Compose", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    // Add RSS Feed dialog
    if (showAddFeedDialog) {
        var newFeedUrl by remember { mutableStateOf("") }
        var newFeedTitle by remember { mutableStateOf("") }
        var newFeedCategory by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showAddFeedDialog = false }) {
            GlassmorphicCard(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ADD CUSTOM FEED", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newFeedTitle,
                        onValueChange = { newFeedTitle = it },
                        label = { Text("Feed Name (e.g. Wired)") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newFeedUrl,
                        onValueChange = { newFeedUrl = it },
                        label = { Text("Feed XML URL") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newFeedCategory,
                        onValueChange = { newFeedCategory = it },
                        label = { Text("Category / Folder (optional)") }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { showAddFeedDialog = false }) { Text("Cancel") }
                        Button(
                            onClick = {
                                if (newFeedUrl.isNotEmpty()) {
                                    coroutineScope.launch {
                                        val title = newFeedTitle.ifEmpty { "Custom Feed" }
                                        val cat = newFeedCategory.trim().ifEmpty { null }
                                        rssDao.insertSource(RssSource(newFeedUrl, title, cat))
                                        rssSources = rssDao.getAllSources()
                                        showAddFeedDialog = false
                                        // Auto-sync new feed
                                        try {
                                            val articles = RssParser.fetchFeed(newFeedUrl)
                                            rssDao.insertArticles(articles)
                                            rssArticles = rssDao.getAllArticles()
                                        } catch (e: Exception) {}
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                        ) {
                            Text("Add", color = DeepSpaceBackground)
                        }
                    }
                }
            }
        }
    }

    // Folders bottom sheet drawer for filtering feeds
    if (showFoldersBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFoldersBottomSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    "FILTER BY FOLDERS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = NeonCyan,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        // "All Feeds" item
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    activeCategoryFilter = null
                                    activeSourceFilter = null
                                    showFoldersBottomSheet = false
                                },
                            color = if (activeCategoryFilter == null && activeSourceFilter == null) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            } else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.List, contentDescription = null, tint = NeonCyan)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("All Feeds", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            }
                        }
                    }
                    
                    // Group sources by category
                    val categorized = rssSources.groupBy { it.category }
                    
                    categorized.forEach { (category, sources) ->
                        if (!category.isNullOrEmpty()) {
                            item {
                                // Folder Item
                                var isExpanded by remember { mutableStateOf(false) }
                                Column {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                activeCategoryFilter = if (activeCategoryFilter == category) null else category
                                                activeSourceFilter = null
                                                isExpanded = !isExpanded
                                            },
                                        color = if (activeCategoryFilter == category) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        } else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = NeonCyan
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                category,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextPrimary,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = { isExpanded = !isExpanded },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                    contentDescription = null,
                                                    tint = TextSecondary
                                                )
                                            }
                                        }
                                    }
                                    
                                    if (isExpanded) {
                                        Column(modifier = Modifier.padding(start = 24.dp, top = 4.dp)) {
                                            sources.forEach { source ->
                                                Surface(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            activeSourceFilter = source.url
                                                            activeCategoryFilter = null
                                                            showFoldersBottomSheet = false
                                                        },
                                                    color = if (activeSourceFilter == source.url) {
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                    } else Color.Transparent,
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(Icons.Default.RssFeed, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(source.title, fontSize = 13.sp, color = TextPrimary)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // Uncategorized feeds
                    val uncategorized = (categorized[null] ?: emptyList()) + (categorized[""] ?: emptyList())
                    if (uncategorized.isNotEmpty()) {
                        item {
                            Text("UNCATEGORIZED", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp))
                        }
                        items(uncategorized) { source ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        activeSourceFilter = source.url
                                        activeCategoryFilter = null
                                        showFoldersBottomSheet = false
                                    },
                                color = if (activeSourceFilter == source.url) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                } else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.RssFeed, contentDescription = null, tint = TextSecondary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(source.title, fontWeight = FontWeight.Medium, color = TextPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Read RSS Article full details sliding-in fullscreen overlay (Feeder style)
    selectedArticle?.let { article ->
        Dialog(
            onDismissRequest = { selectedArticle = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            var isVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                isVisible = true
            }

            val dismissReader = {
                coroutineScope.launch {
                    isVisible = false
                    delay(220) // wait for exit animation
                    selectedArticle = null
                }
            }

            androidx.activity.compose.BackHandler {
                dismissReader()
            }

            AnimatedVisibility(
                visible = isVisible,
                enter = slideInHorizontally(initialOffsetX = { it }),
                exit = slideOutHorizontally(targetOffsetX = { it }),
                modifier = Modifier.fillMaxSize()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(16.dp)
                    ) {
                        // Title/Action Bar
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(onClick = { dismissReader() }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NeonCyan)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ARTICLE READER",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                Text(article.title, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPrimary, lineHeight = 26.sp)
                            }
                            item {
                                Text(article.pubDate, fontSize = 12.sp, color = TextSecondary)
                            }
                            if (article.imageUrl != null) {
                                item {
                                    AsyncImage(
                                        model = article.imageUrl,
                                        contentDescription = "Image preview",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(220.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                            item {
                                Divider(color = TextSecondary.copy(alpha = 0.2f))
                            }
                            item {
                                Text(article.description, fontSize = 15.sp, color = TextPrimary, lineHeight = 22.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Read Email Message full details dialog
    selectedEmail?.let { email ->
        Dialog(onDismissRequest = { selectedEmail = null }) {
            GlassmorphicCard(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.8f)
                    .padding(8.dp),
                borderColors = listOf(GlassBorderPink, GlassBorderPurple)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(email.subject, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("From: ${email.sender} <${email.senderAddress}>", fontSize = 12.sp, color = NeonPink)
                    Text("Date: ${email.date}", fontSize = 11.sp, color = TextSecondary)
                    Divider(color = TextSecondary.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 12.dp))
                    
                    Box(modifier = Modifier.weight(1f)) {
                        LazyColumn {
                            item {
                                Text(email.body, fontSize = 14.sp, color = TextPrimary, lineHeight = 20.sp)
                            }
                        }
                    }
                    
                    Button(
                        onClick = { selectedEmail = null },
                        modifier = Modifier.align(Alignment.End),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPink)
                    ) {
                        Text("Close", color = Color.White)
                    }
                }
            }
        }
    }

    // SMTP Compose email dialog
    if (showComposeDialog) {
        var composeTo by remember { mutableStateOf("") }
        var composeSubject by remember { mutableStateOf("") }
        var composeBody by remember { mutableStateOf("") }
        var isSending by remember { mutableStateOf(false) }
        var sendError by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showComposeDialog = false }) {
            GlassmorphicCard(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.8f)
                    .padding(8.dp),
                borderColors = listOf(GlassBorderPink, GlassBorderPurple)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text("COMPOSE MAIL", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = NeonPink)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = composeTo,
                        onValueChange = { composeTo = it },
                        label = { Text("To") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = composeSubject,
                        onValueChange = { composeSubject = it },
                        label = { Text("Subject") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = composeBody,
                        onValueChange = { composeBody = it },
                        label = { Text("Message Body") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 8.dp)
                    )

                    if (sendError.isNotEmpty()) {
                        Text(sendError, color = NeonPink, fontSize = 11.sp)
                    }

                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = { showComposeDialog = false }) { Text("Discard") }
                        Button(
                            onClick = {
                                if (composeTo.isNotEmpty()) {
                                    if (emailUser.isNotEmpty() && emailPass.isNotEmpty() && smtpHost.isNotEmpty()) {
                                        coroutineScope.launch {
                                            isSending = true
                                            val success = EmailClient.sendEmail(
                                                host = smtpHost,
                                                port = smtpPort,
                                                user = emailUser,
                                                pass = emailPass,
                                                to = composeTo,
                                                subject = composeSubject,
                                                body = composeBody
                                            )
                                            isSending = false
                                            if (success) {
                                                showComposeDialog = false
                                            } else {
                                                sendError = "Send failed. Check SMTP configuration."
                                            }
                                        }
                                    } else {
                                        // Mock send fallback
                                        coroutineScope.launch {
                                            isSending = true
                                            kotlinx.coroutines.delay(1500)
                                            isSending = false
                                            // Add to local mock emails
                                            emails = listOf(
                                                EmailMessage(
                                                    id = "mock_${System.currentTimeMillis()}",
                                                    sender = "Me",
                                                    senderAddress = emailUser.ifEmpty { "me@explorer.app" },
                                                    recipient = composeTo,
                                                    subject = composeSubject,
                                                    date = "Just Now",
                                                    body = composeBody,
                                                    isRead = true
                                                )
                                            ) + emails
                                            showComposeDialog = false
                                        }
                                    }
                                }
                            },
                            enabled = !isSending,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPink)
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                            } else {
                                Text("Send", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RssArticleCard(article: RssArticle, onClick: () -> Unit) {
    GlassmorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            if (article.imageUrl != null) {
                AsyncImage(
                    model = article.imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .padding(end = 8.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = article.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = if (article.isRead) TextSecondary else TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = article.description,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = article.pubDate,
                    fontSize = 9.sp,
                    color = NeonCyan
                )
            }
        }
    }
}

@Composable
fun EmailCard(email: EmailMessage, onClick: () -> Unit) {
    GlassmorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        borderColors = listOf(GlassBorderPink, GlassBorderPurple)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = email.sender,
                    fontWeight = if (email.isRead) FontWeight.Normal else FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = email.date,
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = email.subject,
                fontWeight = if (email.isRead) FontWeight.Normal else FontWeight.Bold,
                fontSize = 13.sp,
                color = if (email.isRead) TextSecondary else NeonPink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = email.body,
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
