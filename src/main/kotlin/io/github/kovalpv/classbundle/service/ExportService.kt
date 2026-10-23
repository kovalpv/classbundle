package io.github.kovalpv.classbundle.service

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiClass
import io.github.kovalpv.classbundle.ClassBundleBundle
import io.github.kovalpv.classbundle.model.ContextFormat
import io.github.kovalpv.classbundle.model.ExportPreview
import io.github.kovalpv.classbundle.model.ExportRequest
import io.github.kovalpv.classbundle.model.ExportResult
import io.github.kovalpv.classbundle.model.OutputMode
import io.github.kovalpv.classbundle.model.RelationType

/**
 * Оркестратор пайплайна экспорта: собрать связанные файлы, затем записать
 * результат в нужном формате, сообщая о прогрессе через [ProgressIndicator].
 */
internal object ExportService {

    fun export(request: ExportRequest, indicator: ProgressIndicator): ExportResult {
        indicator.fraction = 0.0
        indicator.text = ClassBundleBundle.message("export.progress.collecting")

        val related = RelatedFilesCollector().collect(request)
        if (related.isEmpty()) {
            throw IllegalStateException(ClassBundleBundle.message("export.error.noFiles"))
        }
        indicator.checkCanceled()
        indicator.text = ClassBundleBundle.message("export.progress.writing")

        return when (request.outputMode) {
            OutputMode.FILES_ZIP, OutputMode.PATHS_ZIP ->
                ArchiveWriter().write(request, related.map { it.file })
            OutputMode.CONTEXT_FILE ->
                ContextBundleWriter().write(request, related)
        }
    }

    /**
     * Считает превью для диалога (FR-11). Если [contextFormat] не `null`,
     * дополнительно оценивает количество токенов.
     */
    fun preview(
        project: Project,
        targetClass: PsiClass,
        relations: Set<RelationType>,
        contextFormat: ContextFormat? = null,
    ): ExportPreview {
        val related = if (relations.isEmpty()) {
            emptyList()
        } else {
            RelatedFilesCollector().collect(project, targetClass, relations)
        }
        val files = related.map { it.file }
        val totalBytes = files.sumOf { it.length }

        val tokenEstimate = if (contextFormat != null) {
            related.sumOf { rel ->
                if (!rel.file.isValid || rel.file.length > 1_000_000L) {
                    0
                } else {
                    try {
                        TokenEstimator.estimate(VfsUtilCore.loadText(rel.file))
                    } catch (e: Exception) {
                        0
                    }
                }
            }
        } else 0

        return ExportPreview(files, totalBytes, tokenEstimate)
    }
}