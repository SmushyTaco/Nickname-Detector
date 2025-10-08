package com.smushytaco.nickname_detector
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.smushytaco.nickname_detector.mojang_api_parser.MojangApiParser
import com.smushytaco.nickname_detector.mojang_api_parser.UUIDSerializer
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.command.CommandSource
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.projectile.ProjectileUtil
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.hit.HitResult
import org.lwjgl.glfw.GLFW
import java.util.UUID
import kotlin.concurrent.thread
@Environment(EnvType.CLIENT)
object NicknameDetectorClient : ClientModInitializer {
    private const val MOD_ID = "nickname_detector"
    private val KEYBINDING = KeyBinding("key.$MOD_ID.$MOD_ID", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_SEMICOLON, KeyBinding.Category.create(Identifier.of(MOD_ID, "category")))
    private fun nicknameDetector(username: String, clientPlayerEntity: ClientPlayerEntity) {
        thread {
            var uuid: UUID? = null
            try {
                uuid = UUIDSerializer.stringToUUID(username)
            } catch (_: Exception) {}
            val accountInformation = uuid?.let { id -> MojangApiParser.getUsername(id) } ?: MojangApiParser.getUuid(username)
            if (accountInformation == null) {
                clientPlayerEntity.sendMessage(Text.literal("§c${uuid ?: username} §4does not exist!"), false)
                return@thread
            }
            clientPlayerEntity.sendMessage(Text.literal("§b${if (uuid != null) accountInformation.id.toString() else accountInformation.name} §3does exist!"), false)
        }
    }
    override fun onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(KEYBINDING)
        ClientCommandRegistrationCallback.EVENT.register(ClientCommandRegistrationCallback { dispatcher, _ ->
            dispatcher.register(literal("nicknamedetector").executes {
                thread {
                    val nicknamedPlayers = arrayListOf<String>()
                    val normalPlayers = arrayListOf<String>()
                    for (playerName in it.source.playerNames) {
                        val accountInformation = MojangApiParser.getUuid(playerName)
                        if (accountInformation == null) {
                            nicknamedPlayers.add(playerName)
                        } else {
                            normalPlayers.add(accountInformation.name)
                        }
                    }
                    when (normalPlayers.size) {
                        0 -> it.source.player.sendMessage(Text.literal("§4No normal players were detected!"), false)
                        1 -> it.source.player.sendMessage(Text.literal("§b${normalPlayers[0]} §3does exist!"), false)
                        2 -> it.source.player.sendMessage(Text.literal("§b${normalPlayers[0]} §3and §b${normalPlayers[1]} §3do exist!"), false)
                        else -> {
                            val lastElement = normalPlayers.removeLast()
                            val stringBuilder = StringBuilder()
                            for (normalPlayer in normalPlayers) stringBuilder.append("§b$normalPlayer§3, ")
                            it.source.player.sendMessage(Text.literal("${stringBuilder}and §b$lastElement §3do exist!"), false)
                        }
                    }
                    when (nicknamedPlayers.size) {
                        0 -> it.source.player.sendMessage(Text.literal("§3No nicknamed players were detected!"), false)
                        1 -> it.source.player.sendMessage(Text.literal("§c${nicknamedPlayers[0]} §4does not exist!"), false)
                        2 -> it.source.player.sendMessage(Text.literal("§c${nicknamedPlayers[0]} §4and §c${nicknamedPlayers[1]} §4do not exist!"), false)
                        else -> {
                            val lastElement = nicknamedPlayers.removeLast()
                            val stringBuilder = StringBuilder()
                            for (nicknamedPlayer in nicknamedPlayers) stringBuilder.append("§c$nicknamedPlayer§4, ")
                            it.source.player.sendMessage(Text.literal("${stringBuilder}and §c$lastElement §4do not exist!"), false)
                        }
                    }
                }
                return@executes Command.SINGLE_SUCCESS
            }
                .then(ClientCommandManager.argument("username", StringArgumentType.word())
                    .suggests { context, builder ->
                        @Suppress("UNCHECKED_CAST")
                        UsernameSuggestionProvider.getSuggestions(context as CommandContext<CommandSource>, builder)
                    }.executes {
                        nicknameDetector(StringArgumentType.getString(it, "username"), it.source.player)
                        return@executes Command.SINGLE_SUCCESS
                    }))
        })
        ClientTickEvents.START_CLIENT_TICK.register(ClientTickEvents.StartTick {
            while (KEYBINDING.wasPressed()) {
                val player = it.player ?: return@StartTick
                val target = target(it) ?: return@StartTick
                if (target.type != HitResult.Type.ENTITY || target !is EntityHitResult || target.entity !is PlayerEntity) return@StartTick
                val playerToFriend = target.entity as PlayerEntity
                nicknameDetector(playerToFriend.name.string, player)
            }
        })
    }
    private fun target(client: MinecraftClient, range: Double = 250.0, tickDelta: Float = 1.0F): HitResult? {
        val clientCameraEntity = client.cameraEntity ?: return null
        var hitResult = clientCameraEntity.raycast(range, tickDelta, false)
        val rotationVector = clientCameraEntity.getRotationVec(1.0F)
        val positionVector = clientCameraEntity.getCameraPosVec(tickDelta)
        val box = clientCameraEntity.boundingBox.stretch(rotationVector.multiply(range)).expand(1.0, 1.0, 1.0)
        val entityHitResult = ProjectileUtil.raycast(clientCameraEntity, positionVector, positionVector.add(rotationVector.x * range, rotationVector.y * range, rotationVector.z * range), box, { !it.isSpectator && it.canHit() }, range * range)
        if (entityHitResult != null) {
            val distanceFromEntity = positionVector.squaredDistanceTo(entityHitResult.pos)
            if (distanceFromEntity < range * range && (hitResult.type == HitResult.Type.MISS || hitResult.type == HitResult.Type.BLOCK && distanceFromEntity < positionVector.squaredDistanceTo(hitResult.pos))) hitResult = entityHitResult
        }
        return hitResult
    }
}