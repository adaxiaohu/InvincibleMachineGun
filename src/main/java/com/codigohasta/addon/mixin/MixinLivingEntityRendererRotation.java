package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.RoundRotation;
import com.codigohasta.addon.utils.alien.AlienRenderYawOffset;
import com.codigohasta.addon.utils.alien.AlienRotationUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRendererRotation {

    /**
     * ★ 1.21.11 的 yarn 名是 {@code updateRenderState}，不是 {@code extractRenderState}！
     *
     * <p>mappings 实测（class_922）：
     * <pre>
     *   m (Lnet/minecraft/class_1309;Lnet/minecraft/class_10042;F)V  method_62355  updateRenderState
     *   m (Lnet/minecraft/class_10042;Lnet/minecraft/class_4587;...)V method_4054   render
     *   m (Lnet/minecraft/class_1309;FF)F                         method_62482  clampBodyYaw
     * </pre>
     */
    @Inject(
        method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;" +
                 "Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
        at = @At("RETURN")
    )
    private void onUpdateRenderStateReturn(LivingEntity entity, LivingEntityRenderState state,
                                           float tickDelta, CallbackInfo ci) {
        if (RoundRotation.INSTANCE == null || !RoundRotation.INSTANCE.shouldShow()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || entity != mc.player) return;
        if (!AlienRotationUtil.shouldRotate) return;

        // 目标朝向 = 服务端看到的 yaw
        final float targetYaw = AlienRotationUtil.fixYaw;

        // ★ 等价 Alien getRenderYawOffset()：身体平滑跟随，不瞬间对齐
        final float bodyYaw = AlienRenderYawOffset.compute(targetYaw, AlienRenderYawOffset.prevOffset());
        AlienRenderYawOffset.save(bodyYaw);

        state.bodyYaw = bodyYaw;

        // 头部：指向目标 yaw，相对身体的偏移同样受 ±75° 限制
        // （与 getRenderYawOffset 内部同一套限制，避免头身撕裂）
        float headOffset = MathHelper.wrapDegrees(targetYaw - bodyYaw);
        if (headOffset < -75.0F) {
            headOffset = -75.0F;
        } else if (headOffset >= 75.0F) {
            headOffset = 75.0F;
        }
        state.relativeHeadYaw = headOffset;

        // 俯仰：只在开启时覆盖，且夹到合法区间
        if (RoundRotation.INSTANCE.showPitchEnabled()) {
            state.pitch = AlienRotationUtil.normalizePitch(AlienRotationUtil.fixPitch);
        }
    }
}
