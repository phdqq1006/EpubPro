import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.gradle.kotlin.dsl.configure

plugins {
    id("org.jetbrains.kotlin.jvm")
}

extensions.configure<KotlinJvmProjectExtension> {
    jvmToolchain(17)
}
