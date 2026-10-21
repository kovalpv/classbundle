package io.github.kovalpv.classbundle.service

import com.intellij.ide.util.PropertiesComponent
import io.github.kovalpv.classbundle.model.ArchiveMode
import io.github.kovalpv.classbundle.model.PathMode
import io.github.kovalpv.classbundle.model.RelationType

/**
 * Persistent export preferences (FR-12).
 *
 * Stored per user in the application scope via [PropertiesComponent]; the
 * values seed the export dialog and are rewritten on every successful export.
 */
internal object ExportPreferences {

    private const val KEY_ARCHIVE_MODE = "classbundle.export.archiveMode"
    private const val KEY_PATH_MODE = "classbundle.export.pathMode"
    private const val KEY_RELATIONS = "classbundle.export.relations"
    private const val KEY_OUTPUT_DIR = "classbundle.export.outputDir"
    private const val KEY_FILE_PATTERN = "classbundle.export.filePattern"

    const val DEFAULT_FILE_PATTERN = "classbundle.zip"

    private val props = PropertiesComponent.getInstance()

    private val defaultRelations: Set<RelationType> = setOf(RelationType.USAGE, RelationType.INHERITOR)

    var archiveMode: ArchiveMode
        get() = ArchiveMode.entries.firstOrNull { it.name == props.getValue(KEY_ARCHIVE_MODE) }
            ?: ArchiveMode.FILES
        set(value) = props.setValue(KEY_ARCHIVE_MODE, value.name)

    var pathMode: PathMode
        get() = PathMode.entries.firstOrNull { it.name == props.getValue(KEY_PATH_MODE) }
            ?: PathMode.RELATIVE
        set(value) = props.setValue(KEY_PATH_MODE, value.name)

    var selectedRelations: Set<RelationType>
        get() {
            val raw = props.getValue(KEY_RELATIONS) ?: return defaultRelations
            val parsed = raw.split(",").mapNotNull { name ->
                RelationType.entries.firstOrNull { it.name == name.trim() }
            }.toSet()
            return parsed.ifEmpty { defaultRelations }
        }
        set(value) = props.setValue(KEY_RELATIONS, value.joinToString(",") { it.name })

    var outputDir: String
        get() = props.getValue(KEY_OUTPUT_DIR) ?: ""
        set(value) = props.setValue(KEY_OUTPUT_DIR, value)

    var filePattern: String
        get() = props.getValue(KEY_FILE_PATTERN) ?: DEFAULT_FILE_PATTERN
        set(value) = props.setValue(KEY_FILE_PATTERN, value)
}
