package io.github.kovalpv.classbundle.model

import com.intellij.openapi.vfs.VirtualFile

/**
 * Файл, найденный [io.github.kovalpv.classbundle.service.RelatedFilesCollector],
 * вместе с ролью относительно целевого класса.
 */
internal data class RelatedFile(
    val file: VirtualFile,
    val role: FileRole,
)