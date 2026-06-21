// Lumi for Mobile App — native Android SDK.
// Kotlin-first, Java-compatible. Distributed via JitPack.

plugins {
    id("com.android.library") version "8.2.0"
    id("org.jetbrains.kotlin.android") version "1.9.20"
    id("maven-publish")
}

group = "ai.boostboss"
version = "0.1.1-alpha.5"

android {
    namespace = "ai.boostboss.lumi"
    // compileSdk 33 — JitPack's auto-installed Android SDK doesn't always
    // have 34 ready. 33 is the widest-available recent platform.
    compileSdk = 33

    defaultConfig {
        minSdk = 23  // Android 6.0 — matches Play Install Referrer API floor
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.20")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.android.installreferrer:installreferrer:2.2")
    implementation("androidx.lifecycle:lifecycle-process:2.7.0")

    testImplementation("junit:junit:4.13.2")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId    = project.group.toString()
            artifactId = "lumi"
            version    = project.version.toString()

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("Lumi for Mobile App — Android")
                description.set(
                    "Boost Boss's native Android ad SDK."
                )
                url.set("https://boostboss.ai/publish/mobile")
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                scm {
                    url.set("https://github.com/BoostBoss-AI/lumi-mobile-android")
                }
            }
        }
    }
}
