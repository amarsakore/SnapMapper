plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.amar.snapmapper"
    compileSdk = 35

    defaultConfig {
        // ColorOS sends Snap Key presses for its "Translate" function to this package name
        // (ColorOS Translate isn't installed on this phone). See KeyMessengerService.
        // (The hidden "test" slot was tried too: ColorOS drops it from the list on every boot.)
        applicationId = "com.coloros.translate"
        minSdk = 30
        targetSdk = 35
        versionCode = 3
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Signed with the debug key so the release APK installs directly.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
