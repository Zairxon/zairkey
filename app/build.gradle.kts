plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.zairxon.uzkeyboard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.zairxon.uzkeyboard"
        minSdk = 24
        targetSdk = 34
        versionCode = 14
        versionName = "2.3"
    }

    // Фиксированный ключ подписи — одинаковый для локальных и CI-сборок, иначе Android
    // требует удалить приложение перед установкой (несовпадение подписи). Пароль
    // «android» как у debug-хранилища — не секрет; для личного sideload-приложения ок.
    signingConfigs {
        create("stable") {
            storeFile = file("zairkey.keystore")
            storePassword = "android"
            keyAlias = "zairkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("stable")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("stable")
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
