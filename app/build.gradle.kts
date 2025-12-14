import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.open_autoglm_android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.open_autoglm_android"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // 读取签名配置（从 local.properties）
            val keystorePropertiesFile = rootProject.file("local.properties")
            if (keystorePropertiesFile.exists()) {
                val props = Properties()
                keystorePropertiesFile.reader().use {
                    props.load(it)
                }
                
                val keystorePath = props.getProperty("keystore.path")
                val keystorePassword = props.getProperty("keystore.password")
                val keyAliasName = props.getProperty("key.alias")
                val keyPassword = props.getProperty("key.password")
                
                if (!keystorePath.isNullOrBlank() && !keystorePassword.isNullOrBlank() 
                    && !keyAliasName.isNullOrBlank() && !keyPassword.isNullOrBlank()) {
                    storeFile = file(keystorePath)
                    storePassword = keystorePassword
                    keyAlias = keyAliasName
                    this.keyPassword = keyPassword
                }
            }
        }
    }
    
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 只有在签名配置存在时才使用
            if (signingConfigs.findByName("release")?.storeFile?.exists() == true) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    
    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    
    // Data Storage
    implementation(libs.androidx.datastore.preferences)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    
    // Image Loading
    implementation(libs.coil.compose)
    
    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}