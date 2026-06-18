package com.explorer.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.explorer.app.ui.browser.BrowserTab
import com.explorer.app.ui.components.GlassmorphicCard
import com.explorer.app.ui.feeds.FeedsTab
import com.explorer.app.ui.launcher.LauncherTab
import com.explorer.app.ui.media.MediaTab
import com.explorer.app.ui.theme.*
import com.explorer.app.ui.tools.ToolsTab
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permissions state if needed in production
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request launcher and utility permissions
        val permissionsToRequest = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        
        permissionLauncher.launch(permissionsToRequest.toTypedArray())

        setContent {
            TheExplorerAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DeepSpaceBackground
                ) {
                    MainNavigationContainer()
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainNavigationContainer() {
    val pagerState = rememberPagerState(pageCount = { 5 })
    val coroutineScope = rememberCoroutineScope()

    val navItems = listOf(
        NavigationItem("Home", Icons.Default.Home, NeonCyan),
        NavigationItem("Browser", Icons.Default.Language, NeonCyan),
        NavigationItem("Feeds", Icons.Default.RssFeed, NeonCyan),
        NavigationItem("Media", Icons.Default.PlayCircle, NeonCyan),
        NavigationItem("Tools", Icons.Default.Build, NeonCyan)
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Horizontal swipable pages container
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> LauncherTab()
                1 -> BrowserTab()
                2 -> FeedsTab()
                3 -> MediaTab()
                4 -> ToolsTab()
            }
        }

        // Translucent Glassmorphic Bottom Navigation Bar overlay
        GlassmorphicCard(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            shape = RoundedCornerShape(20.dp),
            borderWidth = 1.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                navItems.forEachIndexed { index, item ->
                    val isSelected = pagerState.currentPage == index
                    val activeColor = if (index == 2) NeonPink else NeonCyan // Feeds & Email gets Pink, others Cyan
                    
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = if (isSelected) activeColor else TextSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.label,
                                fontSize = 8.sp,
                                color = if (isSelected) activeColor else TextSecondary.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

data class NavigationItem(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color
)
