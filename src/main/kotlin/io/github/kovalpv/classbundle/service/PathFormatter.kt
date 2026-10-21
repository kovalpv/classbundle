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
        val absolute = file.toNioPath()
        return when (mode) {
            PathMode.ABSOLUTE -> absolute.toAbsolutePath().toString()
            PathMode.RELATIVE -> relativize(absolute, baseDir)
        }
    }

    private fun relativize(path: Path, baseDir: Path?): String {
        if (baseDir == null) {
            return path.toAbsolutePath().toString()
        }
        return try {
            baseDir.relativize(path).toString()
        } catch (e: IllegalArgumentException) {
            logger.info("File $path is outside baseDir $baseDir, using absolute path")
            path.toAbsolutePath().toString()
        }
    }
}