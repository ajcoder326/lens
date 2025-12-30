package com.streambox.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.streambox.app.extension.model.ContentLink
import com.streambox.app.extension.model.DirectLink
import com.streambox.app.ui.theme.Background
import com.streambox.app.ui.viewmodel.InfoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen(
    link: String,
    provider: String?,
    viewModel: InfoViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: (String, String?) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(link, provider) {
        viewModel.loadInfo(link, provider)
    }
    
    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadInfo(link, provider) }) {
                            Text("Retry")
                        }
                    }
                }
                uiState.info != null -> {
                    val info = uiState.info!!
                    
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        // Backdrop
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                            ) {
                                AsyncImage(
                                    model = info.background ?: info.poster ?: info.image,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Transparent, Background),
                                                startY = 100f
                                            )
                                        )
                                )
                            }
                        }
                        
                        // Info
                        item {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                // Title
                                Text(
                                    text = info.title,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Metadata row
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    info.rating?.let { rating ->
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = null,
                                            tint = Color(0xFFFFD700),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = rating,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = 0.8f)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                    }
                                    info.year?.let { year ->
                                        Text(
                                            text = year,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = 0.6f)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                    }
                                    Text(
                                        text = info.type.uppercase(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                // Tags
                                if (!info.tags.isNullOrEmpty()) {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(info.tags) { tag ->
                                            SuggestionChip(
                                                onClick = { },
                                                label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                                
                                // Synopsis
                                if (info.synopsis.isNotBlank()) {
                                    Text(
                                        text = info.synopsis,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                }
                                
                                // Play buttons / Links
                                if (info.linkList.isNotEmpty()) {
                                    Text(
                                        text = "Available Sources",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }
                        }
                        
                        // Link items
                        items(info.linkList) { contentLink ->
                            LinkItem(
                                contentLink = contentLink,
                                onPlayClick = { streamLink, title ->
                                    // Navigate to player with stream extraction
                                    onNavigateToPlayer(streamLink, title ?: info.title)
                                }
                            )
                        }
                        
                        item { Spacer(modifier = Modifier.height(32.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
fun LinkItem(
    contentLink: ContentLink,
    onPlayClick: (String, String?) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = contentLink.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White
                )
                contentLink.quality?.let { quality ->
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = quality,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            
            // Direct links if available
            contentLink.directLinks?.let { links ->
                Spacer(modifier = Modifier.height(8.dp))
                links.forEach { directLink ->
                    TextButton(
                        onClick = { onPlayClick(directLink.link, directLink.title) }
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(directLink.title)
                    }
                }
            }
            
            // Episodes link
            contentLink.episodesLink?.let { epLink ->
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onPlayClick(epLink, contentLink.title) }
                ) {
                    Icon(Icons.Default.List, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Episodes")
                }
            }
        }
    }
}
