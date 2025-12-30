package com.streambox.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambox.app.extension.ExtensionManager
import com.streambox.app.extension.model.ContentInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InfoUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val info: ContentInfo? = null
)

@HiltViewModel
class InfoViewModel @Inject constructor(
    private val extensionManager: ExtensionManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(InfoUiState())
    val uiState: StateFlow<InfoUiState> = _uiState.asStateFlow()
    
    fun loadInfo(link: String, providerId: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                val extensionId = providerId ?: extensionManager.activeExtension.value?.id
                if (extensionId == null) {
                    _uiState.update { 
                        it.copy(isLoading = false, error = "No extension available") 
                    }
                    return@launch
                }
                
                val info = extensionManager.getMetadata(extensionId, link)
                if (info != null) {
                    _uiState.update { 
                        it.copy(isLoading = false, info = info) 
                    }
                } else {
                    _uiState.update { 
                        it.copy(isLoading = false, error = "Failed to load content info") 
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(isLoading = false, error = e.message ?: "Failed to load info") 
                }
            }
        }
    }
}
