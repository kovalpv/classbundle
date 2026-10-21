package io.github.kovalpv.classbundle.model

/**
 * Режим представления путей в списке (FR-7, FR-8).
 *
 * - [RELATIVE] — относительно корня проекта (для файлов вне проекта
 *   используется абсолютный путь);
 * - [ABSOLUTE] — абсолютные пути на файловой системе.
 *
 * Разделитель всегда `File.separator` текущей ОС (FR-8).
 */
enum class PathMode {
    RELATIVE,
    ABSOLUTE,
}
