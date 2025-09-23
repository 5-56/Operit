plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.parcelize)
    id("io.objectbox")
}

android {
    namespace = "com.xihe.assistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.xihe.assistant"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        
        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        isCoreLibraryDesugaringEnabled = true
    }
    
    kotlinOptions {
        jvmTarget = "1.8"
    }
    
    buildFeatures {
        compose = true
        aidl = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE-EPL-1.0.txt"
            excludes += "LICENSE-EPL-1.0.txt"
            excludes += "/META-INF/LICENSE-EDL-1.0.txt"
            excludes += "LICENSE-EDL-1.0.txt"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/license.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/notice.txt"
            excludes += "/META-INF/ASL2.0"
            excludes += "/META-INF/*.SF"
            excludes += "/META-INF/*.DSA"
            excludes += "/META-INF/*.RSA"
            excludes += "/META-INF/*.kotlin_module"
            excludes += "META-INF/versions/9/module-info.class"
            excludes += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/INDEX.LIST"
            pickFirsts += "**/*.so"
        }
    }
}

dependencies {
    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.lifecycle.runtime.ktx)

    // Desugaring support
    coreLibraryDesugaring(libs.desugar.jdk)

    // ML Kit - 文本识别
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.chinese)
    implementation(libs.mlkit.text.japanese)
    implementation(libs.mlkit.text.korean)
    implementation(libs.mlkit.text.devanagari)
    
    // 图片处理库
    implementation(libs.glide)
    implementation(libs.coil)
    implementation(libs.coil.compose)
    
    // XML处理
    implementation(libs.androidx.core.ktx)
    
    // libsu - root access library
    implementation(libs.libsu)
    
    // SVG support
    implementation(libs.androidsvg)
    
    // GIF support
    implementation(libs.android.gif)
    
    // Image Cropper
    implementation(libs.image.cropper)
    
    // ExoPlayer for video
    implementation(libs.exoplayer)
    implementation(libs.exoplayer.core)
    implementation(libs.exoplayer.ui)
    
    // Material 3 Window Size Class
    implementation(libs.material3.window)
    
    // Window metrics library
    implementation(libs.window)
    
    // Document conversion libraries
    implementation(libs.itextg)
    implementation(libs.pdfbox)
    implementation(libs.zip4j)
    
    // LaTeX rendering libraries
    implementation(libs.jlatexmath)
    implementation(libs.renderx)
    
    // Kotlin Serialization
    implementation(libs.kotlinx.serialization)
    
    // UUID dependencies
    implementation(libs.uuid)
    
    // JSON parsing
    implementation(libs.gson)
    implementation(libs.hjson)

    // 中文分词库
    implementation(libs.jieba)

    // 向量搜索库
    implementation(libs.hnswlib.core)
    implementation(libs.hnswlib.utils)
    
    // TensorFlow Lite
    implementation(libs.tensorflow.lite)
    implementation(libs.mediapipe.tasks.text)

    // Room 数据库
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    // ObjectBox
    implementation(libs.objectbox.kotlin)
    kapt(libs.objectbox.processor)

    // Archive/compression libraries
    implementation(libs.commons.compress.v2)
    implementation(libs.junrar)

    // Compose dependencies
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.animation)
    implementation(libs.compose.animation.core)

    // Navigation Compose
    implementation(libs.navigation.compose)

    // Shizuku dependencies
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // Network dependencies
    implementation(libs.okhttp)
    implementation(libs.jsoup)

    // DataStore dependencies
    implementation(libs.datastore.preferences)
    implementation(libs.datastore.preferences.core)

    // Apache POI - Document processing
    implementation(libs.poi)
    implementation(libs.poi.ooxml)
    implementation(libs.poi.scratchpad)

    // Kotlin logging
    implementation(libs.kotlin.logging)
    implementation(libs.slf4j.api)
    implementation(libs.slf4j.simple)

    // Color picker
    implementation(libs.colorpicker)
    
    // NanoHTTPD for local web server
    implementation(libs.nanohttpd)

    // Swipe to reveal actions
    implementation(libs.swipe)

    // Coroutine
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Debug dependencies
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Test dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.rules)
    
    // Coroutine testing
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.coroutines.test)
    
    // Mock testing
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    androidTestImplementation(libs.mockito.android)
    implementation(libs.reorderable)
}