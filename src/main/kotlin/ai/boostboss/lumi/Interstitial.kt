package ai.boostboss.lumi

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Opt-in full-screen interstitial. Publisher calls
 * `Interstitial.present(activity)` between major flows.
 */
object Interstitial {

    private const val PLACEMENT = "interstitial"
    private val BB_PINK = Color.parseColor("#FF2D78")

    fun present(activity: Activity, onDismiss: (() -> Unit)? = null) {
        val pid = LumiSDK.publisherIdInternal()
        if (pid.isNullOrEmpty()) { onDismiss?.invoke(); return }
        val sessionId = LumiSDK.sessionIdInternal()

        Networking.fetchAd(
            publisherId = pid,
            placement   = PLACEMENT,
            contextHint = null,
            sessionId   = sessionId,
        ) { ad ->
            if (ad == null) { onDismiss?.invoke(); return@fetchAd }
            showDialog(activity, ad, onDismiss)
            Networking.fireImpression(ad)
        }
    }

    private fun showDialog(activity: Activity, ad: Ad, onDismiss: (() -> Unit)?) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.argb(180, 0, 0, 0)))

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 20), dp(activity, 20), dp(activity, 20), dp(activity, 20))
            setBackgroundColor(Color.WHITE)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        card.addView(TextView(activity).apply {
            text = "SPONSORED"
            setTextColor(Color.parseColor("#8C8C8C"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            setTypeface(typeface, Typeface.BOLD)
        })

        val img = ImageView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(dp(activity, 160), dp(activity, 160)).apply {
                topMargin = dp(activity, 14)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor("#F0F0F0"))
        }
        ad.imageUrl?.let { Networking.fetchImage(it) { b -> b?.let { img.setImageBitmap(it) } } }
        card.addView(img)

        card.addView(TextView(activity).apply {
            text = ad.headline ?: ad.brand ?: ""
            setTextColor(Color.parseColor("#11111F"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(activity, 16) }
        })
        card.addView(TextView(activity).apply {
            text = ad.body ?: ""
            setTextColor(Color.parseColor("#595959"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            maxLines = 4
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(activity, 8) }
        })
        card.addView(Button(activity).apply {
            text = (ad.ctaLabel ?: "Learn more").uppercase()
            setTextColor(Color.WHITE)
            setBackgroundColor(BB_PINK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(activity, 24), dp(activity, 10), dp(activity, 24), dp(activity, 10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(activity, 16) }
            stateListAnimator = null
            setOnClickListener {
                ad.clickUrl?.let { url ->
                    try {
                        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (_: Throwable) { /* silent */ }
                }
                dialog.dismiss()
            }
        })

        dialog.setContentView(card)
        dialog.setOnDismissListener { onDismiss?.invoke() }
        dialog.show()
    }

    private fun dp(activity: Activity, value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            activity.resources.displayMetrics
        ).toInt()
}
