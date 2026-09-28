plugins {
    id("com.android.application")
}

android {
    namespace = "com.uglygameface.atmosynq"
    compileSdk = 37
    compileSdkMinor = 0

    defaultConfig {
        applicationId = "com.uglygameface.atmosynq"
        minSdk = 26
        targetSdk = 36
        versionCode = 18
        versionName = "0.8.2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all {
            it.useJUnit()
        }
    }
}


dependencies {
    implementation("com.google.android.filament:filament-android:1.77.1")
    implementation("com.google.android.filament:gltfio-android:1.77.1")
    implementation("com.google.android.filament:filament-utils-android:1.77.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
