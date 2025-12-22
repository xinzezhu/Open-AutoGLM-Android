package com.example.open_autoglm_android.ui.viewmodel

import android.app.Application
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.open_autoglm_android.data.InputMode
import com.example.open_autoglm_android.data.PreferencesRepository
import com.example.open_autoglm_android.service.FloatingWindowService
import com.example.open_autoglm_android.util.AccessibilityServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val apiKey: String = "",
    val baseUrl: String = "https://open.bigmodel.cn/api/paas/v4",
    val modelName: String = "autoglm-phone",
    val isAccessibilityEnabled: Boolean = false,
    val floatingWindowEnabled: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val inputMode: InputMode = InputMode.SET_TEXT,
    val isImeEnabled: Boolean = false,
    val isImeSelected: Boolean = false,
    val imageCompressionEnabled: Boolean = false,
    val imageCompressionLevel: Int = 50,
    val screenScaleEnabled: Boolean = false,
    val screenScaleFactor: Float = 0.5f,
    val isLoading: Boolean = false,
    val saveSuccess: Boolean? = null,
    val error: String? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val preferencesRepository = PreferencesRepository(application)
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    init {
        loadSettings()
        checkAccessibilityService()
        checkOverlayPermission()
        checkImeStatus()
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            preferencesRepository.apiKey.collect { apiKey ->
                _uiState.value = _uiState.value.copy(apiKey = apiKey ?: "")
            }
        }
        viewModelScope.launch {
            preferencesRepository.baseUrl.collect { baseUrl ->
                _uiState.value = _uiState.value.copy(baseUrl = baseUrl ?: "https://open.bigmodel.cn/api/paas/v4")
            }
        }
        viewModelScope.launch {
            preferencesRepository.modelName.collect { modelName ->
                _uiState.value = _uiState.value.copy(modelName = modelName ?: "autoglm-phone")
            }
        }
        viewModelScope.launch {
            preferencesRepository.floatingWindowEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(floatingWindowEnabled = enabled)
                updateFloatingWindowService(enabled)
            }
        }
        viewModelScope.launch {
            preferencesRepository.inputMode.collect { mode ->
                _uiState.value = _uiState.value.copy(inputMode = mode)
            }
        }
        viewModelScope.launch {
            preferencesRepository.imageCompressionEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(imageCompressionEnabled = enabled)
            }
        }
        viewModelScope.launch {
            preferencesRepository.imageCompressionLevel.collect { level ->
                _uiState.value = _uiState.value.copy(imageCompressionLevel = level)
            }
        }
        viewModelScope.launch {
            preferencesRepository.screenScaleEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(screenScaleEnabled = enabled)
            }
        }
        viewModelScope.launch {
            preferencesRepository.screenScaleFactor.collect { factor ->
                _uiState.value = _uiState.value.copy(screenScaleFactor = factor)
            }
        }
    }
    
    fun checkAccessibilityService() {
        val enabledInSettings = AccessibilityServiceHelper.isAccessibilityServiceEnabled(getApplication())
        val serviceRunning = AccessibilityServiceHelper.isServiceRunning()
        val enabled = enabledInSettings && serviceRunning
        _uiState.value = _uiState.value.copy(isAccessibilityEnabled = enabled)
    }
    
    fun checkOverlayPermission() {
        val hasPermission = FloatingWindowService.hasOverlayPermission(getApplication())
        _uiState.value = _uiState.value.copy(hasOverlayPermission = hasPermission)
    }

    fun checkImeStatus() {
        val context = getApplication<Application>()
        val imm = context.getSystemService(Application.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabledMethods = imm.enabledInputMethodList
        val myPackageName = context.packageName
        
        val isEnabled = enabledMethods.any { it.packageName == myPackageName }
        val currentIme = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val isSelected = currentIme?.contains(myPackageName) == true
        
        _uiState.value = _uiState.value.copy(
            isImeEnabled = isEnabled,
            isImeSelected = isSelected
        )
    }
    
    fun setFloatingWindowEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.saveFloatingWindowEnabled(enabled)
            _uiState.value = _uiState.value.copy(floatingWindowEnabled = enabled)
            updateFloatingWindowService(enabled)
        }
    }

    fun setInputMode(mode: InputMode) {
        viewModelScope.launch {
            preferencesRepository.saveInputMode(mode)
            _uiState.value = _uiState.value.copy(inputMode = mode)
        }
    }

    fun setImageCompressionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.saveImageCompressionEnabled(enabled)
            _uiState.value = _uiState.value.copy(imageCompressionEnabled = enabled)
        }
    }

    fun setImageCompressionLevel(level: Int) {
        viewModelScope.launch {
            preferencesRepository.saveImageCompressionLevel(level)
            _uiState.value = _uiState.value.copy(imageCompressionLevel = level)
        }
    }

    fun setScreenScaleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.saveScreenScaleEnabled(enabled)
            _uiState.value = _uiState.value.copy(screenScaleEnabled = enabled)
        }
    }

    fun setScreenScaleFactor(factor: Float) {
        viewModelScope.launch {
            preferencesRepository.saveScreenScaleFactor(factor)
            _uiState.value = _uiState.value.copy(screenScaleFactor = factor)
        }
    }
    
    private fun updateFloatingWindowService(enabled: Boolean) {
        val context = getApplication<Application>()
        if (enabled && FloatingWindowService.hasOverlayPermission(context)) {
            FloatingWindowService.startService(context)
        } else {
            FloatingWindowService.stopService(context)
        }
    }
    
    fun updateApiKey(apiKey: String) {
        _uiState.value = _uiState.value.copy(apiKey = apiKey)
    }
    
    fun updateBaseUrl(baseUrl: String) {
        _uiState.value = _uiState.value.copy(baseUrl = baseUrl)
    }
    
    fun updateModelName(modelName: String) {
        _uiState.value = _uiState.value.copy(modelName = modelName)
    }
    
    fun saveSettings() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, saveSuccess = false)
            try {
                preferencesRepository.saveApiKey(_uiState.value.apiKey)
                preferencesRepository.saveBaseUrl(_uiState.value.baseUrl)
                preferencesRepository.saveModelName(_uiState.value.modelName)
                _uiState.value = _uiState.value.copy(isLoading = false, saveSuccess = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "保存失败: ${e.message}")
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null, saveSuccess = false)
    }
}
