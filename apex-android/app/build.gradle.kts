plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.apex.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.apex.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "0.2"
    }

    // Llave de firma FIJA. Así todos los APK compilados en la nube quedan
    // firmados con la misma clave y el celular permite ACTUALIZAR encima
    // (sin tener que desinstalar en cada versión).
    signingConfigs {
        create("apex") {
            storeFile = file("apex.keystore")
            storePassword = "apexapex"
            keyAlias = "apex"
            keyPassword = "apexapex"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("apex")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("apex")
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
    }
    composeOptions {
        // Debe coincidir con Kotlin 1.9.24
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")

    // Jetpack Compose (versiones alineadas por el BOM)
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
