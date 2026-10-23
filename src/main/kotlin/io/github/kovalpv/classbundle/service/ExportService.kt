package io.github.kovalpv.classbundle.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiClass
import io.github.kovalpv.classbundle.ClassBundleBundle
import io.github.kovalpv.classbundle.model.*
import java.awt.datatransfer.StringSelection

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
            throw IllegalArgumentException(ClassBundleBundle.message("export.error.noFiles"))
        }
        indicator.checkCanceled()
        indicator.text = ClassBundleBundle.message("export.progress.writing")

        return when (request.outputMode) {
            OutputMode.FILES_ZIP, OutputMode.PATHS_ZIP ->
                ArchiveWriter().write(request, related.map { it.file })

            OutputMode.CONTEXT_FILE -> {
                if (request.copyToClipboard) {
                    val content = ContextBundleFormatter.format(request, related)
                    copyToClipboard(content)
                    ExportResult(
                        outputPath = null,
                        exportedCount = related.size,
                        failedCount = 0,
                        skippedFiles = emptyList(),
                    )
                } else {
                    ContextBundleWriter().write(request, related)
                }
            }
        }
    }

    private fun copyToClipboard(content: String) {
        ApplicationManager.getApplication().invokeLater({
            CopyPasteManager.getInstance().setContents(StringSelection(content))
        }, ModalityState.any())
    }

    /**
     * Считает превью для диалога (FR-11). Если [contextFormat] не `null`,
     * дополнительно оценивает количество токенов.
     *
     * Всегда вызывает [RelatedFilesCollector] — он сам добавляет target-файл
     * даже при пустом наборе [relations]. Раньше ранний `return emptyList()`
     * приводил к ложному «0 file(s)» в диалоге.
     */
    fun preview(
        project: Project,
        targetClass: PsiClass,
        relations: Set<RelationType>,
        contextFormat: ContextFormat? = null,
    ): ExportPreview {
        val related = RelatedFilesCollector().collect(project, targetClass, relations)
        val files = related.map { it.file }
        val totalBytes = files.sumOf { it.length }

        val tokenEstimate = if (contextFormat != null) {
            related.sumOf { rel ->
                if (!rel.file.isValid || rel.file.length > 1_000_000L) 0
                else try {
                    TokenEstimator.estimate(VfsUtilCore.loadText(rel.file))
                } catch (e: Exception) {
                    0
                }
            }
        } else 0

        return ExportPreview(files, totalBytes, tokenEstimate)
    }
}