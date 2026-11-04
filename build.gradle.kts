// AGP 9 has built-in Kotlin support. To use a KGP newer than the one AGP
// depends on, put that KGP on the buildscript classpath (see the AGP 9
// built-in Kotlin documentation) and apply the plugin by id in subprojects.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.4.21")
        classpath("org.jetbrains.kotlin:kotlin-serialization:2.4.21")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.13")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
}
