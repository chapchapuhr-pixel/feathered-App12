package com.unera.social.data

import com.unera.social.model.AdCampaign
import com.unera.social.model.SponsoredFeedAd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object AdsApiService {
    private const val BASE_URL = "https://featheredsocial.site"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    /**
     * GET /api/ads/feeds (with fallback /api/ads/feed)
     * Fetches real live sponsored feed ads.
     */
    suspend fun fetchFeedAds(): List<SponsoredFeedAd> = withContext(Dispatchers.IO) {
        val adsList = mutableListOf<SponsoredFeedAd>()
        var success = false

        val urls = listOf("$BASE_URL/api/ads/feeds", "$BASE_URL/api/ads/feed")
        for (urlStr in urls) {
            if (success) break
            try {
                val url = URL(urlStr)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", USER_AGENT)
                }

                if (conn.responseCode in 200..299) {
                    val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }.trim()
                    if (responseText.startsWith("{")) {
                        val root = JSONObject(responseText)
                        val arr = root.optJSONArray("ads") ?: root.optJSONArray("data")
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val obj = arr.optJSONObject(i) ?: continue
                                parseFeedAd(obj)?.let { adsList.add(it) }
                            }
                            success = true
                        }
                    } else if (responseText.startsWith("[")) {
                        val arr = JSONArray(responseText)
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            parseFeedAd(obj)?.let { adsList.add(it) }
                        }
                        success = true
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        adsList
    }

    /**
     * GET /api/ads/my
     * Fetches user's created campaigns with x-user-id header.
     */
    suspend fun fetchMyAds(userId: Long): List<AdCampaign> = withContext(Dispatchers.IO) {
        val campaigns = mutableListOf<AdCampaign>()
        try {
            val url = URL("$BASE_URL/api/ads/my")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("x-user-id", userId.toString())
            }

            if (conn.responseCode in 200..299) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }.trim()
                if (responseText.startsWith("{")) {
                    val root = JSONObject(responseText)
                    val arr = root.optJSONArray("ads") ?: root.optJSONArray("campaigns") ?: root.optJSONArray("data")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            parseAdCampaign(obj, userId)?.let { campaigns.add(it) }
                        }
                    }
                } else if (responseText.startsWith("[")) {
                    val arr = JSONArray(responseText)
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        parseAdCampaign(obj, userId)?.let { campaigns.add(it) }
                    }
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        campaigns
    }

    /**
     * POST /api/ads/push
     * Creates or pushes a new ad campaign.
     */
    suspend fun createCampaign(
        userId: Long,
        postId: Long?,
        name: String,
        type: String,
        mediaUrl: String?,
        mediaUrls: List<String>,
        description: String,
        link: String?,
        phone: String?,
        email: String?,
        cta: String,
        location: String,
        budget: Int,
        days: Int
    ): Pair<Boolean, AdCampaign?> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/api/ads/push")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("x-user-id", userId.toString())
            }

            val payload = JSONObject().apply {
                put("advertiser_id", userId)
                if (postId != null && postId > 0L) put("post_id", postId)
                put("campaign_name", name)
                put("name", name)
                put("type", if (type.lowercase() == "video") "video" else "image")
                if (!mediaUrl.isNullOrBlank()) put("media_url", mediaUrl)
                if (mediaUrls.isNotEmpty()) {
                    val arr = JSONArray()
                    mediaUrls.forEach { arr.put(it) }
                    put("media_urls", arr)
                }
                put("description", description)
                put("cta_button", cta)
                put("cta_text", cta)
                if (!link.isNullOrBlank()) put("destination_url", link)
                if (!phone.isNullOrBlank()) put("phone", phone)
                if (!email.isNullOrBlank()) put("email", email)
                put("target_location", location.ifBlank { "Global" })
                put("budget", budget)
                put("days", days)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

            val code = conn.responseCode
            var createdCampaign: AdCampaign? = null
            if (code in 200..299) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }.trim()
                if (responseText.startsWith("{")) {
                    val root = JSONObject(responseText)
                    val adObj = root.optJSONObject("ad") ?: root.optJSONObject("campaign") ?: root
                    createdCampaign = parseAdCampaign(adObj, userId)
                }
            }
            conn.disconnect()
            Pair(code in 200..299, createdCampaign)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, null)
        }
    }

    /**
     * POST /api/ads/pause
     * Action: "pause" or "resume"
     */
    suspend fun pauseOrResumeCampaign(adId: Long, action: String, userId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/api/ads/pause")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("x-user-id", userId.toString())
            }

            val payload = JSONObject().apply {
                put("ad_id", adId)
                put("id", adId)
                put("action", action)
            }
            OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * POST /api/ads/delete
     */
    suspend fun deleteCampaign(adId: Long, userId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/api/ads/delete")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("x-user-id", userId.toString())
            }

            val payload = JSONObject().apply {
                put("ad_id", adId)
                put("id", adId)
            }
            OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun parseFeedAd(obj: JSONObject): SponsoredFeedAd? {
        val id = obj.optLong("id", obj.optLong("ad_id", 0L))
        if (id == 0L) return null

        val postId = obj.optLong("post_id", obj.optLong("postId", 0L)).takeIf { it > 0 }
        val title = obj.optString("title", obj.optString("name", ""))
        val headline = obj.optString("headline", obj.optString("campaign_name", title))
        val rawDescription = if (obj.has("description") && !obj.isNull("description")) obj.optString("description")
            else if (obj.has("content") && !obj.isNull("content")) obj.optString("content")
            else ""
        val description = rawDescription.trim().let { if (it.equals("null", ignoreCase = true) || it.equals("undefined", ignoreCase = true)) "" else it }
        val imageUrl = obj.optString("imageUrl", obj.optString("image_url", obj.optString("media_url", "")))
        val videoUrl = obj.optString("videoUrl", obj.optString("video_url", ""))
        val ctaText = obj.optString("ctaText", obj.optString("cta_button", obj.optString("cta_text", "Learn More")))
        val ctaUrl = obj.optString("ctaUrl", obj.optString("destination_url", obj.optString("cta_url", "")))
        val sponsorName = obj.optString("sponsorName", obj.optString("advertiser_name", obj.optString("name", "Sponsored")))
        val sponsorAvatar = obj.optString("sponsorAvatar", obj.optString("profile_image_url", obj.optString("advertiser_avatar", "")))
        val location = obj.optString("location", obj.optString("target_location", "Global"))
        val brandId = obj.optLong("brandId", obj.optLong("advertiser_id", 0L)).takeIf { it > 0 }
        val isVideo = obj.optString("type", "").equals("video", ignoreCase = true) || !videoUrl.isNullOrBlank()

        return SponsoredFeedAd(
            id = id,
            postId = postId,
            title = title,
            headline = headline,
            description = description,
            imageUrl = imageUrl.ifBlank { null },
            videoUrl = videoUrl.ifBlank { null },
            ctaText = ctaText.ifBlank { "Learn More" },
            ctaUrl = ctaUrl,
            sponsorName = sponsorName,
            sponsorAvatar = sponsorAvatar,
            location = location,
            brandId = brandId,
            isVideo = isVideo
        )
    }

    private fun parseAdCampaign(obj: JSONObject, currentUserId: Long): AdCampaign? {
        val id = obj.optLong("id", 0L)
        if (id == 0L) return null

        val name = obj.optString("name", obj.optString("campaign_name", "Campaign #$id"))
        val type = obj.optString("type", "image")
        val status = obj.optString("status", "active")
        val mediaUrl = obj.optString("mediaUrl", obj.optString("media_url", "")).ifBlank { null }

        val mediaUrls = mutableListOf<String>()
        val arr = obj.optJSONArray("media_urls") ?: obj.optJSONArray("mediaUrls")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val u = arr.optString(i, "")
                if (u.isNotBlank()) mediaUrls.add(u)
            }
        } else if (!mediaUrl.isNullOrBlank()) {
            mediaUrls.add(mediaUrl)
        }

        val rawCampaignDesc = if (obj.has("description") && !obj.isNull("description")) obj.optString("description") else ""
        val description = rawCampaignDesc.trim().let { if (it.equals("null", ignoreCase = true) || it.equals("undefined", ignoreCase = true)) "" else it }
        val cta = obj.optString("cta", obj.optString("cta_button", obj.optString("cta_text", "Learn More")))
        val link = obj.optString("link", obj.optString("destination_url", "")).ifBlank { null }
        val phone = obj.optString("phone", "").ifBlank { null }
        val email = obj.optString("email", "").ifBlank { null }
        val location = obj.optString("location", obj.optString("target_location", "Global"))
        val budget = obj.optInt("budget", obj.optInt("spend", 5))
        val days = obj.optInt("days", 7)
        val startDate = obj.optString("startDate", obj.optString("start_date", "2026-03-01"))
        val endDate = obj.optString("endDate", obj.optString("end_date", "2026-03-08"))

        val analyticsObj = obj.optJSONObject("analytics")
        val impressions = analyticsObj?.optInt("impressions") ?: obj.optInt("impressions", 0)
        val views = analyticsObj?.optInt("views") ?: obj.optInt("views", 0)
        val clicks = analyticsObj?.optInt("clicks") ?: obj.optInt("clicks", 0)
        val postId = obj.optLong("post_id", obj.optLong("postId", 0L)).takeIf { it > 0 }

        return AdCampaign(
            id = id,
            name = name,
            type = type,
            status = status,
            mediaUrl = mediaUrl,
            mediaUrls = mediaUrls,
            description = description,
            cta = cta,
            link = link,
            phone = phone,
            email = email,
            location = location,
            budget = budget,
            days = days,
            startDate = startDate,
            endDate = endDate,
            impressions = impressions,
            views = views,
            clicks = clicks,
            postId = postId
        )
    }
}
