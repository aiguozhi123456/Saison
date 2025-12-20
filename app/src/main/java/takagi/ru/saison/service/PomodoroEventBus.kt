package takagi.ru.saison.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 番茄钟事件总线
 * 用于 Service 与 ViewModel 之间的通信
 */
object PomodoroEventBus {
    
    private val _events = MutableSharedFlow<PomodoroEvent>(extraBufferCapacity = 10)
    val events: SharedFlow<PomodoroEvent> = _events.asSharedFlow()
    
    fun emitEvent(event: PomodoroEvent) {
        _events.tryEmit(event)
    }
}

sealed class PomodoroEvent {
    object Pause : PomodoroEvent()
    object Resume : PomodoroEvent()
    object Stop : PomodoroEvent()
}
