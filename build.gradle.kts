import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.gradle.DokkaTask
import org.owasp.dependencycheck.gradle.extension.DependencyCheckExtension
import java.net.URL

object Meta {
    const val BASE_URL = "https://github.com/eu-digital-identity-wallet/eudi-lib-jvm-sdjwt-kt"
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
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.kotlinx.knit)
}

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
    sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
        compilerOptions {
            optIn = listOf("kotlin.io.encoding.ExperimentalEncodingApi")
        }
    }
}

knit {
    rootDir = project.rootDir
    files = fileTree(project.rootDir) {
        include("docs/examples/**/*.md")
        include("README.md")
    }
    defaultLineSeparator = "\n"
}

spotless {
    val ktlintVersion = libs.versions.ktlintVersion.get()
    kotlin {
        ktlint(ktlintVersion)
        licenseHeaderFile("FileHeader.txt")
    }
    kotlinGradle {
        ktlint(ktlintVersion)
    }
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
        }
    }
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
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

tasks.withType<DokkaTask>().configureEach {
    dokkaSourceSets {
        named("main") {
            // used as project name in the header
            moduleName.set("EUDI SD-JWT")

            // contains descriptions for the module and the packages
            includes.from("Module.md")

            documentedVisibilities.set(
                setOf(
                    DokkaConfiguration.Visibility.PUBLIC,
                    DokkaConfiguration.Visibility.PROTECTED,
                ),
            )

            val remoteSourceUrl = System.getenv()["GIT_REF_NAME"]?.let { URL("${Meta.BASE_URL}/tree/$it/src") }
            remoteSourceUrl
                ?.let {
                    sourceLink {
                        localDirectory.set(projectDir.resolve("src"))
                        remoteUrl.set(it)
                        remoteLineSuffix.set("#L")
                    }
                }
        }
    }
}

mavenPublishing {
    pom {
        ciManagement {
            system = "github"
            url = "${Meta.BASE_URL}/actions"
        }
    }
}

val nvdApiKey: String? = System.getenv("NVD_API_KEY") ?: properties["nvdApiKey"]?.toString()
val dependencyCheckExtension = extensions.findByType(DependencyCheckExtension::class.java)
dependencyCheckExtension?.apply {
    formats = mutableListOf("XML", "HTML")
    nvd.apiKey = nvdApiKey ?: ""
}
