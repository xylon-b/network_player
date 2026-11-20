import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.toy.networkplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.toy.networkplayer"
        minSdk = 26
        targetSdk = 36
        // 플레이스토어에 새 버전을 올릴 때마다 versionCode를 1씩 올린다.
        versionCode = 1
        versionName = "1.0.0"
    }

    // 업로드 키(플레이스토어용). 비밀번호는 저장소에 넣지 않는다.
    // 1) 로컬: 프로젝트 루트의 keystore.properties (git에 안 올라감)
    // 2) CI: 환경 변수 NP_KEYSTORE_FILE / NP_KEYSTORE_PASSWORD / NP_KEY_ALIAS / NP_KEY_PASSWORD
    val keyProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    fun signingValue(prop: String, env: String): String? =
        keyProps.getProperty(prop) ?: System.getenv(env)?.takeIf { it.isNotBlank() }
    val uploadStoreFile = signingValue("storeFile", "NP_KEYSTORE_FILE")
    val hasUploadKey = uploadStoreFile != null && rootProject.file(uploadStoreFile).exists()

    signingConfigs {
        // 고정된 디버그 키로 서명해서, 새 APK를 설치해도 기존 앱 위에 덮어쓰기가 되도록 한다.
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (hasUploadKey) {
            create("upload") {
                storeFile = rootProject.file(uploadStoreFile!!)
                storePassword = signingValue("storePassword", "NP_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "NP_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "NP_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8(코드 축소)은 smbj/BouncyCastle/libVLC 리플렉션 때문에 일단 끈다.
            isMinifyEnabled = false
            // 업로드 키가 없으면 서명 없는 release가 만들어진다(플레이스토어에 올릴 수 없음).
            signingConfig = if (hasUploadKey) signingConfigs.getByName("upload") else null
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
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/versions/**",
                "META-INF/*.kotlin_module",
            )
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // SMB2/3 클라이언트 (윈도우 공유폴더)
    implementation("com.hierynomus:smbj:0.13.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.79")
    // PC의 공유 폴더 목록 조회 (SRVSVC)
    implementation("com.rapid7.client:dcerpc:0.12.13") {
        exclude(group = "com.hierynomus", module = "smbj")
    }

    // 동영상 엔진 (LGPL 2.1+). MKV/AC3/DTS 등 코덱이 넓다.
    // 3.6 이상: 네이티브 라이브러리가 16KB 페이지 정렬이라 플레이스토어 요구사항을 만족한다.
    implementation("org.videolan.android:libvlc-all:3.6.5")
}
