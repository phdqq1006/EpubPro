plugins {
    id("epubpro.android.library")
}

android {
    namespace = "com.epubpro.core.reader.renderer"
}

dependencies {
    implementation(project(":domain"))

    // Jsoup for HTML Sanitizer
    implementation(libs.jsoup)

    testImplementation(libs.junit4)
    testImplementation(libs.json)
}
