plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "gg.dunder.thriveling.core.health"
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
    implementation(project(":core:model"))
    implementation(project(":core:domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.androidx.concurrent.futures.ktx)

    // Health Services on Wear OS
    implementation(libs.androidx.health.services.client)

    // WorkManager: re-registration after boot
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
}

