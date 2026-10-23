package io.github.kovalpv.classbundle.service

import com.intellij.openapi.diagnostic.Logger
import io.github.kovalpv.classbundle.model.ExportRequest
import io.github.kovalpv.classbundle.model.ExportResult
import io.github.kovalpv.classbundle.model.RelatedFile
import java.io.IOException
import java.nio.file.Files

/**
 * Запись context bundle (FR-14): один файл `.md` / `.txt` / `.json` / `.xml`
 * с содержимым связанных классов для передачи LLM.
 *
 * В отличие от [ArchiveWriter], здесь нет ZIP: веб-чаты не принимают архивы,
 * а один текстовый файл можно просто перетащить в окно чата.
 */
internal class ContextBundleWriter {

    private val logger = Logger.getInstance(ContextBundleWriter::class.java)

    fun write(request: ExportRequest, files: List<RelatedFile>): ExportResult {
        val target = request.outputPath
        logger.info(
            "Writing context bundle: format=${request.contextFormat.name}, " +
                    "files=${files.size}, maxTokens=${request.maxTokens}, target=$target"
        )

        return try {
            target.parent?.let { Files.createDirectories(it) }
            val content = ContextBundleFormatter.format(request, files)
            Files.newBufferedWriter(target, Charsets.UTF_8).use { it.write(content) }
            logger.info("Context bundle written: $target (${content.length} chars)")

            ExportResult(
                outputPath = target,
                exportedCount = files.size,
                failedCount = 0,
                skippedFiles = emptyList(),
            )
        } catch (e: IOException) {
            logger.error("Failed to write context bundle $target", e)
            ExportResult.failure(target, "I/O error: ${e.message ?: e::class.java.simpleName}")
        }
    }
}