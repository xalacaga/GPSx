import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val gpsxSourceRevision = providers.gradleProperty("gpsxSourceRevision")
val sourceRevision = gpsxSourceRevision.orNull ?: "development"
val gpsxAsan = providers.gradleProperty("gpsxAsan").map(String::toBoolean).orElse(false)

fun gitOutput(vararg arguments: String): String {
    return providers.exec {
        commandLine("git", *arguments)
    }.standardOutput.asText.get().trim()
}

gradle.taskGraph.whenReady {
    val buildsRelease = allTasks.any { task ->
        task.project == project && task.name.contains("release", ignoreCase = true)
    }
    if (buildsRelease && !sourceRevision.matches(Regex("[0-9a-f]{40}"))) {
        throw GradleException(
            "Release builds require -PgpsxSourceRevision=<40-character lowercase Git commit SHA>.",
        )
    }
    if (buildsRelease) {
        val headRevision = gitOutput("rev-parse", "HEAD")
        if (sourceRevision != headRevision) {
            throw GradleException(
                "Release source revision $sourceRevision does not match checked-out HEAD $headRevision.",
            )
        }
        if (gitOutput("status", "--porcelain", "--untracked-files=all").isNotEmpty()) {
            throw GradleException("Release builds require a clean Git worktree.")
        }
    }
}

android {
    namespace = "com.gpsx.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gpsx.tv"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "SOURCE_REPOSITORY_URL", "\"https://github.com/xalacaga/GPSx\"")
        buildConfigField("String", "SOURCE_REVISION", "\"$sourceRevision\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += if (gpsxAsan.get()) listOf("x86_64") else listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }

        externalNativeBuild {
            ndkBuild {
                arguments += "NDK_APPLICATION_MK:=src/main/cpp/Application.mk"
                if (gpsxAsan.get()) {
                    arguments += "GPSX_ASAN:=1"
                }
            }
        }
    }

    ndkVersion = "28.2.13676358"

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/cpp/Android.mk")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            buildConfigField(
                "String",
                "SOURCE_REPOSITORY_URL",
                "\"https://github.com/xalacaga/GPSx/tree/$sourceRevision\"",
            )
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = gpsxAsan.get()
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

if (!gpsxAsan.get()) {
    android.sourceSets.getByName("main").resources.directories.clear()
}

if (gpsxAsan.get()) {
    val localProperties = Properties().apply {
        val propertiesFile = rootProject.file("local.properties")
        if (propertiesFile.isFile) {
            propertiesFile.inputStream().use(::load)
        }
    }
    val sdkPath =
        localProperties.getProperty("sdk.dir")
            ?: providers.environmentVariable("ANDROID_SDK_ROOT").orNull
            ?: providers.environmentVariable("ANDROID_HOME").orNull
    val sdkDirectory = file(checkNotNull(sdkPath) { "ASan requires sdk.dir, ANDROID_SDK_ROOT or ANDROID_HOME." })
    val asanJniLibs = layout.buildDirectory.dir("generated/asan-jni-libs")
    val prebuiltRoot = sdkDirectory.resolve("ndk/${android.ndkVersion}/toolchains/llvm/prebuilt")
    val asanRuntimes =
        fileTree(prebuiltRoot) {
            include("*/lib/clang/*/lib/linux/libclang_rt.asan-x86_64-android.so")
        }.files
    val asanRuntime =
        checkNotNull(asanRuntimes.singleOrNull()) {
            "Expected exactly one x86_64 Android ASan runtime under $prebuiltRoot; found ${asanRuntimes.size}."
        }
    val prepareAsanRuntime = tasks.register<Copy>("prepareAsanRuntime") {
        from(asanRuntime)
        into(asanJniLibs.map { it.dir("x86_64") })
    }
    android.sourceSets.getByName("debug").jniLibs.directories.add(asanJniLibs.get().asFile.absolutePath)
    tasks.matching { it.name == "mergeDebugJniLibFolders" }.configureEach {
        dependsOn(prepareAsanRuntime)
    }
}

dependencies {
    // Pinned below the API 37-only Compose line so the project remains buildable
    // with the current stable Android 16 SDK while still running on Android 11.
    val composeBom = platform("androidx.compose:compose-bom:2025.08.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.tv:tv-material:1.1.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

