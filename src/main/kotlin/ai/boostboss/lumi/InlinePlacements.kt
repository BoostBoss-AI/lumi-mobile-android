package ai.boostboss.lumi

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

// ────────────────────────────────────────────────────────────────────
// Five opt-in View subclasses publishers embed in their layouts.
// Each fetches an ad on attach, renders inline, fires impression on
// first render, opens click URL on tap.
//
//  - InlineNativeBanner    — full-width 60dp banner
//  - InlineSponsoredCard   — richer card with image + headline + body + CTA
//  - SponsoredCitation     — single-line sponsored citation
//  - SuggestedChip         — tappable quick-reply pill
//  - LoadingStateAd        — pink-bordered card shown during async loading
// ────────────────────────────────────────────────────────────────────

private val BB_PINK = Color.parseColor("#FF2D78")

private fun dp(ctx: Context, value: Int): Int =
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        ctx.resources.displayMetrics
    ).toInt()

private fun loadInto(
    placement: String,
    contextHint: String?,
    completion: (Ad?) -> Unit,
) {
    val pid = LumiSDK.publisherIdInternal()
    if (pid.isNullOrEmpty()) { completion(null); return }
    val sessionId = LumiSDK.sessionIdInternal()
    Networking.fetchAd(
        publisherId = pid,
        placement   = placement,
        contextHint = contextHint,
        sessionId   = sessionId,
        completion  = completion,
    )
}

private fun openClick(ctx: Context, ad: Ad?) {
    val url = ad?.clickUrl ?: return
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Throwable) { /* silent */ }
}

// ──────────────────── InlineNativeBanner ────────────────────

class InlineNativeBanner @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    contextHint: String? = null,
) : LinearLayout(context, attrs) {

    companion object { const val PLACEMENT = "inline_native_banner" }

    private val img = ImageView(context)
    private val headline = TextView(context)
    private val cta = Button(context)
    private var ad: Ad? = null
    private var fired = false

    init {
        orientation = HORIZONTAL
        setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8))
        setBackgroundColor(Color.WHITE)
        elevation = dp(context, 1).toFloat()
        gravity = Gravity.CENTER_VERTICAL

        img.apply {
            layoutParams = LayoutParams(dp(context, 44), dp(context, 44)).apply {
                rightMargin = dp(context, 10)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor("#F0F0F0"))
        }
        addView(img)

        headline.apply {
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setTextColor(Color.parseColor("#11111F"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 2
        }
        addView(headline)

        cta.apply {
            text = "LEARN MORE"
            setTextColor(Color.WHITE)
            setBackgroundColor(BB_PINK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6))
            stateListAnimator = null
            setOnClickListener { openClick(context, ad) }
        }
        addView(cta)

        setOnClickListener { openClick(context, ad) }

        loadInto(PLACEMENT, contextHint) { fetched ->
            ad = fetched
            render()
        }
    }

    private fun render() {
        val a = ad ?: run { visibility = GONE; return }
        headline.text = a.headline ?: a.brand
        cta.text = (a.ctaLabel ?: "Learn more").uppercase()
        a.imageUrl?.let { Networking.fetchImage(it) { b -> b?.let { img.setImageBitmap(it) } } }
        if (!fired) { fired = true; Networking.fireImpression(a) }
    }
}

// ──────────────────── InlineSponsoredCard ────────────────────

class InlineSponsoredCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    contextHint: String? = null,
) : LinearLayout(context, attrs) {

    companion object { const val PLACEMENT = "inline_sponsored_card" }

    private val img = ImageView(context)
    private val headline = TextView(context)
    private val body = TextView(context)
    private val cta = Button(context)
    private var ad: Ad? = null
    private var fired = false

    init {
        orientation = VERTICAL
        setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12))
        setBackgroundColor(Color.WHITE)
        elevation = dp(context, 2).toFloat()

        addView(TextView(context).apply {
            text = "SPONSORED"
            setTextColor(Color.parseColor("#8C8C8C"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            setTypeface(typeface, Typeface.BOLD)
        })

        img.apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 120),
            ).apply { topMargin = dp(context, 8) }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor("#F0F0F0"))
        }
        addView(img)

        headline.apply {
            setTextColor(Color.parseColor("#11111F"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(context, 8) }
        }
        addView(headline)

        body.apply {
            setTextColor(Color.parseColor("#595959"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 2
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(context, 4) }
        }
        addView(body)

        cta.apply {
            text = "LEARN MORE"
            setTextColor(Color.WHITE)
            setBackgroundColor(BB_PINK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(context, 14), dp(context, 6), dp(context, 14), dp(context, 6))
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(context, 10)
                gravity = Gravity.END
            }
            stateListAnimator = null
            setOnClickListener { openClick(context, ad) }
        }
        addView(cta)

        setOnClickListener { openClick(context, ad) }

        loadInto(PLACEMENT, contextHint) { fetched ->
            ad = fetched
            render()
        }
    }

    private fun render() {
        val a = ad ?: run { visibility = GONE; return }
        headline.text = a.headline ?: a.brand
        body.text = a.body
        cta.text = (a.ctaLabel ?: "Learn more").uppercase()
        a.imageUrl?.let { Networking.fetchImage(it) { b -> b?.let { img.setImageBitmap(it) } } }
        if (!fired) { fired = true; Networking.fireImpression(a) }
    }
}

// ──────────────────── SponsoredCitation ────────────────────

class SponsoredCitation @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    contextHint: String? = null,
) : LinearLayout(context, attrs) {

    companion object { const val PLACEMENT = "sponsored_citation" }

    private val pill = TextView(context)
    private val brand = TextView(context)
    private val tagline = TextView(context)
    private var ad: Ad? = null
    private var fired = false

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 6))

        pill.apply {
            text = " BB "
            setTextColor(Color.WHITE)
            setBackgroundColor(BB_PINK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(context, 4), dp(context, 2), dp(context, 4), dp(context, 2))
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { rightMargin = dp(context, 6) }
        }
        addView(pill)

        brand.apply {
            setTextColor(Color.parseColor("#11111F"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { rightMargin = dp(context, 6) }
        }
        addView(brand)

        tagline.apply {
            setTextColor(Color.parseColor("#4D4D4D"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        addView(tagline)

        setOnClickListener { openClick(context, ad) }

        loadInto(PLACEMENT, contextHint) { fetched ->
            ad = fetched
            render()
        }
    }

    private fun render() {
        val a = ad ?: run { visibility = GONE; return }
        brand.text = a.brand ?: a.headline
        tagline.text = a.body ?: a.ctaLabel
        if (!fired) { fired = true; Networking.fireImpression(a) }
    }
}

// ──────────────────── SuggestedChip ────────────────────

class SuggestedChip @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    contextHint: String? = null,
) : Button(context, attrs) {

    companion object { const val PLACEMENT = "suggested_chip" }

    private var ad: Ad? = null
    private var fired = false

    init {
        setBackgroundColor(Color.parseColor("#F2F2F2"))
        setTextColor(Color.parseColor("#11111F"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(context, 14), dp(context, 8), dp(context, 14), dp(context, 8))
        stateListAnimator = null
        text = "Loading…"
        setOnClickListener { openClick(context, ad) }

        loadInto(PLACEMENT, contextHint) { fetched ->
            ad = fetched
            render()
        }
    }

    private fun render() {
        val a = ad ?: run { visibility = GONE; return }
        text = "✨ ${a.headline ?: a.brand ?: "Sponsored"}"
        if (!fired) { fired = true; Networking.fireImpression(a) }
    }
}

// ──────────────────── LoadingStateAd ────────────────────

class LoadingStateAd @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    contextHint: String? = null,
) : FrameLayout(context, attrs) {

    companion object { const val PLACEMENT = "loading_state_ad" }

    private val label = TextView(context)
    private var ad: Ad? = null
    private var fired = false

    init {
        setBackgroundColor(Color.WHITE)
        setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16))
        elevation = dp(context, 1).toFloat()

        label.apply {
            text = "Loading…"
            setTextColor(Color.parseColor("#11111F"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 2
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = Gravity.CENTER }
        }
        addView(label)

        setOnClickListener { openClick(context, ad) }

        loadInto(PLACEMENT, contextHint) { fetched ->
            ad = fetched
            render()
        }
    }

    private fun render() {
        val a = ad ?: return
        label.text = a.headline ?: a.body ?: "Sponsored"
        if (!fired) { fired = true; Networking.fireImpression(a) }
    }
}
