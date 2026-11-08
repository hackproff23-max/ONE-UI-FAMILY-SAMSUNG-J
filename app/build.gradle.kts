plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.samsung.oneui"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.samsung.oneui"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // Chave fixa: todo APK gerado tem a mesma assinatura, entao da pra instalar por cima sem desinstalar.
    signingConfigs {
        create("fixed") {
            storeFile = rootProject.file("keystore/oneui.jks")
            storePassword = "oneui123"
            keyAlias = "oneui"
            keyPassword = "oneui123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
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
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
