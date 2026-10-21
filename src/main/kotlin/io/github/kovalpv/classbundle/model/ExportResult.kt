package io.github.kovalpv.classbundle.model

import java.nio.file.Path

/**
 * Результат экспорта (SDD §3.2, §9).
 *
 * Создаётся сервисами [io.github.kovalpv.classbundle.service] и
 * используется UI-слоем для отображения уведомления.
 *
 * @param archivePath целевой путь ZIP-архива (даже при ошибке);
 * @param exportedCount количество успешно упакованных файлов;
 * @param failedCount количество файлов, пропущенных из-за ошибки I/O;
 * @param skippedFiles имена пропущенных файлов (невалидные/исчезнувшие);
 * @param errorMessage текст ошибки, если экспорт завершился неудачей, иначе `null`.
 */
data class ExportResult(
    val archivePath: Path,
    val exportedCount: Int,
    val failedCount: Int,
    val skippedFiles: List<String>,
    val errorMessage: String? = null
) {
    val success: Boolean
        get() = errorMessage == null && failedCount == 0

    companion object {
        fun failure(archivePath: Path, message: String): ExportResult = ExportResult(
            archivePath = archivePath,
            exportedCount = 0,
            failedCount = 0,
            skippedFiles = emptyList(),
            errorMessage = message
        )
    }
}
