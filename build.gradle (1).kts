plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.knight.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.knight.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "KNIGHT_URL", "\"${(project.findProperty("knightUrl") as String?) ?: "https://YOUR-SITE.workers.dev"}\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes { release { isMinifyEnabled = false } }
}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("net.java.dev.jna:jna:5.13.0@aar")        // needed by the offline speech engine
    implementation("com.alphacephei:vosk-android:0.3.47")    // offline "Hey Knight" listening
}
