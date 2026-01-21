package com.ivarna.mkm.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivarna.mkm.ui.components.*
import com.ivarna.mkm.ui.viewmodel.AppTheme
import com.ivarna.mkm.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val theme by viewModel.theme.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    
    var showThemeDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = 32.dp,
                start = 16.dp,
                end = 16.dp
            )
        ) {
            item {
                AppInfoCard(
                    appName = "Minimal Kernel Manager",
                    version = "v1.0.0",
                    buildDate = "Jan 21, 2026"
                )
            }

            item {
                SettingsSection(title = "Appearance") {
                    SettingsItem(
                        icon = Icons.Default.Palette,
                        title = "Theme",
                        subtitle = when(theme) {
                            AppTheme.SYSTEM -> "System Default"
                            AppTheme.DYNAMIC -> "Dynamic (Material You)"
                            AppTheme.LIGHT -> "Light"
                            AppTheme.DARK -> "Dark"
                            AppTheme.AMOLED -> "Black (AMOLED)"
                        },
                        onClick = { showThemeDialog = true }
                    )
                }
            }

            item {
                SettingsSection(title = "Special Thanks") {
                    SettingsItem(
                        icon = Icons.Default.Favorite,
                        title = "CPU Info",
                        subtitle = "by kamgurgul",
                        onClick = { /* Open link */ }
                    )
                    SettingsItem(
                        icon = Icons.Default.Favorite,
                        title = "SmartPack Kernel Manager",
                        subtitle = "by SmartPack",
                        onClick = { /* Open link */ }
                    )
                    SettingsItem(
                        icon = Icons.Default.Favorite,
                        title = "Wattz",
                        subtitle = "by dubrowgn",
                        onClick = { /* Open link */ }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                AboutMeCard(
                    name = "Abhay Raj",
                    handle = "@abhay-byte",
                    bio = "Passionate about building software, exploring hardware, and all things Linux."
                )
            }

            item {
                SettingsSection(title = "Connect With Me") {
                    SocialItem(
                        icon = Icons.Default.Code,
                        label = "GitHub",
                        description = "View my repositories and projects",
                        onClick = { /* Open GitHub */ }
                    )
                    SocialItem(
                        icon = Icons.Default.Group,
                        label = "LinkedIn",
                        description = "Let's connect professionally",
                        onClick = { /* Open LinkedIn */ }
                    )
                    SocialItem(
                        icon = Icons.Default.Language,
                        label = "Portfolio",
                        description = "Check out my work",
                        onClick = { /* Open Portfolio */ }
                    )
                    SocialItem(
                        icon = Icons.Default.CameraAlt,
                        label = "Instagram",
                        description = "Follow my journey",
                        onClick = { /* Open Instagram */ }
                    )
                    SocialItem(
                        icon = Icons.Default.Public,
                        label = "X (Twitter)",
                        description = "Stay updated with me",
                        onClick = { /* Open Twitter */ }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    ListItem(
                        headlineContent = { Text("Star on GitHub", fontWeight = FontWeight.Bold) },
                        supportingContent = { Text("MKM - If you like this project, please give it a star!") },
                        leadingContent = { 
                            Icon(Icons.Default.Star, contentDescription = null) 
                        },
                        modifier = Modifier.padding(8.dp),
                        colors = ListItemDefaults.colors(
                            containerColor = androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                }
            }
        }
    }

    if (showThemeDialog) {
        SelectionBottomSheet(
            title = "App Theme",
            items = AppTheme.values().map { it.name },
            selectedItem = theme.name,
            onDismiss = { showThemeDialog = false },
            onItemSelected = {
                viewModel.setTheme(AppTheme.valueOf(it))
                showThemeDialog = false
            },
            itemLabel = {
                when(AppTheme.valueOf(it)) {
                    AppTheme.SYSTEM -> "System Default"
                    AppTheme.DYNAMIC -> "Dynamic (Material You)"
                    AppTheme.LIGHT -> "Light"
                    AppTheme.DARK -> "Dark"
                    AppTheme.AMOLED -> "Black (AMOLED)"
                }
            }
        )
    }
}
