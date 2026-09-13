package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.GlobalSetting;
import com.codigohasta.addon.utils.CamUtils;
import com.codigohasta.addon.utils.leaveshack.Rotation;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Shadow
    public abstract void setVelocity(Vec3d velocity);

    @Shadow
    public abstract Vec3d getVelocity();

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void onChangeLookDirection(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if ((Object) this == MeteorClient.mc.player) {
            if (CamUtils.isUsing()) {
                double modifier = MeteorClient.mc.options.getPerspective() == Perspective.THIRD_PERSON_FRONT ? -1.0 : 1.0;
                CamUtils.changeLookDirection(cursorDeltaX * 0.15, cursorDeltaY * 0.15 * modifier);
                ci.cancel();
            } else {
                CamUtils.yaw = MeteorClient.mc.player.getYaw();
                CamUtils.pitch = MeteorClient.mc.player.getPitch();
                CamUtils.prevYaw = CamUtils.yaw;
                CamUtils.prevPitch = CamUtils.pitch;
            }
        }
    }

    @Inject(method = "updateVelocity", at = @At("HEAD"), cancellable = true)
    private void hookUpdateVelocity(float speed, Vec3d movementInput, CallbackInfo ci) {
        if (!GlobalSetting.INSTANCE.moveFix.get()) return;
        Entity entity = (Entity) (Object) this;
        if (entity != MeteorClient.mc.player) return;
        if (!Rotation.rotation) return;

        Vec3d vec3d = movementInputToVelocity(movementInput, speed, Rotation.targetYaw);
        this.setVelocity(this.getVelocity().add(vec3d));
        ci.cancel();
    }

    @Unique
    private static Vec3d movementInputToVelocity(Vec3d movementInput, float speed, float yaw) {
        double d = movementInput.lengthSquared();
        if (d < 1.0E-7) {
            return Vec3d.ZERO;
        }
        Vec3d vec3d = (d > 1.0 ? movementInput.normalize() : movementInput).multiply(speed);
        float sin = MathHelper.sin(yaw * ((float) Math.PI / 180F));
        float cos = MathHelper.cos(yaw * ((float) Math.PI / 180F));
        return new Vec3d(
            vec3d.x * cos - vec3d.z * sin,
            vec3d.y,
            vec3d.z * cos + vec3d.x * sin
        );
    }
}
