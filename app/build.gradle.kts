plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.burnto.disk"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.burnto.disk"
        minSdk = 21
        targetSdk = 34
        versionCode = 24
        versionName = "1.2.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            // Self-signed keystore bundled for non-Play distribution (GitHub
            // Releases). This is NOT a Play-grade secret; for a Play submission,
            // replace with a private upload key kept out of source control.
            storeFile = file("burn2disk-release.keystore")
            storePassword = "burn2disk"
            keyAlias = "burn2disk"
            keyPassword = "burn2disk"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        // Required for java.time / nio APIs on minSdk 21
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // Keep the bundled wimlib-imagex binary uncompressed in assets
        jniLibs {
            useLegacyPackaging = false
        }
    }

    // Do not compress the bundled binary so it can be copied out intact
    androidResources {
        noCompress += "wimlib-imagex"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)

    implementation(libs.libaums.core)
    implementation(libs.rootbeer)

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
}

// --- 16KB page alignment for native libs (Android 16+) ---
afterEvaluate {
    tasks.findByName("mergeDebugNativeLibs")?.doLast {
        val jniDir = file("${layout.buildDirectory.get()}/intermediates/merged_native_libs/debug/mergeDebugNativeLibs/out/lib")
        if (!jniDir.exists()) {
            logger.lifecycle("realignNativeLibs: $jniDir not found, skipping")
            return@doLast
        }
        val script = file("${rootDir}/realign_native_libs.py")
        val python = if (System.getProperty("os.name").lowercase().contains("windows")) "python" else "python3"
        val proc = ProcessBuilder(python, script.absolutePath, jniDir.absolutePath)
            .redirectErrorStream(true)
            .start()
        proc.inputStream.bufferedReader().forEachLine { logger.lifecycle(it) }
        val exit = proc.waitFor()
        if (exit != 0) throw GradleException("realign_native_libs.py failed (exit $exit)")
    }
}

kapt {
    correctErrorTypes = true
}
