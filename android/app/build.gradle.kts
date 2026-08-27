plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.wnoicew.expensetracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wnoicew.expensetracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    applicationVariants.all {
        val variant = this
        val vName = variant.versionName ?: "1.0.0"
        val capitalizedVariantName = variant.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val taskName = "copyApk$capitalizedVariantName"

        val copyTask = tasks.register(taskName) {
            doLast {
                variant.outputs.all {
                    val output = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl
                    val apkFile = output?.outputFile
                    if (apkFile != null && apkFile.exists()) {
                        val rootDir = rootProject.projectDir.parentFile
                        val apksFolder = File(rootDir, "apks")
                        if (!apksFolder.exists()) {
                            apksFolder.mkdirs()
                        }

                        // 1. Current version outside in the main folder
                        val rootApk = File(rootDir, "ExpenseTracker.apk")
                        apkFile.copyTo(rootApk, overwrite = true)

                        // 2. Keep all versions in the apks folder
                        val versionedApk = File(apksFolder, "ExpenseTracker-v${vName}.apk")
                        apkFile.copyTo(versionedApk, overwrite = true)

                        println("--------------------------------------------------")
                        println("APK Distribution updated successfully:")
                        println("  [Main Folder] Current APK: ${rootApk.name}")
                        println("  [APKs Folder] Archive APK: ${versionedApk.name}")
                        println("--------------------------------------------------")
                    }
                }
            }
        }

        variant.assembleProvider.configure {
            finalizedBy(copyTask)
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation("org.json:json:20231013")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
