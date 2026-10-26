package com.sammwy.retrocraft.server.block;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/** Console <-> television binding. Both sides store the link so neither can be left orphaned. */
public final class Televisions {
    public static final int RADIUS = 12;

    /** Nearest free (unbound) television in loaded chunks around the console, or null. */
    public static TvBlockEntity nearestFree(DockedConsoleBlockEntity console) {
        ServerLevel world = console.world();
        BlockPos origin = console.getBlockPos();
        TvBlockEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        ChunkPos min = new ChunkPos(origin.offset(-RADIUS, 0, -RADIUS)),
                max = new ChunkPos(origin.offset(RADIUS, 0, RADIUS));
        for (int cx = min.x; cx <= max.x; cx++)
            for (int cz = min.z; cz <= max.z; cz++) {
                LevelChunk chunk = world.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) continue;
                for (var entity : chunk.getBlockEntities().values()) {
                    if (!(entity instanceof TvBlockEntity tv) || !tv.free() || tv.isRemoved())
                        continue;
                    double distance = tv.getBlockPos().distSqr(origin);
                    if (distance <= RADIUS * RADIUS && distance < bestDistance) {
                        best = tv;
                        bestDistance = distance;
                    }
                }
            }
        return best;
    }

    public static void bind(DockedConsoleBlockEntity console, TvBlockEntity tv, UUID instance) {
        tv.bind(instance, console.getBlockPos());
        console.tv(tv.getBlockPos());
    }

    public static void unbind(DockedConsoleBlockEntity console) {
        BlockPos pos = console.tv();
        if (pos == null) return;
        console.tv(null);
        if (console.getLevel() != null
                && console.getLevel().getBlockEntity(pos) instanceof TvBlockEntity tv
                && console.getBlockPos().equals(tv.console())) tv.unbind();
    }

    private Televisions() {}
}
