package com.smushytaco.nickname_detector.mojang_api_parser
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.*
object MojangApiParser {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private fun executeRequestForGets(request: Request) = runCatching { client.newCall(request).execute().use { response -> json.decodeFromString(NameAndUUID.serializer(), response.body.string()) } }.getOrNull()
    fun getUuid(name: String) = executeRequestForGets(Request(url = "https://api.mojang.com/users/profiles/minecraft/$name/".toHttpUrl()))
    fun getUsername(uuid: UUID) = executeRequestForGets(Request(url = "https://sessionserver.mojang.com/session/minecraft/profile/${uuid.toString().replace("-", "")}/".toHttpUrl()))
}