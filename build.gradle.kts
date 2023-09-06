object Meta {
    const val ORG_URL = "https://github.com/eu-digital-identity-wallet"
    const val PROJ_DESCR = "Implementation of SD-JWT"
    const val PROJ_BASE_URL = "https://github.com/eu-digital-identity-wallet/eudi-lib-jvm-sdjwt-kt.git"
    const val PROJ_GIT_URL =
        "scm:git:git@github.com:eu-digital-identity-wallet/eudi-lib-jvm-sdjwt-kt.git"
    const val PROJ_SSH_URL =
        "scm:git:ssh://github.com:eu-digital-identity-wallet/eudi-lib-jvm-sdjwt-kt.git"
}
plugins {
    id("org.owasp.dependencycheck") version "8.3.1"
    id("org.sonarqube") version "4.3.1.3277"
    kotlin("jvm") version "1.8.21"
    kotlin("plugin.serialization") version "1.8.21"
    id("com.diffplug.spotless") version "6.21.0"
    `java-library`
    `maven-publish`
    signing
    jacoco
}

java.sourceCompatibility = JavaVersion.VERSION_17

extra["isReleaseVersion"] = !version.toString().endsWith("SNAPSHOT")

repositories {
    mavenCentral()
    mavenLocal()
}

val kotlinxSerializationVersion = "1.6.0"
val nimbusJoseJwtVersion = "9.31"

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
    api("com.nimbusds:nimbus-jose-jwt:$nimbusJoseJwtVersion")
    testImplementation(kotlin("test"))
}

java {
    withSourcesJar()
    withJavadocJar()
}
kotlin {
    jvmToolchain(17)
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
        }
    }
}

tasks.jar {
    manifest {
        attributes(
            mapOf(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
            ),
        )
    }
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
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

publishing {
    publications {
        create<MavenPublication>("library") {
            from(components["java"])
            pom {
                name.set(project.name)
                description.set(Meta.PROJ_DESCR)
                url.set(Meta.PROJ_BASE_URL)
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                scm {
                    connection.set(Meta.PROJ_GIT_URL)
                    developerConnection.set(Meta.PROJ_SSH_URL)
                    url.set(Meta.PROJ_BASE_URL)
                }
                issueManagement {
                    system.set("github")
                    url.set(Meta.PROJ_BASE_URL + "/issues")
                }
                ciManagement {
                    system.set("github")
                    url.set(Meta.PROJ_BASE_URL + "/actions")
                }
                developers {
                    organization {
                        url.set(Meta.ORG_URL)
                    }
                }
            }
        }
    }
    repositories {

        val sonaUri =
            if ((extra["isReleaseVersion"]) as Boolean) {
                "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
            } else {
                "https://s01.oss.sonatype.org/content/repositories/snapshots/"
            }

        maven {
            name = "sonatype"
            url = uri(sonaUri)
            credentials(PasswordCredentials::class)
        }
    }
}

signing {
    setRequired({
        (project.extra["isReleaseVersion"] as Boolean) && gradle.taskGraph.hasTask("publish")
    })
    val signingKeyId: String? by project
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
    sign(publishing.publications["library"])
}
