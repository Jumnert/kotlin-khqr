plugins {
    kotlin("multiplatform") version "2.4.0"
}

group = "dev.khqr.web"
version = "0.1.0"

repositories {
    mavenCentral()
}

kotlin {
    // JVM target exists purely to unit-test the shared KHQR logic quickly
    // (no browser/Node needed for tests).
    jvm()

    // Browser target produces the static site that deploys to Vercel et al.
    js {
        browser {
            commonWebpackConfig {
                outputFileName = "khqr-web.js"
            }
            // We verify the shared logic on the JVM; no headless browser in CI.
            testTask {
                enabled = false
            }
        }
        binaries.executable()
    }

    sourceSets {
        getByName("jvmTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
