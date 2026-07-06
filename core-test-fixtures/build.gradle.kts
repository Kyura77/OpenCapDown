plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.opencapdown.core.testfixtures"
    compileSdk = 34
    defaultConfig { minSdk = 26 }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.mockk)
}
