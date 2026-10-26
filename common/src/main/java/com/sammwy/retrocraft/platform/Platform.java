package com.sammwy.retrocraft.platform;

import java.nio.file.Path;
import java.util.function.Supplier;

import com.sammwy.retrocraft.server.console.ConsoleHost;
import com.sammwy.retrocraft.server.screen.ConsoleScreenHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Small loader boundary. No loader classes are visible to common code. */
public final class Platform {
    @FunctionalInterface
    public interface BlockEntityFactory<T extends BlockEntity> {
        T create(BlockPos position, BlockState state);
    }

    public interface Backend {
        Path configDir();

        Path gameDir();

        String version();

        <V, T extends V> Supplier<T> register(
                Registry<V> registry, ResourceLocation id, Supplier<T> factory);

        <T extends BlockEntity> BlockEntityType<T> blockEntityType(
                BlockEntityFactory<T> factory, Block... blocks);

        MenuType<ConsoleScreenHandler> consoleMenuType();

        void openConsoleMenu(ServerPlayer player, ConsoleHost host, int lockedSlot);

        CreativeModeTab creativeTab(
                Component title,
                Supplier<ItemStack> icon,
                CreativeModeTab.DisplayItemsGenerator entries);
    }

    private static Backend backend;

    public static void install(Backend implementation) {
        backend = implementation;
    }

    public static Path configDir() {
        return backend == null ? Path.of("config") : backend.configDir();
    }

    public static Path gameDir() {
        return backend == null ? Path.of("") : backend.gameDir();
    }

    public static String version() {
        return backend == null ? "dev" : backend.version();
    }

    public static <V, T extends V> Supplier<T> register(
            Registry<V> registry, ResourceLocation id, Supplier<T> factory) {
        return backend.register(registry, id, factory);
    }

    public static <T extends BlockEntity> BlockEntityType<T> blockEntityType(
            BlockEntityFactory<T> factory, Block... blocks) {
        return backend.blockEntityType(factory, blocks);
    }

    public static MenuType<ConsoleScreenHandler> consoleMenuType() {
        return backend.consoleMenuType();
    }

    public static void openConsoleMenu(ServerPlayer player, ConsoleHost host, int lockedSlot) {
        backend.openConsoleMenu(player, host, lockedSlot);
    }

    public static CreativeModeTab creativeTab(
            Component title,
            Supplier<ItemStack> icon,
            CreativeModeTab.DisplayItemsGenerator entries) {
        return backend.creativeTab(title, icon, entries);
    }

    private Platform() {}
}
