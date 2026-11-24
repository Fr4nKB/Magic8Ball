plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.frank.magic8ball"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.frank.magic8ball"
        minSdk = 35
        targetSdk = 37
        versionCode = 16
        versionName = "1.0.3"

        ndk {
            debugSymbolLevel = "FULL"
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(files("libs/glyph-matrix-sdk-2.0.aar"))  // import the sdk
}