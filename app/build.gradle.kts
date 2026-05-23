plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.secrets)
}

android {
    namespace = "com.synctrip.app"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.synctrip.app"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // local.properties 키를 AndroidManifest placeholder로 노출
        // 실제 값은 local.properties에서 읽음 (secrets 플러그인)
        manifestPlaceholders["KAKAO_NATIVE_KEY"] = ""
        manifestPlaceholders["GOOGLE_MAPS_API_KEY"] = ""
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("String", "BASE_URL", "\"https://test-api.synctrip.com/\"")
        }
        getByName("release") {
            buildConfigField("String", "BASE_URL", "\"https://api.synctrip.com/\"")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true   // BuildConfig.KAKAO_NATIVE_KEY 등 코드에서 접근할 때 필요
    }
}

// secrets-gradle-plugin: local.properties → BuildConfig + manifestPlaceholders 자동 주입
secrets {
    propertiesFileName = "local.properties"
}

dependencies {
    // ── Compose BOM ─────────────────────────────────────────────────────────
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // ── AndroidX ────────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.navigation.compose)

    // ── Image loading ────────────────────────────────────────────────────────
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // ── Network ──────────────────────────────────────────────────────────────
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)

    // ── Coroutines ───────────────────────────────────────────────────────────
    implementation(libs.coroutines.android)

    // ── Google – 로그인 (Credential Manager) ─────────────────────────────────
    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.google.id)

    // ── Google Maps ──────────────────────────────────────────────────────────
    implementation(libs.play.services.maps)
    implementation(libs.maps.compose)

    // ── Kakao ────────────────────────────────────────────────────────────────
    implementation(libs.kakao.user)
    implementation(libs.kakao.maps)

    // ── Test ─────────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
