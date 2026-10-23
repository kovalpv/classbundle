package io.github.kovalpv.classbundle.model

import java.nio.file.Path

/**
 * Результат экспорта (SDD §3.2, §9).
 *
 * @param outputPath     целевой путь (ZIP или context-файл);
 * @param exportedCount  количество успешно упакованных файлов;
 * @param failedCount    количество файлов, пропущенных из-за ошибки I/O;
 * @param skippedFiles   имена пропущенных файлов (невалидные/исчезнувшие);
 * @param errorMessage   текст ошибки, если экспорт завершился неудачей, иначе `null`.
 */
internal data class ExportResult(
    val outputPath: Path,
    val exportedCount: Int,
    val failedCount: Int,
    val skippedFiles: List<String>,
    val errorMessage: String? = null,
) {
    val success: Boolean
        get() = errorMessage == null && failedCount == 0

    companion object {
        fun failure(outputPath: Path, message: String): ExportResult = ExportResult(
            outputPath = outputPath,
            exportedCount = 0,
            failedCount = 0,
            skippedFiles = emptyList(),
            errorMessage = message,
        )
    }
}