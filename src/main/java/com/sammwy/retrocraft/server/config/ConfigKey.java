package com.sammwy.retrocraft.server.config;

import com.sammwy.retrocraft.cores.KnownCores;
import com.sammwy.retrocraft.server.config.RetrocraftConfig.*;
import com.sammwy.retrocraft.server.console.ConsoleRegistry;
import net.minecraft.registry.Registries;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

/** Typed view over {@link RetrocraftConfig}: one entry per key, shared by /retrocraft config, JSON load and command completion. */
public enum ConfigKey {
    thumbnailIntervalSeconds(c -> Integer.toString(c.thumbnailIntervalSeconds), (c, v) -> c.thumbnailIntervalSeconds = integer(v, 1, 60)),
    thumbnailUpload(c -> Boolean.toString(c.thumbnailUpload), (c, v) -> c.thumbnailUpload = bool(v), () -> Stream.of("true", "false")),
    emulationMode(c -> c.emulationMode.id, (c, v) -> c.emulationMode = EmulationMode.parse(v), () -> Arrays.stream(EmulationMode.values()).map(mode -> mode.id)),
    saveMode(c -> c.saveMode.id, (c, v) -> c.saveMode = SaveMode.parse(v), () -> Arrays.stream(SaveMode.values()).map(mode -> mode.id)),
    streamFps(c -> Integer.toString(c.streamFps), (c, v) -> c.streamFps = integer(v, 1, 20)),
    audioRadius(c -> Integer.toString(c.audioRadius), (c, v) -> c.audioRadius = integer(v, 1, 128)),
    keyframeIntervalSeconds(c -> Integer.toString(c.keyframeIntervalSeconds), (c, v) -> c.keyframeIntervalSeconds = integer(v, 1, 60)),
    autosaveIntervalSeconds(c -> Integer.toString(c.autosaveIntervalSeconds), (c, v) -> c.autosaveIntervalSeconds = integer(v, 5, 3600)),
    maxActiveInstances(c -> Integer.toString(c.maxActiveInstances), (c, v) -> c.maxActiveInstances = integer(v, 1, 64)),
    watcherTimeoutSeconds(c -> Integer.toString(c.watcherTimeoutSeconds), (c, v) -> c.watcherTimeoutSeconds = integer(v, 5, 120)),
    checkpointIntervalSeconds(c -> Integer.toString(c.checkpointIntervalSeconds), (c, v) -> c.checkpointIntervalSeconds = integer(v, 1, 30)),
    transferTimeoutSeconds(c -> Integer.toString(c.transferTimeoutSeconds), (c, v) -> c.transferTimeoutSeconds = integer(v, 10, 120)),
    ownerTimeoutSeconds(c -> Integer.toString(c.ownerTimeoutSeconds), (c, v) -> c.ownerTimeoutSeconds = integer(v, 3, 60)),
    maxRomSizeMiB(c -> Integer.toString(c.maxRomSizeMiB), (c, v) -> c.maxRomSizeMiB = integer(v, 1, 64));

    private final Function<RetrocraftConfig, String> getter;
    private final BiConsumer<RetrocraftConfig, String> setter;
    private final Supplier<Stream<String>> suggestions;
    ConfigKey(Function<RetrocraftConfig, String> getter, BiConsumer<RetrocraftConfig, String> setter) { this(getter, setter, null); }
    ConfigKey(Function<RetrocraftConfig, String> getter, BiConsumer<RetrocraftConfig, String> setter, Supplier<Stream<String>> suggestions) {
        this.getter = getter; this.setter = setter; this.suggestions = suggestions;
    }

    private static int integer(String raw, int min, int max) {
        int value;
        try { value = Integer.parseInt(raw); } catch (NumberFormatException ex) { throw new IllegalArgumentException("Expected an integer between " + min + " and " + max); }
        if (value < min || value > max) throw new IllegalArgumentException("Expected an integer between " + min + " and " + max);
        return value;
    }
    private static boolean bool(String raw) {
        if (!raw.equalsIgnoreCase("true") && !raw.equalsIgnoreCase("false")) throw new IllegalArgumentException("Expected true or false");
        return Boolean.parseBoolean(raw);
    }

    /** All settable keys, including a {@code cores.<cartridge>} entry per registered cartridge. */
    public static Stream<String> keys() {
        Stream<String> scalars = Arrays.stream(values()).map(Enum::name);
        Stream<String> mappings = ConsoleRegistry.cartridges().stream().map(item -> "cores." + Registries.ITEM.getId(item));
        return Stream.concat(scalars, mappings);
    }

    /** Current value for a key, or null when the key does not exist. */
    public static String get(RetrocraftConfig config, String key) {
        ConfigKey scalar = scalar(key);
        if (scalar != null) return scalar.getter.apply(config);
        String cartridge = cartridge(key);
        if (cartridge == null) return null;
        try { return config.coreId(cartridge); } catch (RuntimeException ex) { return null; }
    }

    /** Sets a key; throws {@link IllegalArgumentException} with a user-facing reason on bad input. */
    public static void set(RetrocraftConfig config, String key, String value) {
        ConfigKey scalar = scalar(key);
        if (scalar != null) { scalar.setter.accept(config, value); return; }
        String cartridge = cartridge(key);
        if (cartridge == null) throw new IllegalArgumentException("Unknown config key " + key);
        config.cores.put(cartridge, KnownCores.get(value).id());
    }

    /** Value completions for a key: enum/boolean choices, the current scalar value, or known core ids for cores.*. */
    public static Stream<String> valueSuggestions(RetrocraftConfig config, String key) {
        ConfigKey scalar = scalar(key);
        if (scalar != null)
            return scalar.suggestions != null ? scalar.suggestions.get() : Stream.of(scalar.getter.apply(config));
        return cartridge(key) == null ? Stream.empty() : KnownCores.ids().stream().sorted();
    }

    private static ConfigKey scalar(String key) {
        try { return valueOf(key); } catch (IllegalArgumentException ex) { return null; }
    }
    private static String cartridge(String key) {
        if (!key.startsWith("cores.") || !key.substring(6).contains(":")) return null;
        String cartridge = key.substring(6);
        return ConsoleRegistry.cartridge(net.minecraft.util.Identifier.tryParse(cartridge)) == null ? null : cartridge;
    }
}
