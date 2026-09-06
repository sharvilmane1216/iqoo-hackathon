import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// local.properties holds the developer's real key; fall back to the
// committed template default so fresh checkouts still compile.
val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localPropsFile.inputStream().use { localProps.load(it) }
}
val callmissedKey: String =
    localProps.getProperty("CALLMISSED_API_KEY")
        ?: System.getenv("CALLMISSED_API_KEY")
        ?: "placeholder"
val callmissedKeyReady: Boolean =
    callmissedKey.trim().removeSurrounding("\"").let { it.isNotBlank() && it != "placeholder" }

android {
    namespace = "com.aasra.companion"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aasra.companion"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-trackA"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += "arm64-v8a"
        }
        buildConfigField("String", "CALLMISSED_API_KEY", "\"$callmissedKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    bundle { language { enableSplit = false } }
    packaging {
        jniLibs.useLegacyPackaging = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

gradle.taskGraph.whenReady {
    val needsKey = gradle.taskGraph.allTasks.any {
        it.name.startsWith("assemble") || it.name.startsWith("install") || it.name.startsWith("bundle")
    }
    if (needsKey) {
        check(callmissedKeyReady) {
            "CALLMISSED_API_KEY is required. Copy local.defaults.properties to local.properties " +
                "and set CALLMISSED_API_KEY=cm_... from console.callmissed.com " +
                "(or export CALLMISSED_API_KEY). Do not commit the key."
        }
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)
    implementation(libs.datastore.preferences)
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-devanagari:16.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    val composeBom = libs.compose.bom
    implementation(platform(composeBom))
    androidTestImplementation(platform(composeBom))

    implementation(libs.compose.ui) {
        exclude(group = "androidx.graphics", module = "graphics-path")
    }
    implementation(libs.compose.ui.tooling.preview) {
        exclude(group = "androidx.graphics", module = "graphics-path")
    }
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.navigation.compose)

    // Cloud client types (CallMissedClient) leak OkHttp/coroutines into signatures.
    implementation(libs.okhttp.lib)
    implementation(libs.coroutines.android)
    // Room + serialization types leak via :tools/:data and :cloud-callmissed.
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.serialization.json)

    // Cross-track modules (wired by integrator after parallel tracks landed).
    implementation(project(":core-audio"))
    implementation(project(":core-pipeline"))
    implementation(project(":engine-sherpa"))
    implementation(project(":engine-llama"))
    implementation(project(":cloud-callmissed"))
    implementation(project(":tools"))
    implementation(project(":models"))
    implementation(project(":data"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
