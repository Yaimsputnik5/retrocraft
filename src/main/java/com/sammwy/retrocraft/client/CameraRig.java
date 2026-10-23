package com.sammwy.retrocraft.client;

import com.sammwy.retrocraft.server.block.*;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.*;

/**
 * While an in-world play screen is open, glides the camera to a spot in front of the television or
 * cabinet glass, looking straight at it, and glides back to the player when the screen closes.
 */
final class CameraRig {
    private static BlockPos screen;
    private static boolean active;
    private static float blend;
    private static long last;

    static void focus(BlockPos pos) {
        screen = pos;
        active = true;
        MinecraftClient.getInstance().gameRenderer.setRenderHand(false);
    }

    static void release() {
        active = false;
    }

    static void reset() {
        active = false;
        blend = 0;
        last = 0;
        screen = null;
        MinecraftClient.getInstance().gameRenderer.setRenderHand(true);
    }

    /**
     * Camera pose for this frame, or null when the rig is idle. Returns {x, y, z, yaw, pitch,
     * blend}.
     */
    static double[] pose(Vec3d from, float yaw, float pitch) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(.1f, (now - last) / 1_000_000_000f);
        last = now;
        blend = MathHelper.clamp(blend + (active ? dt : -dt) / .45f, 0, 1);
        MinecraftClient client = MinecraftClient.getInstance();
        Target target = screen == null || client.world == null ? null : target(client);
        if (blend <= 0 || target == null) {
            if (!active && screen != null) {
                client.gameRenderer.setRenderHand(true);
                screen = null;
            }
            return null;
        }
        double t = blend * blend * (3 - 2 * blend);
        float dyaw = MathHelper.wrapDegrees(target.yaw - yaw);
        return new double[] {
            MathHelper.lerp(t, from.x, target.pos.x),
            MathHelper.lerp(t, from.y, target.pos.y),
            MathHelper.lerp(t, from.z, target.pos.z),
            yaw + dyaw * t,
            MathHelper.lerp(t, pitch, target.pitch),
            t
        };
    }

    private record Target(Vec3d pos, float yaw, float pitch) {}

    /**
     * Screen glass centre and size from the block at {@link #screen}; the camera backs off just
     * enough to frame it.
     */
    private static Target target(MinecraftClient client) {
        BlockState state = client.world.getBlockState(screen);
        float x0, x1, y0, y1, z;
        Direction facing;
        float tilt = 0;
        if (state.getBlock() instanceof TvBlock tv) {
            TvBlock.Size size = tv.size;
            facing = state.get(TvBlock.FACING);
            x0 = size.screenX0;
            x1 = size.screenX1;
            y0 = size.screenY0;
            y1 = size.screenY1;
            z = size.screenZ;
        } else if (state.getBlock() instanceof DockedConsoleBlock block
                && block.definition().isArcade()) {
            facing = state.get(DockedConsoleBlock.FACING);
            x0 = ArcadeCabinet.SCREEN_X0;
            x1 = ArcadeCabinet.SCREEN_X1;
            y0 = ArcadeCabinet.SCREEN_Y0;
            y1 = ArcadeCabinet.SCREEN_Y1;
            z = ArcadeCabinet.SCREEN_Z;
            tilt = ArcadeCabinet.SCREEN_TILT;
        } else return null;
        double angle = Math.toRadians(tilt), cosine = Math.cos(angle), sine = Math.sin(angle);
        Vec3d horizontal = Vec3d.of(facing.getVector());
        Vec3d normal = horizontal.multiply(cosine).add(0, sine, 0);
        double halfHeight = (y1 - y0) / 2.0;
        Vec3d centre =
                Vec3d.ofCenter(screen)
                        .add(0, (y0 + halfHeight * cosine) / 16.0 - .5, 0)
                        .add(horizontal.multiply(.5 - (z + halfHeight * sine) / 16.0));
        double width = (x1 - x0) / 16.0, height = (y1 - y0) / 16.0;
        double fov = Math.toRadians(client.options.getFov().getValue()),
                aspect =
                        (double) client.getWindow().getFramebufferWidth()
                                / client.getWindow().getFramebufferHeight();
        double distance =
                Math.max(
                        1,
                        Math.max(
                                        height / (2 * Math.tan(fov / 2)),
                                        width / (2 * Math.tan(fov / 2) * aspect))
                                * 1.15);
        return new Target(
                centre.add(normal.multiply(distance)), facing.getOpposite().asRotation(), tilt);
    }

    private CameraRig() {}
}
