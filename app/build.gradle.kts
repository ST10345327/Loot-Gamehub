import java.io.File

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services") apply false
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.gamehub.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.Loot.gamehub"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storePasswordVal = System.getenv("GAMEHUB_RELEASE_STORE_PASSWORD")
            val keyAliasVal = System.getenv("GAMEHUB_RELEASE_KEY_ALIAS")
            val keystorePathVal = System.getenv("GAMEHUB_RELEASE_KEYSTORE")
            val keyPasswordVal = System.getenv("GAMEHUB_RELEASE_KEY_PASSWORD")

            if (!keystorePathVal.isNullOrBlank()) {
                storeFile = file(keystorePathVal)
            }
            if (!storePasswordVal.isNullOrBlank()) {
                storePassword = storePasswordVal
            }
            if (!keyAliasVal.isNullOrBlank()) {
                keyAlias = keyAliasVal
            }
            if (!keyPasswordVal.isNullOrBlank()) {
                keyPassword = keyPasswordVal
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            val keystorePathVal = System.getenv("GAMEHUB_RELEASE_KEYSTORE")
            if (!keystorePathVal.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

abstract class ValidateReleaseSigningTask : DefaultTask() {
    @TaskAction
    fun validate() {
        val storePasswordVal = System.getenv("GAMEHUB_RELEASE_STORE_PASSWORD")
        val keyAliasVal = System.getenv("GAMEHUB_RELEASE_KEY_ALIAS")
        val keystorePathVal = System.getenv("GAMEHUB_RELEASE_KEYSTORE")
        val keyPasswordVal = System.getenv("GAMEHUB_RELEASE_KEY_PASSWORD")

        val missing = mutableListOf<String>()
        if (storePasswordVal.isNullOrBlank()) missing.add("GAMEHUB_RELEASE_STORE_PASSWORD")
        if (keyAliasVal.isNullOrBlank()) missing.add("GAMEHUB_RELEASE_KEY_ALIAS")
        if (keystorePathVal.isNullOrBlank()) {
            missing.add("GAMEHUB_RELEASE_KEYSTORE")
        } else {
            val keystoreFile = File(keystorePathVal)
            if (!keystoreFile.exists()) {
                throw GradleException("Release signing error: Keystore file does not exist at path specified by GAMEHUB_RELEASE_KEYSTORE: $keystorePathVal")
            }
        }
        if (keyPasswordVal.isNullOrBlank()) missing.add("GAMEHUB_RELEASE_KEY_PASSWORD")

        if (missing.isNotEmpty()) {
            throw GradleException(
                "Release signing error: Missing required release signing environment variables: ${missing.joinToString(", ")}. " +
                "A signed release build cannot be produced without all four environment variables and a valid keystore file."
            )
        }
    }
}

tasks.register<ValidateReleaseSigningTask>("validateReleaseSigning")

tasks.matching { it.name.startsWith("assembleRelease") || it.name.startsWith("bundleRelease") }.configureEach {
    dependsOn("validateReleaseSigning")
}
dependencies {

    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("io.coil-kt:coil:2.7.0")
// Firebase Cloud Messaging & Auth
    implementation(platform("com.google.firebase:firebase-bom:33.9.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
// Credential Manager for Google SSO
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
// Networking (Retrofit + OkHttp + Gson)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
// Saved session
    implementation("androidx.datastore:datastore-preferences:1.1.1")
// Unit tests
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.junit.junit)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}