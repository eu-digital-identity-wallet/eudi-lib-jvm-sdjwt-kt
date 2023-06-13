plugins {
    kotlin("multiplatform") version "1.8.21"
    kotlin("plugin.serialization") version "1.8.21"
    id("com.diffplug.spotless") version "6.19.0"
}

group = "eudi-lib-kmp-sd-jwt-kt"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val kotlinxSerializationVersion = "1.5.1"
val uriKmpVersion = "0.0.11"
val nimbusJoseJwtVersion = "9.31"

kotlin {
    jvm {
        jvmToolchain(8)
        withJava()
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
                implementation("com.eygraber:uri-kmp:$uriKmpVersion")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("com.nimbusds:nimbus-jose-jwt:$nimbusJoseJwtVersion")
            }
        }
        val jvmTest by getting
    }
}

val ktlintVersion = "0.49.1"
spotless {
    kotlin {
        ktlint(ktlintVersion)
        licenseHeaderFile("FileHeader.txt")
    }
    kotlinGradle {
        ktlint(ktlintVersion)
    }
}
