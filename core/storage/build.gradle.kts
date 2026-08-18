plugins {
    id("epubpro.android.library")
    id("epubpro.android.hilt")
}

android {
    namespace = "com.epubpro.core.storage"
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":core:common"))

    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit4)
    testImplementation(libs.mockito.core)
    testImplementation(libs.json)
}
