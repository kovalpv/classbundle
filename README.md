# ClassBundle

Плагин для IntelliJ IDEA, который находит файлы, связанные с выбранным Java-классом
(использования, наследники, вложенные классы) и экспортирует их в ZIP — либо сами файлы,
либо текстовый список путей. Удобно для сборки минимального воспроизведения бага.

<!-- Plugin description -->
**ClassBundle** finds files related to the selected Java class — usages, inheritors,
nested classes — and exports them to a ZIP archive: either the files themselves or a
plain-text list of paths. Built for quickly assembling minimal reproduction cases for
bug reports.

**Класс-бандл**: плагин находит файлы, связанные с выбранным Java-классом (использования,
наследники, вложенные классы) и упаковывает их в ZIP-архив — сами файлы или текстовый
список путей.

<!-- Plugin description end -->

## Features

- Collection of files related to the selected Java class:
  - direct usages;
  - inheritors (subclasses / implementors);
  - nested classes.
- Two export modes: files inside a ZIP archive, or a plain-text `paths.txt` list.
- Absolute or relative path formatting.
- Export dialog with a file preview and a progress bar.
- Persistent export preferences (`PropertiesComponent`).
- Streaming archive writing (no eager full-file byte arrays in memory).

## Installation

- **Marketplace:** after publishing, install via
  `Settings → Plugins → Marketplace` by searching "Class Bundle".
- **From source:**

  ```bash
  ./gradlew buildPlugin
  # → build/distributions/class-bundle-<version>.zip
  ```

  Then install it via `Settings → Plugins → ⚙ → Install Plugin from Disk…`.

## Usage

1. Select a Java class — either in the editor or in the Project view.
2. Right-click → **ClassBundle: Export Related Files…**.
3. In the dialog, pick the relation types to include, the output mode
   (files or `paths.txt`) and the target file.
4. Confirm — the ZIP (or path list) is written and a notification is shown.

## Requirements

| Requirement | Details |
|---|---|
| Java | JDK **21** (Gradle toolchain, `jvmToolchain(21)` in `build.gradle.kts`) |
| IntelliJ IDEA | `since-build = 253` (`gradle.properties`), depends on the **Java** bundled plugin (`com.intellij.java`) |
| Gradle | Wrapper **9.2.1** (use `./gradlew`, no system Gradle needed) |

### ⚠️ Local IDE requirement

The build does **not** download the IntelliJ Platform: it is wired to a
**local IDE installation** in `build.gradle.kts`:

```kotlin
intellijPlatform {
    local("/Applications/IntelliJ IDEA 25.3.app/Contents")
}
```

If your IDEA lives at a different path, edit that line to point at the
`Contents` folder of your installation
(`<InstallDir>/<IDEA App>.app/Contents`). An alternative
(`intellijIdea(providers.gradleProperty("platformVersion"))`) is kept
commented out for when remote downloads are available again.

## Building, Testing, Running

All commands are run from the repository root via the Gradle wrapper:

| Command | Purpose |
|---|---|
| `./gradlew runIde` | Launch a sandbox IDE with the plugin installed — the main way to try it |
| `./gradlew test` | Unit + integration tests (JUnit + IntelliJ Platform test fixtures) |
| `./gradlew koverXmlReport` | Coverage report (Kover); coverage is enforced on `check` |
| `./gradlew buildPlugin` | Build the distributable plugin ZIP in `build/distributions/` |
| `./gradlew verifyPlugin` | Verify compatibility against recommended IDE builds (downloads IDEs from JetBrains) |
| `./gradlew publishPlugin` | Publish to the JetBrains Marketplace (requires `PUBLISH_TOKEN` and, for signing, `CERTIFICATE_CHAIN` / `PRIVATE_KEY` / `PRIVATE_KEY_PASSWORD` env vars) |
| `./gradlew runIdeForUiTests` | Sandbox IDE preconfigured for UI tests (Robot Server on port 8082) |

Example session:

```bash
./gradlew build        # compile + tests + coverage
./gradlew runIde       # try the plugin in a sandbox IDE
./gradlew buildPlugin  # produce the distribution ZIP
```

## Project Structure

```
src/main/kotlin/io/github/kovalpv/classbundle/
  action/    — AnAction entry points
  ui/        — DialogWrapper and views
  service/   — business logic (Collector, Writer, Formatter)
  model/     — data classes and enums
  util/      — utilities (notifications, etc.)
src/main/resources/
  META-INF/plugin.xml
  messages/ClassBundleBundle.properties   (+ _ru)
src/test/kotlin/...                        — tests mirroring src/main
docs/SDD.md                                — software design document
```

## Localization

All UI strings are resolved through `ClassBundleBundle`
(`messages/ClassBundleBundle.properties`). Adding a new user-facing string
requires adding the key to **both** the English and Russian bundles.

## Development Guidelines

- Kotlin only (no Java in `src/main`), no `!!` non-null assertions.
- All PSI access must happen inside `ReadAction`; long-running work goes
  through `ProgressManager` — never spawn raw threads.
- Every public service must have unit tests; do not reduce coverage of `service/`.
- `CHANGELOG.md` must be updated with user-visible changes; changes to
  `plugin.xml` are recorded there as well.

See [AGENTS.md](AGENTS.md) for the full contribution guide and
[docs/SDD.md](docs/SDD.md) for the design document.

## License

See repository settings and `AGENTS.md`.
