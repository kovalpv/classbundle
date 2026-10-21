package io.github.kovalpv.classbundle.ui

import com.intellij.ide.bookmark.BookmarkType
import com.intellij.ide.bookmark.BookmarksManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.util.concurrency.AppExecutorUtil
import io.github.kovalpv.classbundle.ClassBundleBundle
import io.github.kovalpv.classbundle.model.ArchiveMode
import io.github.kovalpv.classbundle.model.ExportRequest
import io.github.kovalpv.classbundle.model.PathMode
import io.github.kovalpv.classbundle.model.RelationType
import io.github.kovalpv.classbundle.service.ExportPreferences
import io.github.kovalpv.classbundle.service.ExportService
import io.github.kovalpv.classbundle.service.RelatedFilesCollector
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridLayout
import java.awt.event.ActionEvent
import java.io.File
import java.util.concurrent.Callable
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

internal class ExportDialog(
    private val project: Project,
    private val className: String,
) : DialogWrapper(project) {

    private val collector = RelatedFilesCollector()

    private val usageCheckbox = JCheckBox(ClassBundleBundle.message("relation.usages"))
    private val inheritorsCheckbox = JCheckBox(ClassBundleBundle.message("relation.inheritors"))
    private val nestedCheckbox = JCheckBox(ClassBundleBundle.message("relation.nested"))
    private val selfCheckbox = JCheckBox(ClassBundleBundle.message("relation.self")).apply {
        isSelected = true
        isEnabled = false
    }
    private val dependencyCheckbox = JCheckBox(ClassBundleBundle.message("relation.dependencies"))

    private val relativeRadio = JRadioButton(ClassBundleBundle.message("path.relative"))
    private val absoluteRadio = JRadioButton(ClassBundleBundle.message("path.absolute"))

    private val filesRadio = JRadioButton(ClassBundleBundle.message("archive.files"))
    private val pathsRadio = JRadioButton(ClassBundleBundle.message("archive.paths"))

    private val outputPathField = TextFieldWithBrowseButton()

    private val previewList = JBList<String>(listOf())
    private val previewCountLabel = JBLabel()

    private val targetClass: PsiClass? by lazy {
        runReadAction {
            JavaPsiFacade.getInstance(project).findClass(
                className,
                GlobalSearchScope.projectScope(project)
            )
        }
    }

    init {
        title = ClassBundleBundle.message("dialog.title")

        val prefs = ExportPreferences
        usageCheckbox.isSelected = RelationType.USAGE in prefs.selectedRelations
        inheritorsCheckbox.isSelected = RelationType.INHERITOR in prefs.selectedRelations
        nestedCheckbox.isSelected = RelationType.NESTED in prefs.selectedRelations

        relativeRadio.isSelected = prefs.pathMode == PathMode.RELATIVE
        absoluteRadio.isSelected = prefs.pathMode == PathMode.ABSOLUTE

        filesRadio.isSelected = prefs.archiveMode == ArchiveMode.FILES
        pathsRadio.isSelected = prefs.archiveMode == ArchiveMode.PATH_LIST

        val dir = prefs.outputDir.ifBlank { project.basePath ?: "" }
        outputPathField.text = if (dir.isNotBlank()) {
            "$dir${File.separator}${prefs.filePattern}"
        } else {
            ""
        }

        outputPathField.button.addActionListener {
            val currentText = outputPathField.text.trim()
            val currentFile = if (currentText.isNotEmpty()) File(currentText) else null

            val descriptor = FileSaverDescriptor(
                ClassBundleBundle.message("export.fileChooser.title"),
                "Select a path to save the exported Class Bundle",
                "zip"
            )
            val dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)

            val initialDir = currentFile?.parentFile?.let {
                LocalFileSystem.getInstance().findFileByIoFile(it)
            }
            val initialName = currentFile?.name ?: "classbundle.zip"

            val wrapper = dialog.save(initialDir, initialName)
            if (wrapper != null) {
                var path = wrapper.file.absolutePath
                if (!path.endsWith(".zip", ignoreCase = true)) {
                    path += ".zip"
                }
                outputPathField.text = path
                updateOkState()
            }
        }

        outputPathField.textField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = updateOkState()
            override fun removeUpdate(e: DocumentEvent) = updateOkState()
            override fun changedUpdate(e: DocumentEvent) = updateOkState()
        })

        super.init()
    }

    private fun updateOkState() {
        isOKActionEnabled = canExport()
    }

    override fun createCenterPanel(): JComponent {
        val content = JPanel(BorderLayout(0, 8))
        content.add(createOptionsPanel(), BorderLayout.CENTER)
        content.add(createPreviewPanel(), BorderLayout.SOUTH)
        attachPreviewListeners()
        isOKActionEnabled = canExport()
        refreshPreview()
        return content
    }

    override fun doOKAction() {
        if (!canExport()) return

        val prefs = ExportPreferences
        prefs.selectedRelations = selectedRelations()
        prefs.pathMode = if (relativeRadio.isSelected) PathMode.RELATIVE else PathMode.ABSOLUTE
        prefs.archiveMode = if (filesRadio.isSelected) ArchiveMode.FILES else ArchiveMode.PATH_LIST

        val outputFile = File(outputPathField.text.trim())
        outputFile.parent?.let { prefs.outputDir = it }
        prefs.filePattern = outputFile.name

        val request = try {
            buildRequest()
        } catch (e: IllegalStateException) {
            notifyError(e.message ?: ClassBundleBundle.message("export.error.noFiles"))
            return
        }

        super.doOKAction()

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "Exporting classes", true) {
                override fun run(indicator: ProgressIndicator) {
                    try {
                        ExportService.export(request, indicator)
                    } catch (e: Exception) {
                        notifyError(e.message ?: "Export failed")
                    }
                }
            }
        )
    }

    override fun createLeftSideActions(): Array<Action> {
        val addToFavoritesAction =
            object : AbstractAction(ClassBundleBundle.message("action.addToFavorites")) {
                override fun actionPerformed(e: ActionEvent?) {
                    addToFavorites()
                }
            }
        return arrayOf(addToFavoritesAction)
    }

    private fun addToFavorites() {
        val relations = selectedRelations()
        val cls = targetClass ?: return

        if (relations.isEmpty()) {
            notifyError(ClassBundleBundle.message("export.error.noFiles"))
            return
        }

        val defaultName = className.substringAfterLast('.').ifBlank { "ClassBundle" }

        val listName = Messages.showInputDialog(
            project,
            ClassBundleBundle.message("dialog.favorites.prompt"),
            ClassBundleBundle.message("dialog.favorites.title"),
            Messages.getQuestionIcon(),
            defaultName,
            null
        ) ?: return

        if (listName.isBlank()) return

        ReadAction.nonBlocking(Callable {
            RelatedFilesCollector().collect(project, cls, relations)
        })
            .inSmartMode(project)
            .finishOnUiThread(ModalityState.any()) { files ->
                if (files.isEmpty()) {
                    notifyError(ClassBundleBundle.message("export.error.noFiles"))
                    return@finishOnUiThread
                }

                try {
                    val bookmarksManager =
                        BookmarksManager.getInstance(project) ?: return@finishOnUiThread

                    var group = bookmarksManager.getGroup(listName)
                    if (group == null) {
                        group = bookmarksManager.addGroup(listName, true)
                    }

                    if (group == null) return@finishOnUiThread

                    val allBookmarks = bookmarksManager.bookmarks
                    for (bookmark in allBookmarks) {
                        if (bookmarksManager.getGroups(bookmark).contains(group)) {
                            bookmarksManager.remove(bookmark)
                        }
                    }

                    val sortedFiles = files.sortedBy { file ->
                        val psiFile = PsiManager.getInstance(project).findFile(file)
                        val psiClass =
                            psiFile?.let { PsiTreeUtil.findChildOfType(it, PsiClass::class.java) }
                        psiClass?.qualifiedName ?: file.name
                    }

                    val commonPrefix = getCommonPackagePrefix(project, sortedFiles)

                    for (file in sortedFiles) {
                        val bookmark = bookmarksManager.createBookmark(file)
                        if (bookmark != null) {
                            val psiFile = PsiManager.getInstance(project).findFile(file)
                            val psiClass = psiFile?.let {
                                PsiTreeUtil.findChildOfType(
                                    it,
                                    PsiClass::class.java
                                )
                            }
                            val qualifiedName = psiClass?.qualifiedName ?: file.name

                            val description =
                                if (commonPrefix.isNotEmpty() && qualifiedName.startsWith(
                                        commonPrefix
                                    )
                                ) {
                                    qualifiedName.removePrefix(commonPrefix)
                                } else {
                                    qualifiedName
                                }

                            group.add(bookmark, BookmarkType.DEFAULT, description)
                        }
                    }

                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("ClassBundle")
                        .createNotification(
                            ClassBundleBundle.message(
                                "notification.favorites.success",
                                files.size,
                                listName
                            ),
                            NotificationType.INFORMATION
                        )
                        .notify(project)

                } catch (e: Exception) {
                    notifyError("Failed to add to bookmarks: ${e.message}")
                }
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun getCommonPackagePrefix(project: Project, files: List<VirtualFile>): String {
        val packages = files.mapNotNull { file ->
            val psiFile = PsiManager.getInstance(project).findFile(file)
            val psiClass = psiFile?.let { PsiTreeUtil.findChildOfType(it, PsiClass::class.java) }
            psiClass?.qualifiedName?.substringBeforeLast('.')
        }.distinct()

        if (packages.size <= 1) return ""

        val splitPackages = packages.map { it.split('.') }
        val first = splitPackages.first()
        var prefixLength = 0

        for (i in first.indices) {
            if (splitPackages.all { it.size > i && it[i] == first[i] }) {
                prefixLength++
            } else {
                break
            }
        }

        return if (prefixLength > 0) first.take(prefixLength).joinToString(".") + "." else ""
    }

    private fun createOptionsPanel(): JPanel {
        val includePanel = JPanel(GridLayout(0, 1, 0, 4))
        includePanel.add(usageCheckbox)
        includePanel.add(inheritorsCheckbox)
        includePanel.add(nestedCheckbox)
        includePanel.add(dependencyCheckbox)
        includePanel.add(selfCheckbox)

        val pathPanel = JPanel(GridLayout(0, 1, 0, 4))
        pathPanel.add(relativeRadio)
        pathPanel.add(absoluteRadio)
        val pathGroup = ButtonGroup()
        pathGroup.add(relativeRadio)
        pathGroup.add(absoluteRadio)

        val archivePanel = JPanel(GridLayout(0, 1, 0, 4))
        archivePanel.add(filesRadio)
        archivePanel.add(pathsRadio)
        val archiveGroup = ButtonGroup()
        archiveGroup.add(filesRadio)
        archiveGroup.add(pathsRadio)

        val outputPanel = JPanel(BorderLayout(8, 0))
        outputPanel.add(JBLabel(ClassBundleBundle.message("output.label")), BorderLayout.WEST)
        outputPanel.add(outputPathField, BorderLayout.CENTER)

        val box = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        for (panel in listOf(includePanel, pathPanel, archivePanel, outputPanel)) {
            box.add(panel)
            box.add(Box.createVerticalStrut(10))
        }
        return box
    }

    private fun createPreviewPanel(): JComponent {
        previewList.isEnabled = false
        previewList.emptyText.text = ClassBundleBundle.message("preview.empty")
        previewCountLabel.text = ClassBundleBundle.message("preview.count", 0)
        val panel = JPanel(BorderLayout(0, 4))
        panel.add(previewCountLabel, BorderLayout.NORTH)
        val scroll = JScrollPane(previewList)
        scroll.preferredSize = Dimension(scroll.preferredSize.width, 180)
        panel.add(scroll, BorderLayout.CENTER)
        return panel
    }

    private fun canExport(): Boolean =
        outputPathField.text.isNotBlank() && targetClass != null && selectedRelations().isNotEmpty()

    private fun attachPreviewListeners() {
        val listener = java.awt.event.ItemListener { _ ->
            isOKActionEnabled = canExport()
            refreshPreview()
        }
        for (component in listOf(
            usageCheckbox, inheritorsCheckbox, nestedCheckbox,
            relativeRadio, absoluteRadio, filesRadio, pathsRadio
        )) {
            component.addItemListener(listener)
        }
    }

    private fun selectedRelations(): Set<RelationType> = buildSet {
        if (usageCheckbox.isSelected) add(RelationType.USAGE)
        if (inheritorsCheckbox.isSelected) add(RelationType.INHERITOR)
        if (nestedCheckbox.isSelected) add(RelationType.NESTED)
        if (dependencyCheckbox.isSelected) add(RelationType.DEPENDENCY)
    }

    private fun buildRequest(): ExportRequest {
        val cls = targetClass
        checkNotNull(cls) { "Target class '$className' not found in project" }
        val pathMode = if (relativeRadio.isSelected) PathMode.RELATIVE else PathMode.ABSOLUTE

        val archiveMode = if (filesRadio.isSelected) ArchiveMode.FILES else ArchiveMode.PATH_LIST

        val outputZip = File(outputPathField.text.trim()).toPath()
        return ExportRequest(
            targetClass = cls,
            relations = selectedRelations(),
            pathMode = pathMode,
            archiveMode = archiveMode,
            outputZip = outputZip,
            project = project
        )
    }

    private fun refreshPreview() {
        val relations = selectedRelations()
        val cls = targetClass
        if (cls == null || relations.isEmpty()) {
            previewList.setListData(arrayOf())
            previewCountLabel.text = ClassBundleBundle.message("preview.count", 0)
            return
        }

        ReadAction.nonBlocking(Callable {
            val previewResult = ExportService.preview(project, cls, relations)
            previewResult.files.map { it.toString() }
        })
            .inSmartMode(project)
            .coalesceBy(this)
            .finishOnUiThread(ModalityState.any()) { fileNames ->
                previewList.setListData(fileNames.toTypedArray())
                previewCountLabel.text = ClassBundleBundle.message("preview.count", fileNames.size)
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun notifyError(message: String) {
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("ClassBundle")
                .createNotification(
                    ClassBundleBundle.message("notification.error.title"),
                    message,
                    NotificationType.ERROR
                )
                .notify(project)
        } catch (e: Exception) {
            // Фоллбек
        }
    }
}