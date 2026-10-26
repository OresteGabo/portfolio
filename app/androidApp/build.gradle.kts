import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    id("com.google.gms.google-services") version "4.5.0" apply false
}

if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

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
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        debug {
            val origin = providers.gradleProperty("developmentApiOrigin").orElse("").get()
            buildConfigField("String", "DEVELOPMENT_API_ORIGIN", "\"${origin.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        }
        release {
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
