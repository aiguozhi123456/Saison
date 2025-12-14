# Saison应用Todo悬浮窗功能添加说明

## 1. 功能概述

本次修改为Saison应用添加了**Todo待办清单悬浮窗**功能。用户现在可以在应用内点击悬浮按钮，启动一个可拖动的浮动窗口，用于快速查看、添加和管理待办事项，无需切换应用。

## 2. 主要修改内容

本次修改主要涉及以下几个方面：

### 2.1. 数据层 (Room Database)

*   **新增实体:** `TodoItem.kt` (位于 `takagi.ru.saison.data.todo`)，用于存储待办事项的标题、完成状态和创建时间。
*   **新增DAO:** `TodoDao.kt` (位于 `takagi.ru.saison.data.todo`)，提供对 `TodoItem` 的 CRUD 操作。
*   **数据库迁移:** 修改 `SaisonDatabase.kt`，将数据库版本从 `17` 升级到 `18`，并添加了 `MIGRATION_17_18` 迁移逻辑，用于创建 `todo_items` 表。
*   **依赖注入:** 在 `DatabaseModule.kt` 和 `RepositoryModule.kt` 中添加了 `TodoDao` 和 `TodoRepository` 的 Hilt 注入绑定。

### 2.2. 业务逻辑层 (Domain Layer)

*   **新增Repository实现:** `TodoRepositoryImpl.kt` (位于 `takagi.ru.saison.data.repository`)，实现了 `TodoRepository` 接口。
*   **新增Use Cases:** `TodoUseCases.kt` (位于 `takagi.ru.saison.domain.todo.usecase`)，包含 `GetTodosUseCase`、`AddTodoUseCase`、`ToggleTodoCompletionUseCase` 和 `DeleteTodoUseCase`，封装了业务逻辑。

### 2.3. 界面层 (UI Layer)

*   **新增ViewModel:** `FloatingTodoViewModel.kt` (位于 `takagi.ru.saison.ui.todo_floating`)，管理悬浮窗UI的状态和数据。
*   **新增Compose UI:** `FloatingTodoScreen.kt` (位于 `takagi.ru.saison.ui.todo_floating`)，实现了悬浮窗内的待办清单界面。
*   **新增Service:** `FloatingTodoService.kt` (位于 `takagi.ru.saison.ui.todo_floating`)，这是一个 `Service`，使用 `WindowManager` 承载 Compose UI，并实现了悬浮窗的拖动逻辑。
*   **新增启动组件:** `FloatingWindowPermissionHelper.kt` (位于 `takagi.ru.saison.util`) 和 `FloatingWindowButton.kt` (位于 `takagi.ru.saison.ui.components`)，用于检查悬浮窗权限和启动/停止 `FloatingTodoService`。
*   **主界面集成:** 在 `MainActivity.kt` 的 `SaisonApp` Composable 中，添加了 `FloatingWindowButton` 作为 `Scaffold` 的 `floatingActionButton`。

### 2.4. AndroidManifest

*   **新增权限:** 在 `AndroidManifest.xml` 中添加了 `android.permission.SYSTEM_ALERT_WINDOW` 权限声明。
*   **新增Service:** 注册了 `FloatingTodoService`。

## 3. 使用说明

### 3.1. 权限要求

由于悬浮窗功能需要特殊的系统权限，首次使用时，应用会引导用户进行授权。

1.  **点击悬浮窗按钮:** 在应用主界面右下角，点击新增的 **待办清单悬浮按钮**（图标为 `List`）。
2.  **权限请求:** 如果应用尚未获得悬浮窗权限，系统会跳转到权限设置页面。
3.  **授权:** 在设置页面中，找到 **Saison** 应用，并开启 **允许显示在其他应用的上层**（或类似名称）的权限。
4.  **返回应用:** 授权完成后，返回Saison应用，再次点击悬浮窗按钮即可启动Todo悬浮窗。

### 3.2. 悬浮窗操作

*   **启动:** 在应用主界面点击右下角的 **待办清单悬浮按钮**。
*   **拖动:** 长按悬浮窗的任意位置，可以拖动它到屏幕的任何位置。
*   **关闭:** 点击悬浮窗右上角的 **关闭** (X) 图标即可关闭悬浮窗。
*   **待办管理:** 在悬浮窗内可以直接添加新的待办事项、标记完成状态或删除待办事项。所有数据都将持久化存储在应用数据库中。

## 4. 提交代码

所有修改已在本地完成，请将这些文件合并到您的项目中。

**修改文件列表:**

| 文件路径 | 描述 |
| :--- | :--- |
| `app/src/main/AndroidManifest.xml` | 添加 `SYSTEM_ALERT_WINDOW` 权限和 `FloatingTodoService` 声明 |
| `app/src/main/java/takagi/ru/saison/MainActivity.kt` | 在 `Scaffold` 中添加 `FloatingWindowButton` |
| `app/src/main/java/takagi/ru/saison/data/local/database/SaisonDatabase.kt` | 数据库版本升级到 18，添加 `TodoItem` 实体和 `MIGRATION_17_18` |
| `app/src/main/java/takagi/ru/saison/data/todo/TodoItem.kt` | 新增Todo数据实体 |
| `app/src/main/java/takagi/ru/saison/data/todo/TodoDao.kt` | 新增Todo数据访问对象 |
| `app/src/main/java/takagi/ru/saison/data/repository/TodoRepositoryImpl.kt` | 新增Todo Repository实现 |
| `app/src/main/java/takagi/ru/saison/di/DatabaseModule.kt` | 在Hilt中提供 `TodoDao` |
| `app/src/main/java/takagi/ru/saison/di/RepositoryModule.kt` | 在Hilt中绑定 `TodoRepository` |
| `app/src/main/java/takagi/ru/saison/domain/todo/TodoRepository.kt` | 新增Todo Repository接口 |
| `app/src/main/java/takagi/ru/saison/domain/todo/usecase/TodoUseCases.kt` | 新增Todo Use Cases |
| `app/src/main/java/takagi/ru/saison/ui/components/FloatingWindowButton.kt` | 新增悬浮窗启动按钮Compose组件 |
| `app/src/main/java/takagi/ru/saison/ui/todo_floating/FloatingTodoScreen.kt` | 新增悬浮窗内的Todo列表Compose UI |
| `app/src/main/java/takagi/ru/saison/ui/todo_floating/FloatingTodoService.kt` | 新增悬浮窗Service和拖动逻辑 |
| `app/src/main/java/takagi/ru/saison/ui/todo_floating/FloatingTodoViewModel.kt` | 新增悬浮窗ViewModel |
| `app/src/main/java/takagi/ru/saison/util/FloatingWindowPermissionHelper.kt` | 新增悬浮窗权限检查和Service启动工具类 |
| `todo_floating_design.md` | 功能设计文档（可删除） |
| `local.properties` | 解决Gradle构建问题的SDK路径配置（可删除） |

**注意:** 由于沙箱环境限制，无法为您直接生成可安装的APK文件。您需要将这些修改应用到您的本地开发环境中，并重新构建应用。
