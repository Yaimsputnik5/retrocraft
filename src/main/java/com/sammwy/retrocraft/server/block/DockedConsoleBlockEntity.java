package com.sammwy.retrocraft.server.block;

import java.util.UUID;

import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.server.console.*;
import com.sammwy.retrocraft.server.item.ArcadeItem;
import com.sammwy.retrocraft.server.item.CartridgeItem;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

public final class DockedConsoleBlockEntity extends BlockEntity implements ConsoleHost {
    private ItemStack cartridge = ItemStack.EMPTY;
    private UUID instance;
    private BlockPos tv;

    public DockedConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(((DockedConsoleBlock) state.getBlock()).entityType(), pos, state);
    }

    @Override
    public ConsoleDefinition definition() {
        return ((DockedConsoleBlock) getCachedState().getBlock()).definition();
    }

    @Override
    public ItemStack cartridge() {
        return cartridge;
    }

    @Override
    public void cartridge(ItemStack stack) {
        if (hasInternalRom()) return;
        cartridge = stack;
        changed();
    }

    public void installInternalRom(ItemStack stack) {
        if (!hasInternalRom()
                || !cartridge.isEmpty()
                || !stack.isOf(definition().cartridge())
                || CartridgeItem.gameId(stack).isEmpty()) return;
        cartridge = stack.copyWithCount(1);
        changed();
    }

    public ItemStack arcadeStack() {
        return ((ArcadeItem) getCachedState().getBlock().asItem())
                .game(CartridgeItem.gameId(cartridge), cartridge.getName().getString());
    }

    @Override
    public UUID instance() {
        return instance;
    }

    @Override
    public void instance(UUID id) {
        instance = id;
        changed();
    }

    public BlockPos tv() {
        return tv;
    }

    public void tv(BlockPos pos) {
        tv = pos;
        changed();
    }

    @Override
    public Vec3d soundPosition() {
        if (tv != null && world != null && world.getBlockEntity(tv) instanceof TvBlockEntity set)
            return Vec3d.ofCenter(tv).add(0, set.size().height / 32.0, 0);
        return position();
    }

    private void changed() {
        markDirty();
        if (world == null || world.isClient) return;
        BlockState before = getCachedState(),
                after = before.with(DockedConsoleBlock.POWERED, instance != null);
        if (!before.equals(after)) world.setBlockState(pos, after, Block.NOTIFY_LISTENERS);
        else world.updateListeners(pos, before, after, Block.NOTIFY_LISTENERS);
    }

    @Override
    public BlockEntityUpdateS2CPacket toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }

    @Override
    public ServerWorld world() {
        return (ServerWorld) world;
    }

    @Override
    public Vec3d position() {
        return pos.toCenterPos();
    }

    @Override
    public boolean valid() {
        return !isRemoved()
                && world instanceof ServerWorld
                && world.isChunkLoaded(pos)
                && world.getBlockEntity(pos) == this;
    }

    @Override
    public boolean canAccess(ServerPlayerEntity player) {
        return valid() && player.getWorld() == world && player.squaredDistanceTo(position()) <= 64;
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.put(
                hasInternalRom() ? "InternalRom" : "Cartridge",
                cartridge.encodeAllowEmpty(registries));
        if (instance != null) nbt.putUuid("InstanceID", instance);
        if (tv != null) nbt.putLong("Tv", tv.asLong());
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        cartridge =
                ItemStack.fromNbtOrEmpty(
                        registries,
                        nbt.getCompound(hasInternalRom() ? "InternalRom" : "Cartridge"));
        instance = nbt.containsUuid("InstanceID") ? nbt.getUuid("InstanceID") : null;
        tv = nbt.contains("Tv", NbtElement.LONG_TYPE) ? BlockPos.fromLong(nbt.getLong("Tv")) : null;
    }

    public static void tick(
            World world, BlockPos pos, BlockState state, DockedConsoleBlockEntity console) {
        if (state.get(DockedConsoleBlock.POWERED) != (console.instance != null)) console.changed();
        if (world.getTime() % 20 == 0 && console.instance != null && Retrocraft.instances() != null)
            Retrocraft.instances().discover(console);
    }
}
