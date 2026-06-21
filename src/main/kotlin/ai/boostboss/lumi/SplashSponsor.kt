package ai.boostboss.lumi

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Full-screen sponsor card shown once per cold launch. A module-level flag
 * prevents re-display if LumiSDK.configure runs again in the same process.
 * Auto-dismisses after 3 seconds or on tap (tap also opens click URL).
 */
internal class SplashSponsor(
    private val activity:    Activity,
    private val publisherId: String,
    private val sessionId:   String,
) {

    companion object {
        const val PLACEMENT = "splash_sponsor"
        private val BB_PINK = Color.parseColor("#FF2E8E")
        @Volatile internal var shownThisLaunch = false
    }

    private var currentAd: Ad? = null
    private var overlay:   View? = null
    private val main = Handler(Looper.getMainLooper())
    private val dismissRunnable = Runnable { dismiss(opened = false) }

    fun showIfFirstLaunch() {
        if (shownThisLaunch) return
        shownThisLaunch = true

        Networking.fetchAd(
            publisherId = publisherId,
            placement   = PLACEMENT,
            contextHint = null,
            sessionId   = sessionId,
        ) { ad ->
            if (ad == null) return@fetchAd
            currentAd = ad
            present(ad)
        }
    }

    private fun present(ad: Ad) {
        val decor = activity.window.decorView as? ViewGroup ?: return
        val overlay = buildOverlay(ad)
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        decor.addView(overlay, lp)
        this.overlay = overlay

        Networking.fireImpression(ad)
        main.postDelayed(dismissRunnable, 3_000)
    }

    private fun dismiss(opened: Boolean) {
        main.removeCallbacks(dismissRunnable)
        overlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
        overlay = null

        if (opened) {
            val url = currentAd?.clickUrl ?: return
            try {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: Throwable) { /* silent */ }
        }
    }

    private fun buildOverlay(ad: Ad): View {
        val frame = FrameLayout(activity).apply {
            setBackgroundColor(BB_PINK)
            setOnClickListener { dismiss(opened = true) }
        }

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val contentLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER }
        frame.addView(content, contentLp)

        // Logo / image
        val image = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(120), dp(120)).apply {
                bottomMargin = dp(20)
            }
            setBackgroundColor(Color.parseColor("#33FFFFFF"))
        }
        ad.imageUrl?.let { Networking.fetchImage(it) { bmp -> bmp?.let { image.setImageBitmap(it) } } }
        content.addView(image)

        // Brand
        content.addView(TextView(activity).apply {
            text = ad.brand ?: ad.headline ?: "Sponsored"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        })

        // Tagline
        content.addView(TextView(activity).apply {
            text = ad.body ?: ""
            setTextColor(Color.parseColor("#E6FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            maxLines = 2
            layoutParams = LinearLayout.LayoutParams(
                dp(280),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })

        // Bottom-anchored footer
        val footer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val footerLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(32)
        }
        frame.addView(footer, footerLp)

        footer.addView(TextView(activity).apply {
            text = "Tap anywhere to continue"
            setTextColor(Color.parseColor("#B3FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
        })
        footer.addView(TextView(activity).apply {
            text = "Powered by Boost Boss"
            setTextColor(Color.parseColor("#99FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            gravity = Gravity.CENTER
        })

        return frame
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            activity.resources.displayMetrics
        ).toInt()
}
