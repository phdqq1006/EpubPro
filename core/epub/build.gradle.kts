plugins {
    id("epubpro.android.library")
    id("epubpro.android.hilt")
}

android {
    namespace = "com.epubpro.core.epub"

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(project(":domain"))
    implementation(libs.kotlinx.coroutines.core)

    // Jsoup for EPUB HTML & OPF Parsing
    implementation(libs.jsoup)

    testImplementation(libs.junit4)
    testImplementation(libs.mockito.core)
    testImplementation(libs.json)
}
