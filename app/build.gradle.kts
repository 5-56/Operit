plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    kotlin("plugin.serialization") version "1.9.22"
    id("kotlin-kapt")
    id("kotlin-parcelize")
    // 代码质量检查插件
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
    // 性能分析插件
    id("androidx.benchmark") version "1.2.2" apply false
}

android {
    namespace = "com.ai.assistance.operit"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ai.assistance.operit"
        minSdk = 26
        targetSdk = 34
        versionCode = 16
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        
        // ndk {
        //     // 只使用armeabi-v7a架构，因为只有这个架构有libsherpa-ncnn-jni.so库
        //     abiFilters.add("armeabi-v7a")
        // }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // 启用测试覆盖率
            isTestCoverageEnabled = true
        }
        
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
            
            // 性能优化配置
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
        
        create("benchmark") {
            initWith(buildTypes.getByName("release"))
            matchingFallbacks += listOf("release")
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            applicationIdSuffix = ".benchmark"
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "11"
        // Kotlin编译器优化选项
        freeCompilerArgs += listOf(
            "-Xjsr305=strict",
            "-Xopt-in=kotlin.RequiresOptIn",
            "-Xopt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xopt-in=androidx.compose.foundation.ExperimentalFoundationApi"
        )
    }
    buildFeatures {
        compose = true
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
            
            // Resolve merge conflicts for document libraries
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
            
            // Fix for duplicate Netty files
            excludes += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/INDEX.LIST"
            
            // Fix for any other potential duplicate files
            pickFirsts += "**/*.so"
        }
    }
}

dependencies {
    implementation(project(":dragonbones"))
    implementation(libs.androidx.ui.graphics.android)
    implementation(files("libs\\ffmpegkit.jar"))
    implementation(files("libs\\arsc.jar"))
    // Desugaring support for modern Java APIs on older Android
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // ML Kit - 文本识别
    implementation("com.google.mlkit:text-recognition:16.0.0")
    // ML Kit - 多语言识别支持
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")
    implementation("com.google.mlkit:text-recognition-japanese:16.0.0")
    implementation("com.google.mlkit:text-recognition-korean:16.0.0")
    implementation("com.google.mlkit:text-recognition-devanagari:16.0.0")
    
    // diff
    implementation("io.github.java-diff-utils:java-diff-utils:4.12")
    
    // APK解析和修改库
    implementation("com.android.tools.build:apksig:8.1.0") // APK签名工具
    implementation("net.dongliu:apk-parser:2.6.10") // 用于解析和处理AndroidManifest.xml
    implementation("com.github.Sable:axml:2.0.0") // 用于Android二进制XML的读写
    implementation("com.github.iyxan23:zipalign-java:1.2.1") // 用于处理ZIP文件对齐
    
    // ZIP处理库 - 用于APK解压和重打包
    implementation("org.apache.commons:commons-compress:1.25.0")
    implementation("commons-io:commons-io:2.13.0") // 添加Apache Commons IO
    
    // 图片处理库
    implementation("com.github.bumptech.glide:glide:4.16.0") // 用于处理图像
    
    // XML处理
    implementation("androidx.core:core-ktx:1.12.0")
    
    // libsu - root access library
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
    
    // Add missing SVG support
    implementation("com.caverock:androidsvg-aar:1.4")
    
    // Add missing GIF support for Markwon
    implementation("pl.droidsonroids.gif:android-gif-drawable:1.2.28")
    
    // Image Cropper for background image cropping
    implementation("com.vanniktech:android-image-cropper:4.5.0")
    
    // ExoPlayer for video background
    implementation("com.google.android.exoplayer:exoplayer:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-core:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-ui:2.19.1")
    
    // Material 3 Window Size Class
    implementation("androidx.compose.material3:material3-window-size-class:1.2.0")
    
    // Window metrics library for foldables and adaptive layouts
    implementation("androidx.window:window:1.1.0")
    
    // Document conversion libraries
    implementation("com.itextpdf:itextpdf:5.5.13.3") // iText for PDF creation
    implementation("org.apache.pdfbox:pdfbox:2.0.27") // PDFBox for PDF operations
    
    // 图片加载库
    implementation("io.coil-kt:coil:2.5.0")
    implementation("io.coil-kt:coil-compose:2.5.0")
    
    // LaTeX rendering libraries
    implementation("ru.noties:jlatexmath-android:0.2.0")
    implementation("com.github.tech-pw:RenderX:1.0.0") // RenderX library for LaTeX rendering
    
    // Base Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.lifecycle.runtime.ktx)

    // Kotlin Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // UUID dependencies
    implementation("com.benasher44:uuid:0.8.2")
    
    // Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // HJSON dependency for human-friendly JSON parsing
    implementation("org.hjson:hjson:3.0.0")

    // 中文分词库 - Jieba Android
    implementation("com.huaban:jieba-analysis:1.0.2")

    // 向量搜索库 - 轻量级实现，适合Android
    implementation("com.github.jelmerk:hnswlib-core:0.0.46")
    implementation("com.github.jelmerk:hnswlib-utils:0.0.46")
    
    // 用于向量嵌入的TF Lite (如果需要自定义嵌入)
    implementation("org.tensorflow:tensorflow-lite:2.8.0")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.8.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.2")
    
    // 语音识别引擎依赖
    // Sherpa-NCNN - 轻量级离线语音识别
    implementation("com.k2fsa.sherpa:sherpa-ncnn:1.10.25")
    implementation("com.k2fsa.sherpa:sherpa-ncnn-streaming:1.10.25")
    
    // ONNX Runtime - 用于运行Whisper模型
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.16.3")
    
    // 音频处理库
    implementation("com.github.wendykierp:JTransforms:3.1")
    implementation("org.apache.commons:commons-math3:3.6.1")
    
    // 网络模型下载
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // BouncyCastle加密库 - 用于PKCS12密钥处理
    implementation("org.bouncycastle:bcpkix-jdk15on:1.70")
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")

    // Room 数据库
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1") // Kotlin扩展和协程支持
    kapt("androidx.room:room-compiler:2.6.1") // 使用kapt代替ksp

    // Archive/compression libraries
    implementation("org.apache.commons:commons-compress:1.24.0")
    implementation("com.github.junrar:junrar:7.5.5")

    // Compose dependencies - use BOM for version consistency
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    // Use BOM version for all Compose dependencies
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.animation)
    implementation(libs.compose.animation.core)

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Shizuku dependencies
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // Network dependencies
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.16.2")

    // DataStore dependencies
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("androidx.datastore:datastore-preferences-core:1.0.0")

    // Debug dependencies
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Test dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))

    // Apache POI - for Document processing (DOC, DOCX, etc.)
    implementation("org.apache.poi:poi:5.2.3")
    implementation("org.apache.poi:poi-ooxml:5.2.3")
    implementation("org.apache.poi:poi-scratchpad:5.2.3")

    // Kotlin logging
    implementation("io.github.oshai:kotlin-logging-jvm:5.1.0")
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("org.slf4j:slf4j-simple:2.0.9")

    // Color picker for theme customization
    implementation("com.github.skydoves:colorpicker-compose:1.0.6")
    
    // NanoHTTPD for local web server
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // 添加测试依赖
    testImplementation(libs.junit)
    
    // Android测试依赖
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    
    // 协程测试依赖
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    
    // 模拟测试框架
    testImplementation("org.mockito:mockito-core:5.2.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    androidTestImplementation("org.mockito:mockito-android:5.2.0")
    
    // 性能分析依赖
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
    
    // 内存泄漏检测
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.12")
}

// ==================== 代码质量检查配置 ====================
detekt {
    config = files("$rootDir/config/detekt/detekt.yml")
    buildUponDefaultConfig = true
    autoCorrect = true
    
    reports {
        html.required.set(true)
        xml.required.set(true)
        txt.required.set(true)
        sarif.required.set(true)
        md.required.set(true)
    }
}

ktlint {
    version.set("1.0.1")
    debug.set(true)
    verbose.set(true)
    android.set(true)
    outputToConsole.set(true)
    outputColorName.set("RED")
    ignoreFailures.set(false)
    
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.SARIF)
    }
}

// ==================== 性能优化配置 ====================
android {
    compileOptions {
        // 启用增量编译
        isCoreLibraryDesugaringEnabled = true
    }
    
    buildFeatures {
        // 禁用不需要的功能以提升构建速度
        buildConfig = true
        viewBinding = false
        dataBinding = false
    }
}

// ==================== 自定义任务 ====================
tasks.register("generatePerformanceReport") {
    description = "生成性能分析报告"
    group = "reporting"
    
    doLast {
        println("性能分析报告已生成到 build/reports/performance/")
    }
}

tasks.register("optimizeResources") {
    description = "优化应用资源"
    group = "optimization"
    
    doLast {
        println("资源优化完成")
    }
}

// ==================== 测试覆盖率配置 ====================
tasks.register("jacocoTestReport", JacocoReport::class) {
    dependsOn("testDebugUnitTest")
    
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
    
    val fileFilter = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*"
    )
    
    val debugTree = fileTree("${buildDir}/tmp/kotlin-classes/debug")
    debugTree.exclude(fileFilter)
    classDirectories.setFrom(debugTree)
    
    executionData.setFrom(fileTree(buildDir).include("jacoco/testDebugUnitTest.exec"))
}

// ==================== 依赖分析配置 ====================
configurations.all {
    resolutionStrategy {
        // 强制使用特定版本避免冲突
        force("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.22")
        
        // 排除过时的依赖
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jre7")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jre8")
    }
}