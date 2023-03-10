plugins {
    kotlin("multiplatform") version "1.8.0"
    kotlin("plugin.serialization") version "1.8.0"
}

group = "niscy.eudiw"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val kotlinxSerializationVersion = "1.5.0"
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
