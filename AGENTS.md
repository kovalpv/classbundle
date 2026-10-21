# AGENTS.md — ClassBundle

Руководство для AI-агентов и контрибьюторов, работающих с этим репозиторием.

## Что это за проект

**ClassBundle** — плагин IntelliJ IDEA (`io.github.kovalpv.classbundle`), который
находит файлы, связанные с выбранным Java-классом (usages, наследники, вложенные),
и экспортирует их в ZIP — либо сами файлы, либо текстовый список путей.

Подробности — в `docs/SDD.md`.

## Tech stack

- **Язык:** Kotlin (JVM 17+)
- **Платформа:** IntelliJ Platform (2024.2+)
- **Сборка:** Gradle (Kotlin DSL) + `org.jetbrains.intellij.platform`
- **Тесты:** JUnit 5, `BasePlatformTestCase`, `LightPlatformCodeInsightFixtureTestCase`
- **UI:** Swing (DialogWrapper, JBPanel/FormBuilder)

## Быстрый старт

```bash
./gradlew runIde        # запустить sandbox-IDE с плагином
./gradlew test          # unit + integration тесты
./gradlew buildPlugin   # собрать ZIP для установки
./gradlew verifyPlugin  # проверка совместимости
```

## Структура

```
src/main/kotlin/io/github/kovalpv/classbundle/
  action/    — AnAction (точки входа)
  ui/        — DialogWrapper и вьюхи
  service/   — бизнес-логика (Collector, Writer, Formatter)
  model/     — data-классы и enum'ы
  util/      — утилиты (Notifications и т.п.)
src/main/resources/
  META-INF/plugin.xml
  messages/ClassBundleBundle.properties   (+ _ru)
src/test/kotlin/...                        — тесты, зеркалят структуру src/main
docs/SDD.md                                — этот дизайн-док
```

## Правила работы с PSI и потоками

- **Любой доступ к PSI — только внутри `ReadAction.compute {}` или
  `ReadAction.run {}`.** Никогда не обращайтесь к `PsiElement` из UI-потока без
  read-lock.
- **Долгие операции** (поиск, I/O) — в `ProgressManager.run(Task)`.
  UI-модификации — через `ApplicationManager.getApplication().invokeLater {}`.
- **Не смешивайте** PSI-чтение и запись файлов в одной лямбде `ReadAction`.
  Сначала соберите `List<VirtualFile>` в read-action, потом пишите ZIP вне lock'а.
- **Не кэшируйте** `PsiElement`/`PsiClass` между вызовами действий — они
  инвалидируются. Кэшируйте `SmartPsiElementPointer` при необходимости.

## Правила кодирования

- **Kotlin только.** Никакого Java в `src/main` (тесты тоже Kotlin).
- **Никаких `!!`** — используйте `?: return`, `requireNotNull` с осмысленным
  сообщением или `safe access`.
- **Все строки UI — через `ClassBundleBundle.message("key")`.** Хардкод строк
  в UI запрещён. Добавляя новую строку, добавьте её сразу в оба
  `.properties` (en + ru).
- **Публичные API сервисов** — `internal` по умолчанию. `public` только то, что
  реально используется снаружи пакета.
- **Именование пакетов:** `io.github.kovalpv.classbundle.<layer>`.
- **Логирование:** `Logger.getInstance(ClassName::class.java)`. Уровни:
  `info` — старт/конец операции, `warn` — пропущенный/невалидный элемент,
  `error` — сбой I/O.

## Чего НЕ делать

- ❌ Не вызывайте `VirtualFile.contentsToByteArray()` для больших файлов в EDT.
- ❌ Не пишите напрямую в `VirtualFile` — для вывода архива используйте
  `java.nio.file.Path` (архив не является VFS-файлом проекта).
- ❌ Не создавайте `new Thread(...)` — только `ApplicationManager.executeOnPooledThread`
  или `ProgressManager`.
- ❌ Не добавляйте зависимости от других плагинов без явного согласования —
  это ломает совместимость с Community Edition.
- ❌ Не коммитьте `build/`, `.idea/`, `*.iml`, `local.properties`.

## Тесты

- Новый публичный сервис (`RelatedFilesCollector`, `ArchiveWriter`,
  `PathFormatter`) **обязан** иметь unit-тест.
- Фикстуры PSI — в `src/test/testData/<testName>/` (стандарт платформы).
- Перед PR: `./gradlew test verifyPlugin` должны быть зелёными.
- Не уменьшайте покрытие `service/` — это критическая логика.

## Изменения в `plugin.xml`

- `<idea-version since-build="...">` — обновляется при смене минимальной
  поддерживаемой версии; согласовывать с мейнтейнером.
- Новые `<action>` — обязательно с уникальным `id` в формате
  `ClassBundle.<ActionName>`, `text` через бандл, `description` через бандл.
- Изменения `plugin.xml` должны сопровождаться записью в `CHANGELOG.md`.

## Процесс работы

1. Задача приходит как issue. Прочитайте `docs/SDD.md` раздел, относящийся к
   задаче.
2. Ветка: `feature/<short-slug>` / `fix/<short-slug>`.
3. Коммиты — Conventional Commits (`feat:`, `fix:`, `chore:`, `docs:`, `test:`).
4. PR должен содержать: описание изменения, ссылку на issue, скриншот диалога
   при изменении UI, обновлённый `CHANGELOG.md`.
5. Ревью — минимум один approve. CI должен быть зелёным.

## Definition of Done

- [ ] Код собирается (`./gradlew build`).
- [ ] Тесты проходят.
- [ ] `verifyPlugin` — без ошибок.
- [ ] Строки UI — в бандлах (en + ru).
- [ ] CHANGELOG обновлён.
- [ ] При изменении архитектуры — обновлён `docs/SDD.md`.

## Контакты

- Maintainer: `@kovalpv` (GitHub)
- Issues: https://github.com/kovalpv/classbundle/issues