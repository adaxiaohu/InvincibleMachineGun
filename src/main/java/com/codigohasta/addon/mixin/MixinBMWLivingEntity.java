package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.BMWSprint;
import com.codigohasta.addon.utils.bmw.BMWDirectionalInput;
import com.codigohasta.addon.utils.bmw.BMWPlayerUtil;
import com.codigohasta.addon.utils.bmw.BMWRotationManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 对应 LB MixinLivingEntity:
 *
 * 在 jump() 执行后直接修正疾跑加速速度方向。
 * 保存 jump() 前的速度，jump() 返回后检测 X/Z 方向的速度增量（即疾跑加速），
 * 将其替换为 WASD 方向对应的加速向量。
 *
 * 只使用 @Inject HEAD/RETURN，无字节码级 INVOKE 注入点，
 * 因此与任何模组（包括 Baritone 的 @Redirect）都不冲突。
 */
@Mixin(value = LivingEntity.class, priority = 1500)
public class MixinBMWLivingEntity {

    @Unique
    private static float invincible$sprintYaw = Float.NaN;

    @Unique
    private static Vec3d invincible$preJumpVelocity = null;

    /**
     * 跳跃前：计算目标 yaw，保存当前速度。
     */
    @Inject(method = "jump", at = @At("HEAD"))
    private void onJumpPre(CallbackInfo ci) {
        invincible$sprintYaw = Float.NaN;
        invincible$preJumpVelocity = null;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || (Object) this != mc.player) {
            return;
        }

        if (BMWSprint.INSTANCE == null || !BMWSprint.INSTANCE.isActive()) {
            return;
        }

        BMWSprint.Mode mode = BMWSprint.INSTANCE.sprintMode.get();

        if (mode == BMWSprint.Mode.OMNIDIRECTIONAL || mode == BMWSprint.Mode.OMNIROTATIONAL) {
            // 强制疾跑，确保 jump() 应用疾跑加速
            mc.player.setSprinting(true);

            // 保存跳前的速度
            invincible$preJumpVelocity = mc.player.getVelocity();

            // 计算目标 yaw
            if (mode == BMWSprint.Mode.OMNIDIRECTIONAL) {
                invincible$sprintYaw = BMWPlayerUtil.getMovementDirectionOfInput(
                    mc.player.getYaw(), BMWDirectionalInput.fromPlayer());
            } else if (mode == BMWSprint.Mode.OMNIROTATIONAL) {
                if (BMWRotationManager.targetRotation != null) {
                    invincible$sprintYaw = BMWRotationManager.targetRotation.yaw;
                }
            }
        }
    }

    /**
     * 跳跃后：修正疾跑加速速度方向。
     *
     * jump() 对速度的修改：
     *   1. setVelocity(vel.x, max(vel.y, jumpPower), vel.z) — 仅改 Y
     *   2. if (isSprinting()) addVelocityInternal(-sin(yaw)*0.2, 0, cos(yaw)*0.2) — 改 X/Z
     *
     * 因此 X/Z 的速度增量仅来自疾跑加速。
     * 我们将其替换为 WASD 方向对应的加速向量。
     */
    @Inject(method = "jump", at = @At("RETURN"))
    private void onJumpPost(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || (Object) this != mc.player) {
            invincible$sprintYaw = Float.NaN;
            invincible$preJumpVelocity = null;
            return;
        }

        if (invincible$preJumpVelocity == null || Float.isNaN(invincible$sprintYaw)) {
            invincible$sprintYaw = Float.NaN;
            invincible$preJumpVelocity = null;
            return;
        }

        // jump() 后的实际速度
        Vec3d currentVel = mc.player.getVelocity();

        // X/Z 方向的速度增量（仅来自疾跑加速）
        double dx = currentVel.x - invincible$preJumpVelocity.x;
        double dz = currentVel.z - invincible$preJumpVelocity.z;

        // 有水平增量说明疾跑加速被应用了
        if (Math.abs(dx) > 1.0E-6 || Math.abs(dz) > 1.0E-6) {
            // 计算正确的加速向量
            float yawRad = invincible$sprintYaw * MathHelper.RADIANS_PER_DEGREE;
            double correctX = -MathHelper.sin(yawRad) * 0.2;
            double correctZ = MathHelper.cos(yawRad) * 0.2;

            // 替换：移除原加速，添加正确方向加速
            mc.player.setVelocity(
                currentVel.x - dx + correctX,
                currentVel.y,
                currentVel.z - dz + correctZ
            );
        }

        invincible$sprintYaw = Float.NaN;
        invincible$preJumpVelocity = null;
    }
}
