plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val developmentApiBaseUrl = providers.gradleProperty("DEV_API_BASE_URL")
    .orElse(providers.environmentVariable("DEV_API_BASE_URL"))
    .getOrElse("http://10.0.2.2:3000/")
val releaseApiBaseUrl = providers.gradleProperty("RELEASE_API_BASE_URL")
    .orElse(providers.environmentVariable("RELEASE_API_BASE_URL"))
    .orNull

fun String.asBuildConfigString(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val validateReleaseApiConfiguration = tasks.register("validateReleaseApiConfiguration") {
    inputs.property("releaseApiBaseUrl", releaseApiBaseUrl ?: "")
    doLast {
        val endpoint = inputs.properties["releaseApiBaseUrl"] as String
        com.gamehub.releaseconfig.ReleaseApiUrlPolicy.validate(endpoint)
    }
}

tasks.register("testReleaseApiConfiguration") {
    doLast {
        val validEndpoints = listOf(
            "https://api.example.test/",
            "https://api.example.test:8443/v1/",
            "https://api.example.test:443/v1/",
            "https://api.example.test:65535/",
            "https://[2001:4860:4860::8888]/",
            "https://localhost.example.test/"
        )
        val invalidEndpoints = listOf(
            null to "Set RELEASE_API_BASE_URL to the hosted HTTPS API before building a release.",
            "" to "Set RELEASE_API_BASE_URL to the hosted HTTPS API before building a release.",
            "   " to "Set RELEASE_API_BASE_URL to the hosted HTTPS API before building a release.",
            "http://api.example.test/" to "Release builds require an HTTPS API base URL.",
            "https://localhost/" to "Release builds must not use a local-only API base URL.",
            "https://localhost./" to "Release builds must not use a local-only API base URL.",
            "https://api.localhost/" to "Release builds must not use a local-only API base URL.",
            "https://api.localhost./" to "Release builds must not use a local-only API base URL.",
            "https://localhost.localdomain/" to "Release builds must not use a local-only API base URL.",
            "https://ip6-localhost/" to "Release builds must not use a local-only API base URL.",
            "https://api.local/" to "Release builds must not use a local-only API base URL.",
            "https://api.lan/" to "Release builds must not use a local-only API base URL.",
            "https://api.internal/" to "Release builds must not use a local-only API base URL.",
            "https://api.home.arpa/" to "Release builds must not use a local-only API base URL.",
            "https://api.intranet/" to "Release builds must not use a local-only API base URL.",
            "https://api.intranet./" to "Release builds must not use a local-only API base URL.",
            "https://127.0.0.1/" to "Release builds must not use a local-only API base URL.",
            "https://127.25.1.2/" to "Release builds must not use a local-only API base URL.",
            "https://10.0.0.5/" to "Release builds must not use a local-only API base URL.",
            "https://192.168.1.5/" to "Release builds must not use a local-only API base URL.",
            "https://169.254.1.5/" to "Release builds must not use a local-only API base URL.",
            "https://172.20.1.5/" to "Release builds must not use a local-only API base URL.",
            "https://100.64.0.1/" to "Release builds must not use a local-only API base URL.",
            "https://2130706433/" to "Release builds must not use a local-only API base URL.",
            "https://0177.1/" to "Release builds must not use a local-only API base URL.",
            "https://0x7f000001/" to "Release builds must not use a local-only API base URL.",
            "https://0x7f.0.0.1/" to "RELEASE_API_BASE_URL must include an absolute URL hostname.",
            "https://[::1]/" to "Release builds must not use a local-only API base URL.",
            "https://[::ffff:127.0.0.1]/" to "Release builds must not use a local-only API base URL.",
            "https://[::ffff:7f00:1]/" to "Release builds must not use a local-only API base URL.",
            "https://[::127.0.0.1]/" to "Release builds must not use a local-only API base URL.",
            "https://[fc00::1]/" to "Release builds must not use a local-only API base URL.",
            "https://api.example.test:0/" to "The API base URL port must be between 1 and 65535.",
            "https://api.example.test:65536/" to "The API base URL port must be between 1 and 65535.",
            "https://api.example.test:-1/" to "RELEASE_API_BASE_URL must include an absolute URL hostname.",
            "https://api.example.test:abc/" to "RELEASE_API_BASE_URL must include an absolute URL hostname.",
            "https://api.example.test" to "The API base URL must end with '/'.",
            "https://user:password@api.example.test/" to "The API base URL must not contain credentials, a query or a fragment.",
            "https://api.example.test/?token=secret/" to "The API base URL must not contain credentials, a query or a fragment.",
            "https://api.example.test/#fragment/" to "The API base URL must not contain credentials, a query or a fragment.",
        )
        validEndpoints.forEach { endpoint ->
            com.gamehub.releaseconfig.ReleaseApiUrlPolicy.validate(endpoint)

        }
        invalidEndpoints.forEach { (endpoint, expectedMessage) ->
            try {
                com.gamehub.releaseconfig.ReleaseApiUrlPolicy.validate(endpoint)
                throw GradleException("Release URL policy unexpectedly accepted: $endpoint")
            } catch (error: IllegalArgumentException) {
                if (error.message != expectedMessage) {
                    throw GradleException(
                        "Release URL policy rejected '$endpoint' with '${error.message}' instead of '$expectedMessage'.",
                        error
                    )
                }
            }
        }
    }
}

tasks.configureEach {
    if (name == "preReleaseBuild") {
        dependsOn(validateReleaseApiConfiguration)
    }
}

android {
    namespace = "com.gamehub.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.gamehub.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", developmentApiBaseUrl.asBuildConfigString())
        }
        release {
            buildConfigField("String", "API_BASE_URL", (releaseApiBaseUrl ?: "").asBuildConfigString())
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
dependencies {

    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Navigation and lifecycle
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.material:material-icons-extended")
// Networking (Retrofit + OkHttp + Gson)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
// Saved session
    implementation("androidx.datastore:datastore-preferences:1.1.1")
// Unit tests
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.material:material-icons-extended") // version comes from the Compose BOM

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.junit.junit)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

}