plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "dev.kaankaya.instagramtracker"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.kaankaya.instagramtracker"
        minSdk = 24
        targetSdk = 36
        versionCode = 43
        versionName = "0.1.42"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { buildConfig = true }
    bundle { language { enableSplit = false } }
    buildTypes { getByName("debug") { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    lint { abortOnError = true }
}
dependencies {
    // 5.5+ requires compileSdk 37; keep this release on the project's SDK 36 toolchain.
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("androidx.webkit:webkit:1.14.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
    testImplementation("com.squareup.okhttp3:mockwebserver3:5.4.0")
    testImplementation("com.squareup.okhttp3:okhttp-tls:5.4.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:core:1.7.0")
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencyLocking { lockAllConfigurations() }
