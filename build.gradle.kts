buildscript {
    dependencies {
        // Filament 1.77.x ships Kotlin 2.4 metadata through its Android utility stack.
        // AGP 9 built-in Kotlin can be upgraded by placing a newer KGP on this classpath.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.0")
    }
}

plugins {
    id("com.android.application") version "9.1.1" apply false
}
