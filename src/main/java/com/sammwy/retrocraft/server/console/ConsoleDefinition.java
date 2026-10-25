package com.sammwy.retrocraft.server.console;

import com.sammwy.retrocraft.server.item.CartridgeItem;

import net.minecraft.block.Block;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;

/**
 * Registered hardware. {@code listed} consoles appear in the consoles tab and media tooltips;
 * {@code internalRom} cabinets carry their game baked into the placed block.
 */
public record ConsoleDefinition(
        Identifier id,
        String name,
        ConsoleType type,
        CartridgeItem cartridge,
        Bounds bounds,
        boolean listed,
        boolean internalRom) {

    /** Outline footprint in model units for a north-facing block. */
    public record Bounds(double x0, double z0, double x1, double z1, double height) {
        public static final Bounds DEFAULT = new Bounds(1, 2, 15, 14, 5.8);

        public VoxelShape shape(Direction facing) {
            return switch (facing) {
                case EAST -> Block.createCuboidShape(16 - z1, 0, x0, 16 - z0, height, x1);
                case SOUTH ->
                        Block.createCuboidShape(16 - x1, 0, 16 - z1, 16 - x0, height, 16 - z0);
                case WEST -> Block.createCuboidShape(z0, 0, 16 - x1, z1, height, 16 - x0);
                default -> Block.createCuboidShape(x0, 0, z0, x1, height, z1);
            };
        }
    }

    public Text displayName() {
        return Text.translatableWithFallback(
                "console." + id.getNamespace() + "." + id.getPath(), name);
    }

    public GameSystem system() {
        return cartridge.system();
    }

    /** Docked consoles without their own screen show their picture on a bound television block. */
    public boolean usesTelevision() {
        return type == ConsoleType.DOCKED;
    }

    public boolean isArcade() {
        return type == ConsoleType.ARCADE;
    }
}
