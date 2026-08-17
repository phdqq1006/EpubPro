plugins {
    id("epubpro.kotlin.jvm.library")
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit4)
}
