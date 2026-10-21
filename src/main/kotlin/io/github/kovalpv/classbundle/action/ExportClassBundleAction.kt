package io.github.kovalpv.classbundle.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import io.github.kovalpv.classbundle.ui.ExportDialog

class ExportClassBundleAction : AnAction("Export Class Bundle...") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val psiClass = findTargetClass(e) ?: return

        val className = psiClass.qualifiedName ?: psiClass.name ?: return
        ExportDialog(project, className).show()
    }

    override fun update(e: AnActionEvent) {
        // Показываем действие только если найден Java-класс
        e.presentation.isEnabledAndVisible = findTargetClass(e) != null
    }

    /**
     * Универсальный поиск класса: работает и в редакторе, и в дереве проекта (Project View).
     */
    private fun findTargetClass(e: AnActionEvent): PsiClass? {
        val project = e.project ?: return null

        // 1. Пытаемся получить класс напрямую из PSI_ELEMENT (редактор)
        val element = e.getData(CommonDataKeys.PSI_ELEMENT)
        val fromElement = when (element) {
            is PsiClass -> element
            is PsiFile -> PsiTreeUtil.findChildOfType(element, PsiClass::class.java)
            else -> null
        }
        if (fromElement != null) return fromElement

        // 2. Пытаемся получить класс из PSI_FILE (редактор)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        if (psiFile != null) {
            val fromFile = PsiTreeUtil.findChildOfType(psiFile, PsiClass::class.java)
            if (fromFile != null) return fromFile
        }

        // 3. Пытаемся получить класс из VIRTUAL_FILE (Project View)
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (virtualFile != null && virtualFile.extension == "java") {
            val psi = PsiManager.getInstance(project).findFile(virtualFile)
            if (psi != null) {
                return PsiTreeUtil.findChildOfType(psi, PsiClass::class.java)
            }
        }

        return null
    }
}