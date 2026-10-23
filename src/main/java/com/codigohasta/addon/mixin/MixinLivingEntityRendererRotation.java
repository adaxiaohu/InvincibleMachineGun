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

/**
 * 显示服务端转头 —— 等价翻译 AlienV4 {@code MixinLivingEntityRenderer}
 * + {@code RotationManager.getRenderYawOffset()}。
 *
 * <h2>★ 1.21.11 渲染架构（与 AlienV4/1.21.1 完全不同）</h2>
 * <pre>
 *   updateRenderState(LivingEntity, LivingEntityRenderState, float)   ← ① 提取（本 mixin）
 *   render(LivingEntityRenderState, PoseStack, SubmitNodeCollector, CameraRenderState)  ← ② 绘制
 * </pre>
 *
 * <h2>★★ Mojang 名 vs yarn 名（曾导致 Critical injection failure）</h2>
 * <table border="1">
 *   <tr><th>Mojang（javap）</th><th>yarn（本代码）</th><th>intermediary</th></tr>
 *   <tr><td>{@code extractRenderState}</td><td><b>{@code updateRenderState}</b></td><td>method_62355</td></tr>
 *   <tr><td>{@code bodyRot}</td><td>{@code bodyYaw}</td><td>field_53446</td></tr>
 *   <tr><td>{@code yRot}</td><td>{@code relativeHeadYaw}</td><td>field_53447</td></tr>
 *   <tr><td>{@code xRot}</td><td>{@code pitch}</td><td>field_53448</td></tr>
 * </table>
 *
 * <h2>★ 平滑机制（这是之前「生硬」的根因）</h2>
 * 直接令 {@code bodyYaw = fixYaw} 会让身体<b>瞬间跳到目标角度</b>，看起来抽搐。
 * Alien 用 {@code getRenderYawOffset()} 实现「头先转、身体慢慢跟上」：
 * <ul>
 *   <li>身体只跟随 30%（一阶低通）</li>
 *   <li>头与身体夹角限制 ±75°</li>
 *   <li>水平移动时身体朝「移动方向」而非视线方向</li>
 * </ul>
 * 见 {@link AlienRenderYawOffset}（逐行移植）。
 *
 * <h2>只影响本地渲染，不发包</h2>
 * {@code RenderState} 是纯客户端渲染数据，服务端收到的完全不变。
 */
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
