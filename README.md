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

## Usage

1. Select a Java class (editor or project view).
2. Right-click → **ClassBundle: Export Related Files…**.
3. Pick relations to include, output mode (files or `paths.txt`) and the target file.

## Development

```bash
./gradlew runIde        # launch a sandbox IDE with the plugin
./gradlew test          # unit + integration tests
./gradlew buildPlugin   # build the plugin ZIP
./gradlew verifyPlugin  # compatibility verification
```

See `docs/SDD.md` for the design document and `AGENTS.md` for contribution guidelines.

## License

See `AGENTS.md` and repository settings.
