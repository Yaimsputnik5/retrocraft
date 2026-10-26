package com.sammwy.retrocraft.server.console;

import java.util.List;

import com.sammwy.retrocraft.cores.worker.HardwareProfile;
import com.sammwy.retrocraft.server.console.component.*;
import com.sammwy.retrocraft.server.item.CartridgeItem;

import net.minecraft.block.Block;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;

/**
 * A piece of hardware. Each console is a class that declares its physical form, the game system it
 * plays (media, ROM formats, cores and BIOS) and the component slots it exposes, and registers its
 * own content. {@code listed} consoles appear in the consoles tab and media tooltips; {@code
 * internalRom} cabinets carry their game baked into the placed block.
 *
 * @see AbstractConsole
 */
public interface ConsoleDefinition {
    Identifier id();

    String name();

    ConsoleType type();

    /** Media item, accepted ROMs, compatible cores and required BIOS. */
    GameSystem system();

    /** Peripheral ports and expansion bays, in menu order. */
    default List<ComponentSlot> componentSlots() {
        return List.of();
    }

    /** Outline footprint of placed consoles; {@code null} for handhelds. */
    default Bounds bounds() {
        return null;
    }

    default boolean listed() {
        return true;
    }

    default boolean internalRom() {
        return false;
    }

    /**
     * Registers everything this console brings: its system media, its components and its own item
     * (plus block and block entity type for placed consoles). Called once by {@link
     * ConsoleRegistry#register}.
     */
    void register();

    /** Outline footprint in model units for a north-facing block. */
    record Bounds(double x0, double z0, double x1, double z1, double height) {
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

    default CartridgeItem cartridge() {
        return ConsoleRegistry.media(system().id());
    }

    default Text displayName() {
        return Text.translatableWithFallback(
                "console." + id().getNamespace() + "." + id().getPath(), name());
    }

    /** Docked consoles without their own screen show their picture on a bound television block. */
    default boolean usesTelevision() {
        return type() == ConsoleType.DOCKED;
    }

    default boolean isArcade() {
        return type() == ConsoleType.ARCADE;
    }

    /**
     * What {@code core} sees for this set of installed components, one entry per slot ({@code null}
     * when empty).
     */
    default HardwareProfile hardware(String core, List<ConsoleComponent> installed) {
        HardwareProfile profile = HardwareProfile.NONE;
        var slots = componentSlots();
        for (int i = 0; i < slots.size(); i++)
            profile =
                    profile.merge(
                            slots.get(i)
                                    .hardware(
                                            core, i < installed.size() ? installed.get(i) : null));
        return profile;
    }

    /**
     * Number of player controllers exposed by the installed peripherals (transport supports four).
     */
    default int controllerPorts(List<ConsoleComponent> installed) {
        return type().isPlaced() ? 4 : 1;
    }

    default boolean links(List<ConsoleComponent> installed) {
        var slots = componentSlots();
        for (int i = 0; i < slots.size() && i < installed.size(); i++)
            if (slots.get(i).accepts(installed.get(i)) && installed.get(i).serialLink())
                return true;
        return false;
    }
}
