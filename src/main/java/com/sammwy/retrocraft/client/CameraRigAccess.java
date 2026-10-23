package com.sammwy.retrocraft.client;

import net.minecraft.util.math.Vec3d;

/** Public bridge for the camera mixin (the rig itself is package-private). */
public final class CameraRigAccess {
    public static double[] pose(Vec3d from, float yaw, float pitch) {
        return CameraRig.pose(from, yaw, pitch);
    }

    private CameraRigAccess() {}
}
