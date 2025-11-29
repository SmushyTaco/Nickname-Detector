package com.smushytaco.nickname_detector
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.commands.SharedSuggestionProvider
import java.util.concurrent.CompletableFuture
object UsernameSuggestionProvider: SuggestionProvider<SharedSuggestionProvider> {
    override fun getSuggestions(context: CommandContext<SharedSuggestionProvider>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        for (playerName in context.source.onlinePlayerNames) if (playerName.contains(context.input.split(' ').last(), true)) builder.suggest(playerName)
        return builder.buildFuture()
    }
}