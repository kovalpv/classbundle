package io.github.kovalpv.classbundle.model

/**
 * Роль файла в bundle. Определяет приоритет в списке (TARGET первым)
 * и подпись в заголовке секции контекста.
 */
enum class FileRole(val priority: Int) {
    TARGET(0),
    INHERITOR(1),
    USAGE(2),
    DEPENDENCY(3),
    NESTED(4),
    ;
}