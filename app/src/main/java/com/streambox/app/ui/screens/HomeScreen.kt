package com.streambox.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.streambox.app.ui.components.ContentSlider
import com.streambox.app.ui.components.HeroSection
import com.streambox.app.ui.theme.Background
import com.streambox.app.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToInfo: (String, String?) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToExtensions: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val activeExtension by viewModel.activeExtension.collectAsState()
    val heroPost by viewModel.heroPost.collectAsState()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        when {
            activeExtension == null -> {
                NoExtensionScreen(onAddExtension = onNavigateToExtensions)
            }
            uiState.isLoading && uiState.catalogs.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            uiState.error != null && uiState.catalogs.isEmpty() -> {
                ErrorScreen(
                    error = uiState.error!!,
                    onRetry = { viewModel.refresh() }
                )
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        HeroSection(
                            post = heroPost,
                            metadata = null,
                            isLoading = uiState.isLoadingHero,
                            onPlayClick = {
                                heroPost?.let { post ->
                                    onNavigateToInfo(post.link, activeExtension?.id)
                                }
                            },
                            onInfoClick = {
                                heroPost?.let { post ->
                                    onNavigateToInfo(post.link, activeExtension?.id)
                                }
                            }
                        )
                    }
                    
                    items(uiState.catalogs.size) { index ->
                        val catalog = uiState.catalogs[index]
                        val posts = uiState.catalogPosts[catalog.filter] ?: emptyList()
                        
                        ContentSlider(
                            title = catalog.title,
                            posts = posts,
                            isLoading = posts.isEmpty() && uiState.isLoading,
                            onPostClick = { post ->
                                onNavigateToInfo(post.link, activeExtension?.id)
                            },
                            onMoreClick = { }
                        )
                    }
                    
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
        
        TopAppBar(
            modifier = Modifier.align(Alignment.TopCenter),
            title = { 
                Text(
                    text = activeExtension?.name ?: "StreamBox",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            actions = {
                IconButton(onClick = onNavigateToSearch) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
                IconButton(onClick = onNavigateToExtensions) {
                    Icon(Icons.Default.Extension, contentDescription = "Extensions")
                }
                IconButton(onClick = onNavigateToSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = Color.White,
                actionIconContentColor = Color.White
            )
        )
    }
}

@Composable
fun NoExtensionScreen(onAddExtension: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Extension,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Welcome to StreamBox",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Add an extension to get started.\nExtensions provide content from various sources.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onAddExtension,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Extension, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Extension")
        }
        Spacer(modifier = Modifier.height(48.dp))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Text(
                text = "⚠️ Extensions are third-party add-ons. You are responsible for ensuring the legality of content accessed through installed extensions.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ErrorScreen(error: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}
