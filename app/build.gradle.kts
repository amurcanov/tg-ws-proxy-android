import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.amurcanov.tgwsproxy"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.amurcanov.tgwsproxy"
        minSdk = 24
        targetSdk = 35
        versionCode = 13
        versionName = "1.1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64"))
        }
    }

    // ABI splits: produce separate APKs per architecture + universal
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true  // Universal APK with all 3 architectures
        }
    }

    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localProperties.load(localPropertiesFile.inputStream())
    }

    signingConfigs {
        create("release") {
            val keyFile = localProperties.getProperty("KEYSTORE_FILE")
            if (keyFile != null) {
                // Резолвим путь: если начинается с "..", берём от корня проекта
                val resolvedFile = if (keyFile.startsWith("..")) {
                    // ../release.keystore -> корень проекта / release.keystore
                    file(rootDir.resolve(keyFile.substring(3)))
                } else {
                    file(keyFile)
                }
                if (resolvedFile.exists()) {
                    storeFile = resolvedFile
                    storePassword = localProperties.getProperty("KEYSTORE_PASSWORD")
                    keyAlias = localProperties.getProperty("KEY_ALIAS")
                    keyPassword = localProperties.getProperty("KEY_PASSWORD")
                } else {
                    println("WARNING: Keystore file not found: $keyFile (resolved: ${resolvedFile.absolutePath})")
                }
            }
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val keyFile = localProperties.getProperty("KEYSTORE_FILE")
            val resolvedFile = if (keyFile != null && keyFile.startsWith("..")) {
                file(rootDir.resolve(keyFile.substring(3)))
            } else if (keyFile != null) {
                file(keyFile)
            } else null
            
            if (resolvedFile != null && resolvedFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
                println("✅ Signing config applied: ${resolvedFile.absolutePath}")
            } else {
                println("⚠️ WARNING: Keystore not found, using debug signing")
                println("   Looked for: ${resolvedFile?.absolutePath ?: keyFile}")
            }
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
    // composeOptions removed — AGP 9.x handles Compose compiler internally
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    sourceSets {
        getByName("main") {
            jniLibs.setSrcDirs(listOf("src/main/jniLibs"))
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // DataStore for persistent settings
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // JNA for easy C-shared library calls
    implementation("net.java.dev.jna:jna:5.14.0@aar")
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
    implementation("androidx.compose.material:material-icons-extended")
}
