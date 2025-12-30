package com.streambox.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambox.app.extension.ExtensionManager
import com.streambox.app.extension.model.Post
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val results: List<Post> = emptyList(),
    val hasSearched: Boolean = false
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val extensionManager: ExtensionManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private var searchJob: Job? = null
    
    fun setQuery(query: String) {
        _searchQuery.value = query
    }
    
    fun search() {
        val query = _searchQuery.value.trim()
        if (query.isEmpty()) return
        
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                val activeExtension = extensionManager.activeExtension.value
                if (activeExtension == null) {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = "No active extension",
                            hasSearched = true
                        )
                    }
                    return@launch
                }
                
                val results = extensionManager.searchPosts(activeExtension.id, query, 1)
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        results = results,
                        hasSearched = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Search failed",
                        hasSearched = true
                    )
                }
            }
        }
    }
}
