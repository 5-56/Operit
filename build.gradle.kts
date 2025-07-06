// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("com.android.library") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22" apply false
    // 代码质量检查插件
    id("io.gitlab.arturbosch.detekt") version "1.23.6" apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0" apply false
}

// ==================== 全局配置优化 ====================
allprojects {
    // 统一仓库配置
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
    }
}

subprojects {
    // Kotlin编译器统一配置
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "11"
            freeCompilerArgs += listOf(
                "-Xjsr305=strict",
                "-Xopt-in=kotlin.RequiresOptIn",
                "-Xopt-in=androidx.compose.material3.ExperimentalMaterial3Api",
                "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
            )
        }
    }
    
    // Java编译器统一配置
    tasks.withType<JavaCompile> {
        sourceCompatibility = JavaVersion.VERSION_11.toString()
        targetCompatibility = JavaVersion.VERSION_11.toString()
    }
}

// ==================== 清理任务优化 ====================
tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
    // 清理所有子项目的构建目录
    subprojects.forEach { project ->
        delete(project.buildDir)
    }
}

// ==================== 代码质量检查任务 ====================
tasks.register("codeQualityCheck") {
    description = "运行所有代码质量检查"
    group = "verification"
    
    dependsOn(
        "detekt",
        "ktlintCheck"
    )
}

// ==================== 性能分析任务 ====================
tasks.register("analyzePerformance") {
    description = "分析构建性能"
    group = "help"
    
    doLast {
        println("构建性能分析完成，请查看 build/reports/profile/ 目录")
    }
}