package com.streambox.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambox.app.extension.ExtensionManager
import com.streambox.app.extension.model.CatalogItem
import com.streambox.app.extension.model.Extension
import com.streambox.app.extension.model.Post
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = false,
    val isLoadingHero: Boolean = false,
    val error: String? = null,
    val catalogs: List<CatalogItem> = emptyList(),
    val catalogPosts: Map<String, List<Post>> = emptyMap()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val extensionManager: ExtensionManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    val activeExtension: StateFlow<Extension?> = extensionManager.activeExtension
    
    private val _heroPost = MutableStateFlow<Post?>(null)
    val heroPost: StateFlow<Post?> = _heroPost.asStateFlow()
    
    init {
        // Watch for active extension changes
        viewModelScope.launch {
            extensionManager.activeExtension.collect { extension ->
                if (extension != null) {
                    loadContent(extension)
                }
            }
        }
    }
    
    fun refresh() {
        viewModelScope.launch {
            activeExtension.value?.let { loadContent(it) }
        }
    }
    
    private suspend fun loadContent(extension: Extension) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        
        try {
            // Load catalog
            val catalogs = extensionManager.getCatalog(extension.id)
            _uiState.update { it.copy(catalogs = catalogs) }
            
            // Load posts for each catalog
            val catalogPosts = mutableMapOf<String, List<Post>>()
            catalogs.take(5).forEach { catalog ->
                try {
                    val posts = extensionManager.getPosts(extension.id, catalog.filter, 1)
                    catalogPosts[catalog.filter] = posts
                    
                    // Set first post as hero
                    if (_heroPost.value == null && posts.isNotEmpty()) {
                        _heroPost.value = posts.first()
                    }
                } catch (e: Exception) {
                    // Continue loading other catalogs
                }
            }
            
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    catalogPosts = catalogPosts
                )
            }
        } catch (e: Exception) {
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load content"
                )
            }
        }
    }
}
