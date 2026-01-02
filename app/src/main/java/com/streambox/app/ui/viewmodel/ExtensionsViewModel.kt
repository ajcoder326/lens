package com.streambox.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambox.app.extension.ExtensionManager
import com.streambox.app.extension.model.Extension
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExtensionsViewModel @Inject constructor(
    private val extensionManager: ExtensionManager
) : ViewModel() {
    
    val extensions: StateFlow<List<Extension>> = extensionManager.extensions
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    fun addExtension(url: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                extensionManager.addExtension(url)
                    .onSuccess {
                        // Extension added successfully
                    }
                    .onFailure { e ->
                        _error.value = e.message ?: "Failed to add extension"
                    }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to add extension"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun removeExtension(extensionId: String) {
        viewModelScope.launch {
            try {
                extensionManager.removeExtension(extensionId)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to remove extension"
            }
        }
    }
    
    fun toggleExtension(extensionId: String) {
        viewModelScope.launch {
            try {
                extensionManager.toggleExtension(extensionId)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to toggle extension"
            }
        }
    }
    
    fun setActiveExtension(extensionId: String) {
        viewModelScope.launch {
            try {
                extensionManager.setActiveExtension(extensionId)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to set active extension"
            }
        }
    }
    
    // Expose active extension for UI
    val activeExtension: StateFlow<Extension?> = extensionManager.activeExtension
}
