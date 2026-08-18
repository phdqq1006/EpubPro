import java.util.Properties

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}
val defaultGeminiApiKey = localProperties.getProperty("GEMINI_API_KEY", "")
val defaultGeminiApiKeyLiteral = 34.toChar() + defaultGeminiApiKey + 34.toChar()

plugins {
    id("epubpro.android.library")
    id("epubpro.android.compose")
    id("epubpro.android.hilt")
}

android {
    namespace = "com.epubpro.feature.reader"

    defaultConfig {
        buildConfigField("String", "DEFAULT_GEMINI_API_KEY", defaultGeminiApiKeyLiteral)
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:storage"))
    implementation(project(":core:epub"))
    implementation(project(":core:reader-renderer"))
    implementation(project(":core:playback"))
    implementation(project(":core:ai"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.appcompat)

    testImplementation(libs.junit4)
}
