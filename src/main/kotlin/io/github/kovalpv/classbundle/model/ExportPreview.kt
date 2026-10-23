package io.github.kovalpv.classbundle.model

import com.intellij.openapi.vfs.VirtualFile

/**
 * Снимок файлов, которые попадут в экспорт, для превью в диалоге (FR-11).
 *
 * @param files         список найденных файлов в стабильном порядке;
 * @param totalBytes    сумма размеров файлов на диске;
 * @param tokenEstimate грубая оценка токенов (0, если превью не в context-режиме).
 */
internal data class ExportPreview(
    val files: List<VirtualFile>,
    val totalBytes: Long,
    val tokenEstimate: Int = 0,
)