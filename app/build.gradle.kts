plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val icsPresent = rootProject.file("ics-openvpn/main").exists()
val enginePresent = rootProject.file("third_party/openvpn3/client/ovpncli.cpp").exists() &&
    rootProject.file("third_party/asio/asio/include/asio.hpp").exists()
val keystorePath: String? = System.getenv("KEYSTORE_PATH")

android {
    namespace = "com.fastvpn.app7k2q9x"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.fastvpn.app7k2q9x"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"
    }
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    sourceSets {
        // IcsEngine is compiled only when ics-openvpn is present; otherwise a null factory is used.
        getByName("main").java.srcDir(if (icsPresent) "src/engineIcs/java" else "src/engineNoIcs/java")
    }
    ndkVersion = "26.1.10909125"
    buildFeatures { prefab = true }
    if (enginePresent) {
        // Native OpenVPN 3 core (see scripts/fetch_engine.sh). Without it the app builds but cannot connect.
        defaultConfig { ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") } }
        externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures.compose = true
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    if (icsPresent) implementation(project(":openvpn"))
    implementation("com.android.ndk.thirdparty:openssl:1.1.1q-beta-1")   // OpenSSL via Prefab for the native core

    testImplementation("junit:junit:4.13.2")
}
