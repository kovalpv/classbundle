package io.github.kovalpv.classbundle.service

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import io.github.kovalpv.classbundle.model.ArchiveMode
import io.github.kovalpv.classbundle.model.ExportRequest
import io.github.kovalpv.classbundle.model.ExportResult
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Запись ZIP-архива (FR-8, FR-9, SDD §5.2).
 *
 * - [ArchiveMode.FILES] — внутрь кладутся сами файлы с сохранением
 *   относительной структуры каталогов к корню проекта (входные пути —
 *   `VirtualFile`, извлекаются содержимое и структура);
 * - [ArchiveMode.PATH_LIST] — внутрь кладётся один текстовый файл [PATH_LIST_ENTRY]
 *   со строками, отформатированными в соответствии с [io.github.kovalpv.classbundle.model.PathMode].
 *
 * I/O выполняется **вне** read-locks и **вне** EDT — на фоновом потоке
 * (см. AGENTS.md §«Правила работы с PSI и потоками»).
 */
internal class ArchiveWriter {

    private val logger = Logger.getInstance(ArchiveWriter::class.java)

    /**
     * Пишет архив в [request.outputZip].
     *
     * @param request запрос на экспорт;
     * @param files   список файлов, собранный [RelatedFilesCollector];
     * @return результат (успех, количество файлов, путь к архиву либо ошибка).
     */
    fun write(request: ExportRequest, files: List<VirtualFile>): ExportResult {
        val target = request.outputZip
        logger.info("Writing archive: mode=${request.archiveMode}, files=${files.size}, target=$target")
        return try {
            target.parent?.let { Files.createDirectories(it) }
            Files.newOutputStream(target).use { stream ->
                ZipOutputStream(stream).use { zip ->
                    when (request.archiveMode) {
                        ArchiveMode.FILES -> writeFiles(zip, request, files)
                        ArchiveMode.PATH_LIST -> writePathList(zip, request, files)
                    }
                }
            }
            logger.info("Archive written: $target (${files.size} files)")
            ExportResult(
                archivePath = target,
                exportedCount = files.size,
                failedCount = 0,
                skippedFiles = emptyList()
            )
        } catch (e: IOException) {
            logger.error("Failed to write archive $target", e)
            ExportResult.failure(target, "I/O error: ${e.message ?: e::class.java.simpleName}")
        }
    }

        private fun writeFiles(zip: ZipOutputStream, request: ExportRequest, files: List<VirtualFile>) {
        val usedEntries = LinkedHashSet<String>()
        val skipped = mutableListOf<String>()
        for (file in files) {
            if (!file.isValid) {
                logger.warn("Skipping invalid file: ${file.path}")
                skipped.add(file.path)
                continue
            }
            val entryName = uniqueEntry(usedEntries, entryName(file, request.projectBasePath))
            zip.putNextEntry(ZipEntry(entryName))
            
            // ИСПРАВЛЕНО: Потоковое копирование вместо загрузки в память (AGENTS.md)
            java.nio.file.Files.newInputStream(file.toNioPath()).use { input ->
                input.copyTo(zip)
            }
            
            zip.closeEntry()
        }
        if (skipped.isNotEmpty()) {
            logger.warn("Skipped ${skipped.size} invalid files: $skipped")
        }
    }

    private fun writePathList(zip: ZipOutputStream, request: ExportRequest, files: List<VirtualFile>) {
        val lines = files.map { file ->
            PathFormatter.format(file, request.projectBasePath, request.pathMode)
        }
        zip.putNextEntry(ZipEntry(PATH_LIST_ENTRY))
        zip.write((lines.joinToString("\n") + "\n").toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    /**
     * Имя ZIP-entry: путь относительно корня проекта (с разделителями `/`
     * согласно спецификации ZIP), либо только имя файла, если файл
     * находится вне корня проекта.
     */
    private fun entryName(file: VirtualFile, projectBasePath: Path?): String {
        if (projectBasePath == null) {
            return file.name
        }
        return try {
            projectBasePath.relativize(file.toNioPath()).toString()
                .replace(File.separatorChar, '/')
                .replace('\\', '/')
        } catch (e: IllegalArgumentException) {
            file.name
        }
    }

    /** Обезопасит от коллизий имён entry (дубликаты после relativize). */
    private fun uniqueEntry(used: MutableSet<String>, name: String): String {
        var candidate = name
        var counter = 1
        while (!used.add(candidate)) {
            candidate = if (name.contains('/')) {
                val parent = name.substringBeforeLast('/')
                val fileName = name.substringAfterLast('/')
                val dot = fileName.lastIndexOf('.')
                if (dot > 0) {
                    "$parent/${fileName.substring(0, dot)}($counter)${fileName.substring(dot)}"
                } else {
                    "$parent/$fileName($counter)"
                }
            } else {
                val dot = name.lastIndexOf('.')
                if (dot > 0) {
                    "${name.substring(0, dot)}($counter)${name.substring(dot)}"
                } else {
                    "$name($counter)"
                }
            }
            counter++
        }
        return candidate
    }

    private companion object {
        // Имя entry для PATH_LIST см. SDD FR-9 и §3.3
        const val PATH_LIST_ENTRY = "paths.txt"
    }
}