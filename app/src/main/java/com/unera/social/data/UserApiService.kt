package com.unera.social.data

import com.unera.social.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class AuthResult(
    val user: User,
    val token: String
)

/**
 * UserApiService
 *
 * Dedicated live network service for authentication, user profile data,
 * People You May Know (suggestions), and follow/unfollow interactions matching
 * Feathered web Auth.tsx and App.tsx.
 */
object UserApiService {

    private const val BASE_URL = "https://featheredsocial.site"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) Feathered/1.0"

    /**
     * GET /api/suggestions?user_id={userId}&limit={limit}
     * Fetches live "People You May Know" suggestions from the database.
     */
    suspend fun fetchSuggestions(userId: Long, limit: Int = 20): List<User> = withContext(Dispatchers.IO) {
        val result = mutableListOf<User>()
        try {
            val url = URL("$BASE_URL/api/suggestions?user_id=$userId&limit=$limit")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("x-user-id", userId.toString())
            }

            if (conn.responseCode in 200..299) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }.trim()
                if (responseText.startsWith("{")) {
                    val root = JSONObject(responseText)
                    val arr = root.optJSONArray("suggestions") ?: root.optJSONArray("data")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            parseSuggestionJson(obj)?.let { result.add(it) }
                        }
                    }
                } else if (responseText.startsWith("[")) {
                    val arr = JSONArray(responseText)
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        parseSuggestionJson(obj)?.let { result.add(it) }
                    }
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        result
    }

    /**
     * GET /api/users?limit={limit}
     * Fetches real registered users from the database.
     */
    suspend fun fetchLiveUsers(limit: Int = 50): List<User> = withContext(Dispatchers.IO) {
        val result = mutableListOf<User>()
        try {
            val url = URL("$BASE_URL/api/users?limit=$limit")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
            }

            if (conn.responseCode in 200..299) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }.trim()
                if (responseText.startsWith("[")) {
                    val arr = JSONArray(responseText)
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        parseUserJson(obj)?.let { result.add(it) }
                    }
                } else if (responseText.startsWith("{")) {
                    val root = JSONObject(responseText)
                    val arr = root.optJSONArray("users") ?: root.optJSONArray("data")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            parseUserJson(obj)?.let { result.add(it) }
                        }
                    }
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        result
    }

    /**
     * POST /api/user-follows
     * Body: { follower_id, following_id }
     */
    suspend fun followUser(followerId: Long, followingId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/api/user-follows")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("x-user-id", followerId.toString())
            }

            val payload = JSONObject().apply {
                put("follower_id", followerId)
                put("following_id", followingId)
            }
            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(payload.toString())
                it.flush()
            }

            val code = conn.responseCode
            conn.disconnect()
            return@withContext code in 200..299
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    /**
     * DELETE /api/user-follows?follower_id={followerId}&following_id={followingId}
     */
    suspend fun unfollowUser(followerId: Long, followingId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/api/user-follows?follower_id=$followerId&following_id=$followingId")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("x-user-id", followerId.toString())
            }

            val code = conn.responseCode
            conn.disconnect()
            return@withContext code in 200..299
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    /**
     * GET /api/user-follows/list?userId={userId}
     * Fetches exact live followers and following user ID arrays.
     */
    suspend fun fetchUserFollowData(userId: Long): FollowDataResult? = withContext(Dispatchers.IO) {
        if (userId <= 0L) return@withContext null
        try {
            val url = URL("$BASE_URL/api/user-follows/list?userId=$userId")
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
                    val followersList = mutableListOf<Long>()
                    val followingList = mutableListOf<Long>()

                    val followersArr = root.optJSONArray("followers")
                    if (followersArr != null) {
                        for (i in 0 until followersArr.length()) {
                            followersList.add(followersArr.optLong(i))
                        }
                    }
                    val followingArr = root.optJSONArray("following")
                    if (followingArr != null) {
                        for (i in 0 until followingArr.length()) {
                            followingList.add(followingArr.optLong(i))
                        }
                    }
                    conn.disconnect()
                    return@withContext FollowDataResult(
                        userId = root.optLong("userId", userId),
                        followers = followersList,
                        following = followingList
                    )
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    /**
     * POST /api/users/login
     * Body: { email, password }
     * Matches web Auth.tsx apiLogin verbatim:
     * 1. POST /api/users/login
     * 2. Returns { success, token, user }
     * 3. Hydrates user with fresh profile via GET /api/users?id=<id>
     */
    suspend fun loginUser(email: String, pass: String): Result<AuthResult> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/api/users/login")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
            }

            val cleanEmail = email.trim().lowercase()
            val cleanPass = pass.trim()
            val payload = JSONObject().apply {
                put("email", cleanEmail)
                put("password", cleanPass)
            }
            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(payload.toString())
                it.flush()
            }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val responseText = BufferedReader(InputStreamReader(stream)).use { it.readText() }.trim()
            conn.disconnect()

            if (responseCode !in 200..299) {
                val errorMsg = try {
                    val root = JSONObject(responseText)
                    root.optString("error", root.optString("message", "Invalid email or password."))
                } catch (e: Exception) {
                    if (responseCode == 401 || responseCode == 403) "Invalid email or password." else "Login failed (code $responseCode)"
                }
                return@withContext Result.failure(Exception(errorMsg))
            }

            val root = JSONObject(responseText)
            val token = root.optString("token", "").trim()
            val userObj = root.optJSONObject("user")
                ?: return@withContext Result.failure(Exception("Login failed: user data missing"))

            val normalized = parseUserJson(userObj)
                ?: return@withContext Result.failure(Exception("Login failed: invalid user ID"))

            if (normalized.id <= 0L) {
                return@withContext Result.failure(Exception("Login failed: invalid user ID"))
            }

            // Freshness fetch matching App.tsx: apiFetch(`/api/users?id=${normalized.id}`)
            var finalUser = normalized
            try {
                val fresh = fetchUserById(normalized.id, token)
                if (fresh != null) {
                    finalUser = normalized.copy(
                        name = fresh.name.ifBlank { normalized.name },
                        username = fresh.username.ifBlank { normalized.username },
                        email = if (fresh.email.isNotBlank()) fresh.email else normalized.email,
                        profileImageUrl = fresh.profileImageUrl.ifBlank { normalized.profileImageUrl },
                        coverImageUrl = fresh.coverImageUrl.ifBlank { normalized.coverImageUrl },
                        bio = fresh.bio.ifBlank { normalized.bio },
                        location = fresh.location.ifBlank { normalized.location },
                        work = fresh.work.ifBlank { normalized.work },
                        education = fresh.education.ifBlank { normalized.education },
                        website = fresh.website.ifBlank { normalized.website },
                        isVerified = fresh.isVerified || normalized.isVerified,
                        role = fresh.role.ifBlank { normalized.role },
                        followersCount = if (fresh.followersCount > 0) fresh.followersCount else normalized.followersCount,
                        followingCount = if (fresh.followingCount > 0) fresh.followingCount else normalized.followingCount
                    )
                }
            } catch (e: Exception) {
                // Non-fatal
            }

            Result.success(AuthResult(user = finalUser, token = token))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * POST /api/users/signup
     * Body: { username, email, password, nationality, location, birth_date, gender }
     * Matches web Auth.tsx apiSignup verbatim:
     * 1. POST /api/users/signup
     * 2. Returns { success, token, user }
     */
    suspend fun signupUser(
        username: String,
        email: String,
        pass: String,
        nationality: String = "Tanzania",
        location: String = "Dar es Salaam",
        birthDate: String = "",
        gender: String = "Female"
    ): Result<AuthResult> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/api/users/signup")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
            }

            val cleanEmail = email.trim().lowercase()
            val cleanPass = pass.trim()
            val payload = JSONObject().apply {
                put("username", username.trim())
                put("email", cleanEmail)
                put("password", cleanPass)
                if (nationality.isNotBlank()) put("nationality", nationality.trim())
                if (location.isNotBlank()) put("location", location.trim())
                if (birthDate.isNotBlank()) put("birth_date", birthDate.trim())
                if (gender.isNotBlank()) put("gender", gender.trim())
            }
            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(payload.toString())
                it.flush()
            }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val responseText = BufferedReader(InputStreamReader(stream)).use { it.readText() }.trim()
            conn.disconnect()

            if (responseCode !in 200..299) {
                val errorMsg = try {
                    val root = JSONObject(responseText)
                    root.optString("error", root.optString("message", "Signup failed (code $responseCode)"))
                } catch (e: Exception) {
                    "Signup failed (code $responseCode)"
                }
                return@withContext Result.failure(Exception(errorMsg))
            }

            val root = JSONObject(responseText)
            val token = root.optString("token", "").trim()
            val userObj = root.optJSONObject("user")
                ?: return@withContext Result.failure(Exception("Signup failed: user data missing"))

            val normalized = parseUserJson(userObj)
                ?: return@withContext Result.failure(Exception("Signup failed: invalid user ID"))

            if (normalized.id <= 0L) {
                return@withContext Result.failure(Exception("Signup failed: invalid user ID"))
            }

            Result.success(AuthResult(user = normalized, token = token))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * GET /api/auth/user/me
     * Matches ENDPOINT 4: Used at app launch to refresh stored token.
     * Headers: Authorization: Bearer <token>
     */
    suspend fun fetchCurrentUser(token: String): User? = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext null
        try {
            val url = URL("$BASE_URL/api/auth/user/me")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("User-Agent", USER_AGENT)
            }

            if (conn.responseCode in 200..299) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }.trim()
                conn.disconnect()
                if (responseText.startsWith("{")) {
                    val root = JSONObject(responseText)
                    val userObj = root.optJSONObject("user") ?: root
                    return@withContext parseUserJson(userObj)
                }
            } else {
                conn.disconnect()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    /**
     * GET /api/users?id={userId}
     * Matches ENDPOINT 3: Fetches fresh user profile by ID.
     */
    suspend fun fetchUserById(userId: Long, token: String? = null): User? = withContext(Dispatchers.IO) {
        if (userId <= 0L) return@withContext null
        try {
            val url = URL("$BASE_URL/api/users?id=$userId")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("x-user-id", userId.toString())
                if (!token.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
            }

            if (conn.responseCode in 200..299) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }.trim()
                conn.disconnect()
                if (responseText.startsWith("{")) {
                    val root = JSONObject(responseText)
                    val userObj = root.optJSONObject("user") ?: root
                    return@withContext parseUserJson(userObj)
                } else if (responseText.startsWith("[")) {
                    val arr = JSONArray(responseText)
                    if (arr.length() > 0) {
                        val first = arr.optJSONObject(0) ?: return@withContext null
                        return@withContext parseUserJson(first)
                    }
                }
            } else {
                conn.disconnect()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    /**
     * PUT /api/users
     * Updates user details including cover_image_url, profile_image_url, bio, etc.
     */
    suspend fun updateUserDetails(userId: Long, data: Map<String, Any?>): Boolean = withContext(Dispatchers.IO) {
        if (userId <= 0L) return@withContext false
        try {
            val url = URL("$BASE_URL/api/users")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("x-user-id", userId.toString())
            }

            val payload = JSONObject().apply {
                put("id", userId)
                data.forEach { (k, v) ->
                    if (v != null) put(k, v)
                }
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(payload.toString())
                it.flush()
            }

            val code = conn.responseCode
            conn.disconnect()
            return@withContext code in 200..299
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    fun parseSuggestionJson(obj: JSONObject): User? {
        val id = obj.optLong("id", obj.optLong("user_id", 0L))
        if (id <= 0L) return null

        val rawUsername = obj.optString("username").trim()
        val rawName = obj.optString("name").trim()
        val username = if (rawUsername.isNotBlank() && rawUsername != "null") rawUsername else "user$id"
        val displayName = if (rawName.isNotBlank() && rawName != "null") rawName else username

        val avatar = obj.optString("profile_image_url").trim().takeIf { it.isNotBlank() && it != "null" }
            ?: obj.optString("avatar").trim().takeIf { it.isNotBlank() && it != "null" }
            ?: ""

        val isVerified = obj.optBoolean("is_verified", false) || obj.optInt("is_verified", 0) == 1
        val role = obj.optString("role", "user")
        val isFollowing = obj.optBoolean("is_following", false)

        return User(
            id = id,
            name = displayName,
            username = if (username.startsWith("@")) username else "@$username",
            profileImageUrl = avatar,
            isVerified = isVerified,
            role = role,
            isFollowing = isFollowing
        )
    }

    fun parseUserJson(obj: JSONObject): User? {
        val id = obj.optLong("id", obj.optLong("user_id", 0L))
        if (id <= 0L) return null

        val rawUsername = obj.optString("username").trim()
        val rawName = obj.optString("name").trim()
        val username = if (rawUsername.isNotBlank() && rawUsername != "null") rawUsername else "user$id"
        val displayName = if (rawName.isNotBlank() && rawName != "null") rawName else username

        val avatar = obj.optString("profile_image_url").trim().takeIf { it.isNotBlank() && it != "null" }
            ?: obj.optString("avatar").trim().takeIf { it.isNotBlank() && it != "null" }
            ?: ""

        val cover = obj.optString("cover_image_url").trim().takeIf { it.isNotBlank() && it != "null" }
            ?: "https://images.unsplash.com/photo-1579546929518-9e396f3cc809?w=1200"

        val bio = obj.optString("bio").trim().takeIf { it.isNotBlank() && it != "null" } ?: ""
        val location = obj.optString("location").trim().takeIf { it.isNotBlank() && it != "null" } ?: ""
        val email = obj.optString("email").trim().takeIf { it.isNotBlank() && it != "null" } ?: ""
        val work = obj.optString("work").trim().takeIf { it.isNotBlank() && it != "null" } ?: ""
        val education = obj.optString("education").trim().takeIf { it.isNotBlank() && it != "null" } ?: ""
        val website = obj.optString("website").trim().takeIf { it.isNotBlank() && it != "null" } ?: ""

        val followersCount = when {
            obj.has("followers") && obj.optJSONArray("followers") != null -> obj.optJSONArray("followers")!!.length()
            else -> obj.optInt("followers_count", obj.optInt("followersCount", 0))
        }
        val followingCount = when {
            obj.has("following") && obj.optJSONArray("following") != null -> obj.optJSONArray("following")!!.length()
            else -> obj.optInt("following_count", obj.optInt("followingCount", 0))
        }

        val isVerified = obj.optBoolean("is_verified", false) || obj.optInt("is_verified", 0) == 1
        val role = obj.optString("role", "user")
        val isFollowing = obj.optBoolean("is_following", false)
        val birthDate = (obj.optString("birth_date").takeIf { it.isNotBlank() && it != "null" }
            ?: obj.optString("birthDate").takeIf { it.isNotBlank() && it != "null" })?.trim()
        val gender = obj.optString("gender", "Female")

        return User(
            id = id,
            name = displayName,
            username = if (username.startsWith("@")) username else "@$username",
            email = email,
            profileImageUrl = avatar,
            coverImageUrl = cover,
            bio = bio,
            location = location,
            work = work,
            education = education,
            website = website,
            followersCount = followersCount,
            followingCount = followingCount,
            isVerified = isVerified,
            isOnline = true,
            birthDate = birthDate,
            gender = gender,
            role = role,
            isFollowing = isFollowing
        )
    }
}

data class FollowDataResult(
    val userId: Long,
    val followers: List<Long>,
    val following: List<Long>
)
