import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

// The signing material stays outside this repository. Values may be supplied
// through -P properties or MIA_SIGNING_* environment variables on the build
// machine; no password or keystore is committed here.
val signingStore = providers.gradleProperty("miaSigningStore")
    .orElse(providers.environmentVariable("MIA_SIGNING_STORE"))
    .orNull
val signingPassword = providers.gradleProperty("miaSigningPassword")
    .orElse(providers.environmentVariable("MIA_SIGNING_PASSWORD"))
    .orNull
val signingAlias = providers.gradleProperty("miaSigningAlias")
    .orElse(providers.environmentVariable("MIA_SIGNING_ALIAS"))
    .orElse("victor")
    .get()
val signingFile = signingStore?.let { File(it) }
val hasWikiSigning = signingFile?.isFile == true && !signingPassword.isNullOrBlank()
val catalogEndpoint = providers.gradleProperty("miaCatalogEndpoint")
    .orElse(providers.environmentVariable("MIA_CATALOG_ENDPOINT"))
    .orElse("")
    .get()
val catalogSha256 = providers.gradleProperty("miaCatalogSha256")
    .orElse(providers.environmentVariable("MIA_CATALOG_SHA256"))
    .orElse("")
    .get()

fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "mia.chinese"
    compileSdk = 34

    defaultConfig {
        applicationId = "mia.chinese"
        minSdk = 28
        targetSdk = 34
        versionCode = 12
        versionName = "0.1.11"
        buildConfigField("String", "CATALOG_ENDPOINT", buildConfigString(catalogEndpoint))
        buildConfigField("String", "CATALOG_SHA256", buildConfigString(catalogSha256))
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasWikiSigning) {
            create("wikiRelease") {
                storeFile = checkNotNull(signingFile)
                storePassword = checkNotNull(signingPassword)
                keyAlias = signingAlias
                keyPassword = checkNotNull(signingPassword)
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasWikiSigning) signingConfig = signingConfigs.getByName("wikiRelease")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

tasks.matching { it.name == "validateSigningRelease" }.configureEach {
    doFirst {
        check(hasWikiSigning) {
            "Release APK must use the wiki signing key. Set MIA_SIGNING_STORE, " +
                "MIA_SIGNING_PASSWORD, and MIA_SIGNING_ALIAS before assembleRelease."
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation(platform("androidx.compose:compose-bom:2024.02.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.webkit:webkit:1.10.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.02"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
