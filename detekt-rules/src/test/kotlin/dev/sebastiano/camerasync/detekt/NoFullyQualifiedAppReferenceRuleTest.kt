package dev.sebastiano.camerasync.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.compileAndLint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoFullyQualifiedAppReferenceRuleTest {
    @Test
    fun `reports fully qualified type references`() {
        val code =
            """
            package sample

            class Example {
                val repo: dev.sebastiano.camerasync.data.Foo = Foo()
            }
            """
                .trimIndent()

        val findings = NoFullyQualifiedAppReferenceRule(Config.empty).compileAndLint(code)

        assertTrue(findings.isNotEmpty())
        assertTrue(findings.all { it.id == "NoFullyQualifiedAppReference" })
    }

    @Test
    fun `reports fully qualified call references`() {
        val code =
            """
            package sample

            class Example {
                val repo = dev.sebastiano.camerasync.data.Foo()
            }
            """
                .trimIndent()

        val findings = NoFullyQualifiedAppReferenceRule(Config.empty).compileAndLint(code)

        assertTrue(findings.isNotEmpty())
        assertTrue(findings.all { it.id == "NoFullyQualifiedAppReference" })
    }

    @Test
    fun `ignores package directive references`() {
        val code =
            """
            package dev.sebastiano.camerasync.sample

            class Example
            """
                .trimIndent()

        val findings = NoFullyQualifiedAppReferenceRule(Config.empty).compileAndLint(code)

        assertEquals(0, findings.size)
    }

    @Test
    fun `ignores imports`() {
        val code =
            """
            package sample

            import dev.sebastiano.camerasync.data.Foo

            class Example {
                val repo: Foo = Foo()
            }
            """
                .trimIndent()

        val findings = NoFullyQualifiedAppReferenceRule(Config.empty).compileAndLint(code)

        assertEquals(0, findings.size)
    }
}
