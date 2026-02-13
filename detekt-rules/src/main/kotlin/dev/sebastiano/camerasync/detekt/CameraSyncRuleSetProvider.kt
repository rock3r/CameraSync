package dev.sebastiano.camerasync.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

class CameraSyncRuleSetProvider : RuleSetProvider {
    override val ruleSetId: String = "camerasync"

    override fun instance(config: Config): RuleSet =
        RuleSet(ruleSetId, listOf(NoFullyQualifiedAppReferenceRule(config)))
}
