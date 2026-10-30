import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    id("com.google.gms.google-services") version "4.5.0" apply false
}

if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

// One checked-in version for both stores. Credentials are environment-only.
val releaseVersion = Properties().apply {
    rootProject.file("Version.xcconfig").inputStream().use { load(it) }
}
val appVersionName = releaseVersion.getProperty("MARKETING_VERSION").trim()
val appVersionCode = releaseVersion.getProperty("CURRENT_PROJECT_VERSION").trim().toInt()
require(appVersionName.matches(Regex("[0-9]+\\.[0-9]+(?:\\.[0-9]+)?"))) { "Invalid MARKETING_VERSION" }
require(appVersionCode in 1..2_100_000_000) { "Invalid CURRENT_PROJECT_VERSION" }
val signingNames = listOf("STRUCT_UPLOAD_STORE_FILE", "STRUCT_UPLOAD_STORE_PASSWORD", "STRUCT_UPLOAD_KEY_ALIAS", "STRUCT_UPLOAD_KEY_PASSWORD")
val uploadSigning = signingNames.associateWith { providers.environmentVariable(it).orNull }
val hasUploadSigning = uploadSigning.values.all { !it.isNullOrBlank() }
require(uploadSigning.values.all { it.isNullOrBlank() } || hasUploadSigning) {
    "Supply all four STRUCT_UPLOAD_* signing variables, or none."
}
val requireReleaseSigning = providers.gradleProperty("requireReleaseSigning").map { it.toBooleanStrict() }.orElse(false)
val validateBetaSigning = tasks.register("validateBetaSigning") {
    doLast {
        check(hasUploadSigning) { "A beta artifact requires all four STRUCT_UPLOAD_* signing variables." }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    if (requireReleaseSigning.get()) dependsOn(validateBetaSigning)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation(project(":app:shared"))

    implementation(libs.androidx.activity.compose)
    implementation("androidx.fragment:fragment:1.8.9")
    implementation(libs.androidx.appcompat)
    implementation(libs.compose.runtime)
    androidTestImplementation(libs.androidx.testExt.junit)
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation(libs.compose.foundation)
    androidTestImplementation(libs.compose.material3)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
    // Experimental capture-layer exclusion is available only in local debug builds.
    debugImplementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
}

android {
    namespace = "dev.orestegabo.portfolio"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.orestegabo.portfolio"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        if (hasUploadSigning) create("upload") {
            storeFile = file(uploadSigning.getValue("STRUCT_UPLOAD_STORE_FILE")!!)
            storePassword = uploadSigning.getValue("STRUCT_UPLOAD_STORE_PASSWORD")
            keyAlias = uploadSigning.getValue("STRUCT_UPLOAD_KEY_ALIAS")
            keyPassword = uploadSigning.getValue("STRUCT_UPLOAD_KEY_PASSWORD")
        }
    }
    buildTypes {
        debug {
            val origin = providers.gradleProperty("developmentApiOrigin").orElse("").get()
            buildConfigField("String", "DEVELOPMENT_API_ORIGIN", "\"${origin.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        }
        release {
            if (hasUploadSigning) signingConfig = signingConfigs.getByName("upload")
            buildConfigField("String", "DEVELOPMENT_API_ORIGIN", "\"\"")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
