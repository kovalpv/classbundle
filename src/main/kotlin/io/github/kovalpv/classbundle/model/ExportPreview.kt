package io.github.kovalpv.classbundle.model

import com.intellij.openapi.vfs.VirtualFile

/**
 * A snapshot of the files an export would contain, computed for the dialog
 * preview (FR-11): the file list plus the aggregate size.
 *
 * @param files the resolved files in stable order
 * @param totalBytes sum of the current on-disk sizes of [files]
 */
internal data class ExportPreview(
    val files: List<VirtualFile>,
    val totalBytes: Long
)
