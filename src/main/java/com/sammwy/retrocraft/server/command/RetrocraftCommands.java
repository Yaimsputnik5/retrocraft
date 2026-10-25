package com.sammwy.retrocraft.server.command;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.sammwy.retrocraft.server.InstanceManager;
import com.sammwy.retrocraft.server.config.ConfigKey;
import com.sammwy.retrocraft.server.config.RetrocraftConfig;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/** {@code /retrocraft} (alias {@code /rc}): gui, scan [onlynews], reload and config. */
public final class RetrocraftCommands {
    private final Supplier<InstanceManager> instances;

    private RetrocraftCommands(Supplier<InstanceManager> instances) {
        this.instances = instances;
    }

    public static void register(Supplier<InstanceManager> instances) {
        var commands = new RetrocraftCommands(instances);
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, access, environment) -> {
                    for (String name : List.of("retrocraft", "rc"))
                        dispatcher.register(commands.tree(name));
                });
    }

    private LiteralArgumentBuilder<ServerCommandSource> tree(String name) {
        return CommandManager.literal(name)
                .then(CommandManager.literal("gui").executes(this::gui))
                .then(
                        CommandManager.literal("scan")
                                .requires(source -> source.hasPermissionLevel(2))
                                .executes(context -> scan(context, false))
                                .then(
                                        CommandManager.literal("onlynews")
                                                .executes(context -> scan(context, true))))
                .then(
                        CommandManager.literal("reload")
                                .requires(source -> source.hasPermissionLevel(2))
                                .executes(this::reload))
                .then(
                        CommandManager.literal("config")
                                .requires(source -> source.hasPermissionLevel(2))
                                .executes(this::listConfig)
                                .then(
                                        CommandManager.argument("key", StringArgumentType.word())
                                                .suggests(
                                                        (context, builder) ->
                                                                suggest(
                                                                        builder,
                                                                        ConfigKey.keys().sorted()))
                                                .executes(this::getConfig)
                                                .then(
                                                        CommandManager.argument(
                                                                        "value",
                                                                        StringArgumentType.string())
                                                                .suggests(this::suggestValues)
                                                                .executes(this::setConfig))));
    }

    private int gui(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        InstanceManager manager = instances.get();
        if (manager == null) return 0;
        manager.openSettings(context.getSource().getPlayerOrThrow());
        return 1;
    }

    private int scan(CommandContext<ServerCommandSource> context, boolean onlyNew) {
        InstanceManager manager = instances.get();
        if (manager == null) return 0;
        if (manager.reloadLibrary(context.getSource(), onlyNew)) return 1;
        context.getSource().sendError(Text.translatable("message.retrocraft.scan_busy"));
        return 0;
    }

    private int reload(CommandContext<ServerCommandSource> context) {
        InstanceManager manager = instances.get();
        if (manager == null) return 0;
        var source = context.getSource();
        if (manager.reloadConfig(source)) {
            source.sendFeedback(() -> Text.literal("Retrocraft config reloaded"), true);
            return 1;
        }
        source.sendError(Text.literal("Retrocraft config reload failed; see log"));
        return 0;
    }

    private int listConfig(CommandContext<ServerCommandSource> context) {
        InstanceManager manager = instances.get();
        if (manager == null) return 0;
        var source = context.getSource();
        ConfigKey.keys()
                .sorted()
                .forEach(
                        key ->
                                source.sendFeedback(
                                        () -> line(key, ConfigKey.get(manager.config(), key)),
                                        false));
        return 1;
    }

    private int getConfig(CommandContext<ServerCommandSource> context) {
        InstanceManager manager = instances.get();
        if (manager == null) return 0;
        String key = StringArgumentType.getString(context, "key");
        String value = ConfigKey.get(manager.config(), key);
        if (value == null) {
            context.getSource().sendError(Text.literal("Unknown config key " + key));
            return 0;
        }
        context.getSource().sendFeedback(() -> line(key, value), false);
        return 1;
    }

    private int setConfig(CommandContext<ServerCommandSource> context) {
        InstanceManager manager = instances.get();
        if (manager == null) return 0;
        String key = StringArgumentType.getString(context, "key");
        String value = StringArgumentType.getString(context, "value");
        try {
            ConfigKey.set(manager.config(), key, value);
            manager.config().save();
            manager.applyConfig();
            context.getSource()
                    .sendFeedback(() -> line(key, ConfigKey.get(manager.config(), key)), true);
            return 1;
        } catch (IllegalArgumentException | IOException ex) {
            context.getSource().sendError(Text.literal(ex.getMessage()));
            return 0;
        }
    }

    private CompletableFuture<Suggestions> suggestValues(
            CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        InstanceManager manager = instances.get();
        String key = StringArgumentType.getString(context, "key");
        return suggest(
                builder,
                ConfigKey.valueSuggestions(
                        manager == null ? new RetrocraftConfig() : manager.config(), key));
    }

    private static CompletableFuture<Suggestions> suggest(
            SuggestionsBuilder builder, Stream<String> candidates) {
        String typed = builder.getRemainingLowerCase();
        candidates.filter(value -> value.toLowerCase().startsWith(typed)).forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static Text line(String key, String value) {
        return Text.literal(key + " = " + value);
    }
}
