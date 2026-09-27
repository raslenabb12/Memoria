plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.legacy.kapt)
    id("kotlin-parcelize")
}

android {
    namespace = "com.youme.memoria"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.youme.memoria"
        minSdk = 28
        targetSdk = 36
        versionCode = 6
        versionName = "v1.4.1-Beta"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    androidResources {
        noCompress += "bin"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.fragment)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation ( "com.github.bumptech.glide:glide:4.12.0")
    annotationProcessor ( "com.github.bumptech.glide:compiler:4.12.0")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")



    implementation("org.tensorflow:tensorflow-lite:2.17.0")


    implementation("androidx.room:room-runtime:2.8.4")
    implementation ("androidx.room:room-ktx:2.8.4")
    kapt ("androidx.room:room-compiler:2.8.4")

    implementation("androidx.paging:paging-runtime-ktx:3.3.0")

    implementation("com.google.android.material:material:1.14.0")

    implementation("androidx.datastore:datastore-preferences:1.2.1")



}