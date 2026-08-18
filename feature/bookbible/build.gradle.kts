plugins {
    id("epubpro.android.library")
    id("epubpro.android.compose")
    id("epubpro.android.hilt")
}

android {
    namespace = "com.epubpro.feature.bookbible"
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:storage"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.coil.compose)

    testImplementation(libs.junit4)
    testImplementation(libs.mockito.core)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
