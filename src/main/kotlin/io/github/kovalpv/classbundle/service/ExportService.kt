package io.github.kovalpv.classbundle.service

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import io.github.kovalpv.classbundle.ClassBundleBundle
import io.github.kovalpv.classbundle.model.ExportPreview
import io.github.kovalpv.classbundle.model.ExportRequest
import io.github.kovalpv.classbundle.model.ExportResult
import io.github.kovalpv.classbundle.model.RelationType

/**
 * Orchestrates the export pipeline: collect related files, then write the
 * archive, reporting progress through the shared [ProgressIndicator].
 *
 * The preview path reuses the collector in read-only mode so the dialog
 * (FR-11) always shows exactly what an export would contain.
 */
internal object ExportService {

    /**
     * Runs the full export for [request].
     *
     * @param indicator progress/cancellation indicator owned by the caller
     * @return the produced [ExportResult]
     * @throws IllegalStateException if no related files were found
     * @throws com.intellij.openapi.cancellation.CancellationException if [indicator] is cancelled
     */
    fun export(request: ExportRequest, indicator: ProgressIndicator): ExportResult {
        indicator.fraction = 0.0
        indicator.text = ClassBundleBundle.message("export.progress.collecting")
        val files = RelatedFilesCollector().collect(request.project, request.targetClass, request.relations)
        if (files.isEmpty()) {
            throw IllegalStateException(ClassBundleBundle.message("export.error.noFiles"))
        }
        indicator.checkCanceled()
        indicator.text = ClassBundleBundle.message("export.progress.writing")
        return ArchiveWriter().write(request, files)
    }

    /**
     * Computes the preview snapshot for the dialog: which files would be
     * exported plus their total size. Must be called from a background thread.
     */
    fun preview(project: Project, targetClass: PsiClass, relations: Set<RelationType>): ExportPreview {
        val files = if (relations.isEmpty()) {
            emptyList()
        } else {
            RelatedFilesCollector().collect(project, targetClass, relations)
        }
        return ExportPreview(files, files.sumOf { it.length })
    }
}
