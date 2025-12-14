package takagi.ru.saison.ui.todo_floating

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import takagi.ru.saison.data.todo.TodoItem
import takagi.ru.saison.domain.todo.usecase.TodoUseCases
import javax.inject.Inject

@HiltViewModel
class FloatingTodoViewModel @Inject constructor(
    private val todoUseCases: TodoUseCases
) : ViewModel() {

    private val _todos = MutableStateFlow<List<TodoItem>>(emptyList())
    val todos: StateFlow<List<TodoItem>> = _todos.asStateFlow()

    private val _newTodoTitle = MutableStateFlow("")
    val newTodoTitle: StateFlow<String> = _newTodoTitle.asStateFlow()

    init {
        todoUseCases.getTodos()().onEach { todos ->
            _todos.value = todos
        }.launchIn(viewModelScope)
    }

    fun onNewTodoTitleChange(title: String) {
        _newTodoTitle.value = title
    }

    fun addTodo() {
        viewModelScope.launch {
            todoUseCases.addTodo(_newTodoTitle.value)
            _newTodoTitle.value = "" // Clear input after adding
        }
    }

    fun toggleTodoCompletion(item: TodoItem) {
        viewModelScope.launch {
            todoUseCases.toggleTodoCompletion(item)
        }
    }

    fun deleteTodo(item: TodoItem) {
        viewModelScope.launch {
            todoUseCases.deleteTodo(item)
        }
    }
}
