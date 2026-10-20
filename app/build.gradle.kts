import java.net.URL

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.c016.pushupflappy"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.c016.pushupflappy"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        // A fixed debug key committed to the repo so APKs from different CI runs
        // can be installed over each other without uninstalling first.
        getByName("debug") {
            storeFile = file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    androidResources {
        noCompress += "task"
    }
}

val poseModelUrl =
    "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task"
val poseModelFile = file("src/main/assets/pose_landmarker_lite.task")

val downloadPoseModel by tasks.registering {
    description = "Downloads the MediaPipe pose landmarker model into assets."
    outputs.file(poseModelFile)
    onlyIf { !poseModelFile.exists() }
    doLast {
        poseModelFile.parentFile.mkdirs()
        URL(poseModelUrl).openStream().use { input ->
            poseModelFile.outputStream().use { output -> input.copyTo(output) }
        }
    }
}

tasks.named("preBuild") { dependsOn(downloadPoseModel) }

dependencies {
    val cameraxVersion = "1.4.1"

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("com.google.mediapipe:tasks-vision:0.10.20")

    testImplementation("junit:junit:4.13.2")
}
