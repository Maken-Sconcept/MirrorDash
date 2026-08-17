import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    compileSdk = 36
    namespace = project.property("APP_ID").toString()
    ndkVersion = "25.2.9519653"

    defaultConfig {
        applicationId = project.property("APP_ID").toString()
        minSdk = 25
        targetSdk = 36
        versionName = project.property("VERSION_NAME").toString()
        versionCode = project.property("VERSION_CODE").toString().toInt()

        // Vendored UxPlay AirPlay receiver (see app/src/main/cpp) - same ABI set as
        // BerthierOptions, whose prebuilt OpenSSL libs under cpp/openssl-android-build
        // only exist for these two ABIs.
        ndk {
            abiFilters += "armeabi-v7a"
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
        }
    }

    sourceSets {
        getByName("main").java.directories.add("src/main/kotlin")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    tasks.withType<KotlinCompile> {
        compilerOptions.jvmTarget.set(JvmTarget.fromTarget("17"))
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-service:2.8.6")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("eu.agno3.jcifs:jcifs-ng:2.1.10")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Photorama's local-storage source - browsing a user-picked SAF folder tree (see
    // LocalPhotoRepository), the same abstraction the system folder picker itself returns.
    implementation("androidx.documentfile:documentfile:1.0.1")

    // IPTV tab's player - HLS/TS live streams off a Stalker/Ministra portal (see the iptv
    // package). Not used by AirPlay, which decodes its own H.264/H.265 mirror stream natively.
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.webkit:webkit:1.16.0")

    // Alternate IPTV player backends (see iptv/player) - selectable in Settings, with automatic
    // fallback between all three if one fails on a given stream. Both are ffmpeg-based, unlike
    // ExoPlayer, so they tolerate malformed/unusual streams ExoPlayer sometimes rejects outright.
    implementation("org.videolan.android:libvlc-all:3.3.10")
    // Community fork (Tencent's iot-ijkplayer) published straight to Maven Central - upstream
    // bilibili/ijkplayer is archived/unmaintained and its old jcenter artifacts no longer resolve
    // at all, so this is the only buildable source for it left, not a preference over the
    // original.
    implementation("com.tencent.iot.thirdparty.android:ijkplayer-java:2.0.19")
    implementation("com.tencent.iot.thirdparty.android:ijkplayer-armv7a:2.0.7")
    implementation("com.tencent.iot.thirdparty.android:ijkplayer-arm64:2.0.10")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
