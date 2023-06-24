package com.smushytaco.nickname_detector.mojang_api_parser
import kotlinx.serialization.Serializable
import java.util.*
@Serializable
data class NameAndUUID(val name: String, @Serializable(with = UUIDSerializer::class) val id: UUID)