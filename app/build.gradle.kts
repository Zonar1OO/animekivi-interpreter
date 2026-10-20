plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.animekivi.livetranslate"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.animekivi.livetranslate"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.4"
    }
}
dependencies {
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:language-id:17.0.6")
}
