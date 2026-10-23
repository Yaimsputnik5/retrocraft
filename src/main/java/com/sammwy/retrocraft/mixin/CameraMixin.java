package com.sammwy.retrocraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.sammwy.retrocraft.client.CameraRigAccess;

import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;

@Mixin(Camera.class)
abstract class CameraMixin {
    @Shadow
    protected abstract void setPos(Vec3d pos);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    public abstract Vec3d getPos();

    @Shadow
    public abstract float getYaw();

    @Shadow
    public abstract float getPitch();

    @Inject(method = "update", at = @At("TAIL"))
    private void retrocraft$rig(
            BlockView area,
            Entity entity,
            boolean thirdPerson,
            boolean inverseView,
            float tickDelta,
            CallbackInfo ci) {
        double[] pose = CameraRigAccess.pose(getPos(), getYaw(), getPitch());
        if (pose == null) return;
        setRotation((float) pose[3], (float) pose[4]);
        setPos(new Vec3d(pose[0], pose[1], pose[2]));
    }
}
