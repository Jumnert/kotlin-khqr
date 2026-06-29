import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    `java-library`
    `maven-publish`
}

group = "dev.khqr"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    // JSON (no compiler-plugin-free runtime parsing + typed models)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // QR image rendering
    implementation("com.google.zxing:core:3.5.4")
    implementation("com.google.zxing:javase:3.5.4")

    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Compiled on whatever JDK runs Gradle (JDK 17+), but always targets JVM 17
// bytecode so the published library works for any consumer on JDK 17 or newer.
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("kotlin-bakong")
                description.set(
                    "Kotlin library for generating Bakong KHQR (EMVCo) payment QR codes, " +
                        "MD5 hashes, deeplinks, QR images, and verifying transactions via the " +
                        "Bakong Open API. For educational and integration-reference purposes."
                )
                url.set("https://github.com/Jumnert/Kotlin-Bakong")
                licenses {
                    license {
                        name.set("Educational Use License")
                        url.set("https://github.com/Jumnert/Kotlin-Bakong/blob/main/LICENSE")
                    }
                }
                developers {
                    developer {
                        id.set("Jumnert")
                        name.set("Jumnert")
                    }
                }
                scm {
                    url.set("https://github.com/Jumnert/Kotlin-Bakong")
                    connection.set("scm:git:https://github.com/Jumnert/Kotlin-Bakong.git")
                }
            }
        }
    }
    repositories {
        // GitHub Packages target — only registered when running inside GitHub Actions
        // (so local `publishToMavenLocal` and JitPack builds are unaffected).
        val ghRepo = System.getenv("GITHUB_REPOSITORY")
        val ghActor = System.getenv("GITHUB_ACTOR")
        val ghToken = System.getenv("GITHUB_TOKEN")
        if (ghRepo != null && ghActor != null && ghToken != null) {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/$ghRepo")
                credentials {
                    username = ghActor
                    password = ghToken
                }
            }
        }
    }
}
