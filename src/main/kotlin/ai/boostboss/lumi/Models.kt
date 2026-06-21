package ai.boostboss.lumi

/**
 * Ad payload returned by /api/lumi-fetch. JSON decoding is hand-written in
 * [Networking] using android.util.JsonReader to avoid pulling Gson/Moshi as
 * deps (we want zero non-AndroidX, non-Kotlin runtime dependencies).
 */
data class Ad(
    val adId:          String? = null,
    val headline:      String? = null,
    val body:          String? = null,
    val imageUrl:      String? = null,
    val ctaLabel:      String? = null,
    val clickUrl:      String? = null,
    val impressionUrl: String? = null,
    val brand:         String? = null,
)
