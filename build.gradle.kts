import kotlin.jvm.optionals.getOrNull

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
    base
    `java-library`
    `maven-publish`
    signing
    jacoco
    alias(libs.plugins.dokka)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.spotless)
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.dependencycheck)
}

extra["isReleaseVersion"] = !version.toString().endsWith("SNAPSHOT")

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.nimbus.jose.jwt)
    testImplementation(kotlin("test"))
}

java {
    withSourcesJar()
    withJavadocJar()
    val javaVersion = getVersionFromCatalog("java")
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
}
kotlin {
    jvmToolchain {
        val javaVersion = getVersionFromCatalog("java")
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
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

fun getVersionFromCatalog(lookup: String): String {
    val versionCatalog: VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
    return versionCatalog
        .findVersion(lookup)
        .getOrNull()
        ?.requiredVersion
        ?: throw GradleException("Version '$lookup' is not specified in the version catalog")
}
