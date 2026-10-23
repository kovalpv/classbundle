package io.github.kovalpv.classbundle.service

import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.kovalpv.classbundle.model.FileRole
import io.github.kovalpv.classbundle.model.RelationType

class RelatedFilesCollectorTest : BasePlatformTestCase() {

    fun testCollectsTargetFileWhenRelationsEmpty() {
        val fooFile = myFixture.addFileToProject(
            "com/example/Foo.java",
            "package com.example; public class Foo {}",
        )
        val cls = (fooFile as PsiJavaFile).classes[0]

        val result = RelatedFilesCollector().collect(project, cls, emptySet())

        assertEquals(1, result.size)
        assertEquals(FileRole.TARGET, result[0].role)
        assertEquals(fooFile.virtualFile, result[0].file)
    }

    fun testCollectsInheritorWithRole() {
        myFixture.addFileToProject(
            "com/example/Foo.java",
            "package com.example; public class Foo {}",
        )
        myFixture.addFileToProject(
            "com/example/Bar.java",
            "package com.example; public class Bar extends Foo {}",
        )
        val fooFile = myFixture.findFileInTempDir("com/example/Foo.java")
        val cls = (com.intellij.psi.PsiManager.getInstance(project)
            .findFile(fooFile) as PsiJavaFile).classes[0]

        val result = RelatedFilesCollector().collect(
            project, cls, setOf(RelationType.INHERITOR),
        )

        assertEquals(2, result.size)
        assertEquals(FileRole.TARGET, result[0].role)
        assertEquals(FileRole.INHERITOR, result[1].role)
        assertTrue(result[1].file.path.endsWith("Bar.java"))
    }

    fun testCollectsUsageWithRole() {
        myFixture.addFileToProject(
            "com/example/Foo.java",
            "package com.example; public class Foo {}",
        )
        myFixture.addFileToProject(
            "com/example/User.java",
            """
            package com.example;
            public class User { void use(Foo f) {} }
            """.trimIndent(),
        )
        val fooFile = myFixture.findFileInTempDir("com/example/Foo.java")
        val cls = (com.intellij.psi.PsiManager.getInstance(project)
            .findFile(fooFile) as PsiJavaFile).classes[0]

        val result = RelatedFilesCollector().collect(
            project, cls, setOf(RelationType.USAGE),
        )

        assertEquals(2, result.size)
        assertEquals(FileRole.TARGET, result[0].role)
        assertTrue(result.any { it.role == FileRole.USAGE })
    }

    fun testTargetIsAlwaysFirstEvenWithMixedRoles() {
        myFixture.addFileToProject(
            "com/example/Foo.java",
            "package com.example; public class Foo {}",
        )
        myFixture.addFileToProject(
            "com/example/Bar.java",
            "package com.example; public class Bar extends Foo {}",
        )
        myFixture.addFileToProject(
            "com/example/User.java",
            """
            package com.example;
            public class User { void use(Foo f) {} }
            """.trimIndent(),
        )
        val fooFile = myFixture.findFileInTempDir("com/example/Foo.java")
        val cls = (com.intellij.psi.PsiManager.getInstance(project)
            .findFile(fooFile) as PsiJavaFile).classes[0]

        val result = RelatedFilesCollector().collect(
            project, cls,
            setOf(RelationType.INHERITOR, RelationType.USAGE),
        )

        assertEquals(FileRole.TARGET, result.first().role)
        val inheritorIdx = result.indexOfFirst { it.role == FileRole.INHERITOR }
        val usageIdx = result.indexOfFirst { it.role == FileRole.USAGE }
        if (inheritorIdx >= 0 && usageIdx >= 0) {
            assertTrue(
                "Inheritor should come before usage (priority)",
                inheritorIdx < usageIdx,
            )
        }
    }

    fun testResultIsDeterministicOnRepeatedCalls() {
        myFixture.addFileToProject(
            "com/example/Foo.java",
            "package com.example; public class Foo {}",
        )
        myFixture.addFileToProject(
            "com/example/Bar.java",
            "package com.example; public class Bar extends Foo {}",
        )
        val fooFile = myFixture.findFileInTempDir("com/example/Foo.java")
        val cls = (com.intellij.psi.PsiManager.getInstance(project)
            .findFile(fooFile) as PsiJavaFile).classes[0]

        val first = RelatedFilesCollector().collect(
            project, cls, setOf(RelationType.INHERITOR),
        )
        val second = RelatedFilesCollector().collect(
            project, cls, setOf(RelationType.INHERITOR),
        )

        assertEquals(first.size, second.size)
        assertEquals(first.map { it.file.path }, second.map { it.file.path })
        assertEquals(first.map { it.role }, second.map { it.role })
    }

    fun testTransitiveDependencyIncludesSecondLevel() {
        myFixture.addFileToProject(
            "com/example/A.java",
            """
        package com.example;
        public class A { void use(B b) {} }
        """.trimIndent(),
        )
        myFixture.addFileToProject(
            "com/example/B.java",
            """
        package com.example;
        public class B { void use(C c) {} }
        """.trimIndent(),
        )
        myFixture.addFileToProject(
            "com/example/C.java",
            "package com.example; public class C {}",
        )
        val aFile = myFixture.findFileInTempDir("com/example/A.java")
        val targetA = (PsiManager.getInstance(project).findFile(aFile) as PsiJavaFile).classes[0]

        val result = RelatedFilesCollector().collect(
            project, targetA, setOf(RelationType.TRANSITIVE_DEPENDENCY),
        )

        val paths = result.map { it.file.path }
        assertTrue("Expected A.java in $paths", paths.any { it.endsWith("A.java") })
        assertTrue("Expected B.java in $paths", paths.any { it.endsWith("B.java") })
        assertTrue("Expected C.java in $paths", paths.any { it.endsWith("C.java") })
        assertEquals(FileRole.DEPENDENCY, result.first { it.file.path.endsWith("C.java") }.role)
    }

    fun testDirectDependencyDoesNotIncludeSecondLevel() {
        myFixture.addFileToProject(
            "com/example/A.java",
            """
        package com.example;
        public class A { void use(B b) {} }
        """.trimIndent(),
        )
        myFixture.addFileToProject(
            "com/example/B.java",
            """
        package com.example;
        public class B { void use(C c) {} }
        """.trimIndent(),
        )
        myFixture.addFileToProject(
            "com/example/C.java",
            "package com.example; public class C {}",
        )
        val aFile = myFixture.findFileInTempDir("com/example/A.java")
        val targetA = (PsiManager.getInstance(project).findFile(aFile) as PsiJavaFile).classes[0]

        val result = RelatedFilesCollector().collect(
            project, targetA, setOf(RelationType.DEPENDENCY),
        )

        val paths = result.map { it.file.path }
        assertTrue(paths.any { it.endsWith("B.java") })
        assertFalse("Direct search must not include C.java: $paths",
            paths.any { it.endsWith("C.java") })
    }

    fun testTransitiveAndDirectTogetherDoNotDuplicateWork() {
        myFixture.addFileToProject(
            "com/example/A.java",
            "package com.example; public class A { void use(B b) {} }",
        )
        myFixture.addFileToProject(
            "com/example/B.java",
            "package com.example; public class B { void use(C c) {} }",
        )
        myFixture.addFileToProject(
            "com/example/C.java",
            "package com.example; public class C {}",
        )
        val aFile = myFixture.findFileInTempDir("com/example/A.java")
        val targetA = (PsiManager.getInstance(project).findFile(aFile) as PsiJavaFile).classes[0]

        val both = RelatedFilesCollector().collect(
            project, targetA,
            setOf(RelationType.DEPENDENCY, RelationType.TRANSITIVE_DEPENDENCY),
        )
        val transitiveOnly = RelatedFilesCollector().collect(
            project, targetA, setOf(RelationType.TRANSITIVE_DEPENDENCY),
        )

        assertEquals(
            "Both-checked must equal transitive-only",
            transitiveOnly.map { it.file.path to it.role },
            both.map { it.file.path to it.role },
        )
    }
}