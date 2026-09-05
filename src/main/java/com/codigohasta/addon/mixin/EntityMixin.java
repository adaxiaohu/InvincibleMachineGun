package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.GlobalSetting;
import com.codigohasta.addon.utils.CamUtils;
import com.codigohasta.addon.utils.leaveshack.Rotation;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Shadow
    public abstract void setDeltaMovement(Vec3 velocity);

    @Shadow
    public abstract Vec3 getDeltaMovement();

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void onChangeLookDirection(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if ((Object) this == MeteorClient.mc.player) {
            if (CamUtils.isUsing()) {
                double modifier = MeteorClient.mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT ? -1.0 : 1.0;
                CamUtils.changeLookDirection(cursorDeltaX * 0.15, cursorDeltaY * 0.15 * modifier);
                ci.cancel();
            } else {
                CamUtils.yaw = MeteorClient.mc.player.getYRot();
                CamUtils.pitch = MeteorClient.mc.player.getXRot();
                CamUtils.prevYaw = CamUtils.yaw;
                CamUtils.prevPitch = CamUtils.pitch;
            }
        }
    }

    @Inject(method = "moveRelative", at = @At("HEAD"), cancellable = true)
    private void hookUpdateVelocity(float speed, Vec3 movementInput, CallbackInfo ci) {
        if (!GlobalSetting.INSTANCE.moveFix.get()) return;
        Entity entity = (Entity) (Object) this;
        if (entity != MeteorClient.mc.player) return;
        if (!Rotation.rotation) return;

        Vec3 vec3d = movementInputToVelocity(movementInput, speed, Rotation.targetYaw);
        this.setDeltaMovement(this.getDeltaMovement().add(vec3d));
        ci.cancel();
    }

    @Unique
    private static Vec3 movementInputToVelocity(Vec3 movementInput, float speed, float yaw) {
        double d = movementInput.lengthSqr();
        if (d < 1.0E-7) {
            return Vec3.ZERO;
        }
        Vec3 vec3d = (d > 1.0 ? movementInput.normalize() : movementInput).scale(speed);
        float sin = Mth.sin(yaw * ((float) Math.PI / 180F));
        float cos = Mth.cos(yaw * ((float) Math.PI / 180F));
        return new Vec3(
            vec3d.x * cos - vec3d.z * sin,
            vec3d.y,
            vec3d.z * cos + vec3d.x * sin
        );
    }
}
