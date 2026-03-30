plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/** URL base del API para la variante "device". Override: en [rootProject]/gradle.properties define dev.api.base.url */
val devApiBaseUrl: String =
    run {
        val raw =
            (project.findProperty("dev.api.base.url") as? String)?.trim()?.takeIf { it.isNotEmpty() }
                ?: "http://127.0.0.1:8080/"
        if (raw.endsWith("/")) raw else "$raw/"
    }

android {
    namespace = "com.example.widget_android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.widget_android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // "device" = móvil físico (USB): http://127.0.0.1 + adb reverse tcp:8080 tcp:8080
    // "emulator" = solo emulador: http://10.0.2.2
    // Por orden alfabético, deviceDebug suele ser la variante por defecto al abrir el proyecto.
    flavorDimensions += "endpoint"
    productFlavors {
        create("device") {
            dimension = "endpoint"
            buildConfigField("String", "API_BASE_URL", "\"${devApiBaseUrl.replace("\"", "\\\"")}\"")
        }
        create("emulator") {
            dimension = "endpoint"
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080/\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}