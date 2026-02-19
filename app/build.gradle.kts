plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.frank.magic8ball"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.frank.magic8ball"
        minSdk = 35
        targetSdk = 36
        versionCode = 15
        versionName = "1.0.2"

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
    implementation(files("libs/glyph-matrix-sdk-1.0.aar"))  // import the sdk
}