package ai.boostboss.lumi

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Auto-mounted sponsored card at the activity's bottom safe-inset. Tap →
 * opens click URL. Dismiss button removes the view; next ad fetch reattaches.
 */
internal class BottomBanner(
    private val activity:    Activity,
    private val publisherId: String,
    private val sessionId:   String,
) {

    companion object {
        const val PLACEMENT = "bottom_banner"
        private val BB_PINK = Color.parseColor("#FF2D78")
    }

    private var currentAd: Ad? = null
    private var rootView: View? = null
    private var impressionFired = false

    fun attach() {
        if (rootView != null) return
        val decor = activity.window.decorView as? ViewGroup ?: return

        val container = buildView()
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            dp(96)
        ).apply {
            gravity = Gravity.BOTTOM
            leftMargin   = dp(12)
            rightMargin  = dp(12)
            bottomMargin = dp(8)
        }
        decor.addView(container, lp)
        rootView = container

        // Apply safe-area inset bottom on devices with gesture nav.
        container.setOnApplyWindowInsetsListener { v, insets ->
            val bottom = insets.systemWindowInsetBottom
            (v.layoutParams as? FrameLayout.LayoutParams)?.bottomMargin = dp(8) + bottom
            v.requestLayout()
            insets
        }

        loadAd(container)
    }

    fun detach() {
        rootView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        rootView = null
    }

    private fun loadAd(host: FrameLayout) {
        Networking.fetchAd(
            publisherId = publisherId,
            placement   = PLACEMENT,
            contextHint = null,
            sessionId   = sessionId,
        ) { ad ->
            if (ad == null) { host.visibility = View.GONE; return@fetchAd }
            currentAd = ad
            render(host, ad)
        }
    }

    private fun render(host: FrameLayout, ad: Ad) {
        host.removeAllViews()
        host.addView(buildCard(ad))

        if (!impressionFired) {
            impressionFired = true
            Networking.fireImpression(ad)
        }
    }

    // MARK: - View construction (programmatic, no XML, no findViewById)

    private fun buildView(): FrameLayout = FrameLayout(activity).apply {
        setBackgroundColor(Color.TRANSPARENT)
    }

    private fun buildCard(ad: Ad): View {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundColor(Color.WHITE)
            // Drop shadow + rounded corners via background drawable not used here
            // (keep deps zero). Elevation supplies a system shadow on API 21+.
            elevation = dp(2).toFloat()
        }

        // Image
        val image = ImageView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64)).apply {
                rightMargin = dp(10)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor("#F0F0F0"))
        }
        ad.imageUrl?.let { Networking.fetchImage(it) { bmp -> bmp?.let { image.setImageBitmap(it) } } }
        card.addView(image)

        // Text column
        val textCol = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            gravity = Gravity.CENTER_VERTICAL
        }

        textCol.addView(TextView(activity).apply {
            text = "SPONSORED"
            setTextColor(Color.parseColor("#8C8C8C"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            setTypeface(typeface, Typeface.BOLD)
        })
        textCol.addView(TextView(activity).apply {
            text = ad.headline ?: ad.brand ?: ""
            setTextColor(Color.parseColor("#11111F"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
        })
        textCol.addView(TextView(activity).apply {
            text = ad.body ?: ""
            setTextColor(Color.parseColor("#666666"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            maxLines = 1
        })
        card.addView(textCol)

        // CTA button
        val cta = Button(activity).apply {
            text = (ad.ctaLabel ?: "Learn more").uppercase()
            setTextColor(Color.WHITE)
            setBackgroundColor(BB_PINK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            stateListAnimator = null  // remove material elevation animation
            setOnClickListener { openClick() }
        }
        card.addView(cta)

        // Dismiss
        val dismiss = TextView(activity).apply {
            text = "✕"
            setTextColor(Color.parseColor("#8C8C8C"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(8), 0, dp(4), 0)
            setOnClickListener { detach() }
        }
        card.addView(dismiss)

        // Make whole card tappable as fallback CTA.
        card.setOnClickListener { openClick() }

        return card
    }

    private fun openClick() {
        val url = currentAd?.clickUrl ?: return
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Throwable) { /* silent */ }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            activity.resources.displayMetrics
        ).toInt()
}
