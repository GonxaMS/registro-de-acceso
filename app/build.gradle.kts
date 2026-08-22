import java.util.Properties

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val sheetsUrl = localProperties.getProperty("sheets.url", "")
val sheetsKey = localProperties.getProperty("sheets.key", "")

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
        applicationId = "com.ejemplo.registroguardias.llaves"
        minSdk = 23
        targetSdk = 35
        versionCode = 20
        versionName = "3.3.6-llaves"
        buildConfigField("String", "SHEETS_URL", "\"${sheetsUrl.replace("\"", "\\\"")}\"")
        buildConfigField("String", "SHEETS_KEY", "\"${sheetsKey.replace("\"", "\\\"")}\"")
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.2.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
}