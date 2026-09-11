import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.emotion.pet"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.emotion.pet"
        minSdk = 21
        targetSdk = 35
        versionCode = 4
        versionName = "1.3"
    }

    signingConfigs {
        getByName("debug") {
            // Фиксиран debug ключ, чекнат в repo-то (keystore/debug.keystore). Без него всеки
            // CI runner генерира различен debug ключ и APK-та от различни build-ове взаимно се
            // отхвърлят при инсталация ("Приложението не е инсталирано" — конфликт в подписа).
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            // Фиксиран release ключ (keystore/release.p12, PKCS12). Release APK-то не е
            // debuggable — някои устройства/политики отказват debuggable приложения.
            storeFile = rootProject.file("keystore/release.p12")
            storeType = "PKCS12"
            storePassword = "emotionpet"
            keyAlias = "emotionpet"
            keyPassword = "emotionpet"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
            // Без applicationIdSuffix: приложението винаги е "com.emotion.pet".
            // Старите sideload-нати APK-та бяха "com.emotion.pet.debug" и блокираха
            // инсталацията с „Приложението не е инсталирано“ (конфликт на подписи).
            // С нов пакет инсталацията минава като чисто ново приложение.
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.testLogging {
                events("failed", "skipped")
                showStandardStreams = true
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.test.ext:junit-ktx:1.2.1")
}
