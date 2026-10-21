package io.github.kovalpv.classbundle.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.PsiElement
import io.github.kovalpv.classbundle.ui.ExportDialog

class ExportClassBundleAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val context = e.getData(CommonDataKeys.PSI_ELEMENT) ?: return
        val dialog = ExportDialog(project, context)
        if (dialog.showAndGet()) {
            dialog.doExport()
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}