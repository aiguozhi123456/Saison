package takagi.ru.saison.ui.screens.pomodoro

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import takagi.ru.saison.R
import takagi.ru.saison.data.local.datastore.PreferencesManager
import takagi.ru.saison.data.repository.PomodoroRepository
import takagi.ru.saison.data.repository.RoutineRepository
import takagi.ru.saison.data.repository.TaskRepository
import takagi.ru.saison.domain.model.PomodoroSession
import takagi.ru.saison.domain.model.routine.RoutineTask
import takagi.ru.saison.service.PomodoroEvent
import takagi.ru.saison.service.PomodoroEventBus
import takagi.ru.saison.service.PomodoroTimerService
import takagi.ru.saison.util.PomodoroNotificationManager
import takagi.ru.saison.util.VibrationManager
import javax.inject.Inject

@HiltViewModel
class PomodoroViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pomodoroRepository: PomodoroRepository,
    private val routineRepository: RoutineRepository,
    private val taskRepository: TaskRepository,
    private val preferencesManager: PreferencesManager,
    private val notificationManager: PomodoroNotificationManager,
    private val vibrationManager: VibrationManager,
    private val saisonNotificationManager: takagi.ru.saison.notification.SaisonNotificationManager
) : ViewModel() {
    
    // UI State
    private val _uiState = MutableStateFlow(PomodoroUiState())
    val uiState: StateFlow<PomodoroUiState> = _uiState.asStateFlow()
    
    private var timerJob: Job? = null
    private var currentSessionId: Long? = null
    private var sessionStartTime: Long = 0
    private var totalPausedDuration: Long = 0  // 累计暂停时长（毫秒）
    private var pauseStartTime: Long = 0       // 暂停开始时间
    
    init {
        // 加载设置
        viewModelScope.launch {
            preferencesManager.pomodoroWorkDuration.collect { duration ->
                _uiState.update { state ->
                    // 检查是否应该更新计时器显示
                    val shouldUpdateTimer = (state.timerState is TimerState.Idle || 
                                           state.timerState is TimerState.Completed) &&
                                          state.selectedRoutineTask == null
                    
                    val newSettings = state.settings.copy(workDuration = duration)
                    
                    if (shouldUpdateTimer) {
                        // 空闲状态且没有选择任务时，同步更新显示时间
                        state.copy(
                            settings = newSettings,
                            totalSeconds = duration * 60,
                            remainingSeconds = duration * 60
                        )
                    } else {
                        // 运行/暂停状态或已选择任务时，只更新设置
                        state.copy(settings = newSettings)
                    }
                }
            }
        }
        
        viewModelScope.launch {
            preferencesManager.pomodoroBreakDuration.collect { duration ->
                _uiState.update { state ->
                    val newSettings = state.settings.copy(shortBreakDuration = duration)
                    state.copy(settings = newSettings)
                }
            }
        }
        
        viewModelScope.launch {
            preferencesManager.pomodoroLongBreakDuration.collect { duration ->
                _uiState.update { state ->
                    val newSettings = state.settings.copy(longBreakDuration = duration)
                    state.copy(settings = newSettings)
                }
            }
        }
        
        viewModelScope.launch {
            preferencesManager.pomodoroSoundEnabled.collect { enabled ->
                _uiState.update { it.copy(
                    settings = it.settings.copy(soundEnabled = enabled)
                )}
            }
        }
        
        viewModelScope.launch {
            preferencesManager.pomodoroVibrationEnabled.collect { enabled ->
                _uiState.update { it.copy(
                    settings = it.settings.copy(vibrationEnabled = enabled)
                )}
            }
        }
        
        // 加载今日统计
        viewModelScope.launch {
            pomodoroRepository.getTodayStats()
                .map { sessions ->
                    PomodoroStats(
                        completedSessions = sessions.count { it.isCompleted && !it.isBreak },
                        totalMinutes = sessions.filter { it.isCompleted }.sumOf { it.duration },
                        interruptions = sessions.sumOf { it.interruptions }
                    )
                }
                .collect { stats ->
                    _uiState.update { it.copy(todayStats = stats) }
                }
        }
        
        // 初始化通知管理器
        notificationManager.initialize()
        
        // 监听来自通知栏的事件
        observeNotificationEvents()
    }
    
    private fun observeNotificationEvents() {
        viewModelScope.launch {
            PomodoroEventBus.events.collect { event ->
                when (event) {
                    is PomodoroEvent.Pause -> handleNotificationPause()
                    is PomodoroEvent.Resume -> handleNotificationResume()
                    is PomodoroEvent.Stop -> handleNotificationStop()
                }
            }
        }
    }
    
    // 处理来自通知栏的暂停操作
    private fun handleNotificationPause() {
        if (_uiState.value.timerState is TimerState.Running) {
            timerJob?.cancel()
            pauseStartTime = System.currentTimeMillis()
            _uiState.update { it.copy(
                timerState = TimerState.Paused(pauseStartTime)
            )}
        }
    }
    
    // 处理来自通知栏的继续操作
    private fun handleNotificationResume() {
        if (_uiState.value.timerState is TimerState.Paused) {
            if (pauseStartTime > 0) {
                totalPausedDuration += System.currentTimeMillis() - pauseStartTime
                pauseStartTime = 0
            }
            _uiState.update { it.copy(
                timerState = TimerState.Running(sessionStartTime)
            )}
            startTimerLoop()
        }
    }
    
    // 处理来自通知栏的停止操作
    private fun handleNotificationStop() {
        timerJob?.cancel()
        viewModelScope.launch {
            currentSessionId?.let { sessionId ->
                pomodoroRepository.markSessionInterrupted(sessionId)
                saisonNotificationManager.cancelPomodoroReminder(sessionId)
            }
        }
        resetTimer()
    }
    
    // ========== 任务选择 ==========
    
    fun loadAvailableRoutineTasks(): Flow<List<RoutineTask>> {
        return routineRepository.getRoutineTasksWithDuration()
    }
    
    fun selectRoutineTask(task: RoutineTask?) {
        if (_uiState.value.timerState !is TimerState.Idle) {
            // 只能在空闲状态下选择任务
            return
        }
        
        _uiState.update { state ->
            val duration = task?.durationMinutes ?: state.settings.workDuration
            state.copy(
                selectedRoutineTask = task,
                totalSeconds = duration * 60,
                remainingSeconds = duration * 60
            )
        }
    }
    
    // ========== 计时器控制 ==========
    
    fun startTimer() {
        if (_uiState.value.timerState is TimerState.Running) return
        
        val state = _uiState.value
        val duration = state.totalSeconds / 60
        
        sessionStartTime = System.currentTimeMillis()
        totalPausedDuration = 0  // 重置暂停时长
        pauseStartTime = 0
        
        viewModelScope.launch {
            // 创建新会话
            val session = PomodoroSession(
                taskId = null,
                routineTaskId = state.selectedRoutineTask?.id,
                startTime = sessionStartTime,
                duration = duration,
                isBreak = false
            )
            currentSessionId = pomodoroRepository.insertSession(session)
            
            _uiState.update { it.copy(
                timerState = TimerState.Running(sessionStartTime),
                totalSeconds = duration * 60,
                remainingSeconds = duration * 60
            )}
            
            // 调度番茄钟完成通知
            val completionTime = sessionStartTime + (duration * 60 * 1000)
            saisonNotificationManager.schedulePomodoroReminder(
                sessionId = currentSessionId ?: 0,
                isWorkEnd = true,
                triggerTime = completionTime
            )
            
            // 启动前台服务显示通知
            PomodoroTimerService.startService(
                context = context,
                totalSeconds = duration * 60,
                startTime = sessionStartTime,
                taskName = state.selectedRoutineTask?.title
            )
            
            startTimerLoop()
        }
    }
    
    fun pauseTimer() {
        timerJob?.cancel()
        pauseStartTime = System.currentTimeMillis()  // 记录暂停开始时间
        _uiState.update { it.copy(
            timerState = TimerState.Paused(pauseStartTime)
        )}
        // 更新通知栏状态
        PomodoroTimerService.updateService(context, _uiState.value.remainingSeconds, true)
    }
    
    fun resumeTimer() {
        if (_uiState.value.timerState is TimerState.Paused) {
            // 累加本次暂停的时长
            if (pauseStartTime > 0) {
                totalPausedDuration += System.currentTimeMillis() - pauseStartTime
                pauseStartTime = 0
            }
            _uiState.update { it.copy(
                timerState = TimerState.Running(sessionStartTime)
            )}
            // 更新通知栏状态
            PomodoroTimerService.updateService(context, _uiState.value.remainingSeconds, false)
            startTimerLoop()
        }
    }
    
    fun stopTimer() {
        timerJob?.cancel()
        viewModelScope.launch {
            currentSessionId?.let { sessionId ->
                pomodoroRepository.markSessionInterrupted(sessionId)
                // 取消番茄钟通知
                saisonNotificationManager.cancelPomodoroReminder(sessionId)
            }
        }
        // 停止前台服务
        PomodoroTimerService.stopService(context)
        resetTimer()
    }
    
    fun earlyFinish(markComplete: Boolean) {
        timerJob?.cancel()
        // 停止前台服务
        PomodoroTimerService.stopService(context)
        
        val state = _uiState.value
        val actualDuration = (state.totalSeconds - state.remainingSeconds) / 60
        
        viewModelScope.launch {
            if (markComplete && state.selectedRoutineTask != null) {
                // 创建打卡记录
                try {
                    val note = context.getString(R.string.pomodoro_checkin_note_early_finish, actualDuration)
                    routineRepository.checkInWithNote(state.selectedRoutineTask.id, note)
                    
                    _uiState.update { it.copy(
                        successMessage = context.getString(R.string.pomodoro_message_marked_complete)
                    )}
                } catch (e: Exception) {
                    _uiState.update { it.copy(
                        error = context.getString(R.string.pomodoro_message_checkin_failed, e.message ?: "")
                    )}
                }
            }
            
            // 更新会话记录
            currentSessionId?.let { sessionId ->
                val session = pomodoroRepository.getSessionById(sessionId)
                session?.let {
                    pomodoroRepository.updateSession(
                        it.copy(
                            isCompleted = markComplete,
                            isEarlyFinish = true,
                            actualDuration = actualDuration,
                            endTime = System.currentTimeMillis()
                        )
                    )
                }
                // 取消番茄钟通知
                saisonNotificationManager.cancelPomodoroReminder(sessionId)
            }
            
            _uiState.update { it.copy(
                timerState = TimerState.Completed(isEarlyFinish = true),
                completedPomodoros = if (markComplete) it.completedPomodoros + 1 else it.completedPomodoros
            )}
        }
    }
    
    // ========== 设置管理 ==========
    
    fun updateSettings(settings: PomodoroSettings) {
        viewModelScope.launch {
            preferencesManager.setPomodoroWorkDuration(settings.workDuration)
            preferencesManager.setPomodoroBreakDuration(settings.shortBreakDuration)
            preferencesManager.setPomodoroLongBreakDuration(settings.longBreakDuration)
            preferencesManager.setPomodoroSoundEnabled(settings.soundEnabled)
            preferencesManager.setPomodoroVibrationEnabled(settings.vibrationEnabled)
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }
    
    // ========== 私有方法 ==========
    
    private fun startTimerLoop() {
        timerJob = viewModelScope.launch {
            val totalDurationMs = _uiState.value.totalSeconds * 1000L
            
            while (true) {
                // 基于时间戳计算已过去的时间，即使应用在后台也能正确计算
                val elapsedMs = System.currentTimeMillis() - sessionStartTime - totalPausedDuration
                val remainingMs = totalDurationMs - elapsedMs
                val remainingSeconds = (remainingMs / 1000).toInt().coerceAtLeast(0)
                
                if (remainingSeconds <= 0) {
                    _uiState.update { it.copy(remainingSeconds = 0) }
                    break
                }
                
                _uiState.update { it.copy(remainingSeconds = remainingSeconds) }
                
                // 每秒更新一次UI
                delay(1000)
            }
            
            // 计时器完成
            onTimerComplete()
        }
    }
    
    private fun onTimerComplete() {
        val state = _uiState.value
        
        viewModelScope.launch {
            // 播放通知和震动
            if (state.settings.soundEnabled) {
                notificationManager.playCompletionSound()
            }
            if (state.settings.vibrationEnabled) {
                vibrationManager.vibrateCompletion()
            }
            
            // 完成会话
            currentSessionId?.let { sessionId ->
                pomodoroRepository.completeSession(sessionId)
                // 用户在应用内看到完成，取消通知（如果还未触发）
                saisonNotificationManager.cancelPomodoroReminder(sessionId)
            }
            
            // 完成时打卡
            if (state.selectedRoutineTask != null) {
                try {
                    val note = context.getString(R.string.pomodoro_checkin_note_complete)
                    routineRepository.checkInWithNote(state.selectedRoutineTask.id, note)
                    
                    _uiState.update { it.copy(
                        successMessage = context.getString(R.string.pomodoro_message_auto_checkin_success),
                        completedPomodoros = it.completedPomodoros + 1
                    )}
                } catch (e: Exception) {
                    _uiState.update { it.copy(
                        error = context.getString(R.string.pomodoro_message_auto_checkin_failed, e.message ?: ""),
                        completedPomodoros = it.completedPomodoros + 1
                    )}
                }
            } else {
                _uiState.update { it.copy(
                    completedPomodoros = it.completedPomodoros + 1
                )}
            }
            
            // 完成
            _uiState.update { it.copy(
                timerState = TimerState.Completed(isEarlyFinish = false)
            )}
            
            // 停止前台服务
            PomodoroTimerService.stopService(context)
        }
    }
    
    private fun resetTimer() {
        val state = _uiState.value
        val duration = state.settings.workDuration
        _uiState.update { it.copy(
            timerState = TimerState.Idle,
            totalSeconds = duration * 60,
            remainingSeconds = duration * 60,
            selectedRoutineTask = null
        )}
        currentSessionId = null
        sessionStartTime = 0
        totalPausedDuration = 0
        pauseStartTime = 0
    }
    
    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        notificationManager.release()
        // 停止前台服务
        PomodoroTimerService.stopService(context)
    }
}

// ========== 数据类 ==========

data class PomodoroUiState(
    val timerState: TimerState = TimerState.Idle,
    val remainingSeconds: Int = 25 * 60,
    val totalSeconds: Int = 25 * 60,
    val selectedRoutineTask: RoutineTask? = null,
    val completedPomodoros: Int = 0,
    val todayStats: PomodoroStats = PomodoroStats(),
    val settings: PomodoroSettings = PomodoroSettings(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

sealed class TimerState {
    object Idle : TimerState()
    data class Running(val startTime: Long) : TimerState()
    data class Paused(val pausedAt: Long) : TimerState()
    data class Completed(val isEarlyFinish: Boolean = false) : TimerState()
}

data class PomodoroSettings(
    val workDuration: Int = 25,
    val shortBreakDuration: Int = 5,
    val longBreakDuration: Int = 15,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true
)

data class PomodoroStats(
    val completedSessions: Int = 0,
    val totalMinutes: Int = 0,
    val interruptions: Int = 0
)
