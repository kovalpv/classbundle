package io.github.kovalpv.classbundle.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import io.github.kovalpv.classbundle.ui.ExportDialog

class ExportClassBundleAction : AnAction() {

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = resolveTargetClass(e) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val psiClass = resolveTargetClass(e) ?: return
        val dialog = ExportDialog(project, psiClass)
        if (dialog.showAndGet()) {
            dialog.doExport()
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private fun resolveTargetClass(e: AnActionEvent): PsiClass? {
        val context = e.getData(CommonDataKeys.PSI_ELEMENT) ?: return null
        val classOrKtClass = if (context is PsiClass) {
            context
        } else {
            context.containingKtClass
        }
        if (classOrKtClass != null) return classOrKtClass.java
        return context.containingClass
    }
}