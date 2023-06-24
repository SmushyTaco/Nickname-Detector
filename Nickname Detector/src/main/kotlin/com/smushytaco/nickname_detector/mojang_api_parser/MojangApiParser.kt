package com.smushytaco.nickname_detector.mojang_api_parser
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.*
object MojangApiParser {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private fun executeRequestForGets(request: Request): NameAndUUID? {
        return try {
            client.newCall(request).execute().use { response -> response.body?.string()?.let { json.decodeFromString(NameAndUUID.serializer(), it) } }
        } catch (e: Exception) { null }
    }
    fun getUuid(name: String): NameAndUUID? {
        val request = Request.Builder().url("https://api.mojang.com/users/profiles/minecraft/$name/").build()
        return executeRequestForGets(request)
    }
    fun getUsername(uuid: UUID): NameAndUUID? {
        val request = Request.Builder().url("https://sessionserver.mojang.com/session/minecraft/profile/${uuid.toString().replace("-", "")}/").build()
        return executeRequestForGets(request)
    }
}