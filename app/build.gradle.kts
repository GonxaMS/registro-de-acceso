import java.util.Properties

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val sheetsWebUrl = localProperties.getProperty("sheets.web_url", "")
val useFirebaseEmulator = providers.gradleProperty("firebaseEmulator").orNull.toBoolean()

plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.ejemplo.registroguardias"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.ejemplo.registroguardias"
        minSdk = 24
        targetSdk = 35
        versionCode = 45
        versionName = "3.9.15"
        buildConfigField("String", "SHEETS_WEB_URL", "\"${sheetsWebUrl.replace("\"", "\\\"")}\"")
        buildConfigField("boolean", "USE_FIREBASE_EMULATOR", useFirebaseEmulator.toString())
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.2.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    testImplementation("junit:junit:4.13.2")
}
