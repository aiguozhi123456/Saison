package takagi.ru.saison.domain.todo.usecase

import takagi.ru.saison.data.todo.TodoItem
import takagi.ru.saison.domain.todo.TodoRepository
import javax.inject.Inject

// 1. 获取所有Todo
class GetTodosUseCase @Inject constructor(
    private val repository: TodoRepository
) {
    operator fun invoke() = repository.getAllTodos()
}

// 2. 添加Todo
class AddTodoUseCase @Inject constructor(
    private val repository: TodoRepository
) {
    suspend operator fun invoke(title: String) {
        if (title.isBlank()) return
        val todoItem = TodoItem(title = title.trim())
        repository.addTodo(todoItem)
    }
}

// 3. 切换Todo完成状态
class ToggleTodoCompletionUseCase @Inject constructor(
    private val repository: TodoRepository
) {
    suspend operator fun invoke(item: TodoItem) {
        val updatedItem = item.copy(isCompleted = !item.isCompleted)
        repository.updateTodo(updatedItem)
    }
}

// 4. 删除Todo
class DeleteTodoUseCase @Inject constructor(
    private val repository: TodoRepository
) {
    suspend operator fun invoke(item: TodoItem) {
        repository.deleteTodo(item)
    }
}

data class TodoUseCases @Inject constructor(
    val getTodos: GetTodosUseCase,
    val addTodo: AddTodoUseCase,
    val toggleTodoCompletion: ToggleTodoCompletionUseCase,
    val deleteTodo: DeleteTodoUseCase
)
