package com.codigohasta.addon.mixin;

import com.codigohasta.addon.utils.alien.AlienRotationUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code jump()} 换 yaw —— 等价翻译 AlienV4 {@code RotationManager.onJump}
 * （{@code RotationManager.java:221-238}）。
 *
 * <h2>为什么跳跃也要换</h2>
 * 1.21.11 {@code LivingEntity.jump()} 偏移 48：
 * <pre>
 *   if (isSprinting()) {
 *       float rad = getYaw() * 0.017453292f;
 *       addVelocity(-sin(rad) * 0.2, 0, cos(rad) * 0.2);   // ★ 疾跑跳跃加速
 *   }
 * </pre>
 * 服务端 Grim {@code JumpPower.java:30-37} 用<b>包里的 yaw</b> 预测这 0.2 的方向。
 * 若不换，客户端按真实 yaw 加、包却发 fixYaw ⇒ 每跳 0.2 格方向误差 ⇒ Simulation 回弹。
 *
 * <h2>⛔ 已撤回的两个错误判断（都有日志实证）</h2>
 * <ol>
 *   <li><b>「jump 没被调用」</b> —— 错。{@code JUMP-CALLED} 日志实测有 37 条</li>
 *   <li><b>「{@code (Object) this == mc.player} 判断」</b> —— 错。
 *       日志实测 {@code this=class_746} 但 {@code same=false}，
 *       因为它缓存了 {@code static final MinecraftClient mc}，
 *       而 Mixin 类可能在 {@code MinecraftClient} 构造完成<b>之前</b>就被类加载</li>
 * </ol>
 *
 * <h2>✅ 现在的做法</h2>
 * <ul>
 *   <li>不缓存 {@code MinecraftClient}，每次 {@link #mc()} 现取</li>
 *   <li>不用对象相等判断，改用 {@code instanceof ClientPlayerEntity}</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
public class MixinLivingEntitySprint {

    /**
     * ⚠️ 不要写成 {@code private static final MinecraftClient mc = ...getInstance();}
     * —— Mixin 类在极早期就可能被类加载/验证，那样缓存到的是 null 或错误实例。
     */
    private static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }

    /** 对应 Alien 的 {@code prevYaw/prevPitch}，仅用于 jump。 */
    private float alien$jumpPrevYaw;
    private float alien$jumpPrevPitch;
    /** Pre 是否真的替换过（RETURN 必须据此还原，不能看 shouldRotate）。 */
    private boolean alien$jumpApplied;

    @Inject(method = "jump", at = @At("HEAD"))
    private void onJumpPre(CallbackInfo ci) {
        this.alien$jumpApplied = false;

        MinecraftClient client = mc();
        if (client == null || client.player == null) return;

        // 只处理本地玩家（用 instanceof，不用对象相等 —— 见类注释 ⛔2）
        if (!((Object) this instanceof ClientPlayerEntity)) return;

        // Alien 条件：movementSync && !isRiding
        if (!AlienRotationUtil.shouldRotate) return;

        // ★★★ 关键修复：一律操作 `self`（= this），不要用 client.player。
        //
        //   执行 jump() 的实体是 `this`，偏移 48 读的 getYaw() 也是 `this.getYaw()`。
        //   之前写的是 client.player.setYaw(...)，若两者不是同一实例，改的是别人，
        //   跳跃那 0.2 加速仍按真实视角 yaw 施加 ——
        //   表现为「转头方向对了，但跳跃方向还是视角前方」。
        final LivingEntity self = (LivingEntity) (Object) this;

        this.alien$jumpPrevYaw = self.getYaw();
        this.alien$jumpPrevPitch = self.getPitch();

        self.setYaw(AlienRotationUtil.sendYaw);
        self.setPitch(AlienRotationUtil.fixPitch);
        this.alien$jumpApplied = true;
    }

    @Inject(method = "jump", at = @At("RETURN"))
    private void onJumpPost(CallbackInfo ci) {
        if (!this.alien$jumpApplied) return;
        this.alien$jumpApplied = false;

        // 同样操作 this，与 HEAD 对称
        final LivingEntity self = (LivingEntity) (Object) this;
        self.setYaw(this.alien$jumpPrevYaw);
        self.setPitch(this.alien$jumpPrevPitch);
    }
}
