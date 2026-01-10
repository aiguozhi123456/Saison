package takagi.ru.saison.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import takagi.ru.saison.R
import takagi.ru.saison.data.local.datastore.PreferencesManager
import takagi.ru.saison.data.repository.TaskRepository
import takagi.ru.saison.domain.model.Task
import takagi.ru.saison.ui.theme.SaisonTheme
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named

@AndroidEntryPoint
class FloatingWindowService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "floating_window_channel"
        
        const val ACTION_SHOW = "takagi.ru.saison.FloatingWindow.SHOW"
        const val ACTION_HIDE = "takagi.ru.saison.FloatingWindow.HIDE"
        
        fun startService(context: android.content.Context) {
            val intent = Intent(context, FloatingWindowService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
        
        fun stopService(context: android.content.Context) {
            val intent = Intent(context, FloatingWindowService::class.java)
            context.stopService(intent)
        }
    }

    @Inject
    lateinit var taskRepository: TaskRepository
    
    @Inject
    lateinit var preferencesManager: PreferencesManager

    @Inject
    @Named("applicationContext")
    lateinit var appContext: android.content.Context

    private lateinit var windowManager: WindowManager
    private var floatingView: ComposeView? = null
    private var isFloating = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showFloatingWindow()
            ACTION_HIDE -> hideFloatingWindow()
            else -> showFloatingWindow()
        }
        return START_STICKY
    }

    private fun showFloatingWindow() {
        if (isFloating) return
        
        try {
            floatingView = ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    FloatingWindowContent(
                        taskRepository = taskRepository,
                        onClose = { hideFloatingWindow() },
                        onNavigateToApp = { openMainApp() }
                    )
                }
            }

            val params = WindowManager.LayoutParams(
                400,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 100
                y = 200
            }

            windowManager.addView(floatingView, params)
            isFloating = true
        } catch (e: Exception) {
            android.util.Log.e("FloatingWindowService", "Failed to show floating window", e)
            stopSelf()
        }
    }

    private fun hideFloatingWindow() {
        try {
            floatingView?.let {
                windowManager.removeView(it)
            }
            floatingView = null
            isFloating = false
        } catch (e: Exception) {
            android.util.Log.e("FloatingWindowService", "Failed to hide floating window", e)
        }
    }

    private fun openMainApp() {
        val intent = Intent(this, takagi.ru.saison.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        hideFloatingWindow()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮窗通知",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持悬浮窗运行的通知"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Saison 悬浮窗")
            .setContentText("任务管理悬浮窗正在运行")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        hideFloatingWindow()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@Composable
fun FloatingWindowContent(
    taskRepository: TaskRepository,
    onClose: () -> Unit,
    onNavigateToApp: () -> Unit
) {
    var tasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newTaskTitle by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val themeViewModel: takagi.ru.saison.ui.theme.ThemeViewModel = viewModel()
    val currentTheme by themeViewModel.currentTheme.collectAsStateWithLifecycle()
    val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
    val useDynamicColor by themeViewModel.useDynamicColor.collectAsStateWithLifecycle()
    
    val systemInDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val timeOfDayConfig = if (themeMode == takagi.ru.saison.data.local.datastore.ThemeMode.AUTO_TIME) {
        takagi.ru.saison.util.TimeOfDayHelper.getCurrentConfig()
    } else {
        null
    }
    
    val actualTheme = timeOfDayConfig?.theme ?: currentTheme
    val darkTheme = when (themeMode) {
        takagi.ru.saison.data.local.datastore.ThemeMode.FOLLOW_SYSTEM -> systemInDarkTheme
        takagi.ru.saison.data.local.datastore.ThemeMode.LIGHT -> false
        takagi.ru.saison.data.local.datastore.ThemeMode.DARK -> true
        takagi.ru.saison.data.local.datastore.ThemeMode.AUTO_TIME -> timeOfDayConfig?.isDark ?: false
    }

    LaunchedEffect(Unit) {
        taskRepository.getIncompleteTasks().collect { taskList ->
            tasks = taskList.take(5)
        }
    }

    SaisonTheme(
        seasonalTheme = actualTheme,
        darkTheme = darkTheme,
        dynamicColor = useDynamicColor
    ) {
        Card(
            modifier = Modifier.width(360.dp).padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("待办任务", style = MaterialTheme.typography.titleMedium)
                    Row {
                        IconButton(onClick = { showAddDialog = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Add, "添加", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, "关闭")
                        }
                    }
                }
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (tasks.isEmpty()) {
                        item {
                            Text("暂无待办任务", modifier = Modifier.padding(vertical = 24.dp))
                        }
                    } else {
                        items(tasks) { task ->
                            TaskItem(
                                task = task,
                                onToggleComplete = {
                                    coroutineScope.launch {
                                        taskRepository.toggleTaskCompletion(task.id, !task.isCompleted)
                                    }
                                },
                                onDelete = {
                                    coroutineScope.launch {
                                        taskRepository.deleteTask(task.id)
                                    }
                                },
                                onClick = onNavigateToApp
                            )
                        }
                    }
                }
                TextButton(onClick = onNavigateToApp, modifier = Modifier.fillMaxWidth()) {
                    Text("打开应用查看更多", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    if (showAddDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showAddDialog = false }) {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("快速添加任务", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newTaskTitle,
                        onValueChange = { newTaskTitle = it },
                        placeholder = { Text("任务标题") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAddDialog = false }) { Text("取消") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newTaskTitle.isNotBlank()) {
                                    coroutineScope.launch {
                                        val now = System.currentTimeMillis()
                                        val newTask = Task(
                                            title = newTaskTitle,
                                            createdAt = java.time.LocalDateTime.ofInstant(
                                                java.time.Instant.ofEpochMilli(now),
                                                java.time.ZoneId.systemDefault()
                                            ),
                                            updatedAt = java.time.LocalDateTime.ofInstant(
                                                java.time.Instant.ofEpochMilli(now),
                                                java.time.ZoneId.systemDefault()
                                            )
                                        )
                                        taskRepository.insertTask(newTask)
                                        newTaskTitle = ""
                                        showAddDialog = false
                                    }
                                }
                            },
                            enabled = newTaskTitle.isNotBlank()
                        ) {
                            Text("添加")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TaskItem(
    task: Task,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggleComplete() },
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (task.dueDate != null) {
                    val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.getDefault())
                    Text(
                        text = task.dueDate.format(formatter),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            }
        }
    }
}
