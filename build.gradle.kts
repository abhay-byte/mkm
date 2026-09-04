plugins {
    id("com.android.application") version "9.3.2" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}

// AGP 9+ has built-in Kotlin; declare a newer KGP via buildscript to use
// Kotlin 2.4.10 (Gradle 9.5 compatible) instead of AGP's bundled runtime.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}
