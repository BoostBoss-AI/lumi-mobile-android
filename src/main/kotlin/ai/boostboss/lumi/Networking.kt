package ai.boostboss.lumi

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * HttpURLConnection-based client for /api/lumi-fetch + /api/track. Zero
 * external dependencies — pure JDK + Android Bitmap. All calls run on
 * a background executor; completions post back to the main thread so
 * UI updates don't need explicit threading.
 */
internal object Networking {

    private const val API_ORIGIN  = "https://boostboss.ai"
    private const val LUMI_FETCH  = "$API_ORIGIN/api/lumi-fetch"
    private const val TRACK       = "$API_ORIGIN/api/track"

    private const val TIMEOUT_MS_CONNECT = 8_000
    private const val TIMEOUT_MS_READ    = 15_000

    private val executor = Executors.newCachedThreadPool { r ->
        Thread(r, "boostboss-lumi-net").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())

    // ── Handshake ──────────────────────────────────────────────────────

    fun fireHandshake(publisherId: String, sessionId: String) {
        val body = JSONObject().apply {
            put("event",              "impression")
            put("campaign_id",        "lumi_android_native_handshake")
            put("session_id",         sessionId)
            put("developer_id",       publisherId)
            put("integration_method", LumiSDK.DOOR)
            put("surface",            "mobile")
            put("placement_id",       "lumi_handshake")
            put("context", JSONObject().apply {
                put("sdk_version", LumiSDK.SDK_VERSION)
                put("handshake",   true)
            })
        }
        post(TRACK, body.toString()) { /* fire-and-forget */ }
    }

    // ── Ad fetch ───────────────────────────────────────────────────────

    fun fetchAd(
        publisherId: String,
        placement:   String,
        contextHint: String?,
        sessionId:   String,
        completion:  (Ad?) -> Unit,
    ) {
        val body = JSONObject().apply {
            put("publisher_id", publisherId)
            put("door",         LumiSDK.DOOR)
            put("context",      contextHint ?: "android native")
            put("placement",    placement)
            put("format",       "native")
            put("session_id",   sessionId)
            put("page_url",     JSONObject.NULL)
        }
        post(LUMI_FETCH, body.toString()) { responseBody ->
            val ad = if (responseBody != null) parseFetchResponse(responseBody) else null
            main.post { completion(ad) }
        }
    }

    private fun parseFetchResponse(json: String): Ad? {
        return try {
            val root = JSONObject(json)
            if (!root.has("ad") || root.isNull("ad")) return null
            val a = root.getJSONObject("ad")
            Ad(
                adId          = a.optString("ad_id",          "").ifEmpty { null },
                headline      = a.optString("headline",       "").ifEmpty { null },
                body          = a.optString("body",           "").ifEmpty { null },
                imageUrl      = a.optString("image_url",      "").ifEmpty { null },
                ctaLabel      = a.optString("cta_label",      "").ifEmpty { null },
                clickUrl      = a.optString("click_url",      "").ifEmpty { null },
                impressionUrl = a.optString("impression_url", "").ifEmpty { null },
                brand         = a.optString("brand",          "").ifEmpty { null },
            )
        } catch (_: Throwable) { null }
    }

    // ── Impression beacon ──────────────────────────────────────────────

    fun fireImpression(ad: Ad) {
        val url = ad.impressionUrl ?: return
        executor.execute {
            try {
                (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = TIMEOUT_MS_CONNECT
                    readTimeout    = TIMEOUT_MS_READ
                    inputStream.close()
                    disconnect()
                }
            } catch (_: Throwable) { /* best-effort */ }
        }
    }

    // ── Image download ─────────────────────────────────────────────────

    fun fetchImage(url: String, completion: (Bitmap?) -> Unit) {
        executor.execute {
            val bmp = try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = TIMEOUT_MS_CONNECT
                conn.readTimeout    = TIMEOUT_MS_READ
                conn.inputStream.use { BitmapFactory.decodeStream(it) }
            } catch (_: Throwable) { null }
            main.post { completion(bmp) }
        }
    }

    // ── Internal POST helper ──────────────────────────────────────────

    private fun post(urlString: String, jsonBody: String, completion: (String?) -> Unit) {
        executor.execute {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    requestMethod  = "POST"
                    connectTimeout = TIMEOUT_MS_CONNECT
                    readTimeout    = TIMEOUT_MS_READ
                    doOutput       = true
                    setRequestProperty("Content-Type", "application/json")
                }
                OutputStreamWriter(conn.outputStream).use { it.write(jsonBody) }
                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    completion(body)
                } else {
                    completion(null)
                }
            } catch (_: Throwable) {
                completion(null)
            } finally {
                conn?.disconnect()
            }
        }
    }
}
