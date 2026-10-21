package io.github.kovalpv.classbundle.model

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import java.nio.file.Path

/**
 * Immutable-запрос на экспорт связных файлов (SDD §3.2).
 *
 * Создаётся только диалогом [io.github.kovalpv.classbundle.ui.ExportDialog]
 * на основе выбора пользователя; потребляется сервисами слоя `service`.
 *
 * @param targetClass целевой Java-класс, выбранный пользователем;
 * @param relations множество типов связей для поиска (FR-2…FR-6);
 * @param pathMode режим представления путей в списке (FR-7, FR-8);
 * @param archiveMode режим содержимого архива (FR-11, FR-12);
 * @param outputZip целевой путь ZIP-архива;
 * @param project проект IntelliJ, в котором выполняется поиск.
 */
data class ExportRequest(
    val targetClass: PsiClass,
    val relations: Set<RelationType>,
    val pathMode: PathMode,
    val archiveMode: ArchiveMode,
    val outputZip: Path,
    val project: Project
) {
    /**
     * Корень, от которого считаются относительные пути (FR-7).
     * `null`, если у проекта нет базового каталога.
     */
    val projectBasePath: Path?
        get() = project.basePath?.let { Path.of(it) }
}
