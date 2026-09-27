plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "gg.dunder.thriveling.core.model"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    testImplementation(libs.junit)
}

