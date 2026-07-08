plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")

}

val tirtirApiBaseUrl = providers.gradleProperty("TIRTIR_API_BASE_URL")
    .orElse("https://tirtir-project.onrender.com/")
    .map { url -> if (url.endsWith("/")) url else "$url/" }
    .get()

android {
    namespace = "com.example.tirtir_mcommerce"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.tirtir_mcommerce"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("release-key.keystore")
            storePassword = "password"
            keyAlias = "tirtir_alias"
            keyPassword = "password"
        }
    }

    buildTypes {
        debug {
            // Dùng backend Local cho lúc chạy thử nghiệm (Emulator)
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:5000/\"")
        }
        release {
            // Dùng backend Render cho bản phát hành thật (APK release)
            buildConfigField("String", "API_BASE_URL", "\"https://tirtir-project.onrender.com/\"")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // Retrofit + Gson
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation(libs.firebase.database)
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // Firebase BOM
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-analytics")

    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // ksoap2 - SOAP client cho Viettel Post shipping API
    implementation("com.google.code.ksoap2-android:ksoap2-android:3.6.4")

    // MPAndroidChart
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Lottie
    implementation("com.airbnb.android:lottie:6.4.0")
    implementation("com.facebook.shimmer:shimmer:0.5.0")

    // Phase 3 frontend widgets
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("com.google.android.flexbox:flexbox:3.0.0")
    implementation("com.google.android.gms:play-services-ads:23.6.0")
    implementation("de.hdodenhof:circleimageview:3.1.0")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("androidx.camera:camera-mlkit-vision:1.4.2")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("androidx.palette:palette:1.0.0")

    // AR Try-On (ARCore & Sceneform)
    implementation("com.google.ar:core:1.40.0")
    implementation("com.gorisse.thomas.sceneform:sceneform:1.23.0")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
