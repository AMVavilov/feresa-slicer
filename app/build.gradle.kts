import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val uploadSigningProperties = Properties().apply {
    val localProperties = rootProject.file("keystore.properties")
    if (localProperties.isFile) localProperties.inputStream().use(::load)
}

fun signingValue(propertyName: String, environmentName: String): String? =
    uploadSigningProperties.getProperty(propertyName)?.trim()?.takeIf(String::isNotEmpty)
        ?: System.getenv(environmentName)?.trim()?.takeIf(String::isNotEmpty)

val uploadStoreFile = signingValue("storeFile", "FERESA_UPLOAD_STORE_FILE")
val uploadStorePassword = signingValue("storePassword", "FERESA_UPLOAD_STORE_PASSWORD")
val uploadKeyAlias = signingValue("keyAlias", "FERESA_UPLOAD_KEY_ALIAS")
val uploadKeyPassword = signingValue("keyPassword", "FERESA_UPLOAD_KEY_PASSWORD")
val uploadSigningConfigured = listOf(
    uploadStoreFile,
    uploadStorePassword,
    uploadKeyAlias,
    uploadKeyPassword,
).all { !it.isNullOrBlank() }

val generatedLegalAssets = layout.buildDirectory.dir("generated/legalAssets")

android {
    namespace = "tech.g24.feresaslicer"
    compileSdk = 36
    ndkVersion = "27.1.12297006"

    defaultConfig {
        applicationId = "tech.g24.feresaslicer"
        minSdk = 28
        targetSdk = 36
        versionCode = 28
        versionName = "0.14.0-alpha.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Public OrcaCloud client configuration from OrcaSlicer's AGPL source.
        // This is a publishable identifier, not a service secret.
        buildConfigField("String", "ORCA_AUTH_URL", "\"https://auth.orcaslicer.com\"")
        buildConfigField("String", "ORCA_API_URL", "\"https://api.orcaslicer.com\"")
        buildConfigField("String", "ORCA_PUBLIC_KEY", "\"sb_publishable_lvVe_whOi80SU9BPSxM1kA_tbt9AbR_\"")
        buildConfigField(
            "String",
            "SOURCE_CODE_URL",
            "\"https://github.com/AMVavilov/feresa-slicer\"",
        )
        buildConfigField(
            "String",
            "PRIVACY_POLICY_URL",
            "\"https://sync-and-slice-g24.lovable.app/privacy\"",
        )
        buildConfigField(
            "String",
            "ORCA_ACCOUNT_SETTINGS_URL",
            "\"https://cloud.orcaslicer.com/app/settings\"",
        )

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_shared"
                cppFlags += listOf("-std=c++17", "-Wall", "-Wextra")
            }
        }
    }

    signingConfigs {
        if (uploadSigningConfigured) {
            create("upload") {
                storeFile = rootProject.file(requireNotNull(uploadStoreFile))
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("upload")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs.useLegacyPackaging = false
    }

    sourceSets {
        getByName("main").assets.srcDirs(
            layout.buildDirectory.dir("generated/orcaSystemPresets/assets"),
            generatedLegalAssets,
        )
    }
}

tasks.matching { it.name == "bundleRelease" || it.name == "assembleRelease" }.configureEach {
    doFirst {
        check(uploadSigningConfigured) {
            "Release signing is not configured. Copy keystore.properties.example to " +
                "keystore.properties or set FERESA_UPLOAD_* environment variables."
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.webkit:webkit:1.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    // Android's org.json implementation is provided by the platform in production. Use the
    // matching JVM implementation so the process-settings wire contract can be tested locally.
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.ext:junit-ktx:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}

val fetchOrcaMobileEngine by tasks.registering(Exec::class) {
    group = "build setup"
    description = "Fetches the checksummed ARM64 OrcaSlicer Mobile native engine"
    commandLine("bash", rootProject.file("scripts/fetch-orca-mobile-engine.sh").absolutePath)
    inputs.property(
        "orcaMobileEngineSha256",
        "25bd3b72ff698b43991005f0df65ac57f67766ed4b240c48b8f3ec943eafbbdd",
    )
    outputs.file(layout.projectDirectory.file("src/main/jniLibs/arm64-v8a/.orca-mobile-engine.sha256"))
}

val fetchOrcaSystemPresets by tasks.registering(Exec::class) {
    group = "build setup"
    description = "Fetches the checksummed Orca system profile bundle"
    commandLine("bash", rootProject.file("scripts/fetch-orca-system-presets.sh").absolutePath)
    environment(
        "FERESA_ORCA_PRESET_TARGET_DIR",
        layout.buildDirectory.dir("generated/orcaSystemPresets/assets/orca_profiles")
            .get().asFile.absolutePath,
    )
    inputs.property(
        "orcaSystemPresetManifestSha256",
        "e6cd5b0f71b0d1f2b0b1202e177d2df2b4af0bb2a8a91f2872715a72ee37b98d",
    )
    outputs.dir(layout.buildDirectory.dir("generated/orcaSystemPresets/assets/orca_profiles"))
}

val syncLegalNotices by tasks.registering(Sync::class) {
    group = "build setup"
    description = "Packages project and third-party license notices as Android assets"

    into(generatedLegalAssets.map { it.dir("legal") })
    from(rootProject.file("LICENSE")) {
        rename { "AGPL-3.0.txt" }
    }
    from(rootProject.file("NOTICE.md"))
    from(rootProject.file("THIRD_PARTY_NOTICES.md"))
    from(rootProject.file("third_party_licenses")) {
        into("licenses")
    }
}

tasks.named("preBuild").configure {
    dependsOn(fetchOrcaMobileEngine, fetchOrcaSystemPresets, syncLegalNotices)
}
