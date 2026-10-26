import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

// Explicit local smoke check using the real shared HTTP client. No credentials in build arguments.
tasks.register<JavaExec>("verifyLocalSpringApi") {
    val target = kotlin.targets.getByName("jvm") as org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
    val compilation = target.compilations.getByName("test")
    dependsOn(compilation.compileTaskProvider)
    classpath(compilation.output.allOutputs, compilation.runtimeDependencyFiles)
    mainClass.set("dev.orestegabo.portfolio.investments.LocalSpringApiCheckKt")
}

kotlin {
    iosArm64()
    iosSimulatorArm64()
    
    jvm()
    
    android {
       namespace = "dev.orestegabo.portfolio.core"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
    }
    
    sourceSets {
        androidMain.dependencies { implementation(libs.ktor.client.android) }
        iosMain.dependencies { implementation(libs.ktor.client.darwin) }
        jvmMain.dependencies { implementation(libs.ktor.client.cio) }
        commonMain.dependencies {
            api(libs.kotlinx.json)
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.coroutines)
            api(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
