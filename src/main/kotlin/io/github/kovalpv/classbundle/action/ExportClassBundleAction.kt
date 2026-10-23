package io.github.kovalpv.classbundle.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import io.github.kovalpv.classbundle.ClassBundleBundle
import io.github.kovalpv.classbundle.ui.ExportDialog
import javax.swing.Icon

class ExportClassBundleAction : AnAction(
    ClassBundleBundle.messagePointer("action.exportClassBundle.text"),
    ClassBundleBundle.messagePointer("action.exportClassBundle.description"),
    AllIcons.Actions.Upload
) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val psiClass = findTargetClass(e) ?: return

        val className = psiClass.qualifiedName ?: psiClass.name ?: return
        ExportDialog(project, className).show()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = findTargetClass(e) != null
    }

    /**
     * Универсальный поиск класса: работает и в редакторе, и в дереве проекта (Project View).
     */
    private fun findTargetClass(e: AnActionEvent): PsiClass? {
        val project = e.project ?: return null

        val fromElement = when (val element = e.getData(CommonDataKeys.PSI_ELEMENT)) {
            is PsiClass -> element
            is PsiFile -> PsiTreeUtil.findChildOfType(element, PsiClass::class.java)
            else -> null
        }
        if (fromElement != null) return fromElement

        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        if (psiFile != null) {
            val fromFile = PsiTreeUtil.findChildOfType(psiFile, PsiClass::class.java)
            if (fromFile != null) return fromFile
        }

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