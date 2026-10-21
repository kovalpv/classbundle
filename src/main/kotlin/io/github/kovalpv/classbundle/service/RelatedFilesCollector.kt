package io.github.kovalpv.classbundle.service

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.ReferencesSearch
import io.github.kovalpv.classbundle.model.ExportRequest
import io.github.kovalpv.classbundle.model.RelationType

/**
 * Сборка списка связанных файлов (FR-4, FR-5, FR-6, FR-7, SDD §9).
 *
 * Правила:
 * - USAGES — `ReferencesSearch.search(psiClass, projectScope, …)` → `containingFile.virtualFile`;
 * - INHERITORS — `ClassInheritorsSearch.search(psiClass, projectScope, true)` → `containingFile.virtualFile`;
 * - NESTED — `psiClass.nestedClasses` → `containingFile.virtualFile`;
 * - файл целевого класса (self) всегда включается, независимо от выбранных связей (FR-6);
 * - дедупликация по `VirtualFile` (FR-7).
 *
 * Все обращения к PSI выполняются внутри `ReadAction`, вызов возможен
 * из фонового потока (а не только из EDT). Ошибки PSI (исчезнувшие
 * элементы) логируются на уровне warn и пропускаются (SDD §9).
 */
internal class RelatedFilesCollector {

    private val logger = Logger.getInstance(RelatedFilesCollector::class.java)

    /**
     * Возвращает уникальный отсортированный по пути набор связанных файлов.
     * Вызов разрешён из фонового потока; read-action приобретается внутри.
     */
    fun collect(project: Project, targetClass: PsiClass, relations: Set<RelationType>): List<VirtualFile> =
        ReadAction.compute<List<VirtualFile>, Throwable> {
            doCollect(project, targetClass, relations)
        }

    /** Удобная обёртка: параметры берутся из готового [ExportRequest]. */
    fun collect(request: ExportRequest): List<VirtualFile> =
        collect(request.project, request.targetClass, request.relations)

    private fun doCollect(project: Project, targetClass: PsiClass, relations: Set<RelationType>): List<VirtualFile> {
        if (!targetClass.isValid) {
            logger.warn("Target class is invalid, nothing to collect: ${safeName(targetClass)}")
            return emptyList()
        }

        val result = LinkedHashSet<VirtualFile>()
        addFile(targetClass.containingFile?.virtualFile, result)

        val scope = GlobalSearchScope.projectScope(project)
        relations.forEach { relation ->
            try {
                when (relation) {
                    RelationType.USAGE -> collectUsages(targetClass, scope, result)
                    RelationType.INHERITOR -> collectInheritors(targetClass, scope, result)
                    RelationType.NESTED -> collectNested(targetClass, result)
                }
            } catch (e: Exception) {
                // PSI error (invalid element) → logger.warn, skip (SDD §9)
                logger.warn("PSI search failed for relation $relation: ${e.message}", e)
            }
        }

        logger.info("Collected ${result.size} related files for ${safeName(targetClass)}")
        return result.toList().sortedBy { it.path }
    }

    private fun collectUsages(targetClass: PsiClass, scope: GlobalSearchScope, result: MutableSet<VirtualFile>) {
        // ReferencesSearch.search(targetClass, scope).forEach { reference ->
        //     addFile(reference.element.containingFile?.virtualFile, result)
        // }
        ReferencesSearch.search(targetClass, scope).forEach { reference ->
            val psiElement = reference.element
            if (psiElement != null) {
                addFile(psiElement.containingFile?.virtualFile, result)
            }
        }
    }

    private fun collectInheritors(targetClass: PsiClass, scope: GlobalSearchScope, result: MutableSet<VirtualFile>) {
        ClassInheritorsSearch.search(targetClass, scope, true).forEach { inheritor ->
            addFile(inheritor.containingFile?.virtualFile, result)
        }
    }

    private fun collectNested(targetClass: PsiClass, result: MutableSet<VirtualFile>) {
        targetClass.allInnerClasses.forEach { nested ->
            addFile(nested.containingFile?.virtualFile, result)
        }
    }

    private fun addFile(file: VirtualFile?, result: MutableSet<VirtualFile>) {
        if (file != null && file.isValid) {
            result.add(file)
        }
    }

    private fun safeName(targetClass: PsiClass): String =
        try {
            targetClass.qualifiedName ?: targetClass.name ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
}