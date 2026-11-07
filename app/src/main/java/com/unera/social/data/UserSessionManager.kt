package com.unera.social.data

import android.content.Context
import com.unera.social.model.User
import org.json.JSONObject

/**
 * UserSessionManager
 *
 * Implements persistent session storage matching Feathered web saveAuth and LS_USER_KEY:
 * - 'user' / 'feathered_user' (serialized User profile)
 * - 'token' / 'feathered_token' (JWT / auth token)
 * - 'user_id' / 'feathered_user_id' (numeric ID)
 */
object UserSessionManager {

    private const val PREFS_NAME = "feathered_prefs"
    private const val LS_USER_KEY = "user"
    private const val LS_TOKEN_KEY = "token"
    private const val LS_USER_ID_KEY = "user_id"

    @Volatile
    private var cachedUser: User? = null

    fun setCurrentUser(user: User?) {
        cachedUser = user
    }

    fun getCurrentUser(): User? = cachedUser

    fun saveAuth(context: Context, token: String?, user: User) {
        if (user.id <= 0L) return
        cachedUser = user
        try {
            val json = JSONObject().apply {
                put("id", user.id)
                put("name", user.name)
                put("username", user.username)
                put("email", user.email)
                put("profile_image_url", user.profileImageUrl)
                put("cover_image_url", user.coverImageUrl)
                put("bio", user.bio)
                put("location", user.location)
                put("work", user.work)
                put("education", user.education)
                put("website", user.website)
                put("followers_count", user.followersCount)
                put("following_count", user.followingCount)
                put("is_verified", if (user.isVerified) 1 else 0)
                put("is_online", user.isOnline)
                put("role", user.role)
                put("birth_date", user.birthDate ?: "")
                put("birthDate", user.birthDate ?: "")
                put("gender", user.gender)
            }
            val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            editor.putString(LS_USER_KEY, json.toString())
            editor.putString("feathered_user", json.toString())
            editor.putLong(LS_USER_ID_KEY, user.id)
            editor.putLong("feathered_user_id", user.id)
            if (!token.isNullOrBlank()) {
                editor.putString(LS_TOKEN_KEY, token)
                editor.putString("feathered_token", token)
            }
            editor.apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveUser(context: Context, user: User) {
        val currentToken = getAuthToken(context)
        saveAuth(context, currentToken, user)
    }

    fun getAuthToken(context: Context): String? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(LS_TOKEN_KEY, prefs.getString("feathered_token", null))
        } catch (e: Exception) {
            null
        }
    }

    fun getUserId(context: Context): Long {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getLong(LS_USER_ID_KEY, prefs.getLong("feathered_user_id", 0L))
        } catch (e: Exception) {
            0L
        }
    }

    fun getSavedUser(context: Context): User? {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(LS_USER_KEY, prefs.getString("feathered_user", null)) ?: return null
            if (jsonStr.isBlank()) return null
            val obj = JSONObject(jsonStr)
            val id = obj.optLong("id", obj.optLong("user_id", 0L))
            if (id <= 0L) return null

            val rawUsername = obj.optString("username").trim()
            val rawName = obj.optString("name").trim()
            val username = if (rawUsername.isNotBlank() && rawUsername != "null") rawUsername else "user$id"
            val name = if (rawName.isNotBlank() && rawName != "null") rawName else username
            val email = obj.optString("email").trim()
            val avatar = obj.optString("profile_image_url").trim().takeIf { it.isNotBlank() && it != "null" }
                ?: obj.optString("avatar").trim().takeIf { it.isNotBlank() && it != "null" }
                ?: ""
            val cover = obj.optString("cover_image_url").trim().takeIf { it.isNotBlank() && it != "null" }
                ?: ""
            val bio = obj.optString("bio").trim().takeIf { it != "null" } ?: ""
            val location = obj.optString("location").trim().takeIf { it != "null" } ?: ""
            val work = obj.optString("work").trim().takeIf { it != "null" } ?: ""
            val education = obj.optString("education").trim().takeIf { it != "null" } ?: ""
            val website = obj.optString("website").trim().takeIf { it != "null" } ?: ""
            val followersCount = obj.optInt("followers_count", obj.optInt("followersCount", 0))
            val followingCount = obj.optInt("following_count", obj.optInt("followingCount", 0))
            val isVerified = obj.optBoolean("is_verified", false) || obj.optInt("is_verified", 0) == 1
            val isOnline = obj.optBoolean("is_online", true)
            val role = obj.optString("role", "user")
            val birthDate = (obj.optString("birth_date").takeIf { it.isNotBlank() && it != "null" }
                ?: obj.optString("birthDate").takeIf { it.isNotBlank() && it != "null" })?.trim()
            val gender = obj.optString("gender", "Female")

            val user = User(
                id = id,
                name = name,
                username = if (username.startsWith("@")) username else "@$username",
                email = email,
                profileImageUrl = avatar,
                coverImageUrl = cover.ifBlank { "https://images.unsplash.com/photo-1579546929518-9e396f3cc809?w=1200" },
                bio = bio,
                location = location,
                work = work,
                education = education,
                website = website,
                followersCount = followersCount,
                followingCount = followingCount,
                isVerified = isVerified,
                isOnline = isOnline,
                role = role,
                birthDate = birthDate,
                gender = gender
            )
            cachedUser = user
            return user
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun clearUser(context: Context) {
        cachedUser = null
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(LS_USER_KEY)
                .remove("feathered_user")
                .remove(LS_TOKEN_KEY)
                .remove("feathered_token")
                .remove(LS_USER_ID_KEY)
                .remove("feathered_user_id")
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
