package ai.boostboss.lumi

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Opt-in rewarded video — the highest-CPM placement in adtech.
 * Uses MediaPlayer (built-in, no ExoPlayer dep). Falls back to a card
 * with a mandatory 15s wait if the ad has no .mp4/.mov URL.
 */
object RewardedVideo {

    private const val PLACEMENT = "rewarded_video"
    private const val SKIP_AFTER_MS = 5_000L

    data class Reward(val amount: Int, val unit: String)

    fun present(
        activity: Activity,
        rewardAmount: Int = 10,
        rewardUnit: String = "credits",
        onReward: ((Reward) -> Unit)? = null,
        onSkip:   (() -> Unit)? = null,
    ) {
        val pid = LumiSDK.publisherIdInternal()
        if (pid.isNullOrEmpty()) { onSkip?.invoke(); return }
        val sessionId = LumiSDK.sessionIdInternal()

        Networking.fetchAd(
            publisherId = pid,
            placement   = PLACEMENT,
            contextHint = null,
            sessionId   = sessionId,
        ) { ad ->
            if (ad == null) { onSkip?.invoke(); return@fetchAd }
            showDialog(activity, ad, rewardAmount, rewardUnit, onReward, onSkip)
            Networking.fireImpression(ad)
        }
    }

    private fun showDialog(
        activity: Activity,
        ad: Ad,
        rewardAmount: Int,
        rewardUnit: String,
        onReward: ((Reward) -> Unit)?,
        onSkip:   (() -> Unit)?,
    ) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        val root = FrameLayout(activity).apply {
            setBackgroundColor(Color.BLACK)
        }

        var rewarded = false
        val grantReward: () -> Unit = {
            if (!rewarded) {
                rewarded = true
                dialog.dismiss()
                onReward?.invoke(Reward(rewardAmount, rewardUnit))
            }
        }

        val videoUrl = ad.imageUrl?.takeIf {
            val lower = it.lowercase()
            lower.endsWith(".mp4") || lower.endsWith(".mov")
        }

        if (videoUrl != null) {
            // MediaPlayer + SurfaceView
            val surface = SurfaceView(activity)
            root.addView(surface, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ))
            surface.holder.addCallback(object : SurfaceHolder.Callback {
                var mp: MediaPlayer? = null
                override fun surfaceCreated(holder: SurfaceHolder) {
                    mp = MediaPlayer().apply {
                        setDisplay(holder)
                        setDataSource(videoUrl)
                        setOnCompletionListener { grantReward() }
                        prepareAsync()
                        setOnPreparedListener { it.start() }
                    }
                }
                override fun surfaceChanged(h: SurfaceHolder, fmt: Int, w: Int, ht: Int) {}
                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    mp?.release()
                    mp = null
                }
            })
        } else {
            // Card fallback with 15s mandatory wait
            val card = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor("#FF2E8E"))
            }
            val img = ImageView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    dp(activity, 160), dp(activity, 160)
                ).apply { bottomMargin = dp(activity, 16) }
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(Color.parseColor("#33FFFFFF"))
            }
            ad.imageUrl?.let { Networking.fetchImage(it) { b -> b?.let { img.setImageBitmap(it) } } }
            card.addView(img)
            card.addView(TextView(activity).apply {
                text = ad.headline ?: ad.brand ?: "Sponsored"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
            })
            root.addView(card, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ))
            Handler(Looper.getMainLooper()).postDelayed({ grantReward() }, 15_000L)
        }

        // SPONSORED label (top-left)
        root.addView(TextView(activity).apply {
            text = "SPONSORED"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#80000000"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(activity, 8), dp(activity, 4), dp(activity, 8), dp(activity, 4))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                topMargin = dp(activity, 12)
                leftMargin = dp(activity, 12)
            }
        })

        // Skip button — appears after SKIP_AFTER_MS
        Handler(Looper.getMainLooper()).postDelayed({
            if (rewarded) return@postDelayed
            val skip = Button(activity).apply {
                text = "Skip ✕"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#80000000"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(activity, 12), dp(activity, 6), dp(activity, 12), dp(activity, 6))
                stateListAnimator = null
                setOnClickListener {
                    dialog.dismiss()
                    onSkip?.invoke()
                }
            }
            root.addView(skip, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = dp(activity, 12)
                rightMargin = dp(activity, 12)
            })
        }, SKIP_AFTER_MS)

        dialog.setContentView(root)
        dialog.show()
    }

    private fun dp(activity: Activity, value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            activity.resources.displayMetrics
        ).toInt()
}
