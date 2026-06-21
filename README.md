# ai.boostboss:lumi (Android)

Boost Boss's native Android ad SDK. Kotlin-first, Java-compatible. Distributed via JitPack for v0.1; Maven Central + Sonatype at v1.0.

> **Most publishers install through the cross-platform CLI rather than by hand:**
>
> ```bash
> npx @boostbossai/install-mobile pub_xxx
> ```

## Install

In your root `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

In your app module `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.BoostBoss-AI:lumi-mobile-android:0.1.0-alpha.2")
}
```

## Wire-up

In `Application.kt`:

```kotlin
import ai.boostboss.lumi.LumiSDK

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LumiSDK.configure(this, "pub_a8x2k9f9")
    }
}
```

Declare your Application class in `AndroidManifest.xml`:

```xml
<application android:name=".MyApp" ...>
```

Per [Publisher Agreement §4.1](https://boostboss.ai/publisher-agreement#section-4), `configure(context, publisherId)` is sufficient consent to auto-mount every Mobile App placement. Suppress individual placements with `LumiSDK.suppress(Placement.SPLASH_SPONSOR)`.

## What ships in v0.1

- Handshake → publisher verify badge flips to Connected
- `BottomBanner` auto-mounts on every activity resume via `ActivityLifecycleCallbacks`
- `SplashSponsor` full-screen overlay once per cold launch
- Tap → opens click URL via `Intent.ACTION_VIEW`; impression beacon fires on first render

## What v0.1 does NOT cover

- **Play Install Referrer attribution** — declared as a Gradle dependency for v0.2, not yet wired into a postback. CPI campaigns will pick up Install Referrer in v0.2.
- **Rewarded video, interstitial, pre-roll** — separate v0.2 work
- **Inline placements** — use the React Native wrapper

## Compatibility

- Android 6.0 (API 23) and above — matches Play Install Referrer floor
- Kotlin 1.9+ / Java 17 source compatibility
- Architectures: arm64-v8a, armeabi-v7a, x86_64

## License

MIT
