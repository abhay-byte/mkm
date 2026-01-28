package com.ivarna.mkm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ivarna.mkm.navigation.Screen
import com.ivarna.mkm.navigation.navItems
import com.ivarna.mkm.ui.screens.CpuScreen
import com.ivarna.mkm.ui.screens.GpuScreen
import com.ivarna.mkm.ui.screens.HomeScreen
import com.ivarna.mkm.ui.screens.PowerScreen
import com.ivarna.mkm.ui.screens.RamScreen
import com.ivarna.mkm.ui.screens.OverlayScreen
import com.ivarna.mkm.ui.screens.StorageScreen
import com.ivarna.mkm.ui.screens.SettingsScreen
import com.ivarna.mkm.ui.theme.MKMTheme
import com.ivarna.mkm.ui.viewmodel.HomeViewModel
import com.ivarna.mkm.ui.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val homeViewModel: HomeViewModel = viewModel()
            val theme by settingsViewModel.theme.collectAsState()
            
            MKMTheme(appTheme = theme) {
                MainScreen(settingsViewModel, homeViewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(settingsViewModel: SettingsViewModel, homeViewModel: HomeViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val homeData by homeViewModel.uiState.collectAsState()
    
    val isAccessGranted = homeData?.overview?.let { it.isShizukuActive || it.isRootActive } ?: false
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentScreen = navItems.find { it.route == currentDestination?.route }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher),
                        contentDescription = "MKM Logo",
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "MKM",
                        style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                navItems.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    val isEnabled = isAccessGranted || screen == Screen.Home || screen == Screen.Settings || screen == Screen.Overlay
                    
                    NavigationDrawerItem(
                        label = { Text(screen.label) },
                        icon = {
                            Icon(
                                imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = null
                            )
                        },
                        selected = selected,
                        onClick = {
                            if (isEnabled) {
                                scope.launch { drawerState.close() }
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }
    ) {
        val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
        
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Screen.Home.route) { 
                HomeScreen(
                    viewModel = homeViewModel, 
                    onOpenDrawer = openDrawer
                ) 
            }
            composable(Screen.RAM.route) { RamScreen(onOpenDrawer = openDrawer) }
            composable(Screen.CPU.route) { CpuScreen(onOpenDrawer = openDrawer) }
            composable(Screen.GPU.route) { GpuScreen(onOpenDrawer = openDrawer) }
            composable(Screen.Storage.route) { StorageScreen(onOpenDrawer = openDrawer) }
            composable(Screen.Power.route) { PowerScreen(onOpenDrawer = openDrawer) }
            composable(Screen.Overlay.route) { OverlayScreen(onOpenDrawer = openDrawer) }
            composable(Screen.Settings.route) { 
                SettingsScreen(
                    viewModel = settingsViewModel, 
                    onOpenDrawer = openDrawer
                ) 
            }
        }
    }
}

@Composable
fun PlaceholderScreen(name: String) {
    Text(text = "$name Screen coming soon...", modifier = Modifier.padding(androidx.compose.foundation.layout.PaddingValues()))
}
