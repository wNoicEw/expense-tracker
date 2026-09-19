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
        versionCode = 18
        versionName = "1.6.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
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
        // Only the debug variant is ever built/shipped by this project's release workflow
        // (see AGENTS.md); registering this for release too would race both variants to
        // overwrite the same ExpenseTracker.apk / versioned archive file.
        if (variant.buildType.name != "debug") return@all

        val vName = variant.versionName ?: "unknown"
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

                        // 2. Keep versioned APK in apks folder
                        val versionedApk = File(apksFolder, "ExpenseTracker-v${vName}.apk")
                        apkFile.copyTo(versionedApk, overwrite = true)

                        // 3. Keep only the last 5 versions for emergency fallback
                        val versionRegex = Regex("""ExpenseTracker-v(\d+)\.(\d+)\.(\d+)\.apk""")
                        val archivedApks = apksFolder.listFiles { f -> f.isFile && versionRegex.matches(f.name) }
                        if (archivedApks != null && archivedApks.size > 5) {
                            archivedApks.sortedWith(Comparator { a, b ->
                                val mA = versionRegex.matchEntire(a.name)
                                val mB = versionRegex.matchEntire(b.name)
                                if (mA != null && mB != null) {
                                    val (majA, minA, patA) = mA.destructured
                                    val (majB, minB, patB) = mB.destructured
                                    val cMaj = majA.toInt().compareTo(majB.toInt())
                                    if (cMaj != 0) return@Comparator cMaj
                                    val cMin = minA.toInt().compareTo(minB.toInt())
                                    if (cMin != 0) return@Comparator cMin
                                    patA.toInt().compareTo(patB.toInt())
                                } else {
                                    a.name.compareTo(b.name)
                                }
                            }).take(archivedApks.size - 5).forEach { oldApk ->
                                if (oldApk.delete()) {
                                    println("  [APKs Folder] Pruned old fallback version: ${oldApk.name}")
                                } else {
                                    println("  [APKs Folder] WARNING: could not delete ${oldApk.name} (file locked or in use)")
                                }
                            }
                        }

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

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
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

    // PDF Parser
    implementation(libs.pdfbox.android)

    testImplementation(libs.junit)
    testImplementation("org.json:json:20231013")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
