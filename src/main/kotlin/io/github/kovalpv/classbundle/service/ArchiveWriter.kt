package io.github.kovalpv.classbundle.service

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import io.github.kovalpv.classbundle.model.ExportRequest
import io.github.kovalpv.classbundle.model.ExportResult
import io.github.kovalpv.classbundle.model.OutputMode
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Запись ZIP-архива (FR-8, FR-9, SDD §5.2). Обрабатывает только
 * [OutputMode.FILES_ZIP] и [OutputMode.PATHS_ZIP]; для [OutputMode.CONTEXT_FILE]
 * используется [ContextBundleWriter].
 */
internal class ArchiveWriter {

    private val logger = Logger.getInstance(ArchiveWriter::class.java)

    fun write(request: ExportRequest, files: List<VirtualFile>): ExportResult {
        require(request.outputMode.isZip) {
            "ArchiveWriter does not handle ${request.outputMode}; use ContextBundleWriter"
        }
        val target = request.outputPath
        logger.info(
            "Writing archive: mode=${request.outputMode.name}, files=${files.size}, target=$target"
        )

        return try {
            target.parent?.let { Files.createDirectories(it) }
            Files.newOutputStream(target).use { stream ->
                ZipOutputStream(stream).use { zip ->
                    when (request.outputMode) {
                        OutputMode.FILES_ZIP -> writeFiles(zip, request, files)
                        OutputMode.PATHS_ZIP -> writePathList(zip, request, files)
                        OutputMode.CONTEXT_FILE -> error("unreachable")
                    }
                }
            }
            logger.info("Archive written: $target (${files.size} files)")
            ExportResult(
                outputPath = target,
                exportedCount = files.size,
                failedCount = 0,
                skippedFiles = emptyList(),
            )
        } catch (e: IOException) {
            logger.error("Failed to write archive $target", e)
            ExportResult.failure(target, "I/O error: ${e.message ?: e::class.java.simpleName}")
        }
    }

    private fun writeFiles(
        zip: ZipOutputStream,
        request: ExportRequest,
        files: List<VirtualFile>,
    ) {
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
            Files.newInputStream(file.toNioPath()).use { input -> input.copyTo(zip) }
            zip.closeEntry()
        }
        if (skipped.isNotEmpty()) {
            logger.warn("Skipped ${skipped.size} invalid files: $skipped")
        }
    }

    private fun writePathList(
        zip: ZipOutputStream,
        request: ExportRequest,
        files: List<VirtualFile>,
    ) {
        val lines = files.map { file ->
            PathFormatter.format(file, request.projectBasePath, request.pathMode)
        }
        zip.putNextEntry(ZipEntry(PATH_LIST_ENTRY))
        zip.write((lines.joinToString("\n") + "\n").toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun entryName(file: VirtualFile, projectBasePath: Path?): String {
        if (projectBasePath == null) return file.name
        return try {
            projectBasePath.relativize(file.toNioPath()).toString()
                .replace(File.separatorChar, '/')
                .replace('\\', '/')
        } catch (e: IllegalArgumentException) {
            file.name
        }
    }

    private fun uniqueEntry(used: MutableSet<String>, name: String): String {
        var candidate = name
        var counter = 1
        while (!used.add(candidate)) {
            candidate = if (name.contains('/')) {
                val parent = name.substringBeforeLast('/')
                val fileName = name.substringAfterLast('/')
                val dot = fileName.lastIndexOf('.')
                if (dot > 0) {
                    "$parent/${fileName.substring(0, dot)}_$counter/${fileName.substring(dot)}"
                } else {
                    "$parent/${fileName}_$counter"
                }
            } else {
                val dot = name.lastIndexOf('.')
                if (dot > 0) {
                    "${name.substring(0, dot)}_$counter${name.substring(dot)}"
                } else {
                    "${name}_$counter"
                }
            }
            counter++
        }
        return candidate
    }

    private companion object {
        const val PATH_LIST_ENTRY = "paths.txt"
    }
}