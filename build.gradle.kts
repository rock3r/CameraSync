// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ktfmt)
    alias(libs.plugins.detekt)
}

ktfmt {
    kotlinLangStyle()
}

detekt {
    toolVersion = libs.versions.detekt.get()
    config.setFrom(files("detekt.yml"))
    buildUponDefaultConfig = true
}

dependencies {
    detektPlugins(libs.compose.rules.detekt)
}
