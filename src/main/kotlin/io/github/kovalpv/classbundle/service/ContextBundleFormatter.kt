package io.github.kovalpv.classbundle.service

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import io.github.kovalpv.classbundle.model.ContextFormat
import io.github.kovalpv.classbundle.model.ExportRequest
import io.github.kovalpv.classbundle.model.FileRole
import io.github.kovalpv.classbundle.model.RelatedFile
import java.time.Instant

/**
 * Форматирование context bundle в один из [ContextFormat] (FR-14).
 *
 * Пути файлов формируются через [PathFormatter] с учётом
 * [ExportRequest.pathMode] и [ExportRequest.projectBasePath], поэтому
 * при `PathMode.RELATIVE` в bundle попадают относительные пути
 * (`com/example/Foo.java`), а не абсолютные (`/Users/.../Foo.java`).
 *
 * Порядок файлов задан [RelatedFilesCollector]: target первым, затем
 * inheritors / usages / dependencies / nested.
 */
internal object ContextBundleFormatter {

    private val logger = Logger.getInstance(ContextBundleFormatter::class.java)

    private const val MAX_FILE_BYTES = 1_000_000L

    fun format(request: ExportRequest, files: List<RelatedFile>): String {
        val prepared = files.map(::prepare)
        val (included, skipped) = selectByBudget(request.maxTokens, prepared)
        return when (request.contextFormat) {
            ContextFormat.MARKDOWN -> renderMarkdown(request, included, skipped)
            ContextFormat.PLAIN -> renderPlain(request, included, skipped)
            ContextFormat.JSON -> renderJson(request, included, skipped)
            ContextFormat.XML -> renderXml(request, included, skipped)
        }
    }

    // --- Preparation ---------------------------------------------------------

    private sealed class Prepared {
        abstract val related: RelatedFile

        data class Ok(override val related: RelatedFile, val content: String) : Prepared()
        data class Skipped(override val related: RelatedFile, val reason: String) : Prepared()
    }

    private fun prepare(related: RelatedFile): Prepared {
        val file = related.file
        if (!file.isValid) return Prepared.Skipped(related, "invalid")
        if (file.length > MAX_FILE_BYTES) return Prepared.Skipped(related, "too large")
        if (file.fileType.isBinary) return Prepared.Skipped(related, "binary")
        return try {
            Prepared.Ok(related, VfsUtilCore.loadText(file))
        } catch (e: Exception) {
            logger.warn("Failed to read file ${file.path}", e)
            Prepared.Skipped(related, "unreadable")
        }
    }

    private fun selectByBudget(
        maxTokens: Int?,
        prepared: List<Prepared>,
    ): Pair<List<Prepared.Ok>, List<Prepared>> {
        val oks = prepared.filterIsInstance<Prepared.Ok>()
        val others = prepared.filterIsInstance<Prepared.Skipped>()
        if (maxTokens == null) return oks to others

        val included = mutableListOf<Prepared.Ok>()
        val dropped = mutableListOf<Prepared>()
        var accumulated = 0
        for (entry in oks) {
            val tokens = TokenEstimator.estimate(entry.content)
            if (included.isNotEmpty() && accumulated + tokens > maxTokens) {
                dropped.add(Prepared.Skipped(entry.related, "over token budget"))
            } else {
                included.add(entry)
                accumulated += tokens
            }
        }
        return included to (others + dropped)
    }

    // --- Path helper ---------------------------------------------------------

    /**
     * Путь файла для отображения в bundle: учитывает [ExportRequest.pathMode]
     * и [ExportRequest.projectBasePath] через [PathFormatter].
     */
    private fun displayPath(request: ExportRequest, file: VirtualFile): String =
        PathFormatter.format(file, request.projectBasePath, request.pathMode)

    // --- Renderers -----------------------------------------------------------

    private fun renderMarkdown(
        request: ExportRequest,
        included: List<Prepared.Ok>,
        skipped: List<Prepared>,
    ): String {
        val sb = StringBuilder()
        sb.append("# ClassBundle Context\n\n")
        appendMarkdownMeta(sb, request, included, skipped)
        sb.append("\n## Contents\n\n")
        appendMarkdownToc(sb, request, included, skipped)
        for (entry in included) {
            sb.append("\n---\n\n## ")
                .append(displayPath(request, entry.related.file))
                .append("\n\n")
            sb.append("_role: ").append(roleLabel(entry.related.role)).append("_\n\n")
            sb.append("```").append(languageFor(entry.related.file)).append("\n")
            sb.append(entry.content)
            if (!entry.content.endsWith("\n")) sb.append("\n")
            sb.append("```\n")
        }
        return sb.toString()
    }

    private fun appendMarkdownMeta(
        sb: StringBuilder,
        request: ExportRequest,
        included: List<Prepared.Ok>,
        skipped: List<Prepared>,
    ) {
        val tokens = included.sumOf { TokenEstimator.estimate(it.content) }
        sb.append("- **Target class:** `").append(safeName(request)).append("`\n")
        sb.append("- **Relations:** ").append(request.relations.joinToString(", ")).append("\n")
        sb.append("- **Files:** ").append(included.size)
        if (skipped.isNotEmpty()) sb.append(" (+").append(skipped.size).append(" skipped)")
        sb.append("\n")
        sb.append("- **Estimated tokens:** ~").append(tokens).append("\n")
        sb.append("- **Generated:** ").append(Instant.now().toString()).append("\n")
    }

    private fun appendMarkdownToc(
        sb: StringBuilder,
        request: ExportRequest,
        included: List<Prepared.Ok>,
        skipped: List<Prepared>,
    ) {
        included.forEachIndexed { idx, entry ->
            sb.append(idx + 1).append(". `")
                .append(displayPath(request, entry.related.file))
                .append("` (").append(roleLabel(entry.related.role)).append(")\n")
        }
        if (skipped.isNotEmpty()) {
            sb.append("\n_Skipped:_\n")
            for (s in skipped) {
                sb.append("- `")
                    .append(displayPath(request, s.related.file))
                    .append("` (").append(skipReasonLabel(s)).append(")\n")
            }
        }
    }

    private fun renderPlain(
        request: ExportRequest,
        included: List<Prepared.Ok>,
        skipped: List<Prepared>,
    ): String {
        val sb = StringBuilder()
        sb.append("===== ClassBundle Context =====\n\n")
        sb.append("Target class: ").append(safeName(request)).append("\n")
        sb.append("Relations: ").append(request.relations.joinToString(", ")).append("\n")
        sb.append("Files: ").append(included.size)
        if (skipped.isNotEmpty()) sb.append(" (+").append(skipped.size).append(" skipped)")
        sb.append("\n")
        sb.append("Estimated tokens: ~")
            .append(included.sumOf { TokenEstimator.estimate(it.content) }).append("\n")
        sb.append("Generated: ").append(Instant.now().toString()).append("\n\n")

        sb.append("===== Contents =====\n\n")
        included.forEachIndexed { idx, entry ->
            sb.append(idx + 1).append(". ")
                .append(displayPath(request, entry.related.file))
                .append(" (").append(roleLabel(entry.related.role)).append(")\n")
        }
        if (skipped.isNotEmpty()) {
            sb.append("\nSkipped:\n")
            for (s in skipped) {
                sb.append("- ").append(displayPath(request, s.related.file))
                    .append(" (").append(skipReasonLabel(s)).append(")\n")
            }
        }
        for (entry in included) {
            sb.append("\n===== ")
                .append(displayPath(request, entry.related.file))
                .append(" =====\n\n")
            sb.append("role: ").append(roleLabel(entry.related.role)).append("\n\n")
            sb.append(entry.content)
            if (!entry.content.endsWith("\n")) sb.append("\n")
        }
        return sb.toString()
    }

    private fun renderJson(
        request: ExportRequest,
        included: List<Prepared.Ok>,
        skipped: List<Prepared>,
    ): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"targetClass\": \"").append(jsonEscape(safeName(request))).append("\",\n")
        sb.append("  \"relations\": [")
            .append(request.relations.joinToString(", ") { "\"${it.name}\"" })
            .append("],\n")
        sb.append("  \"files\": ").append(included.size).append(",\n")
        sb.append("  \"estimatedTokens\": ")
            .append(included.sumOf { TokenEstimator.estimate(it.content) }).append(",\n")
        sb.append("  \"generated\": \"").append(Instant.now().toString()).append("\",\n")
        sb.append("  \"entries\": [\n")
        included.forEachIndexed { idx, entry ->
            sb.append("    {\n")
            sb.append("      \"path\": \"")
                .append(jsonEscape(displayPath(request, entry.related.file)))
                .append("\",\n")
            sb.append("      \"role\": \"").append(entry.related.role.name.lowercase())
                .append("\",\n")
            sb.append("      \"content\": \"").append(jsonEscape(entry.content)).append("\"\n")
            sb.append("    }").append(if (idx < included.size - 1) "," else "").append("\n")
        }
        sb.append("  ]")
        if (skipped.isNotEmpty()) {
            sb.append(",\n  \"skipped\": [\n")
            skipped.forEachIndexed { idx, s ->
                sb.append("    {\"path\": \"")
                    .append(jsonEscape(displayPath(request, s.related.file)))
                    .append("\", \"reason\": \"").append(skipReasonLabel(s)).append("\"}")
                    .append(if (idx < skipped.size - 1) "," else "").append("\n")
            }
            sb.append("  ]")
        }
        sb.append("\n}\n")
        return sb.toString()
    }

    private fun renderXml(
        request: ExportRequest,
        included: List<Prepared.Ok>,
        skipped: List<Prepared>,
    ): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<context")
        sb.append(" target-class=\"").append(xmlEscape(safeName(request))).append("\"")
        sb.append(" relations=\"")
            .append(request.relations.joinToString(" ") { it.name }).append("\"")
        sb.append(" files=\"").append(included.size).append("\"")
        sb.append(" estimated-tokens=\"")
            .append(included.sumOf { TokenEstimator.estimate(it.content) }).append("\"")
        sb.append(" generated=\"").append(Instant.now().toString()).append("\"")
        sb.append(">\n")
        for (entry in included) {
            sb.append("  <file path=\"")
                .append(xmlEscape(displayPath(request, entry.related.file)))
                .append("\" role=\"").append(entry.related.role.name.lowercase()).append("\">")
            sb.append(xmlCdata(entry.content))
            sb.append("</file>\n")
        }
        if (skipped.isNotEmpty()) {
            sb.append("  <skipped>\n")
            for (s in skipped) {
                sb.append("    <file path=\"")
                    .append(xmlEscape(displayPath(request, s.related.file)))
                    .append("\" reason=\"").append(xmlEscape(skipReasonLabel(s))).append("\"/>\n")
            }
            sb.append("  </skipped>\n")
        }
        sb.append("</context>\n")
        return sb.toString()
    }

    // --- Helpers -------------------------------------------------------------

    private fun safeName(request: ExportRequest): String =
        try {
            request.targetClass.qualifiedName ?: request.targetClass.name ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }

    private fun roleLabel(role: FileRole): String = when (role) {
        FileRole.TARGET -> "target"
        FileRole.INHERITOR -> "inheritor"
        FileRole.USAGE -> "usage"
        FileRole.DEPENDENCY -> "dependency"
        FileRole.NESTED -> "nested"
    }

    private fun skipReasonLabel(prepared: Prepared): String = when (prepared) {
        is Prepared.Skipped -> when (prepared.reason) {
            "too large" -> "too large (>$MAX_FILE_BYTES bytes)"
            "binary" -> "binary"
            "invalid" -> "invalid"
            "unreadable" -> "unreadable"
            "over token budget" -> "over token budget"
            else -> prepared.reason
        }

        is Prepared.Ok -> ""
    }

    private fun languageFor(file: VirtualFile): String = file.extension?.lowercase() ?: ""

    private fun jsonEscape(s: String): String {
        val sb = StringBuilder(s.length + 16)
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun xmlEscape(s: String): String =
        s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")

    private fun xmlCdata(text: String): String {
        if (!text.contains("]]>")) return "<![CDATA[$text]]>"
        return "<![CDATA[" + text.replace("]]>", "]]]]><![CDATA[>") + "]]>"
    }
}