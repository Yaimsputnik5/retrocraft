package com.sammwy.retrocraft.server.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.*;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.UUID;

/** Holds the emulation instance currently shown on this set and the console block it is bound to. */
public final class TvBlockEntity extends BlockEntity {
    private UUID instance;
    private BlockPos console;
    public TvBlockEntity(BlockPos pos, BlockState state) { super(((TvBlock) state.getBlock()).entityType(), pos, state); }
    public TvBlock.Size size() { return ((TvBlock) getCachedState().getBlock()).size; }
    public UUID instance() { return instance; }
    public BlockPos console() { return console; }
    public boolean free() { return instance == null; }

    public void bind(UUID id, BlockPos consolePos) { instance = id; console = consolePos; changed(); }
    public void unbind() { instance = null; console = null; changed(); }

    private void changed() {
        markDirty();
        if (world == null || world.isClient) return;
        BlockState before = getCachedState(), after = before.with(TvBlock.POWERED, instance != null);
        if (!before.equals(after)) world.setBlockState(pos, after, Block.NOTIFY_LISTENERS);
        else world.updateListeners(pos, before, after, Block.NOTIFY_LISTENERS);
    }

    @Override public BlockEntityUpdateS2CPacket toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }
    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) { return createNbt(registries); }
    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        if (instance != null) nbt.putUuid("InstanceID", instance);
        if (console != null) nbt.putLong("Console", console.asLong());
    }
    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        instance = nbt.containsUuid("InstanceID") ? nbt.getUuid("InstanceID") : null;
        console = nbt.contains("Console", NbtElement.LONG_TYPE) ? BlockPos.fromLong(nbt.getLong("Console")) : null;
    }

    /** Drops a binding whose console vanished or moved on to another instance, so the set never stays orphaned. */
    public static void tick(World world, BlockPos pos, BlockState state, TvBlockEntity tv) {
        if (state.get(TvBlock.POWERED) != (tv.instance != null)) tv.changed();
        if (world.getTime() % 40 != 0 || tv.instance == null) return;
        if (tv.console == null || !world.isChunkLoaded(tv.console)) return;
        if (!(world.getBlockEntity(tv.console) instanceof DockedConsoleBlockEntity console) || !tv.instance.equals(console.instance()) || !pos.equals(console.tv())) tv.unbind();
    }
}
