object Meta {
    const val orgUrl = "https://github.com/eu-digital-identity-wallet"
    const val projectDescription = "Implementation of SD-JWT"
    const val projectBaseUrl = "https://github.com/eu-digital-identity-wallet/eudi-lib-jvm-sdjwt-kt.git"
    const val projectGitUrl =
        "scm:git:git@github.com:eu-digital-identity-wallet/eudi-lib-jvm-sdjwt-kt.git"
    const val projectSshUrl =
        "scm:git:ssh://github.com:eu-digital-identity-wallet/eudi-lib-jvm-sdjwt-kt.git"
}
plugins {
    id("org.sonarqube") version "4.0.0.2929"
    kotlin("jvm") version "1.8.21"
    kotlin("plugin.serialization") version "1.8.21"
    id("com.diffplug.spotless") version "6.19.0"
    `java-library`
    `maven-publish`
    signing
}

java.sourceCompatibility = JavaVersion.VERSION_17

extra["isReleaseVersion"] = !version.toString().endsWith("SNAPSHOT")

repositories {
    mavenCentral()
    mavenLocal()
}

val kotlinxSerializationVersion = "1.5.1"
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

tasks.test {
    useJUnitPlatform()
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
                description.set(Meta.projectDescription)
                url.set(Meta.projectBaseUrl)
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                scm {
                    connection.set(Meta.projectGitUrl)
                    developerConnection.set(Meta.projectSshUrl)
                    url.set(Meta.projectBaseUrl)
                }
                issueManagement {
                    system.set("github")
                    url.set(Meta.projectBaseUrl + "/issues")
                }
                ciManagement {
                    system.set("github")
                    url.set(Meta.projectBaseUrl + "/actions")
                }
                developers {
                    organization {
                        url.set(Meta.orgUrl)
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
