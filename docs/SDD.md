# Название плагина

Предлагаю: **ClassBundle** — короткое, отражает суть («связать класс в пакет/архив»), хорошо читается и не пересекается с существующими плагинами в Marketplace.

- **Plugin name (display):** `ClassBundle — Export Related Files`
- **Plugin ID:** `io.github.kovalpv.classbundle`
- **Gradle `group`:** `io.github.kovalpv`
- **Artifact name:** `classbundle`
- **Пакеты:** `io.github.kovalpv.classbundle.*`

---

# 📄 SDD — Software Design Document

## 1. Обзор

**ClassBundle** — плагин для IntelliJ IDEA, позволяющий разработчику выбрать Java-класс (или иной PSI-элемент) и выгрузить в ZIP-архив набор файлов, связанных с этим классом. Пользователь может выбрать *типы связей* (использования, наследники, вложенные классы и т.д.) и *формат путей* (абсолютные / относительные) внутри архива.

Типичный сценарий: подготовка минимального воспроизводимого примера (repro-case) для баг-репорта, шаринг фрагмента кода коллегам, архивация модуля для аудита.

## 2. Цели и не-цели

### Цели (v1.0)
- Найти все файлы, связанные с выбранным классом через PSI.
- Дать пользователю выбрать:
  - набор типов связей (usages / inheritors / containing file / nested classes);
  - формат путей в архиве (абсолютные / относительные от корня проекта);
  - режим упаковки: **архивировать сами файлы** или **архивировать текстовый список путей**.
- Сохранить результат в ZIP по указанному пользователем пути.
- Работать асинхронно, не блокировать UI.
- Показывать прогресс и корректно обрабатывать ошибки.

### Не-цели (v1.0)
- Поддержка не-Java языков (Kotlin, Groovy — в бэклоге).
- Транзитивный анализ зависимостей (зависимости зависимостей).
- Публикация в JetBrains Marketplace (цель v1.1).
- Интеграция с системой контроля версий.

## 3. Функциональные требования

| ID | Требование | Приоритет |
|----|------------|-----------|
| FR-1 | Действие доступно в контекстном меню Project View и Editor Popup для Java-файлов | Must |
| FR-2 | Действие активно только когда выбран `PsiClass` (или файл, содержащий класс) | Must |
| FR-3 | Диалог настроек экспорта: типы связей, формат путей, режим упаковки, путь сохранения | Must |
| FR-4 | Поиск usages через `ReferencesSearch` | Must |
| FR-5 | Поиск наследников через `ClassInheritorsSearch` | Must |
| FR-6 | Включение самого исходного файла и вложенных классов | Must |
| FR-7 | Сбор результатов в `Set<VirtualFile>` (дедупликация) | Must |
| FR-8 | Генерация ZIP: режим «файлы» — с сохранением относительной структуры каталогов | Must |
| FR-9 | Генерация ZIP: режим «список путей» — текстовый файл `paths.txt` | Must |
| FR-10 | Прогресс-бар с возможностью отмены | Should |
| FR-11 | Превью результата перед сохранением (список найденных файлов) | Should |
| FR-12 | Сохранение последних настроек в `PropertiesComponent` | Should |
| FR-13 | Поддержка Kotlin (KtClass) | Could (v1.1) |

## 4. Нефункциональные требования

- **Производительность:** анализ класса до 1000 usages — не более 2 сек на среднестатистическом проекте. UI не блокируется.
- **Потокобезопасность:** PSI-запросы выполняются в read-action (`ReadAction`), файловый I/O — в фоновом пуле.
- **Совместимость:** IntelliJ IDEA 2024.2+ (Community и Ultimate).
- **Локализация:** RU + EN бандлы ресурсов (`messages/ClassBundleBundle.properties`).
- **Надежность:** любая ошибка I/O или PSI логируется в `logger.warn/error` и показывается пользователю через `NotificationGroup`.

## 5. Архитектура

```
io.github.kovalpv.classbundle
├── action/
│   └── ExportRelatedFilesAction.kt        // точка входа (AnAction)
├── ui/
│   ├── ExportDialog.kt                    // DialogWrapper с настройками
│   └── ExportDialogState.kt               // data class состояния диалога
├── service/
│   ├── RelatedFilesCollector.kt           // поиск связанных файлов (PSI)
│   ├── ArchiveWriter.kt                   // запись ZIP (файлы / список путей)
│   └── PathFormatter.kt                   // абсолютные / относительные пути
├── model/
│   ├── RelationType.kt                    // enum: USAGES, INHERITORS, NESTED
│   ├── PathMode.kt                        // enum: ABSOLUTE, RELATIVE
│   └── ExportRequest.kt                   // параметры запроса
└── util/
    └── Notifications.kt                   // обертка над NotificationGroup
```

### Диаграмма потока

```
User (context menu)
   │
   ▼
ExportRelatedFilesAction.actionPerformed()
   │  1. Извлекает PsiClass из AnActionEvent
   │  2. Открывает ExportDialog (modal)
   ▼
ExportDialog → ExportRequest (выбор пользователя)
   │
   ▼
BackgroundTask (ProgressManager.run)
   │
   ├── ReadAction:
   │     RelatedFilesCollector.collect(psiClass, request)
   │        ├── ReferencesSearch.search(...)
   │        ├── ClassInheritorsSearch.search(...)
   │        └── include containing file / nested classes
   │     → Set<VirtualFile>
   │
   ├── I/O (background, non-EDT):
   │     PathFormatter.map(vFiles, request.pathMode)
   │     ArchiveWriter.write(zipPath, files, request.mode)
   │
   ▼
Notification (success / error)
```

## 6. Модель данных

```kotlin
enum class RelationType { USAGES, INHERITORS, NESTED_CLASSES }
enum class PathMode { ABSOLUTE, RELATIVE }
enum class ArchiveMode { FILES, PATH_LIST }

data class ExportRequest(
    val targetClass: PsiClass,
    val relations: Set<RelationType>,
    val pathMode: PathMode,
    val archiveMode: ArchiveMode,
    val outputZip: Path,
    val baseDir: VirtualFile,           // корень проекта для относительных путей
)
```

## 7. Ключевые API платформы

| Задача | API |
|--------|-----|
| Получить `PsiClass` из действия | `CommonDataKeys.PSI_ELEMENT` / `PSI_FILE` |
| Поиск usages | `com.intellij.psi.search.searches.ReferencesSearch` |
| Поиск наследников | `com.intellij.psi.search.searches.ClassInheritorsSearch` |
| Потокобезопасное чтение PSI | `com.intellij.openapi.application.ReadAction` |
| Фоновые задачи | `com.intellij.openapi.progress.ProgressManager.run(Task)` |
| Диалог | `com.intellij.openapi.ui.DialogWrapper` |
| Уведомления | `com.intellij.notification.NotificationGroupManager` |
| Настройки | `com.intellij.ide.util.PropertiesComponent` |
| VFS | `com.intellij.openapi.vfs.VirtualFile` + `com.intellij.openapi.util.io.FileUtil` |

## 8. UX-поток

1. ПКМ на классе в Project View (или в редакторе) → пункт **ClassBundle: Export Related Files…**
2. Открывается модальный диалог:
   - **Include** (чекбоксы): ☑ Usages, ☑ Inheritors, ☑ Nested classes, ☑ Class itself (checked, disabled)
   - **Path format** (radio): ⦿ Relative to project root, ○ Absolute
   - **Archive content** (radio): ⦿ Files, ○ List of paths (paths.txt)
   - **Output file:** `[___________]` + кнопка «…» (FileChooser)
   - **Found files preview** (список, обновляется при изменении настроек, max 200 строк + «…and N more»)
3. «OK» → прогресс-бар «Collecting related files…» → «Writing archive…»
4. Success: notification «Exported N files to /path/to/archive.zip», кликабельная ссылка на файл.
5. Error: notification с текстом ошибки и кнопкой «Show log».

## 9. Обработка ошибок

| Ситуация | Поведение |
|----------|-----------|
| Не выбран класс | Действие disabled |
| 0 файлов после поиска | Info-notification, архив не создается |
| Нет прав на запись | Error-notification с путём |
| Прервано пользователем | Info-notification «Cancelled» |
| Target-файл переименован/удалён во время поиска | Пропустить, залогировать |
| PSI-ошибка (невалидный элемент) | `logger.warn`, пропустить элемент |

## 10. Тестирование

**Unit (JUnit 5 + `BasePlatformTestCase`):**
- `RelatedFilesCollectorTest`: поиск usages, наследников, вложенных классов на фикстурах.
- `PathFormatterTest`: относительные/абсолютные пути, Windows/Linux разделители.
- `ArchiveWriterTest`: содержимое ZIP при обоих режимах.

**Integration (LightPlatformCodeInsightFixtureTestCase):**
- Действие активной/неактивной кнопки при разных выделениях.

**Manual:**
- Проверка на трёх проектах: пустой, средний (~10k файлов), большой (~100k файлов).
- Проверка отмены длительной операции.

## 11. Сборка и релиз

- Gradle + `org.jetbrains.intellij.platform`.
- `./gradlew buildPlugin` → `build/distributions/classbundle-<version>.zip`.
- `./gradlew runIde` — dev-среда.
- `./gradlew verifyPlugin` — проверка совместимости перед публикацией.
- SemVer: `1.0.0` — первый релиз в Marketplace.

## 12. Вехи (Milestones)

| Веха | Содержание |
|------|------------|
| M1 | Скелет плагина, действие в меню, извлечение `PsiClass` |
| M2 | `RelatedFilesCollector` (usages + содержащий файл) |
| M3 | Диалог настроек + превью |
| M4 | `ArchiveWriter` (оба режима) |
| M5 | Наследники + вложенные классы, прогресс/отмена |
| M6 | Тесты, локализация, уведомления, сохранение настроек |
| M7 | Релиз 1.0.0 + публикация в Marketplace |
