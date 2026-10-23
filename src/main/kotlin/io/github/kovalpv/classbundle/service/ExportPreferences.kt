package io.github.kovalpv.classbundle.service

import com.intellij.ide.util.PropertiesComponent
import io.github.kovalpv.classbundle.model.ContextFormat
import io.github.kovalpv.classbundle.model.OutputMode
import io.github.kovalpv.classbundle.model.PathMode
import io.github.kovalpv.classbundle.model.RelationType

internal object ExportPreferences {

    private const val KEY_OUTPUT_MODE = "classbundle.export.outputMode"
    private const val KEY_CONTEXT_FORMAT = "classbundle.export.contextFormat"
    private const val KEY_COPY_TO_CLIPBOARD = "classbundle.export.copyToClipboard"
    private const val KEY_MAX_TOKENS = "classbundle.export.maxTokens"
    private const val KEY_PATH_MODE = "classbundle.export.pathMode"
    private const val KEY_RELATIONS = "classbundle.export.relations"
    private const val KEY_OUTPUT_DIR = "classbundle.export.outputDir"
    private const val KEY_FILE_PATTERN = "classbundle.export.filePattern"
    private const val KEY_LEGACY_ARCHIVE_MODE = "classbundle.export.archiveMode"

    const val DEFAULT_FILE_PATTERN = "classbundle.zip"
    const val DEFAULT_MAX_TOKENS = 100_000

    private val props = PropertiesComponent.getInstance()
    private val defaultRelations: Set<RelationType> =
        setOf(RelationType.USAGE, RelationType.INHERITOR)

    var outputMode: OutputMode
        get() {
            val raw = props.getValue(KEY_OUTPUT_MODE)
            if (raw != null) {
                return OutputMode.entries.firstOrNull { it.name == raw } ?: OutputMode.FILES_ZIP
            }
            return when (props.getValue(KEY_LEGACY_ARCHIVE_MODE)) {
                "PATH_LIST" -> OutputMode.PATHS_ZIP
                else -> OutputMode.FILES_ZIP
            }
        }
        set(value) = props.setValue(KEY_OUTPUT_MODE, value.name)

    var contextFormat: ContextFormat
        get() = ContextFormat.entries
            .firstOrNull { it.name == props.getValue(KEY_CONTEXT_FORMAT) }
            ?: ContextFormat.MARKDOWN
        set(value) = props.setValue(KEY_CONTEXT_FORMAT, value.name)

    var copyContextToClipboard: Boolean
        get() = props.getBoolean(KEY_COPY_TO_CLIPBOARD, false)
        set(value) = props.setValue(KEY_COPY_TO_CLIPBOARD, value, false)

    var maxTokens: Int?
        get() = props.getValue(KEY_MAX_TOKENS)?.toIntOrNull()
        set(value) {
            if (value == null) props.unsetValue(KEY_MAX_TOKENS)
            else props.setValue(KEY_MAX_TOKENS, value.toString())
        }

    var pathMode: PathMode
        get() = PathMode.entries
            .firstOrNull { it.name == props.getValue(KEY_PATH_MODE) }
            ?: PathMode.RELATIVE
        set(value) = props.setValue(KEY_PATH_MODE, value.name)

    var selectedRelations: Set<RelationType>
        get() {
            val raw = props.getValue(KEY_RELATIONS) ?: return defaultRelations
            val parsed = raw.split(",")
                .mapNotNull { name -> RelationType.entries.firstOrNull { it.name == name.trim() } }
                .toSet()
            return if (parsed.isEmpty()) defaultRelations else parsed
        }
        set(value) = props.setValue(KEY_RELATIONS, value.joinToString(",") { it.name })

    var outputDir: String
        get() = props.getValue(KEY_OUTPUT_DIR) ?: ""
        set(value) = props.setValue(KEY_OUTPUT_DIR, value)

    var filePattern: String
        get() = props.getValue(KEY_FILE_PATTERN) ?: DEFAULT_FILE_PATTERN
        set(value) = props.setValue(KEY_FILE_PATTERN, value)
}