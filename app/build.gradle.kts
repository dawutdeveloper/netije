import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localPropsFile.inputStream().use { localProps.load(it) }
}

android {
    namespace = "com.example.netije"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.netije"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }

        kapt {
            arguments {
                arg("room.schemaLocation", "$projectDir/schemas")
            }
        }

        buildConfigField(
            "String",
            "GOOGLE_SERVER_CLIENT_ID",
            "\"" + (localProps.getProperty("GOOGLE_SERVER_CLIENT_ID")
                ?: System.getenv("NETIJE_GOOGLE_CLIENT_ID")
                ?: "your_google_oauth_web_client_id_here") + "\""
        )
        buildConfigField(
            "String",
            "BASE_URL",
            "\"" + (localProps.getProperty("BASE_URL")
                ?: System.getenv("NETIJE_BASE_URL")
                ?: "http://10.0.2.2:3000/api/") + "\""
        )
        buildConfigField(
            "boolean",
            "ALLOW_HTTP_CLEARTEXT",
            (localProps.getProperty("ALLOW_HTTP_CLEARTEXT")
                ?: System.getenv("NETIJE_ALLOW_HTTP_CLEARTEXT")
                ?: "false")
        )

    signingConfigs {
        create("release") {
            val storeFileProp = localProps.getProperty("SIGNING_STORE_FILE")
            val storePassProp = localProps.getProperty("SIGNING_STORE_PASSWORD")
            val keyAliasProp = localProps.getProperty("SIGNING_KEY_ALIAS")
            val keyPassProp = localProps.getProperty("SIGNING_KEY_PASSWORD")
            if (storeFileProp != null && storePassProp != null && keyAliasProp != null && keyPassProp != null) {
                storeFile = file(storeFileProp)
                storePassword = storePassProp
                keyAlias = keyAliasProp
                keyPassword = keyPassProp
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            val userRules = file("proguard-rules.pro")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                *(if (userRules.exists()) arrayOf(userRules) else emptyArray())
            )
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
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
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.constraintlayout.compose)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.gson)

    implementation(libs.coil.compose)

    implementation(libs.google.android.material)

    implementation(libs.play.services.auth)
    implementation(libs.google.id)

    implementation(libs.work.runtime.ktx)
    implementation(libs.androidx.preference.ktx)
}

kapt {
    correctErrorTypes = true
}

hilt {
    enableAggregatingTask = true
}}
