import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.secrets)
    alias(libs.plugins.owasp.dependencycheck)
}

kotlin {
    androidTarget()
    
    jvm("desktop")
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)
                
                implementation(libs.androidx.lifecycle.runtime.compose)
                implementation(libs.androidx.lifecycle.viewmodel.compose)
                implementation(libs.androidx.navigation.compose)
                

                implementation(libs.kotlinx.coroutines.core)
                
                implementation(libs.moshi.kotlin)
                
                implementation(libs.coil.compose)
            }
        }
        
        val androidMain by getting {
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.lifecycle.runtime.ktx)
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.androidx.documentfile)
            }
        }
        
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.openpdf)
                implementation(libs.pdfbox)
            }
        }
    }
}

android {
    namespace = "com.example"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aistudio.projectpdf.qyrtxp"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

compose.desktop {
    application {
        mainClass = "com.example.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Seguimiento Obras"
            packageVersion = "1.0.3"
            
            windows {
                iconFile.set(project.file("src/desktopMain/resources/icon.ico"))
                shortcut = true
                menu = true
                // El upgradeUuid permite instalar nuevas versiones sobre las antiguas sin tener que desinstalar.
                // IMPORTANTE: Este UUID debe mantenerse igual para siempre en esta app.
                upgradeUuid = "f1e3c88a-9876-4d2a-b12e-43a1b82f0c7e"
            }
        }
    }
}

secrets {
    propertiesFileName = ".env"
    defaultPropertiesFileName = ".env.example"
}

dependencyCheck {
    analyzers {
        assemblyEnabled = false
        nodeEnabled = false
        nodeAuditEnabled = false
        retirejs {
            enabled = false
        }
    }
    nvd {
        // Obtén clave gratuita en https://nvd.nist.gov/developers/request-an-api-key
        // para evitar rate-limiting severo (5 req/30s sin clave vs 50/30s con clave)
        apiKey = System.getenv("NVD_API_KEY") ?: ""
    }
    // Actualiza la BD NVD al ejecutar sin -PnoAutoUpdate. Para CI sin red, pasa -PnoAutoUpdate.
    autoUpdate = !project.hasProperty("noAutoUpdate")
    formats = listOf("HTML", "JSON")
    outputDirectory = layout.buildDirectory.dir("reports/dependency-check").get().asFile.absolutePath
    // Falla el build solo si hay vulnerabilidades CRÍTICAS (CVSS >= 9.0)
    failBuildOnCVSS = 9.0f
    suppressionFile = "dependency-check-suppressions.xml"
}

dependencies {

    add("kspAndroid", libs.moshi.kotlin.codegen)
    add("kspDesktop", libs.moshi.kotlin.codegen)
}
