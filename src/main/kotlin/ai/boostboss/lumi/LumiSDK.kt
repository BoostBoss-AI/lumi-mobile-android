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
 */
object LumiSDK {

    const val SDK_VERSION = "0.1.1-alpha"
    const val DOOR        = "android-native"

    private var publisherId: String? = null
    private val sessionId:   String  = UUID.randomUUID().toString()
    private val suppressed:  MutableSet<Placement> = mutableSetOf()
    private var configured = false
    private var lifecycleHook: Application.ActivityLifecycleCallbacks? = null

    @JvmStatic
    fun configure(context: Context, publisherId: String): Boolean {
        if (publisherId.isEmpty()) {
            android.util.Log.w("BoostBossLumi", "configure() called with empty publisherId — SDK inert.")
            return false
        }
        if (configured && this.publisherId == publisherId) return true

        this.publisherId = publisherId
        this.configured = true

        Networking.fireHandshake(publisherId, sessionId)

        val app = context.applicationContext as? Application ?: return true
        if (lifecycleHook == null) {
            lifecycleHook = Hook()
            app.registerActivityLifecycleCallbacks(lifecycleHook!!)
        }
        return true
    }

    @JvmStatic
    fun suppress(placement: Placement) { suppressed.add(placement) }

    // ── Internal accessors used by sibling placement files ───────────
    // Kotlin's `private` is class-scoped; other files in the module need
    // these accessors to read the configured publisher id + session.
    internal fun publisherIdInternal(): String? = publisherId
    internal fun sessionIdInternal(): String = sessionId

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

    // ── Lifecycle hook for auto-mounted placements ────────────────────

    private class Hook : Application.ActivityLifecycleCallbacks {
        private val banners = mutableMapOf<Activity, BottomBanner>()

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}

        override fun onActivityResumed(activity: Activity) {
            val pubId = publisherId ?: return

            if (Placement.BOTTOM_BANNER !in suppressed && banners[activity] == null) {
                val banner = BottomBanner(activity, pubId, sessionId)
                banner.attach()
                banners[activity] = banner
            }

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
