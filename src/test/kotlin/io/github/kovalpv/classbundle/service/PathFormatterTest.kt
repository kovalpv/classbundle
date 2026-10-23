package io.github.kovalpv.classbundle.service

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.kovalpv.classbundle.model.PathMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.nio.file.Files
import java.nio.file.Path

class PathFormatterTest : BasePlatformTestCase() {

    private lateinit var diskDir: Path
    private lateinit var diskFile: VirtualFile

    override fun setUp() {
        super.setUp()
        diskDir = Files.createTempDirectory("classbundle-pf")
        val file = diskDir.resolve("com/example/Foo.java")
        Files.createDirectories(file.parent)
        Files.writeString(file, "package com.example; public class Foo {}")

        diskFile = LocalFileSystem.getInstance()
            .refreshAndFindFileByNioFile(file)
            ?: error("Cannot find $file via LocalFileSystem")
    }

    override fun tearDown() {
        try {
            diskDir.toFile().deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    fun testAbsoluteReturnsFullPath() {
        val out = PathFormatter.format(diskFile, null, PathMode.ABSOLUTE)

        assertTrue("Expected absolute: $out", Path.of(out).isAbsolute)
        assertTrue(
            "Expected .../com/example/Foo.java, got: $out",
            out.endsWith("com/example/Foo.java") || out.endsWith("com\\example\\Foo.java"),
        )
    }

    fun testRelativeUsesBaseDir() {
        val out = PathFormatter.format(diskFile, diskDir, PathMode.RELATIVE)

        assertFalse("Expected relative: $out", Path.of(out).isAbsolute)
        assertTrue(
            "Expected com/example/Foo.java, got: $out",
            out.endsWith("com/example/Foo.java") || out.endsWith("com\\example\\Foo.java"),
        )
    }

    fun testRelativeFallsBackToAbsoluteWhenBaseDirIsNull() {
        val out = PathFormatter.format(diskFile, null, PathMode.RELATIVE)

        assertTrue("Expected absolute fallback: $out", Path.of(out).isAbsolute)
    }

    fun testRelativeFallsBackToAbsoluteForFileOutsideBaseDir() {
        val unrelated = Files.createTempDirectory("classbundle-other")
        try {
            val out = PathFormatter.format(diskFile, unrelated, PathMode.RELATIVE)
            assertTrue("Outside baseDir should be absolute: $out", Path.of(out).isAbsolute)
        } finally {
            unrelated.toFile().deleteRecursively()
        }
    }
}