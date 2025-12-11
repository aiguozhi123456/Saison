package takagi.ru.saison.ui.screens.settings.database

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import takagi.ru.saison.data.local.database.DatabaseBackupInfo
import takagi.ru.saison.data.local.database.DatabaseMigrationHelper
import java.util.Date
import javax.inject.Inject

data class DatabaseManagementUiState(
    val backups: List<DatabaseBackupInfo> = emptyList(),
    val databaseSize: Long = 0,
    val isCreatingBackup: Boolean = false,
    val isRestoringBackup: Boolean = false,
    val isDeletingBackup: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val showRestoreConfirmation: DatabaseBackupInfo? = null
)

@HiltViewModel
class DatabaseManagementViewModel @Inject constructor(
    private val migrationHelper: DatabaseMigrationHelper
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(DatabaseManagementUiState())
    val uiState: StateFlow<DatabaseManagementUiState> = _uiState.asStateFlow()
    
    init {
        loadBackups()
    }
    
    fun loadBackups() {
        viewModelScope.launch {
            try {
                val backups = migrationHelper.listBackups()
                val dbSize = migrationHelper.getDatabaseSize()
                
                _uiState.update {
                    it.copy(
                        backups = backups,
                        databaseSize = dbSize,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "加载备份列表失败: ${e.message}")
                }
            }
        }
    }
    
    fun createManualBackup() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingBackup = true, error = null) }
            
            migrationHelper.createDatabaseBackup("manual").fold(
                onSuccess = { backupFile ->
                    _uiState.update {
                        it.copy(
                            isCreatingBackup = false,
                            successMessage = "备份创建成功: ${backupFile.name}"
                        )
                    }
                    loadBackups()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isCreatingBackup = false,
                            error = "创建备份失败: ${error.message}"
                        )
                    }
                }
            )
        }
    }
    
    fun showRestoreConfirmation(backup: DatabaseBackupInfo) {
        _uiState.update {
            it.copy(showRestoreConfirmation = backup)
        }
    }
    
    fun hideRestoreConfirmation() {
        _uiState.update {
            it.copy(showRestoreConfirmation = null)
        }
    }
    
    fun restoreBackup(backup: DatabaseBackupInfo) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRestoringBackup = true,
                    error = null,
                    showRestoreConfirmation = null
                )
            }
            
            migrationHelper.restoreDatabaseBackup(backup.file).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isRestoringBackup = false,
                            successMessage = "数据库恢复成功！请重启应用以使更改生效。"
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isRestoringBackup = false,
                            error = "恢复数据库失败: ${error.message}"
                        )
                    }
                }
            )
        }
    }
    
    fun deleteBackup(backup: DatabaseBackupInfo) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeletingBackup = true, error = null) }
            
            val success = migrationHelper.deleteBackup(backup)
            
            if (success) {
                _uiState.update {
                    it.copy(
                        isDeletingBackup = false,
                        successMessage = "备份已删除"
                    )
                }
                loadBackups()
            } else {
                _uiState.update {
                    it.copy(
                        isDeletingBackup = false,
                        error = "删除备份失败"
                    )
                }
            }
        }
    }
    
    fun clearMessages() {
        _uiState.update {
            it.copy(error = null, successMessage = null)
        }
    }
    
    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
    
    fun formatDate(timestamp: Long): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(Date(timestamp))
    }
}
