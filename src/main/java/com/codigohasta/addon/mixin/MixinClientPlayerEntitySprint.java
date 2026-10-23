package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.AlienSprint;
import com.codigohasta.addon.utils.alien.AlienMovementUtil;
import com.codigohasta.addon.utils.alien.AlienRotationUtil;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * AlienSprint 的第 ③ 步：发包期把 yaw 换成服务端朝向。
 *
 * <p>还必须配合 {@code MixinKeyboardInput}（输入反向旋转）与
 * {@code MixinPlayerEntity}（{@code travel()} 期换 yaw）。
 * 三者都读同一个 {@code fixYaw}，缺任何一个都会产生 45°/90° 的 Simulation offset。
 *
 * <h2>★ ForceSync（等价 AlienV4 {@code ServerCheckSync}，默认 true）</h2>
 * Alien 在 {@code RotationManager.java:438-443}：
 * <pre>
 * public void setLastYaw(float lastYaw) {
 *    this.lastYaw = lastYaw;
 *    if (ServerCheckSync.INSTANCE.forceSync.getValue() &amp;&amp; Alien.SERVER.playerNull.passedS(0.15)) {
 *       ((IClientPlayerEntity)mc.player).setLastYaw(lastYaw);    // ★ 写回 client 字段
 *    }
 * }
 * </pre>
 *
 * <p>1.21.11 对应字段（mappings 实测 class_746）：
 * <ul>
 *   <li>{@code lastYawClient} (field_3941) ← Alien 的 {@code lastYaw}</li>
 *   <li>{@code lastPitchClient} (field_3925) ← Alien 的 {@code lastPitch}</li>
 * </ul>
 *
 * <p><b>为什么必须同步</b>：vanilla {@code LocalPlayer.tick() → sendPosition()}
 * 用 {@code getYRot() - lastYawClient} 判断要不要发 Rot 包。
 * 我们在 {@code sendMovementPackets} 里临时改了 {@code player.yaw}，
 * 若不把 {@code lastYawClient} 一起改，vanilla 下一 tick 会认为
 * 「yaw 变了」而<b>多发一个 Rot 包</b> ——
 * 一 tick 两个 yaw 包 ⇒ Grim 每个包都算一次 Simulation ⇒ 回弹。
 *
 * <p>⚠️ Alien 的 {@code passedS(0.15)} 是「服务器在线超过 0.15 秒」的保护，
 * 避免刚进服就同步。这里用「是否已发过包」等价替代。
 */
@Mixin(ClientPlayerEntity.class)
public class MixinClientPlayerEntitySprint {

    /** 1.21.11 yarn: {@code lastYawClient}（= Alien 的 lastYaw）。 */
    @Shadow private float lastYawClient;
    /** 1.21.11 yarn: {@code lastPitchClient}（= Alien 的 lastPitch）。 */
    @Shadow private float lastPitchClient;

    @Inject(method = "sendMovementPackets", at = @At("HEAD"))
    private void onSendMovementPacketsHead(CallbackInfo ci) {
        if (!AlienRotationUtil.shouldRotate) return;

        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        AlienRotationUtil.preYaw = player.getYaw();
        AlienRotationUtil.preBodyYaw = player.bodyYaw;
        AlienRotationUtil.preHeadYaw = player.headYaw;

        // ★★ 直接用 AlienSprint 每 tick 算好的 sendYaw。
        final float sendYaw = AlienRotationUtil.sendYaw;

        AlienRotationUtil.rotationYaw = sendYaw;
        AlienRotationUtil.rotationPitch = AlienRotationUtil.fixPitch;
        AlienRotationUtil.lastSentYaw = sendYaw;

        player.setYaw(sendYaw);
        player.bodyYaw = sendYaw;
        player.headYaw = sendYaw;
        AlienRotationUtil.packetApplied = true;
    }

    /**
     * 发包结束后：把已发的 yaw/pitch 同步到 vanilla 的 {@code lastYawClient/lastPitchClient}。
     *
     * <p>等价 Alien 的 {@code ForceSync}。这样 vanilla 的 {@code sendPosition}
     * 不会因为「以为 yaw 变了」而多发一个 Rot 包。
     */
    @Inject(method = "sendMovementPackets", at = @At("TAIL"))
    private void onSendMovementPacketsTail(CallbackInfo ci) {
        if (!AlienRotationUtil.packetApplied) return;
        AlienRotationUtil.packetApplied = false;

        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        player.setYaw(AlienRotationUtil.preYaw);
        player.bodyYaw = AlienRotationUtil.preBodyYaw;
        player.headYaw = AlienRotationUtil.preHeadYaw;

        // ★ ForceSync：告诉 vanilla「这个 yaw 我已经发出去了，别再补发」。
        this.lastYawClient = AlienRotationUtil.rotationYaw;
        this.lastPitchClient = AlienRotationUtil.rotationPitch;
    }

    /**
     * 兜底：阻止原版因「没按前进」取消疾跑。
     *
     * <p>经过 {@code MixinKeyboardInput} 补偿后 {@code moveVector.y > 0}，
     * {@code hasForwardImpulse()} 本应为 true，原版不会走到这里。
     * 保留它是<b>安全网</b>：万一补偿在某条分支没生效，至少不会瞬间掉疾跑。
     */
    @WrapWithCondition(method = "tickMovement", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/network/ClientPlayerEntity;setSprinting(Z)V", ordinal = 3))
    private boolean wrapStopSprinting(ClientPlayerEntity instance, boolean b) {
        return !(AlienSprint.INSTANCE != null && AlienSprint.INSTANCE.isActive()
            && AlienSprint.INSTANCE.mode.get() == AlienSprint.Mode.Rotation
            && AlienMovementUtil.isMoving());
    }
}
