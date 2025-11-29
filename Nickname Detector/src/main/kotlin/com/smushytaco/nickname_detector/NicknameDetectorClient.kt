package com.smushytaco.nickname_detector
import com.mojang.blaze3d.platform.InputConstants
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
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import org.lwjgl.glfw.GLFW
import java.util.*
import kotlin.concurrent.thread
@Environment(EnvType.CLIENT)
object NicknameDetectorClient : ClientModInitializer {
    private const val MOD_ID = "nickname_detector"
    private val KEYBINDING = KeyMapping("key.$MOD_ID.$MOD_ID", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_SEMICOLON, KeyMapping.Category.register(
        ResourceLocation.fromNamespaceAndPath(MOD_ID, "category")))
    private fun nicknameDetector(username: String, clientPlayerEntity: LocalPlayer) {
        val minecraftClient = Minecraft.getInstance()
        thread {
            var uuid: UUID? = null
            try {
                uuid = UUIDSerializer.stringToUUID(username)
            } catch (_: Exception) {}
            val accountInformation = uuid?.let { id -> MojangApiParser.getUsername(id) } ?: MojangApiParser.getUuid(username)
            if (accountInformation == null) {
                minecraftClient.execute {
                    clientPlayerEntity.displayClientMessage(Component.literal("§c${uuid ?: username} §4does not exist!"), false)
                }
                return@thread
            }
            minecraftClient.execute {
                clientPlayerEntity.displayClientMessage(Component.literal("§b${if (uuid != null) accountInformation.id.toString() else accountInformation.name} §3does exist!"), false)
            }
        }
    }
    override fun onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(KEYBINDING)
        ClientCommandRegistrationCallback.EVENT.register(ClientCommandRegistrationCallback { dispatcher, _ ->
            dispatcher.register(literal("nicknamedetector").executes {
                val minecraftClient = Minecraft.getInstance()
                val playerNames = it.source.onlinePlayerNames
                thread {
                    val nicknamedPlayers = arrayListOf<String>()
                    val normalPlayers = arrayListOf<String>()
                    for (playerName in playerNames) {
                        val accountInformation = MojangApiParser.getUuid(playerName)
                        if (accountInformation == null) {
                            nicknamedPlayers.add(playerName)
                        } else {
                            normalPlayers.add(accountInformation.name)
                        }
                    }
                    minecraftClient.execute {
                        when (normalPlayers.size) {
                            0 -> it.source.player.displayClientMessage(Component.literal("§4No normal players were detected!"), false)
                            1 -> it.source.player.displayClientMessage(Component.literal("§b${normalPlayers[0]} §3does exist!"), false)
                            2 -> it.source.player.displayClientMessage(Component.literal("§b${normalPlayers[0]} §3and §b${normalPlayers[1]} §3do exist!"), false)
                            else -> {
                                val lastElement = normalPlayers.removeLast()
                                val stringBuilder = StringBuilder()
                                for (normalPlayer in normalPlayers) stringBuilder.append("§b$normalPlayer§3, ")
                                it.source.player.displayClientMessage(Component.literal("${stringBuilder}and §b$lastElement §3do exist!"), false)
                            }
                        }
                        when (nicknamedPlayers.size) {
                            0 -> it.source.player.displayClientMessage(Component.literal("§3No nicknamed players were detected!"), false)
                            1 -> it.source.player.displayClientMessage(Component.literal("§c${nicknamedPlayers[0]} §4does not exist!"), false)
                            2 -> it.source.player.displayClientMessage(Component.literal("§c${nicknamedPlayers[0]} §4and §c${nicknamedPlayers[1]} §4do not exist!"), false)
                            else -> {
                                val lastElement = nicknamedPlayers.removeLast()
                                val stringBuilder = StringBuilder()
                                for (nicknamedPlayer in nicknamedPlayers) stringBuilder.append("§c$nicknamedPlayer§4, ")
                                it.source.player.displayClientMessage(Component.literal("${stringBuilder}and §c$lastElement §4do not exist!"), false)
                            }
                        }
                    }
                }
                return@executes Command.SINGLE_SUCCESS
            }
                .then(ClientCommandManager.argument("username", StringArgumentType.word())
                    .suggests { context, builder ->
                        @Suppress("UNCHECKED_CAST")
                        UsernameSuggestionProvider.getSuggestions(context as CommandContext<SharedSuggestionProvider>, builder)
                    }.executes {
                        nicknameDetector(StringArgumentType.getString(it, "username"), it.source.player)
                        return@executes Command.SINGLE_SUCCESS
                    }))
        })
        ClientTickEvents.START_CLIENT_TICK.register(ClientTickEvents.StartTick {
            while (KEYBINDING.consumeClick()) {
                val player = it.player ?: return@StartTick
                val target = target(it) ?: return@StartTick
                if (target.type != HitResult.Type.ENTITY || target !is EntityHitResult || target.entity !is Player) return@StartTick
                val playerToFriend = target.entity as Player
                nicknameDetector(playerToFriend.name.string, player)
            }
        })
    }
    private fun target(client: Minecraft, range: Double = 250.0, tickDelta: Float = 1.0F): HitResult? {
        val clientCameraEntity = client.cameraEntity ?: return null
        var hitResult = clientCameraEntity.pick(range, tickDelta, false)
        val rotationVector = clientCameraEntity.getViewVector(1.0F)
        val positionVector = clientCameraEntity.getEyePosition(tickDelta)
        val box = clientCameraEntity.boundingBox.expandTowards(rotationVector.scale(range)).inflate(1.0, 1.0, 1.0)
        val entityHitResult = ProjectileUtil.getEntityHitResult(clientCameraEntity, positionVector, positionVector.add(rotationVector.x * range, rotationVector.y * range, rotationVector.z * range), box, { !it.isSpectator && it.isPickable }, range * range)
        if (entityHitResult != null) {
            val distanceFromEntity = positionVector.distanceToSqr(entityHitResult.location)
            if (distanceFromEntity < range * range && (hitResult.type == HitResult.Type.MISS || hitResult.type == HitResult.Type.BLOCK && distanceFromEntity < positionVector.distanceToSqr(hitResult.location))) hitResult = entityHitResult
        }
        return hitResult
    }
}