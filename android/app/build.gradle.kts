plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "pl.fuelmanagement.tracker"
    compileSdk = 34

    defaultConfig {
        applicationId = "pl.fuelmanagement.tracker"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        ndk {
            // Ogranicza natywne biblioteki (CameraX/Tesseract4Android) do arm64-v8a w celu
            // zmniejszenia rozmiaru debug APK -- pokrywa niemal wszystkie współczesne telefony.
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    sourceSets["main"].kotlin.srcDirs("src/main/kotlin")
    sourceSets["test"].kotlin.srcDirs("src/test/kotlin")
    sourceSets["androidTest"].kotlin.srcDirs("src/androidTest/kotlin")
}

dependencies {
    // UI: Jetpack Compose (research.md -> "Interfejs użytkownika")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")

    // Przechwytywanie zdjęcia paragonu (research.md -> "Przechwytywanie zdjęcia paragonu")
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    // OCR w pełni on-device (research.md -> "Rozpoznawanie liczby litrów z paragonu (OCR)", FR-002).
    // Tesseract4Android zamiast ML Kit Text Recognition -- realne testy pokazały, że ML Kit
    // niekontrolowanie segmentuje dwukolumnowe paragony na osobne bloki, gubiąc dopasowanie
    // etykiety do wartości; Tesseract pozwala jawnie wymusić PSM_SINGLE_BLOCK.
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Lokalne przechowywanie danych (research.md -> "Przechowywanie danych")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Okresowe czyszczenie zdjęć paragonów starszych niż 12 mies. (research.md -> "Czyszczenie zdjęć paragonów", FR-015)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
