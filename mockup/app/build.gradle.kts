plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ai.pleos.challenge.arrival"
    compileSdk = 35

    defaultConfig {
        applicationId = "ai.pleos.challenge.arrival"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // Release signing comes from the local Gradle properties (route94StoreFile / route94StorePassword /
    // route94KeyAlias / route94KeyPassword), so the keystore never enters the repository. Without them the
    // release build is unsigned, which is fine for CI compiles but not for the submitted APK.
    val storePath = providers.gradleProperty("route94StoreFile").orNull
    if (storePath != null) {
        signingConfigs {
            create("release") {
                storeFile = file(storePath)
                storePassword = providers.gradleProperty("route94StorePassword").orNull
                keyAlias = providers.gradleProperty("route94KeyAlias").orNull
                keyPassword = providers.gradleProperty("route94KeyPassword").orNull
            }
        }
    }
    buildTypes {
        release {
            // No shrinking: the Pleos SDKs talk to system services over AIDL and reflection-free code is small anyway.
            isMinifyEnabled = false
            if (storePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    // CarPropertyManager for gear changes (GEAR_SELECTION)
    useLibrary("android.car")

    buildFeatures { compose = true; buildConfig = true }

    // Configure locally with ORG_GRADLE_PROJECT_kakaoRestApiKey or -PkakaoRestApiKey=...
    val kakaoKey = providers.gradleProperty("kakaoRestApiKey").orNull.orEmpty()
    defaultConfig {
        buildConfigField("String", "KAKAO_REST_API_KEY", "\"${kakaoKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:FusedLocation:1.1.3")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("ai.pleos.playground:SpeechToText:3.3.1")

    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
