plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    id("com.google.devtools.ksp")
//    id("com.chaquo.python")
    id("org.jetbrains.kotlin.plugin.allopen") version "1.8.10"
}

android {
    namespace = "com.ml.shubham0204.docqa"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ml.shubham0204.docqa"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        ndk {
            // Add NDK properties if wanted, e.g.
//            abiFilters += listOf("arm64-v8a")
        }
    }
    signingConfigs {
        create("release") {
            storeFile = file("../keystore.jks")
            storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("RELEASE_KEYSTORE_ALIAS")
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
        }
    }
    buildTypes {
        // Add the field 'geminiKey' in the build config
        // See https://stackoverflow.com/a/60474096/13546426
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
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
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "META-INF/DEPENDENCIES"
        }
    }
    applicationVariants.configureEach {
        kotlin.sourceSets {
            getByName(name) {
                kotlin.srcDir("build/generated/ksp/$name/kotlin")
            }
        }
    }
}

ksp {
    arg("KOIN_CONFIG_CHECK", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.material3.icons.extended)
    implementation(libs.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // LLama
    implementation(project(":llama"))

    // Apache POI
    implementation(libs.apache.poi)
    implementation(libs.apache.poi.ooxml)

    // Sentence Embeddings
    // https://github.com/shubham0204/Sentence-Embeddings-Android
    implementation(libs.sentence.embeddings.android)

    // iTextPDF - for parsing PDFs
    implementation(libs.itextpdf)
    implementation(libs.androidx.ui.test.junit4.android)
    implementation(libs.androidx.junit.ktx)

    // ObjectBox - vector database
    debugImplementation(libs.objectbox.android.objectbrowser)
    releaseImplementation(libs.objectbox.android)

    // Gemini SDK - LLM
    implementation(libs.generativeai)

    // compose-markdown
    // https://github.com/jeziellago/compose-markdown
    implementation(libs.compose.markdown)

    // Koin dependency injection
    implementation(libs.koin.android)
    implementation(libs.koin.annotations)
    implementation(libs.koin.androidx.compose)
    ksp(libs.koin.ksp.compiler)

    // For secured/encrypted shared preferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // testing
    implementation("com.github.doyaaaaaken:kotlin-csv-jvm:0.15.0")

    testImplementation("junit:junit:4.13.2") // JUnit 4
    // atau jika pakai JUnit 5:
    // testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.2")
    // testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.2")

    testImplementation(kotlin("test"))
    // Mockito Core
    testImplementation("org.mockito:mockito-core:5.12.0")

    // Mockito + Kotlin (jika perlu)
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")

    // (Optional) Jika butuh Android context mocking
    androidTestImplementation("org.mockito:mockito-android:5.2.0")

    testImplementation("org.robolectric:robolectric:4.7.3")
    implementation("com.google.code.gson:gson:2.10.1")

    // xlsx
    implementation("org.apache.poi:poi-ooxml:5.2.3")

    // lucene
    implementation("org.apache.lucene:lucene-core:8.11.1")
    implementation("org.apache.lucene:lucene-analyzers-common:8.11.1")
    implementation("org.apache.lucene:lucene-queryparser:8.11.1")

    // wordnet
    implementation("net.sf.extjwnl:extjwnl:2.0.5")  // extJWNL library
    implementation("net.sf.extjwnl:extjwnl-data-wn31:1.2")  // WordNet data (version 3.1)
    implementation("org.jetbrains.kotlin:kotlin-stdlib")  // Kotlin standard library

    // Sastrawi
    implementation("com.andylibrian.jsastrawi:jsastrawi:0.1")

}

apply(plugin = "io.objectbox")
//
//chaquopy {
//    defaultConfig {
//        version = "3.11"
//        buildPython("C:/Users/ameli/AppData/Local/Programs/Python/Python311/python.exe")
//        pip {
//            // "-r"` followed by a requirements filename, relative to the
//            // project directory:
//            install("-r", "src/main/python/requirements.txt")
//        }
//    }
//}
//
//chaquopy {
//    sourceSets {
//        getByName("main") {
//            srcDir("src/main/python")
//        }
//    }
//}
