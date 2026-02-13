package dev.sebastiano.camerasync.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class NoFullyQualifiedAppReferenceRule(config: Config) : Rule(config) {
    override val issue: Issue =
        Issue(
            id = "NoFullyQualifiedAppReference",
            severity = Severity.Style,
            description = "Use imports instead of fully qualified app references.",
            debt = io.gitlab.arturbosch.detekt.api.Debt.FIVE_MINS,
        )

    private val packagePrefix: String = "dev.sebastiano.camerasync."

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        if (expression.getParentOfType<KtImportDirective>(true) != null) {
            return
        }
        if (expression.getParentOfType<KtPackageDirective>(true) != null) {
            return
        }
        val packageDirectiveText = expression.containingKtFile.packageDirective?.text
        if (packageDirectiveText != null && packageDirectiveText.contains(expression.text)) {
            return
        }
        val fileText = expression.containingKtFile.text
        if (isInsidePackageDirective(expression)) {
            return
        }
        if (isOnPackageLine(fileText, expression.textOffset)) {
            return
        }
        val text = expression.text
        if (text.startsWith(packagePrefix)) {
            report(CodeSmell(issue, Entity.from(expression), "Use an import for $packagePrefix"))
        }
        super.visitDotQualifiedExpression(expression)
    }

    override fun visitUserType(type: KtUserType) {
        if (type.getParentOfType<KtImportDirective>(true) != null) {
            return
        }
        if (type.getParentOfType<KtPackageDirective>(true) != null) {
            return
        }
        val packageDirectiveText = type.containingKtFile.packageDirective?.text
        if (packageDirectiveText != null && packageDirectiveText.contains(type.text)) {
            return
        }
        val fileText = type.containingKtFile.text
        if (isInsidePackageDirective(type)) {
            return
        }
        if (isOnPackageLine(fileText, type.textOffset)) {
            return
        }
        val text = type.text
        if (text.startsWith(packagePrefix)) {
            report(CodeSmell(issue, Entity.from(type), "Use an import for $packagePrefix"))
        }
        super.visitUserType(type)
    }

    private fun isInsidePackageDirective(element: KtElement): Boolean {
        val fileText = element.containingKtFile.text
        if (!fileText.startsWith("package ")) return false
        val lineEnd = fileText.indexOf('\n').let { if (it == -1) fileText.length else it }
        return element.textRange.startOffset <= lineEnd
    }

    private fun isOnPackageLine(fileText: String, offset: Int): Boolean {
        val safeOffset = offset.coerceIn(0, fileText.length)
        val lineStart = fileText.lastIndexOf('\n', safeOffset).let { if (it == -1) 0 else it + 1 }
        val lineEnd = fileText.indexOf('\n', safeOffset).let { if (it == -1) fileText.length else it }
        val lineText = fileText.substring(lineStart, lineEnd)
        return lineText.trimStart().startsWith("package ")
    }
}
