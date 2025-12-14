package takagi.ru.saison.data.repository

import kotlinx.coroutines.flow.Flow
import takagi.ru.saison.data.todo.TodoDao
import takagi.ru.saison.data.todo.TodoItem
import takagi.ru.saison.domain.todo.TodoRepository
import javax.inject.Inject

class TodoRepositoryImpl @Inject constructor(
    private val todoDao: TodoDao
) : TodoRepository {

    override fun getAllTodos(): Flow<List<TodoItem>> {
        return todoDao.getAllTodos()
    }

    override suspend fun addTodo(item: TodoItem) {
        todoDao.insert(item)
    }

    override suspend fun updateTodo(item: TodoItem) {
        todoDao.update(item)
    }

    override suspend fun deleteTodo(item: TodoItem) {
        todoDao.delete(item)
    }
}
