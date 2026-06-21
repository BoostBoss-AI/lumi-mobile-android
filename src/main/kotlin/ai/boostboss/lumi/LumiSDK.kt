package ai.boostboss.lumi

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import java.util.UUID

/**
 * Lumi for Mobile App — native Android SDK.
 *
 * Public entry point. A publisher's [Application.onCreate] calls
 * `LumiSDK.configure(context, publisherId)` once on launch.
 *
 * Per Publisher Agreement §4.1, that one call is sufficient consent to
 * auto-mount every Mobile App placement. Publishers suppress individual
 * placements via `LumiSDK.suppress(Placement.SPLASH_SPONSOR)`.
 *
 * Wire contract (mirror of /api/lumi-fetch + /api/track on the server):
 *   POST https://boostboss.ai/api/lumi-fetch
 *     { publisher_id, door="android-native", context, placement,
 *       session_id, page_url=null }
 *   POST https://boostboss.ai/api/track
 *     { event="impression", developer_id, integration_method="android-native",
 *       surface="mobile", placement_id, session_id, context: { sdk_version,
 *       handshake: true } }
 */
object LumiSDK {

    const val SDK_VERSION = "0.1.0-alpha.2"
    const val DOOR        = "android-native"

    private var publisherId: String? = null
    private val sessionId:   String  = UUID.randomUUID().toString()
    private val suppressed:  MutableSet<Placement> = mutableSetOf()
    private var configured = false
    private var lifecycleHook: Application.ActivityLifecycleCallbacks? = null

    /**
     * Configure the SDK on app launch. Idempotent — safe to call multiple
     * times; subsequent calls reset the publisher binding.
     */
    @JvmStatic
    fun configure(context: Context, publisherId: String): Boolean {
        if (publisherId.isEmpty()) {
            android.util.Log.w("BoostBossLumi", "configure() called with empty publisherId — SDK inert.")
            return false
        }
        if (configured && this.publisherId == publisherId) return true

        this.publisherId = publisherId
        this.configured = true

        // 1. Handshake — flips the publisher's Mobile App verify badge.
        Networking.fireHandshake(publisherId, sessionId)

        // 2. Register an ActivityLifecycleCallbacks so we can mount the
        //    BottomBanner on every activity that comes to the foreground,
        //    and present SplashSponsor on the first one.
        val app = context.applicationContext as? Application ?: return true
        if (lifecycleHook == null) {
            lifecycleHook = Hook()
            app.registerActivityLifecycleCallbacks(lifecycleHook!!)
        }
        return true
    }

    @JvmStatic
    fun suppress(placement: Placement) {
        suppressed.add(placement)
    }

    enum class Placement(val key: String) {
        BOTTOM_BANNER("bottom-banner"),
        SPLASH_SPONSOR("splash-sponsor"),
        INTERSTITIAL("interstitial"),
        REWARDED_VIDEO("rewarded-video"),
        PRE_ROLL_VIDEO("pre-roll-video"),
        INLINE_NATIVE_BANNER("inline-native-banner"),
        SPONSORED_CITATION("sponsored-citation"),
        SUGGESTED_CHIP("suggested-chip"),
        LOADING_STATE_AD("loading-state-ad"),
        INLINE_SPONSORED_CARD("inline-sponsored-card");
    }

    // MARK: - Internal lifecycle hook

    private inner class Hook : Application.ActivityLifecycleCallbacks {
        private val banners = mutableMapOf<Activity, BottomBanner>()

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}

        override fun onActivityResumed(activity: Activity) {
            val pubId = publisherId ?: return

            // BottomBanner — one per activity, unless suppressed.
            if (Placement.BOTTOM_BANNER !in suppressed && banners[activity] == null) {
                val banner = BottomBanner(activity, pubId, sessionId)
                banner.attach()
                banners[activity] = banner
            }

            // SplashSponsor — once per cold launch, on first activity to resume.
            if (Placement.SPLASH_SPONSOR !in suppressed && !SplashSponsor.shownThisLaunch) {
                SplashSponsor(activity, pubId, sessionId).showIfFirstLaunch()
            }
        }

        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

        override fun onActivityDestroyed(activity: Activity) {
            banners.remove(activity)?.detach()
        }
    }
}
