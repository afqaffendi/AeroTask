plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.gms.google-services") // Connects your app to Firebase
}

android {
    namespace = "com.example.classmatetaskshare"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.classmatetaskshare"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // FIX: Clears the Room Schema Warning
        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }
    }

    buildFeatures { viewBinding = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Standard UI

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.navigation:navigation-fragment-ktx:2.8.5")
    implementation("androidx.navigation:navigation-ui-ktx:2.8.5")
    implementation("androidx.cardview:cardview:1.0.0")
    // Firebase Authentication
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation(libs.androidx.cardview)

    //Push Notification
    implementation ("androidx.work:work-runtime-ktx:2.9.0")
    // FEATURE 1: Offline (Room)
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    kapt("androidx.room:room-compiler:$room_version")

    // FEATURE 2: Cloud Firestore (THIS FIXES THE HANG)
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-firestore-ktx")

    // FIX: Coroutines (Fixes red 'Dispatchers' and 'launch')
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // FIX: JUnit (Fixes the 15 build errors)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}