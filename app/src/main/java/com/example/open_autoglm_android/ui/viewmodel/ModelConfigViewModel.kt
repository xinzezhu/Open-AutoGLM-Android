package com.example.open_autoglm_android.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.open_autoglm_android.data.database.AppDatabase
import com.example.open_autoglm_android.data.database.ModelConfig
import com.example.open_autoglm_android.data.database.ModelConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 模型配置ViewModel，管理ModelConfigScreen的状态和逻辑
 */
class ModelConfigViewModel(application: Application) : AndroidViewModel(application) {
    // 数据库和仓库初始化
    private val database = AppDatabase.getDatabase(application)
    private val modelConfigRepository = ModelConfigRepository(database.modelConfigDao())

    // UI状态数据类
    data class ModelConfigUiState(
        val modelConfigs: List<ModelConfig> = emptyList(),
        val selectedModel: ModelConfig? = null,
        val isLoading: Boolean = false
    )

    // UI状态的MutableStateFlow
    private val _uiState = MutableStateFlow(ModelConfigUiState())
    val uiState = _uiState.asStateFlow()

    init {
        // 初始化默认模型配置
        initializeDefaultModels()
        // 加载所有模型配置
        loadModelConfigs()
    }
    
    /**
     * 初始化默认模型配置
     * 在首次启动时，添加预置的模型配置供用户使用
     */
    private fun initializeDefaultModels() {
        viewModelScope.launch {
            // 检查数据库是否为空，如果为空则添加默认模型
            val existingModels = modelConfigRepository.allModelConfigs
            existingModels.collect { models ->
                if (models.isEmpty()) {
                    // 添加智谱 AutoGLM 模型配置
                    modelConfigRepository.insertModelConfig(
                        ModelConfig(
                            name = "智谱 AutoGLM",
                            apiKey = "",
                            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
                            modelName = "autoglm-phone",
                            isSelected = false
                        )
                    )
                    
                    // 添加阿里云百炼 GUI-plus 模型配置
                    modelConfigRepository.insertModelConfig(
                        ModelConfig(
                            name = "阿里云百炼 GUI-plus",
                            apiKey = "",
                            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
                            modelName = "gui-plus",
                            isSelected = false
                        )
                    )
                }
                // 只检查一次，然后退出Flow收集
                return@collect
            }
        }
    }

    /**
     * 加载所有模型配置
     */
    private fun loadModelConfigs() {
        viewModelScope.launch {
            // 监听所有模型配置的变化
            modelConfigRepository.allModelConfigs.collect {
                _uiState.value = _uiState.value.copy(modelConfigs = it)
            }
        }

        // 监听当前选中模型的变化
        viewModelScope.launch {
            modelConfigRepository.selectedModelConfig.collect {
                _uiState.value = _uiState.value.copy(selectedModel = it)
            }
        }
    }

    /**
     * 插入新的模型配置
     */
    fun insertModelConfig(modelConfig: ModelConfig) {
        viewModelScope.launch {
            modelConfigRepository.insertModelConfig(modelConfig)
        }
    }

    /**
     * 更新模型配置
     */
    fun updateModelConfig(modelConfig: ModelConfig) {
        viewModelScope.launch {
            modelConfigRepository.updateModelConfig(modelConfig)
        }
    }

    /**
     * 删除模型配置
     */
    fun deleteModelConfig(modelConfig: ModelConfig) {
        viewModelScope.launch {
            modelConfigRepository.deleteModelConfig(modelConfig)
        }
    }

    /**
     * 选择指定ID的模型
     */
    fun selectModel(id: Long) {
        viewModelScope.launch {
            modelConfigRepository.selectModelById(id)
        }
    }

    /**
     * 获取当前选中的模型配置
     */
    fun getSelectedModel(): ModelConfig? {
        return _uiState.value.selectedModel
    }
}