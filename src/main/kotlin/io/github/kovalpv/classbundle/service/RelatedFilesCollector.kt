package io.github.kovalpv.classbundle.service

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.ReferencesSearch
import io.github.kovalpv.classbundle.model.ExportRequest
import io.github.kovalpv.classbundle.model.FileRole
import io.github.kovalpv.classbundle.model.RelatedFile
import io.github.kovalpv.classbundle.model.RelationType
import io.github.kovalpv.classbundle.service.RelatedFilesCollector.Companion.MAX_TRANSITIVE_FILES

/**
 * Сборка списка связанных файлов с ролями (FR-4, FR-5, FR-6, FR-7, SDD §9).
 *
 * Все обращения к PSI выполняются внутри `ReadAction`. Control-flow исключения
 * ([ProcessCanceledException], [IndexNotReadyException]) пробрасываются: это
 * сигналы «отменились» и «повтори позже», а не ошибка конкретной связи.
 *
 * Возвращает файлы, отсортированные по [FileRole.priority] (target первым),
 * внутри одной роли — по пути.
 */
internal class RelatedFilesCollector {

    private val logger = Logger.getInstance(RelatedFilesCollector::class.java)

    fun collect(
        project: Project,
        targetClass: PsiClass,
        relations: Set<RelationType>,
    ): List<RelatedFile> =
        ReadAction.compute<List<RelatedFile>, Throwable> {
            doCollect(project, targetClass, relations)
        }

    fun collect(request: ExportRequest): List<RelatedFile> =
        collect(request.project, request.targetClass, request.relations)

    private fun doCollect(
        project: Project,
        targetClass: PsiClass,
        relations: Set<RelationType>,
    ): List<RelatedFile> {
        if (!targetClass.isValid) {
            logger.warn("Target class is invalid, nothing to collect: ${safeName(targetClass)}")
            return emptyList()
        }

        val result = LinkedHashMap<VirtualFile, FileRole>()
        targetClass.containingFile?.virtualFile?.let { addFile(it, FileRole.TARGET, result) }

        val scope = GlobalSearchScope.projectScope(project)
        val hasTransitive = RelationType.TRANSITIVE_DEPENDENCY in relations

        relations.forEach { relation ->
            try {
                when (relation) {
                    RelationType.USAGE ->
                        collectUsages(targetClass, scope, result)

                    RelationType.INHERITOR ->
                        collectInheritors(targetClass, scope, result)

                    RelationType.NESTED ->
                        collectNested(targetClass, result)

                    RelationType.DEPENDENCY ->
                        if (!hasTransitive) {
                            // Если выбрана транзитивная зависимость, прямой поиск
                            // не запускаем — она включает прямые как подмножество.
                            collectDirectDependencies(project, targetClass, result)
                        }

                    RelationType.TRANSITIVE_DEPENDENCY ->
                        collectTransitiveDependencies(project, targetClass, scope, result)
                }
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: IndexNotReadyException) {
                throw e
            } catch (e: Exception) {
                logger.warn("PSI search failed for relation $relation", e)
            }
        }

        logger.info("Collected ${result.size} related files for ${safeName(targetClass)}")
        return result.map { (file, role) -> RelatedFile(file, role) }
            .sortedWith(compareBy({ it.role.priority }, { it.file.path }))
    }

    private fun collectUsages(
        targetClass: PsiClass,
        scope: GlobalSearchScope,
        result: MutableMap<VirtualFile, FileRole>,
    ) {
        ReferencesSearch.search(targetClass, scope).forEach { reference ->
            addFile(reference.element.containingFile?.virtualFile, FileRole.USAGE, result)
        }
    }

    private fun collectInheritors(
        targetClass: PsiClass,
        scope: GlobalSearchScope,
        result: MutableMap<VirtualFile, FileRole>,
    ) {
        ClassInheritorsSearch.search(targetClass, scope, true).forEach { inheritor ->
            addFile(inheritor.containingFile?.virtualFile, FileRole.INHERITOR, result)
        }
    }

    private fun collectNested(
        targetClass: PsiClass,
        result: MutableMap<VirtualFile, FileRole>,
    ) {
        targetClass.innerClasses.forEach { nested ->
            addFile(nested.containingFile?.virtualFile, FileRole.NESTED, result)
        }
    }

    /** Прямые зависимости целевого класса: один уровень ссылок. */
    private fun collectDirectDependencies(
        project: Project,
        targetClass: PsiClass,
        result: MutableMap<VirtualFile, FileRole>,
    ) {
        val projectScope = GlobalSearchScope.projectScope(project)
        targetClass.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitReferenceElement(reference: PsiJavaCodeReferenceElement) {
                super.visitReferenceElement(reference)
                val resolved = resolveOrNull(reference) ?: return
                if (resolved == targetClass) return

                val virtualFile = resolved.containingFile?.virtualFile ?: return
                if (projectScope.contains(virtualFile)) {
                    addFile(virtualFile, FileRole.DEPENDENCY, result)
                }
            }
        })
    }

    /**
     * Транзитивные зависимости: BFS по графу ссылок, начиная с [targetClass].
     * Включает прямые зависимости как подмножество.
     *
     * Ограничения:
     * - посещаются только классы в [GlobalSearchScope.projectScope];
     * - защита от «утащить пол-проекта» через [MAX_TRANSITIVE_FILES];
     * - циклы обрезаются через `visited`.
     */
    private fun collectTransitiveDependencies(
        project: Project,
        targetClass: PsiClass,
        scope: GlobalSearchScope,
        result: MutableMap<VirtualFile, FileRole>,
    ) {
        val visited = mutableSetOf<PsiClass>()
        visited.add(targetClass)
        val queue = ArrayDeque<PsiClass>()
        queue.add(targetClass)
        var filesAdded = 0

        while (queue.isNotEmpty()) {
            if (filesAdded >= MAX_TRANSITIVE_FILES) {
                logger.warn(
                    "Transitive dependency walk stopped at $MAX_TRANSITIVE_FILES files; " +
                            "consider narrowing relations"
                )
                return
            }

            val current = queue.removeFirst()
            val refs = mutableListOf<PsiClass>()
            current.accept(object : JavaRecursiveElementWalkingVisitor() {
                override fun visitReferenceElement(reference: PsiJavaCodeReferenceElement) {
                    super.visitReferenceElement(reference)
                    val resolved = resolveOrNull(reference) ?: return
                    refs.add(resolved)
                }
            })

            for (resolved in refs) {
                if (!visited.add(resolved)) continue
                val virtualFile = resolved.containingFile?.virtualFile ?: continue
                if (!scope.contains(virtualFile)) continue

                val sizeBefore = result.size
                addFile(virtualFile, FileRole.DEPENDENCY, result)
                if (result.size > sizeBefore) filesAdded++

                queue.addLast(resolved)
            }
        }
    }

    /**
     * `reference.resolve()` с изоляцией control-flow и прочих ошибок:
     * `ProcessCanceledException` и `IndexNotReadyException` пробрасываются,
     * остальные логируются как `warn` и превращаются в `null`.
     */
    private fun resolveOrNull(reference: PsiJavaCodeReferenceElement): PsiClass? =
        try {
            reference.resolve() as? PsiClass
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: IndexNotReadyException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Failed to resolve reference: ${reference.text}", e)
            null
        }

    private fun addFile(
        file: VirtualFile?,
        role: FileRole,
        result: MutableMap<VirtualFile, FileRole>,
    ) {
        if (file == null || !file.isValid) return
        val existing = result[file]
        if (existing == null || role.priority < existing.priority) {
            result[file] = role
        }
    }

    private fun safeName(targetClass: PsiClass): String =
        try {
            targetClass.qualifiedName ?: targetClass.name ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }

    private companion object {
        /** Верхняя граница числа файлов в транзитивном обходе. */
        private const val MAX_TRANSITIVE_FILES = 5_000
    }
}
