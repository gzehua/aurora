import org.jetbrains.compose.compose

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("com.vanniktech.maven.publish")
    id("org.jetbrains.dokka")
}

kotlin {
    jvm("desktop")
    sourceSets {
        named("commonMain") {
            dependencies {
                api(compose.runtime)
                api(compose.foundation)
                implementation(project(":bitmapfilter"))
                implementation(project(":skin"))
            }
        }
        named("desktopMain") {
            dependencies {
                api(compose.desktop.common)
                implementation(project(":bitmapfilter"))
                implementation(project(":skin"))
            }
        }
    }
}
