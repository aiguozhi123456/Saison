package takagi.ru.saison.data.local.webdav

import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebDAV 路径管理器
 * 
 * 统一管理 WebDAV 相关的路径生成逻辑，提供:
 * - 备份目录路径
 * - 备份文件完整 URL
 * - 测试文件路径
 * 
 * 所有路径生成逻辑集中在这里，避免字符串拼接分散在代码各处
 */
@Singleton
class WebDavPathManager @Inject constructor() {
    
    companion object {
        /**
         * 备份目录名称
         */
        private const val BACKUP_DIR_NAME = "saison_backups"
        
        /**
         * 测试文件名
         */
        private const val TEST_FILE_NAME = ".saison_test"
    }
    
    /**
     * 获取备份目录路径
     * 
     * @param serverUrl 服务器基础 URL（会自动去除尾部斜杠）
     * @return 备份目录的完整路径
     * 
     * 示例:
     * - 输入: "https://webdav.example.com/dav"
     * - 输出: "https://webdav.example.com/dav/saison_backups"
     */
    fun getBackupDirPath(serverUrl: String): String {
        return "${serverUrl.trimEnd('/')}/$BACKUP_DIR_NAME"
    }
    
    /**
     * 获取备份文件的完整 URL
     * 
     * @param serverUrl 服务器基础 URL
     * @param fileName 备份文件名
     * @return 备份文件的完整 URL
     * 
     * 示例:
     * - 输入: serverUrl="https://webdav.example.com/dav", fileName="saison_backup_20241211_120000.zip"
     * - 输出: "https://webdav.example.com/dav/saison_backups/saison_backup_20241211_120000.zip"
     */
    fun getBackupFilePath(serverUrl: String, fileName: String): String {
        return "${getBackupDirPath(serverUrl)}/$fileName"
    }
    
    /**
     * 获取测试文件路径（用于测试写权限）
     * 
     * @param serverUrl 服务器基础 URL
     * @return 测试文件的完整 URL
     * 
     * 示例:
     * - 输入: "https://webdav.example.com/dav"
     * - 输出: "https://webdav.example.com/dav/saison_backups/.saison_test"
     */
    fun getTestFilePath(serverUrl: String): String {
        return "${getBackupDirPath(serverUrl)}/$TEST_FILE_NAME"
    }
    
    /**
     * 从完整 URL 中提取文件名
     * 
     * @param fullUrl 完整的文件 URL
     * @return 文件名
     * 
     * 示例:
     * - 输入: "https://webdav.example.com/dav/saison_backups/saison_backup_20241211_120000.zip"
     * - 输出: "saison_backup_20241211_120000.zip"
     */
    fun extractFileName(fullUrl: String): String {
        return fullUrl.substringAfterLast('/')
    }
    
    /**
     * 验证 URL 格式是否有效
     * 
     * @param url 待验证的 URL
     * @return true 如果 URL 格式有效
     */
    fun isValidUrl(url: String): Boolean {
        if (url.isBlank()) return false
        
        // 检查是否以 http:// 或 https:// 开头
        if (!url.startsWith("http://", ignoreCase = true) && 
            !url.startsWith("https://", ignoreCase = true)) {
            return false
        }
        
        // 简单的 URL 格式验证
        return try {
            java.net.URL(url)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 标准化服务器 URL
     * 
     * 去除尾部斜杠并验证格式
     * 
     * @param url 原始 URL
     * @return 标准化后的 URL
     * @throws IllegalArgumentException 如果 URL 格式无效
     */
    fun normalizeServerUrl(url: String): String {
        val trimmed = url.trim().trimEnd('/')
        if (!isValidUrl(trimmed)) {
            throw IllegalArgumentException("无效的服务器 URL: $url")
        }
        return trimmed
    }
    
    /**
     * 获取路径信息摘要（用于调试）
     * 
     * @param serverUrl 服务器 URL
     * @return 路径信息摘要
     */
    fun getPathSummary(serverUrl: String): String {
        return buildString {
            appendLine("WebDAV 路径信息:")
            appendLine("- 服务器 URL: $serverUrl")
            appendLine("- 备份目录: ${getBackupDirPath(serverUrl)}")
            appendLine("- 测试文件: ${getTestFilePath(serverUrl)}")
        }
    }
}
