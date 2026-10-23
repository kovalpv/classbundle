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
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
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
import com.intellij.ui.components.JBTextField
import com.intellij.util.Alarm
import com.intellij.util.concurrency.AppExecutorUtil
import io.github.kovalpv.classbundle.ClassBundleBundle
import io.github.kovalpv.classbundle.model.*
import io.github.kovalpv.classbundle.service.ExportPreferences
import io.github.kovalpv.classbundle.service.ExportService
import io.github.kovalpv.classbundle.service.RelatedFilesCollector
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
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

    // --- Preview infrastructure ----------------------------------------------
    private var previewSeq: Int = 0
    private val previewAlarm = Alarm(disposable)

    // --- UI controls ---------------------------------------------------------
    private val usageCheckbox = JCheckBox(ClassBundleBundle.message("relation.usages"))
    private val inheritorsCheckbox = JCheckBox(ClassBundleBundle.message("relation.inheritors"))
    private val nestedCheckbox = JCheckBox(ClassBundleBundle.message("relation.nested"))
    private val selfCheckbox = JCheckBox(ClassBundleBundle.message("relation.self")).apply {
        isSelected = true
        isEnabled = false
    }
    private val dependencyCheckbox = JCheckBox(ClassBundleBundle.message("relation.dependencies"))
    private val transitiveDependencyCheckbox =
        JCheckBox(ClassBundleBundle.message("relation.dependencies.transitive"))

    private val relativeRadio = JRadioButton(ClassBundleBundle.message("path.relative"))
    private val absoluteRadio = JRadioButton(ClassBundleBundle.message("path.absolute"))

    private val filesRadio = JRadioButton(ClassBundleBundle.message("archive.files"))
    private val pathsRadio = JRadioButton(ClassBundleBundle.message("archive.paths"))
    private val contextRadio = JRadioButton(ClassBundleBundle.message("output.context"))

    private val contextFormatLabel = JBLabel(ClassBundleBundle.message("output.context.format"))
    private val contextFormatCombo = JComboBox(ContextFormat.entries.toTypedArray()).apply {
        renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: javax.swing.JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): java.awt.Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                if (value is ContextFormat) text = value.displayName
                return this
            }
        }
    }
    private val maxTokensLabel = JBLabel(ClassBundleBundle.message("output.context.maxTokens"))
    private val maxTokensField = JBTextField(ExportPreferences.DEFAULT_MAX_TOKENS.toString(), 8)

    private val outputPathField = TextFieldWithBrowseButton()
    private val previewList = JBList<String>(listOf())
    private val previewCountLabel = JBLabel()

    private val targetClass: PsiClass? by lazy {
        runReadAction {
            JavaPsiFacade.getInstance(project).findClass(
                className,
                GlobalSearchScope.projectScope(project),
            )
        }
    }

    init {
        title = ClassBundleBundle.message("dialog.title")

        val prefs = ExportPreferences
        usageCheckbox.isSelected = RelationType.USAGE in prefs.selectedRelations
        inheritorsCheckbox.isSelected = RelationType.INHERITOR in prefs.selectedRelations
        nestedCheckbox.isSelected = RelationType.NESTED in prefs.selectedRelations
        dependencyCheckbox.isSelected = RelationType.DEPENDENCY in prefs.selectedRelations
        transitiveDependencyCheckbox.isSelected =
            RelationType.TRANSITIVE_DEPENDENCY in prefs.selectedRelations

        relativeRadio.isSelected = prefs.pathMode == PathMode.RELATIVE
        absoluteRadio.isSelected = prefs.pathMode == PathMode.ABSOLUTE

        filesRadio.isSelected = prefs.outputMode == OutputMode.FILES_ZIP
        pathsRadio.isSelected = prefs.outputMode == OutputMode.PATHS_ZIP
        contextRadio.isSelected = prefs.outputMode == OutputMode.CONTEXT_FILE

        contextFormatCombo.selectedItem = prefs.contextFormat
        maxTokensField.text = (prefs.maxTokens ?: ExportPreferences.DEFAULT_MAX_TOKENS).toString()

        val dir = prefs.outputDir.ifBlank { project.basePath ?: "" }
        outputPathField.text = if (dir.isNotBlank()) {
            "$dir${File.separator}${prefs.filePattern}"
        } else {
            ""
        }

        val isContext = contextRadio.isSelected
        contextFormatLabel.isEnabled = isContext
        contextFormatCombo.isEnabled = isContext
        maxTokensLabel.isEnabled = isContext
        maxTokensField.isEnabled = isContext

        outputPathField.button.addActionListener {
            val currentText = outputPathField.text.trim()
            val currentFile = if (currentText.isNotEmpty()) File(currentText) else null
            val ext = currentExtension()

            val descriptor = FileSaverDescriptor(
                ClassBundleBundle.message("export.fileChooser.title"),
                "Select a path to save the exported Class Bundle",
                ext,
            )
            val dialog = FileChooserFactory.getInstance()
                .createSaveFileDialog(descriptor, project)

            val initialDir = currentFile?.parentFile?.let {
                LocalFileSystem.getInstance().findFileByIoFile(it)
            }
            val initialName = currentFile?.name ?: "classbundle.$ext"

            val wrapper = dialog.save(initialDir, initialName)
            if (wrapper != null) {
                var path = wrapper.file.absolutePath
                if (!path.endsWith(".$ext", ignoreCase = true)) {
                    path += ".$ext"
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

    // --- DialogWrapper overrides ---------------------------------------------

    override fun createCenterPanel(): JComponent {
        val content = JPanel(BorderLayout(0, 8))
        content.add(createOptionsPanel(), BorderLayout.CENTER)
        content.add(createPreviewPanel(), BorderLayout.SOUTH)
        attachPreviewListeners()
        isOKActionEnabled = canExport()
        schedulePreview()
        return content
    }

    override fun doOKAction() {
        if (!canExport()) return

        val prefs = ExportPreferences
        prefs.selectedRelations = selectedRelations()
        prefs.pathMode = if (relativeRadio.isSelected) PathMode.RELATIVE else PathMode.ABSOLUTE
        prefs.outputMode = selectedOutputMode()
        prefs.contextFormat =
            contextFormatCombo.selectedItem as? ContextFormat ?: ContextFormat.MARKDOWN
        prefs.maxTokens = parseMaxTokens()

        val request = try {
            buildRequest()
        } catch (e: IllegalStateException) {
            notifyError(e.message ?: ClassBundleBundle.message("export.error.noFiles"))
            return
        }

        val parent = request.outputPath.toFile().parentFile
        if (parent != null) prefs.outputDir = parent.absolutePath
        prefs.filePattern = request.outputPath.toFile().name

        super.doOKAction()

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "Exporting classes", true) {
                override fun run(indicator: ProgressIndicator) {
                    try {
                        DumbService.getInstance(project).runReadActionInSmartMode<Unit> {
                            ExportService.export(request, indicator)
                        }
                    } catch (e: ProcessCanceledException) {
                        throw e
                    } catch (e: Exception) {
                        notifyError(e.message ?: "Export failed")
                    }
                }
            },
        )
    }

    override fun createLeftSideActions(): Array<javax.swing.Action> {
        val addToFavoritesAction = object :
            AbstractAction(ClassBundleBundle.message("action.addToFavorites")) {
            override fun actionPerformed(e: ActionEvent?) {
                addToFavorites()
            }
        }
        return arrayOf(addToFavoritesAction)
    }

    // --- Actions -------------------------------------------------------------

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
            null,
        ) ?: return
        if (listName.isBlank()) return

        ReadAction.nonBlocking(Callable {
            RelatedFilesCollector().collect(project, cls, relations).map { it.file }
        })
            .inSmartMode(project)
            .expireWith(disposable)
            .finishOnUiThread(ModalityState.any()) { files ->
                if (files.isEmpty()) {
                    notifyError(ClassBundleBundle.message("export.error.noFiles"))
                    return@finishOnUiThread
                }
                try {
                    val bookmarksManager = BookmarksManager.getInstance(project)
                        ?: return@finishOnUiThread
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
                        val psiClass = psiFile?.let {
                            PsiTreeUtil.findChildOfType(it, PsiClass::class.java)
                        }
                        psiClass?.qualifiedName ?: file.name
                    }

                    val commonPrefix = getCommonPackagePrefix(project, sortedFiles)

                    for (file in sortedFiles) {
                        val bookmark = bookmarksManager.createBookmark(file)
                        if (bookmark != null) {
                            val psiFile = PsiManager.getInstance(project).findFile(file)
                            val psiClass = psiFile?.let {
                                PsiTreeUtil.findChildOfType(it, PsiClass::class.java)
                            }
                            val qualifiedName = psiClass?.qualifiedName ?: file.name
                            val description = if (
                                commonPrefix.isNotEmpty() &&
                                qualifiedName.startsWith(commonPrefix)
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
                                listName,
                            ),
                            NotificationType.INFORMATION,
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
            val psiClass = psiFile?.let {
                PsiTreeUtil.findChildOfType(it, PsiClass::class.java)
            }
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

    // --- Panels --------------------------------------------------------------

    private fun createOptionsPanel(): JPanel {
        val includePanel = JPanel(GridLayout(0, 1, 0, 4))
        includePanel.add(usageCheckbox)
        includePanel.add(inheritorsCheckbox)
        includePanel.add(nestedCheckbox)
        includePanel.add(dependencyCheckbox)
        includePanel.add(transitiveDependencyCheckbox)
        includePanel.add(selfCheckbox)

        val pathPanel = JPanel(GridLayout(0, 1, 0, 4))
        pathPanel.add(relativeRadio)
        pathPanel.add(absoluteRadio)
        ButtonGroup().apply {
            add(relativeRadio)
            add(absoluteRadio)
        }

        val archivePanel = JPanel(GridLayout(0, 1, 0, 4))
        archivePanel.add(filesRadio)
        archivePanel.add(pathsRadio)
        archivePanel.add(contextRadio)
        ButtonGroup().apply {
            add(filesRadio)
            add(pathsRadio)
            add(contextRadio)
        }

        val contextOptions = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        contextOptions.add(contextFormatLabel)
        contextOptions.add(contextFormatCombo)
        contextOptions.add(maxTokensLabel)
        contextOptions.add(maxTokensField)
        archivePanel.add(contextOptions)

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

    // --- Preview wiring ------------------------------------------------------

    private fun attachPreviewListeners() {
        val listener = ActionListener { invalidatePreview() }
        for (component in listOf(
            usageCheckbox, inheritorsCheckbox, nestedCheckbox,
            dependencyCheckbox, transitiveDependencyCheckbox,
            relativeRadio, absoluteRadio,
        )) {
            component.addActionListener(listener)
        }

        val modeListener = ActionListener {
            val isContext = contextRadio.isSelected
            contextFormatLabel.isEnabled = isContext
            contextFormatCombo.isEnabled = isContext
            maxTokensLabel.isEnabled = isContext
            maxTokensField.isEnabled = isContext
            updateOutputExtension()
            invalidatePreview()
        }
        filesRadio.addActionListener(modeListener)
        pathsRadio.addActionListener(modeListener)
        contextRadio.addActionListener(modeListener)

        contextFormatCombo.addActionListener {
            updateOutputExtension()
            invalidatePreview()
        }

        maxTokensField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = invalidatePreview()
            override fun removeUpdate(e: DocumentEvent) = invalidatePreview()
            override fun changedUpdate(e: DocumentEvent) = invalidatePreview()
        })
    }

    private fun invalidatePreview() {
        isOKActionEnabled = canExport()
        schedulePreview()
    }

    private fun schedulePreview() {
        previewAlarm.cancelAllRequests()

        val seq = ++previewSeq
        val relations = selectedRelations().toSet()
        val format = if (selectedOutputMode() == OutputMode.CONTEXT_FILE) {
            contextFormatCombo.selectedItem as? ContextFormat ?: ContextFormat.MARKDOWN
        } else {
            null
        }
        val cls = targetClass
        if (cls == null) {
            updatePreview(ExportPreview(emptyList(), 0L))
            return
        }

        previewAlarm.addRequest({
            ReadAction.nonBlocking(Callable {
                ExportService.preview(project, cls, relations, format)
            })
                .inSmartMode(project)
                .expireWith(disposable)
                .finishOnUiThread(ModalityState.any()) { preview ->
                    if (seq != previewSeq) return@finishOnUiThread
                    updatePreview(preview)
                }
                .submit(AppExecutorUtil.getAppExecutorService())
        }, PREVIEW_DEBOUNCE_MS)
    }

    private fun updatePreview(preview: ExportPreview) {
        val files = preview.files
        val labels = files.map { file ->
            val psiFile = PsiManager.getInstance(project).findFile(file)
            val psiClass = psiFile?.let {
                PsiTreeUtil.findChildOfType(it, PsiClass::class.java)
            }
            psiClass?.qualifiedName ?: file.name
        }
        val shown = if (labels.size <= PREVIEW_MAX_ROWS) {
            labels
        } else {
            labels.take(PREVIEW_MAX_ROWS) +
                    ClassBundleBundle.message("preview.more", labels.size - PREVIEW_MAX_ROWS)
        }
        previewList.setListData(shown.toTypedArray())

        val countText = ClassBundleBundle.message("preview.count", files.size)
        previewCountLabel.text = if (selectedOutputMode() == OutputMode.CONTEXT_FILE) {
            val tokens = preview.tokenEstimate
            val limit = parseMaxTokens()
            val tokenText = if (limit != null && tokens > limit) {
                ClassBundleBundle.message("preview.tokens.warning", tokens, limit)
            } else {
                ClassBundleBundle.message("preview.tokens", tokens)
            }
            "$countText — $tokenText"
        } else {
            countText
        }
        isOKActionEnabled = canExport()
    }

    // --- Helpers -------------------------------------------------------------

    private fun selectedRelations(): Set<RelationType> = buildSet {
        if (usageCheckbox.isSelected) add(RelationType.USAGE)
        if (inheritorsCheckbox.isSelected) add(RelationType.INHERITOR)
        if (nestedCheckbox.isSelected) add(RelationType.NESTED)
        if (dependencyCheckbox.isSelected) add(RelationType.DEPENDENCY)
        if (transitiveDependencyCheckbox.isSelected) add(RelationType.TRANSITIVE_DEPENDENCY)
    }

    private fun selectedOutputMode(): OutputMode = when {
        contextRadio.isSelected -> OutputMode.CONTEXT_FILE
        pathsRadio.isSelected -> OutputMode.PATHS_ZIP
        else -> OutputMode.FILES_ZIP
    }

    private fun parseMaxTokens(): Int? =
        maxTokensField.text.trim().toIntOrNull()

    private fun currentExtension(): String = when (selectedOutputMode()) {
        OutputMode.CONTEXT_FILE ->
            (contextFormatCombo.selectedItem as? ContextFormat ?: ContextFormat.MARKDOWN).extension

        else -> "zip"
    }

    private fun updateOutputExtension() {
        val raw = outputPathField.text.trim()
        if (raw.isEmpty()) return
        val current = File(raw)
        val known = setOf("zip", "md", "txt", "json", "xml")
        val currentExt = current.extension.lowercase()
        if (currentExt !in known) return

        val newExt = currentExtension()
        if (currentExt == newExt) return

        val newName = if (current.nameWithoutExtension.isEmpty()) {
            "classbundle.$newExt"
        } else {
            "${current.nameWithoutExtension}.$newExt"
        }
        val newFile = current.parentFile?.let { File(it, newName) } ?: File(newName)
        outputPathField.text = newFile.absolutePath
    }

    private fun canExport(): Boolean =
        outputPathField.text.isNotBlank() &&
                targetClass != null &&
                selectedRelations().isNotEmpty()

    private fun buildRequest(): ExportRequest {
        val cls = targetClass
            ?: error("Target class '$className' not found in project")
        val mode = selectedOutputMode()
        val format = contextFormatCombo.selectedItem as? ContextFormat ?: ContextFormat.MARKDOWN
        val raw = outputPathField.text.trim()

        val outputFile = when (mode) {
            OutputMode.CONTEXT_FILE ->
                if (raw.endsWith(".${format.extension}", ignoreCase = true)) File(raw)
                else File("$raw.${format.extension}")

            else ->
                if (raw.endsWith(".zip", ignoreCase = true)) File(raw)
                else File("$raw.zip")
        }

        return ExportRequest(
            targetClass = cls,
            relations = selectedRelations(),
            pathMode = if (relativeRadio.isSelected) PathMode.RELATIVE else PathMode.ABSOLUTE,
            outputMode = mode,
            contextFormat = format,
            maxTokens = parseMaxTokens(),
            outputPath = outputFile.toPath(),
            project = project,
        )
    }

    private fun updateOkState() {
        isOKActionEnabled = canExport()
    }

    private fun notifyError(message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("ClassBundle")
            .createNotification(message, NotificationType.ERROR)
            .notify(project)
    }

    private companion object {
        private const val PREVIEW_DEBOUNCE_MS = 200
        private const val PREVIEW_MAX_ROWS = 200
    }
}