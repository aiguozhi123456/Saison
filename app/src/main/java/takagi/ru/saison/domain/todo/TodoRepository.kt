package takagi.ru.saison.domain.todo

import kotlinx.coroutines.flow.Flow
import takagi.ru.saison.data.todo.TodoItem

interface TodoRepository {
    fun getAllTodos(): Flow<List<TodoItem>>
    suspend fun addTodo(item: TodoItem)
    suspend fun updateTodo(item: TodoItem)
    suspend fun deleteTodo(item: TodoItem)
}
