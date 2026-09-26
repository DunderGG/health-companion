import com.android.build.api.variant.HostTestBuilder

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.healthcompanion.core.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

androidComponents {
    onVariants { variant ->
        // Exported Room schemas double as unit-test assets for MigrationTestHelper (not shipped in the APK).
        variant.hostTests[HostTestBuilder.UNIT_TEST_TYPE]
            ?.sources?.assets?.addStaticSourceDirectory("$projectDir/schemas")
    }
}

ksp {
    // Export Room schema JSON per version; committed to git and used by migration tests.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Room SQLite Database
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // DataStore Preferences
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
}

