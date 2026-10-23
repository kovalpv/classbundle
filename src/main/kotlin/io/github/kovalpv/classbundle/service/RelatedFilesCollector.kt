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
import io.github.kovalpv.classbundle.model.RelationType

/**
 * Сборка списка связанных файлов (FR-4, FR-5, FR-6, FR-7, SDD §9).
 *
 * Все обращения к PSI выполняются внутри `ReadAction`; вызов возможен
 * из фонового потока. Ошибки PSI (исчезнувшие элементы) логируются на
 * уровне warn и пропускаются. Control-flow исключения
 * ([ProcessCanceledException], [IndexNotReadyException]) пробрасываются:
 * это сигналы «отменились» и «повтори позже», а не ошибка конкретной связи.
 */
internal class RelatedFilesCollector {

    private val logger = Logger.getInstance(RelatedFilesCollector::class.java)

    fun collect(
        project: Project,
        targetClass: PsiClass,
        relations: Set<RelationType>
    ): List<VirtualFile> =
        ReadAction.compute<List<VirtualFile>, Throwable> {
            doCollect(project, targetClass, relations)
        }

    fun collect(request: ExportRequest): List<VirtualFile> =
        collect(request.project, request.targetClass, request.relations)

    private fun doCollect(
        project: Project,
        targetClass: PsiClass,
        relations: Set<RelationType>
    ): List<VirtualFile> {
        if (!targetClass.isValid) {
            logger.warn("Target class is invalid, nothing to collect: ${safeName(targetClass)}")
            return emptyList()
        }

        val result = LinkedHashSet<VirtualFile>()
        addFile(targetClass.containingFile.virtualFile, result)

        val scope = GlobalSearchScope.projectScope(project)

        relations.forEach { relation ->
            try {
                when (relation) {
                    RelationType.USAGE -> collectUsages(targetClass, scope, result)
                    RelationType.INHERITOR -> collectInheritors(targetClass, scope, result)
                    RelationType.NESTED -> collectNested(targetClass, result)
                    RelationType.DEPENDENCY -> collectDependencies(project, targetClass, result)
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
        return result.toList().sortedBy { it.path }
    }

    private fun collectUsages(
        targetClass: PsiClass,
        scope: GlobalSearchScope,
        result: MutableSet<VirtualFile>
    ) {
        ReferencesSearch.search(targetClass, scope).forEach { reference ->
            addFile(reference.element.containingFile?.virtualFile, result)
        }
    }

    private fun collectInheritors(
        targetClass: PsiClass,
        scope: GlobalSearchScope,
        result: MutableSet<VirtualFile>
    ) {
        ClassInheritorsSearch.search(targetClass, scope, true).forEach { inheritor ->
            addFile(inheritor.containingFile?.virtualFile, result)
        }
    }

    private fun collectNested(targetClass: PsiClass, result: MutableSet<VirtualFile>) {
        targetClass.innerClasses.forEach { nested ->
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

    private fun collectDependencies(
        project: Project,
        targetClass: PsiClass,
        result: MutableSet<VirtualFile>
    ) {
        val projectScope = GlobalSearchScope.projectScope(project)

        targetClass.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitReferenceElement(reference: PsiJavaCodeReferenceElement) {
                super.visitReferenceElement(reference)

                val resolved: PsiClass? = try {
                    reference.resolve() as? PsiClass
                } catch (e: ProcessCanceledException) {
                    throw e
                } catch (e: IndexNotReadyException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn("Failed to resolve reference: ${reference.text}", e)
                    null
                }
                if (resolved == null || resolved == targetClass) return

                val virtualFile = resolved.containingFile?.virtualFile ?: return
                if (projectScope.contains(virtualFile)) {
                    addFile(virtualFile, result)
                }
            }
        })
    }
}