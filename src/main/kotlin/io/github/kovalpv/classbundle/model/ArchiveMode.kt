package io.github.kovalpv.classbundle.model

/**
 * Режим экспорта архива (FR-11, FR-12).
 *
 * - [FILES] — внутрь ZIP кладутся сами файлы, пути берутся от корня проекта;
 * - [PATH_LIST] — внутрь ZIP кладётся один текстовый файл `paths.txt`
 *   с одним путём в строке (формат определяется [PathMode]).
 */
enum class ArchiveMode {
    FILES,
    PATH_LIST,
}
