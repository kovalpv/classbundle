package io.github.kovalpv.classbundle.service

import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.kovalpv.classbundle.model.ContextFormat
import io.github.kovalpv.classbundle.model.ExportRequest
import io.github.kovalpv.classbundle.model.FileRole
import io.github.kovalpv.classbundle.model.OutputMode
import io.github.kovalpv.classbundle.model.PathMode
import io.github.kovalpv.classbundle.model.RelatedFile
import io.github.kovalpv.classbundle.model.RelationType
import java.nio.file.Path

class ContextBundleFormatterTest : BasePlatformTestCase() {

    private lateinit var targetClass: com.intellij.psi.PsiClass
    private lateinit var baseRequest: ExportRequest

    override fun setUp() {
        super.setUp()
        val fooFile = myFixture.addFileToProject(
            "com/example/Foo.java",
            """
            package com.example;
            public class Foo {
                public void bar() {}
            }
            """.trimIndent(),
        )
        targetClass = (fooFile as PsiJavaFile).classes[0]
        baseRequest = ExportRequest(
            targetClass = targetClass,
            relations = setOf(RelationType.USAGE, RelationType.INHERITOR),
            pathMode = PathMode.RELATIVE,
            outputMode = OutputMode.CONTEXT_FILE,
            contextFormat = ContextFormat.MARKDOWN,
            maxTokens = null,
            outputPath = Path.of("unused.md"),
            project = project,
        )
    }

    // --- Markdown -----------------------------------------------------------

    fun testMarkdownContainsHeaderAndTarget() {
        val out = ContextBundleFormatter.format(
            baseRequest,
            listOf(RelatedFile(targetClass.containingFile.virtualFile, FileRole.TARGET)),
        )

        assertTrue(out.startsWith("# ClassBundle Context"))
        assertTrue(out.contains("**Target class:** `com.example.Foo`"))
        assertTrue(out.contains("**Relations:** USAGE, INHERITOR"))
        assertTrue(out.contains("**Files:** 1"))
        assertTrue(out.contains("**Estimated tokens:** ~"))
    }

    fun testMarkdownContainsTocAndFence() {
        val out = ContextBundleFormatter.format(
            baseRequest,
            listOf(RelatedFile(targetClass.containingFile.virtualFile, FileRole.TARGET)),
        )

        assertTrue(out.contains("## Contents"))
        assertTrue("Expected '(target)' role in TOC: $out", out.contains("(target)"))
        assertTrue("Expected java fence: $out", out.contains("```java"))
        assertTrue(out.contains("public class Foo"))
    }

    // --- Plain --------------------------------------------------------------

    fun testPlainUsesSeparatorsAndRole() {
        val out = ContextBundleFormatter.format(
            baseRequest.copy(contextFormat = ContextFormat.PLAIN),
            listOf(RelatedFile(targetClass.containingFile.virtualFile, FileRole.TARGET)),
        )

        assertTrue(out.startsWith("===== ClassBundle Context ====="))
        assertTrue(out.contains("===== Contents ====="))
        assertTrue("Expected '===== ...Foo.java =====': $out", out.contains("Foo.java ====="))
        assertTrue(out.contains("role: target"))
    }

    // --- JSON ---------------------------------------------------------------

    fun testJsonIsWellFormedAndEscaped() {
        val out = ContextBundleFormatter.format(
            baseRequest.copy(contextFormat = ContextFormat.JSON),
            listOf(RelatedFile(targetClass.containingFile.virtualFile, FileRole.TARGET)),
        )

        assertTrue(out.trimStart().startsWith("{"))
        assertTrue(out.trimEnd().endsWith("}"))
        assertTrue(out.contains("\"targetClass\": \"com.example.Foo\""))
        assertTrue(out.contains("\"entries\": ["))
        assertTrue("Expected Foo.java in entries: $out", out.contains("Foo.java"))
        assertTrue(out.contains("\"role\": \"target\""))
        assertTrue(out.contains("\\n"))
        assertEquals(out.count { it == '{' }, out.count { it == '}' })
        assertEquals(out.count { it == '[' }, out.count { it == ']' })
    }

    // --- XML ----------------------------------------------------------------

    fun testXmlContainsDeclarationAndCdata() {
        val out = ContextBundleFormatter.format(
            baseRequest.copy(contextFormat = ContextFormat.XML),
            listOf(RelatedFile(targetClass.containingFile.virtualFile, FileRole.TARGET)),
        )

        assertTrue(out.startsWith("<?xml"))
        assertTrue(out.contains("<context target-class=\"com.example.Foo\""))
        assertTrue(out.contains("role=\"target\""))
        assertTrue(out.contains("<![CDATA["))
        assertTrue(out.contains("</context>"))
    }

    // --- Token budget -------------------------------------------------------

    fun testMaxTokensNullIncludesAllFiles() {
        val f1 = myFixture.addFileToProject("com/example/A.java", "public class A {}")
        val related = listOf(
            RelatedFile(targetClass.containingFile.virtualFile, FileRole.TARGET),
            RelatedFile(f1.virtualFile, FileRole.USAGE),
        )

        val out = ContextBundleFormatter.format(baseRequest.copy(maxTokens = null), related)

        assertTrue(out.contains("com/example/Foo.java"))
        assertTrue(out.contains("com/example/A.java"))
    }

    fun testTinyMaxTokensDropsExcessFiles() {
        val big1 = myFixture.addFileToProject(
            "com/example/A.java",
            "public class A { " + "x".repeat(5_000) + " }",
        )
        val big2 = myFixture.addFileToProject(
            "com/example/B.java",
            "public class B { " + "y".repeat(5_000) + " }",
        )
        val related = listOf(
            RelatedFile(targetClass.containingFile.virtualFile, FileRole.TARGET),
            RelatedFile(big1.virtualFile, FileRole.USAGE),
            RelatedFile(big2.virtualFile, FileRole.USAGE),
        )

        val out = ContextBundleFormatter.format(baseRequest.copy(maxTokens = 50), related)

        assertTrue(out.contains("Foo.java"))
        assertTrue("Expected skipped section: $out", out.contains("Skipped"))
        assertTrue("Expected reason for dropped file: $out", out.contains("over token budget"))
    }

    fun testLargeFileIsSkippedAsTooLarge() {
        val big = myFixture.addFileToProject(
            "com/example/Huge.java",
            "public class Huge { " + "z".repeat(1_100_000) + " }",
        )
        val related = listOf(
            RelatedFile(targetClass.containingFile.virtualFile, FileRole.TARGET),
            RelatedFile(big.virtualFile, FileRole.USAGE),
        )

        val out = ContextBundleFormatter.format(baseRequest, related)

        assertTrue(out.contains("Foo.java"))
        assertTrue("Expected 'too large' skip reason: $out", out.contains("too large"))
        assertTrue("Huge file content must be omitted", !out.contains("z".repeat(100)))
    }
}