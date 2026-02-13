import java.io.FileInputStream
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.metro)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.ktfmt)
    alias(libs.plugins.detekt)
    id("kotlin-parcelize")
}

val keystorePropertiesFile: File = project.file("keystore.properties")

val keystoreProperties =
    Properties().apply {
        fun hasReleaseStoreEnv(): Boolean {
            val env = System.getenv("RELEASE_STORE_FILE") ?: return false
            return file(env).isFile
        }

        if (keystorePropertiesFile.isFile) {
            load(FileInputStream(keystorePropertiesFile))
        } else if (hasReleaseStoreEnv()) {
            // Fallback to environment variables for CI
            setProperty("keyAlias", System.getenv("RELEASE_KEY_ALIAS"))
            setProperty("keyPassword", System.getenv("RELEASE_KEY_PASSWORD"))
            setProperty("storeFile", System.getenv("RELEASE_STORE_FILE"))
            setProperty("storePassword", System.getenv("RELEASE_STORE_PASSWORD"))
        }
    }

android {
    namespace = "dev.sebastiano.camerasync"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.sebastiano.camerasync"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (keystoreProperties["storeFile"] != null) {
            create("release") {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (signingConfigs.findByName("release") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures { compose = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    installation { installOptions += listOf("--user", "0") }
}

ktfmt { kotlinLangStyle() }

tasks.withType<Test>().configureEach {
    if (name == "testReleaseUnitTest") {
        // Robolectric fails to resolve test activities for the release variant.
        // Debug unit tests still run, so keep release unit tests disabled for now.
        enabled = false
        return@configureEach
    }
    if (!name.endsWith("UnitTest")) {
        return@configureEach
    }
    val variantName = name.removePrefix("test").removeSuffix("UnitTest")
    if (variantName.isEmpty()) {
        return@configureEach
    }
    val variantDir = variantName.replaceFirstChar { it.lowercase() }
    val manifestPath =
        layout.buildDirectory
            .file(
                "intermediates/packaged_manifests/${variantDir}UnitTest/" +
                    "process${variantName}UnitTestManifest/AndroidManifest.xml"
            )
            .get()
            .asFile
            .absolutePath
    doFirst { systemProperty("robolectric.manifest", manifestPath) }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }

detekt {
    config.setFrom(files("${project.rootDir}/detekt.yml"))
    buildUponDefaultConfig = true
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.animation.graphics)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.dataStore)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.text.google.fonts)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kable)
    implementation(libs.khronicle.core)
    implementation(libs.play.services.location)
    implementation(libs.maplibre.core)
    implementation(libs.maplibre.material3)
    implementation(libs.maplibre.spatialk)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.protobuf.kotlin.lite)

    debugImplementation(libs.androidx.ui.test.manifest)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.mockwebserver)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.androidx.ui.test.manifest)
    testImplementation(libs.robolectric)

    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))

    detektPlugins(libs.compose.rules.detekt)
    detektPlugins(project(":detekt-rules"))
}

// Setup protobuf configuration, generating lite Java and Kotlin classes
protobuf {
    protoc { artifact = libs.protobuf.protoc.get().toString() }

    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                register("java") { option("lite") }
                register("kotlin") { option("lite") }
            }
        }
    }
}
