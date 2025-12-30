package com.streambox.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.streambox.app.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToExtensions: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Extensions section
            SettingsSection(title = "Content") {
                SettingsItem(
                    title = "Manage Extensions",
                    subtitle = "Add, remove, or update extensions",
                    icon = Icons.Default.Extension,
                    onClick = onNavigateToExtensions
                )
            }
            
            // Player section
            SettingsSection(title = "Player") {
                SettingsItem(
                    title = "Default Quality",
                    subtitle = "Auto",
                    icon = Icons.Default.HighQuality,
                    onClick = { /* TODO */ }
                )
                SettingsItem(
                    title = "Background Playback",
                    subtitle = "Continue playing when app is in background",
                    icon = Icons.Default.PlayCircle,
                    onClick = { /* TODO */ },
                    trailing = {
                        Switch(checked = false, onCheckedChange = { })
                    }
                )
                SettingsItem(
                    title = "Resume Playback",
                    subtitle = "Remember playback position",
                    icon = Icons.Default.History,
                    onClick = { /* TODO */ },
                    trailing = {
                        Switch(checked = true, onCheckedChange = { })
                    }
                )
            }
            
            // Data section
            SettingsSection(title = "Data") {
                SettingsItem(
                    title = "Clear Watch History",
                    subtitle = "Remove all watch history",
                    icon = Icons.Default.Delete,
                    onClick = { /* TODO */ }
                )
                SettingsItem(
                    title = "Clear Cache",
                    subtitle = "Clear cached images and data",
                    icon = Icons.Default.CleaningServices,
                    onClick = { /* TODO */ }
                )
            }
            
            // Debug section
            SettingsSection(title = "Developer") {
                var showDebugDialog by remember { mutableStateOf(false) }
                var debugOutput by remember { mutableStateOf("Tap 'Test Extension' to run a test") }
                var isLoading by remember { mutableStateOf(false) }
                
                SettingsItem(
                    title = "Debug Logs",
                    subtitle = "Test extension and view logs",
                    icon = Icons.Default.BugReport,
                    onClick = { showDebugDialog = true }
                )
                
                if (showDebugDialog) {
                    AlertDialog(
                        onDismissRequest = { showDebugDialog = false },
                        title = { Text("Debug Logs") },
                        text = {
                            Column {
                                Text(
                                    text = "Check Logcat for detailed logs:\nadb logcat -s JSRuntime:* ExtensionExecutor:* JS:*",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = debugOutput,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.heightIn(min = 100.dp, max = 300.dp)
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showDebugDialog = false }) {
                                Text("Close")
                            }
                        }
                    )
                }
            }
            
            // About section
            SettingsSection(title = "About") {
                SettingsItem(
                    title = "Version",
                    subtitle = BuildConfig.VERSION_NAME,
                    icon = Icons.Default.Info,
                    onClick = { }
                )
            }
            
            // Disclaimer
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Disclaimer",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "StreamBox is a media player application. It does not host, store, or distribute any media content. Users are responsible for adding third-party extensions and ensuring the legality of any content accessed through those extensions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Surface(
        onClick = onClick,
        color = androidx.compose.ui.graphics.Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            trailing?.invoke()
        }
    }
}
