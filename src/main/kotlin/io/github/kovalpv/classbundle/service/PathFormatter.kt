package io.github.kovalpv.classbundle.service

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import io.github.kovalpv.classbundle.model.PathMode
import java.nio.file.Path

/**
 * Форматирование путей для списка (FR-7, FR-8, SDD §5.2).
 *
 * - [PathMode.ABSOLUTE] — абсолютный путь (никогда не зависит от baseDir);
 * - [PathMode.RELATIVE] — путь относительно [baseDir]; если файл вне baseDir
 *   (или baseDir == null) — используется абсолютный путь.
 *
 * Разделитель путей — всегда `File.separator` текущей ОС (NIO `Path`),
 * что соответствует FR-8 (SDD §3.3, §5.2).
 */
internal object PathFormatter {

    private val logger = Logger.getInstance("PathFormatter")

    /**
     * Возвращает строковое представление пути файла в соответствии с [mode].
     *
     * @param file     исходный файл проекта;
     * @param baseDir  корень для относительных путей (обычно `project.basePath`);
     * @param mode     абсолютный или относительный формат;
     * @return строка пути в ОС-зависимых разделителях.
     */
    fun format(file: VirtualFile, baseDir: Path?, mode: PathMode): String {
        val absolute = try {
            file.toNioPath()
        } catch (e: UnsupportedOperationException) {
            // VFS без NIO-представления (temp:, jar:) — используем строковый путь.
            logger.info("File ${file.path} is not NIO-backed, using string path")
            return file.path
        }
        return when (mode) {
            PathMode.ABSOLUTE -> absolute.toAbsolutePath().normalize().toString()
            PathMode.RELATIVE -> relativize(absolute, baseDir)
        }
    }

    private fun relativize(path: Path, baseDir: Path?): String {
        if (baseDir == null) {
            return path.toAbsolutePath().normalize().toString()
        }

        val absolutePath = path.toAbsolutePath().normalize()
        val absoluteBase = baseDir.toAbsolutePath().normalize()

        return try {
            val relative = absoluteBase.relativize(absolutePath)
            if (relative.startsWith("..")) {
                logger.info(
                    "File $absolutePath is outside baseDir $absoluteBase, using absolute path",
                )
                absolutePath.toString()
            } else {
                relative.toString()
            }
        } catch (e: IllegalArgumentException) {
            // Разные корни (Windows drive letters, разные провайдеры).
            logger.info(
                "File $absolutePath cannot be relativized to $absoluteBase, using absolute path",
            )
            absolutePath.toString()
        }
    }
}