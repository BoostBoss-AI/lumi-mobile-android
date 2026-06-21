// Lumi for Mobile App — native Android SDK.
// Kotlin-first, Java-compatible. Distributed via JitPack (Maven Central + Sonatype to follow at v1.0).

plugins {
    id("com.android.library") version "8.2.0"
    id("org.jetbrains.kotlin.android") version "1.9.20"
    id("maven-publish")
}

group = "ai.boostboss"
version = "0.1.0-alpha.2"

android {
    namespace = "ai.boostboss.lumi"
    compileSdk = 34

    defaultConfig {
        minSdk = 23  // Android 6.0 — matches Play Install Referrer API floor
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    // Play Install Referrer — the standard Android install attribution API.
    // Roughly the SKAdNetwork equivalent for Google Play.
    implementation("com.android.installreferrer:installreferrer:2.2")

    // Lifecycle, for activity attach-on-resume callbacks.
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
                    "Boost Boss's native Android ad SDK. Auto-mounts BottomBanner " +
                    "and SplashSponsor placements, fires the publisher handshake on " +
                    "launch, integrates Play Install Referrer for install attribution."
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
