plugins {
    id("com.android.application")
}

android {
    namespace = "com.lenerd.lyricsnative"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lenerd.lyricsnative"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {

    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.14.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    implementation("com.google.android.flexbox:flexbox:3.0.0")
    compileOnly(files("$rootDir/lib/spotifyplus-sdk.aar"))
}