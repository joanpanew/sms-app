plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.joanpa.estadowa"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.joanpa.estadowa"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
    }

    // Clave fija en el repo: así cada APK nueva se instala ENCIMA de la anterior
    // (con la clave debug aleatoria de GitHub Actions Android decía "conflicto con un paquete").
    // Solo sirve para uso propio; no es una clave de publicación.
    signingConfigs {
        create("fija") {
            storeFile = file("estado-debug.keystore")
            storePassword = "android"
            keyAlias = "estado"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("fija")
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("fija")
            isMinifyEnabled = false
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.1")
}
