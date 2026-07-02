
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "pico.haxx"
    compileSdk = 33

    defaultConfig {
        applicationId = "pico.haxx"
        minSdk = 29
        targetSdk = 33
        versionCode = 1
        versionName = "1.25"
    }
    packagingOptions {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isJniDebuggable = true
        }
        getByName("debug") {
            isJniDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
    buildToolsVersion = "33.0.1"
}

dependencies {

}

val binaryPath = "E:\\home\\adb\\bin\\__CVE\\___CVE-2023-33107_KETO\\..releaseBranch\\picohaxx"

// Erstelle einen Task, der die Datei kopiert
tasks.register<Copy>("copyBinToJniLibs") {
    from(binaryPath)
    into("src/main/jniLibs/arm64-v8a/")
    rename { "libpicohaxx.so" }
}

// Sag Gradle, dass dieser Task VOR dem 'preBuild' laufen muss
tasks.named("preBuild") {
    dependsOn("copyBinToJniLibs")
}