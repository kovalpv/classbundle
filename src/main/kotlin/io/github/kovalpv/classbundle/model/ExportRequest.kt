package io.github.kovalpv.classbundle.model

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import java.nio.file.Path

/**
 * Immutable-запрос на экспорт связных файлов (SDD §3.2).
 *
 * @param targetClass     целевой Java-класс, выбранный пользователем;
 * @param relations       множество типов связей для поиска (FR-2...FR-6);
 * @param pathMode        режим представления путей в списке (FR-7, FR-8);
 * @param outputMode      режим вывода (ZIP с файлами / ZIP со списком / context bundle);
 * @param contextFormat   формат содержимого для [OutputMode.CONTEXT_FILE];
 * @param maxTokens       soft-limit токенов для [OutputMode.CONTEXT_FILE] (`null` = без лимита);
 * @param outputPath      целевой путь (ZIP или текстовый файл); `null` при `copyToClipboard = true`;
 * @param copyToClipboard если `true` — содержимое пишется только в буфер обмена, файл не создаётся;
 * @param project         проект IntelliJ, в котором выполняется поиск.
 */
data class ExportRequest(
    val targetClass: PsiClass,
    val relations: Set<RelationType>,
    val pathMode: PathMode,
    val outputMode: OutputMode,
    val contextFormat: ContextFormat,
    val maxTokens: Int?,
    val outputPath: Path?,
    val copyToClipboard: Boolean = false,
    val project: Project,
) {
    /**
     * Корень, от которого считаются относительные пути (FR-7).
     * `null`, если у проекта нет базового каталога.
     */
    val projectBasePath: Path?
        get() = project.basePath?.let { Path.of(it) }
}