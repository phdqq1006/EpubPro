plugins {
    id("epubpro.android.library")
    id("epubpro.android.hilt")
}

android {
    namespace = "com.epubpro.core.database"
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":core:common"))

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit4)
    testImplementation(libs.mockito.core)
}
