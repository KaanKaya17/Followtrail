plugins { id("com.android.application") }
android {
    namespace = "dev.kaankaya.instagramtracker"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.kaankaya.instagramtracker"
        minSdk = 24
        targetSdk = 36
        versionCode = 104
        versionName = "0.1.103"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { buildConfig = true }
    bundle { language { enableSplit = false } }
    buildTypes { getByName("debug") { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" } }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        abortOnError = true
    }
}
dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
    implementation("org.jsoup:jsoup:1.24.1")
    implementation("com.squareup.okhttp3:okhttp:5.5.1")
    implementation("androidx.webkit:webkit:1.14.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
    testImplementation("com.squareup.okhttp3:mockwebserver3:5.5.1")
    testImplementation("com.squareup.okhttp3:okhttp-tls:5.5.1")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:core:1.7.0")
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencyLocking { lockAllConfigurations() }
