package com.sammwy.retrocraft.server.block;

import java.util.UUID;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.chunk.WorldChunk;

/** Console <-> television binding. Both sides store the link so neither can be left orphaned. */
public final class Televisions {
    public static final int RADIUS = 12;

    /** Nearest free (unbound) television in loaded chunks around the console, or null. */
    public static TvBlockEntity nearestFree(DockedConsoleBlockEntity console) {
        ServerWorld world = console.world();
        BlockPos origin = console.getPos();
        TvBlockEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        ChunkPos min = new ChunkPos(origin.add(-RADIUS, 0, -RADIUS)),
                max = new ChunkPos(origin.add(RADIUS, 0, RADIUS));
        for (int cx = min.x; cx <= max.x; cx++)
            for (int cz = min.z; cz <= max.z; cz++) {
                WorldChunk chunk = world.getChunkManager().getWorldChunk(cx, cz, false);
                if (chunk == null) continue;
                for (var entity : chunk.getBlockEntities().values()) {
                    if (!(entity instanceof TvBlockEntity tv) || !tv.free() || tv.isRemoved())
                        continue;
                    double distance = tv.getPos().getSquaredDistance(origin);
                    if (distance <= RADIUS * RADIUS && distance < bestDistance) {
                        best = tv;
                        bestDistance = distance;
                    }
                }
            }
        return best;
    }

    public static void bind(DockedConsoleBlockEntity console, TvBlockEntity tv, UUID instance) {
        tv.bind(instance, console.getPos());
        console.tv(tv.getPos());
    }

    public static void unbind(DockedConsoleBlockEntity console) {
        BlockPos pos = console.tv();
        if (pos == null) return;
        console.tv(null);
        if (console.getWorld() != null
                && console.getWorld().getBlockEntity(pos) instanceof TvBlockEntity tv
                && console.getPos().equals(tv.console())) tv.unbind();
    }

    private Televisions() {}
}
