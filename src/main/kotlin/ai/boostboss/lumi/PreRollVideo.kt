package ai.boostboss.lumi

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.media.MediaPlayer
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
import android.widget.TextView

/**
 * Opt-in pre-roll video. Shorter and less ceremonious than rewarded —
 * no reward grant, just watch-or-skip. Fires onComplete when done.
 */
object PreRollVideo {

    private const val PLACEMENT = "pre_roll_video"
    private const val SKIP_AFTER_MS = 5_000L

    fun present(activity: Activity, onComplete: (() -> Unit)? = null) {
        val pid = LumiSDK.publisherIdInternal()
        if (pid.isNullOrEmpty()) { onComplete?.invoke(); return }
        val sessionId = LumiSDK.sessionIdInternal()

        Networking.fetchAd(
            publisherId = pid,
            placement   = PLACEMENT,
            contextHint = null,
            sessionId   = sessionId,
        ) { ad ->
            if (ad == null) { onComplete?.invoke(); return@fetchAd }
            showDialog(activity, ad, onComplete)
            Networking.fireImpression(ad)
        }
    }

    private fun showDialog(activity: Activity, ad: Ad, onComplete: (() -> Unit)?) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        val root = FrameLayout(activity).apply { setBackgroundColor(Color.BLACK) }

        var done = false
        val finish: () -> Unit = {
            if (!done) { done = true; dialog.dismiss(); onComplete?.invoke() }
        }

        val videoUrl = ad.imageUrl?.takeIf {
            val lower = it.lowercase()
            lower.endsWith(".mp4") || lower.endsWith(".mov")
        }
        if (videoUrl != null) {
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
                        setOnCompletionListener { finish() }
                        prepareAsync()
                        setOnPreparedListener { it.start() }
                    }
                }
                override fun surfaceChanged(h: SurfaceHolder, fmt: Int, w: Int, ht: Int) {}
                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    mp?.release(); mp = null
                }
            })
        } else {
            root.addView(TextView(activity).apply {
                text = ad.headline ?: ad.brand ?: "Sponsored"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )
            })
            Handler(Looper.getMainLooper()).postDelayed({ finish() }, 5_000L)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            if (done) return@postDelayed
            root.addView(Button(activity).apply {
                text = "Skip ✕"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#80000000"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(activity, 12), dp(activity, 6), dp(activity, 12), dp(activity, 6))
                stateListAnimator = null
                setOnClickListener { finish() }
            }, FrameLayout.LayoutParams(
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
