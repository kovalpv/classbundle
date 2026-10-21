package io.github.kovalpv.classbundle.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import io.github.kovalpv.classbundle.ClassBundleBundle
import io.github.kovalpv.classbundle.model.ArchiveMode
import io.github.kovalpv.classbundle.model.ExportRequest
import io.github.kovalpv.classbundle.model.ExportResult
import io.github.kovalpv.classbundle.model.PathMode
import io.github.kovalpv.classbundle.model.RelationType
import io.github.kovalpv.classbundle.service.ArchiveWriter
import io.github.kovalpv.classbundle.service.RelatedFilesCollector
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import java.nio.file.Paths
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class ExportDialog(
    private val project: Project,
    private val context: PsiElement
) : DialogWrapper(project) {

    private val relationsCheckBoxes = RelationType.entries.associateWith { relation ->
        JBCheckBox(relationName(relation), relation in setOf(RelationType.USAGE, RelationType.INHERITOR))
    }

    private val archiveModeCombo = ComboBox(ArchiveMode.entries.map { archiveModeName(it) }.toTypedArray())

    private val pathModeCombo = ComboBox(PathMode.entries.map { pathModeName(it) }.toTypedArray())
    private val outputPathField = JBTextField()
    private val browseButton = JButton()
    private var exportResult: ExportResult? = null

    init {
        title = ClassBundleBundle.message("export.dialog.title")
        browseButton.text = ClassBundleBundle.message("export.dialog.browse")
        browseButton.addActionListener { browseForOutputFile() }
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        val top = JPanel()
        top.add(JBLabel(ClassBundleBundle.message("export.dialog.target.label")))
        
        val psiClass = context as? com.intellij.psi.PsiClass
        val targetName = psiClass?.qualifiedName 
            ?: (context as? com.intellij.psi.PsiNamedElement)?.name 
            ?: context.text

        top.add(JBLabel(targetName))
        top.add(Box.createHorizontalGlue())
        panel.add(top, BorderLayout.NORTH)

        val centerPanel = JPanel()
        centerPanel.layout = BoxLayout(centerPanel, BoxLayout.Y_AXIS)
        centerPanel.add(JBLabel(ClassBundleBundle.message("export.dialog.relations.label")))
        relationsCheckBoxes.values.forEach { checkbox ->
            centerPanel.add(Box.createVerticalStrut(4))
            centerPanel.add(checkbox)
        }
        centerPanel.add(Box.createVerticalStrut(12))
        centerPanel.add(JBLabel(ClassBundleBundle.message("export.dialog.archiveMode.label")))
        centerPanel.add(Box.createVerticalStrut(4))
        centerPanel.add(archiveModeCombo)
        centerPanel.add(Box.createVerticalStrut(12))
        centerPanel.add(JBLabel(ClassBundleBundle.message("export.dialog.pathMode.label")))
        centerPanel.add(Box.createVerticalStrut(4))
        centerPanel.add(pathModeCombo)
        centerPanel.add(Box.createVerticalStrut(12))
        centerPanel.add(JBLabel(ClassBundleBundle.message("export.dialog.outputFile.label")))
        centerPanel.add(Box.createVerticalStrut(4))
        val pathRow = JPanel()
        pathRow.add(outputPathField)
        pathRow.add(browseButton)
        centerPanel.add(pathRow)

        panel.add(centerPanel, BorderLayout.CENTER)
        panel.preferredSize = Dimension(480, 420)
        return panel
    }

    private fun browseForOutputFile() {
        val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
        descriptor.setTitle(ClassBundleBundle.message("export.fileChooser.title"))
        val chosen: VirtualFile = FileChooser.chooseFile(descriptor, project, null) ?: return
        outputPathField.text = chosen.path
    }

    private fun selectedRelations(): Set<RelationType> =
        relationsCheckBoxes.filter { (_, checkbox) -> checkbox.isSelected }.keys

    private fun buildRequest(targetClass: PsiClass): ExportRequest {
        val output = outputPathField.text?.trim().orEmpty()
        if (output.isEmpty()) {
            Messages.showErrorDialog(
                project,
                ClassBundleBundle.message("export.error.outputPath"),
                ClassBundleBundle.message("export.error.outputPath.title"),
            )
            throw IllegalStateException("Output path is not specified")
        }
        return ExportRequest(
            project = project,
            targetClass = targetClass,
            relations = selectedRelations(),
            archiveMode = ArchiveMode.entries[archiveModeCombo.selectedIndex],
            pathMode = PathMode.entries[pathModeCombo.selectedIndex],
            outputZip = Paths.get(output),
        )
    }

    fun doExport(): Boolean {
        val targetClass = context.let { element ->
            if (element is PsiClass) element else PsiTreeUtil.getParentOfType(context, PsiClass::class.java)
        }
        if (targetClass == null) {
            Messages.showErrorDialog(
                project,
                ClassBundleBundle.message("export.error.noClass"),
                ClassBundleBundle.message("export.error.noClass.title"),
            )
            return false
        }

        val request = try {
            buildRequest(targetClass)
        } catch (exception: IllegalStateException) {
            return false
        }

        object : Task.Backgroundable(project, ClassBundleBundle.message("export.progress.title"), true) {
            override fun run(indicator: ProgressIndicator) {
                val collector = RelatedFilesCollector()
                val files = collector.collect(request)
                val result = ArchiveWriter().write(request, files)
                exportResult = result
                ApplicationManager.getApplication().invokeLater {
                    if (result.success) {
                        Messages.showInfoMessage(
                            project,
                            ClassBundleBundle.message("export.success", result.exportedCount, result.archivePath),
                            ClassBundleBundle.message("export.success.title"),
                        )
                    } else {
                        Messages.showErrorDialog(
                            project,
                            result.errorMessage ?: ClassBundleBundle.message("export.failure.unknown"),
                            ClassBundleBundle.message("export.failure.title"),
                        )
                    }
                    close(OK_EXIT_CODE)
                }
            }
        }.queue()
        return true
    }

    private fun relationName(relation: RelationType): String =
        when (relation) {
            RelationType.USAGE -> ClassBundleBundle.message("export.relation.USAGE")
            RelationType.INHERITOR -> ClassBundleBundle.message("export.relation.INHERITOR")
            RelationType.NESTED -> ClassBundleBundle.message("export.relation.NESTED")
        }

    private fun archiveModeName(archiveMode: ArchiveMode): String =
        when (archiveMode) {
            ArchiveMode.FILES -> ClassBundleBundle.message("export.archiveMode.FILES")
            ArchiveMode.PATH_LIST -> ClassBundleBundle.message("export.archiveMode.PATHS")
        }

    private fun pathModeName(pathMode: PathMode): String =
        when (pathMode) {
            PathMode.RELATIVE -> ClassBundleBundle.message("export.pathMode.RELATIVE")
            PathMode.ABSOLUTE -> ClassBundleBundle.message("export.pathMode.ABSOLUTE")
        }
}