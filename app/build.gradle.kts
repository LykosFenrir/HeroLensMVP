import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The app has no Java sources. This opt-in exists for sandboxed Windows builds
// where javac can be denied while closing dependency ZIP files. CI remains unchanged.
if (providers.environmentVariable("HEROLENS_SKIP_EMPTY_JAVA_COMPILE").orNull == "true") {
    tasks.withType<JavaCompile>().configureEach {
        enabled = false
    }
}

android {
    namespace = "com.herolens.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.herolens.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 20
        versionName = "0.12.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    // Direct-download APKs stay practical in size by shipping one native runtime
    // per CPU architecture. The universal APK remains available as a fallback.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.04.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    val cameraXVersion = "1.6.1"
    implementation("androidx.camera:camera-core:$cameraXVersion")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    implementation("androidx.camera:camera-view:$cameraXVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.27.0")
    // Bundled, offline-first OCR: match screenshots work immediately and never upload.
    implementation("com.google.mlkit:text-recognition:16.0.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    if (providers.environmentVariable("HEROLENS_USE_GRADLE_JUNIT").orNull == "true") {
        testImplementation(files(
            File(gradle.gradleHomeDir, "lib/junit-4.13.2.jar"),
            File(gradle.gradleHomeDir, "lib/hamcrest-core-1.3.jar")
        ))
    } else {
        testImplementation("junit:junit:4.13.2")
    }
}
