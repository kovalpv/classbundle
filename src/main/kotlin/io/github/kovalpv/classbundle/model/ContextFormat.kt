package io.github.kovalpv.classbundle.model

/**
 * Формат содержимого context bundle (FR-14).
 *
 * Применяется только при [OutputMode.CONTEXT_FILE]; для ZIP-режимов игнорируется.
 */
enum class ContextFormat(val extension: String, val displayName: String) {
    MARKDOWN("md", "Markdown"),
    PLAIN("txt", "Plain text"),
    JSON("json", "JSON"),
    XML("xml", "XML"),
    ;
}