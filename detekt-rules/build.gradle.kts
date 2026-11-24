plugins {
    kotlin("jvm")
}

val detektVersion = libs.versions.detekt.get()

dependencies {
    compileOnly("io.gitlab.arturbosch.detekt:detekt-api:$detektVersion")
    testImplementation("io.gitlab.arturbosch.detekt:detekt-test:$detektVersion")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(11)
}
