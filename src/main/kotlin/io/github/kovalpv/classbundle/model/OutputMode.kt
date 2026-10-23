package io.github.kovalpv.classbundle.model

/**
 * Режим вывода результата экспорта (FR-11, FR-12, FR-14).
 *
 * - [FILES_ZIP]    — ZIP с самими файлами (ранее `ArchiveMode.FILES`);
 * - [PATHS_ZIP]    — ZIP с одним текстовым файлом `paths.txt` (ранее `ArchiveMode.PATH_LIST`);
 * - [CONTEXT_FILE] — один текстовый файл (`.md` / `.txt` / `.json` / `.xml`) с содержимым
 *   связанных файлов для передачи LLM (см. [ContextFormat]).
 */
enum class OutputMode {
    FILES_ZIP,
    PATHS_ZIP,
    CONTEXT_FILE,
    ;

    val isZip: Boolean
        get() = this == FILES_ZIP || this == PATHS_ZIP
}