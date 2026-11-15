plugins {
    id("com.android.application") version "8.2.0" apply false // (Aapka jo Android Gradle Plugin version ho)
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false // (Aapka Kotlin version)
    id("org.jetbrains.kotlin.plugin.compose") version "1.9.24" apply false // Yeh line add karein (Kotlin version ke sath match honi chahiye)
    id("com.google.gms.google-services") version "4.4.2" apply false
}
