package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.AlienSprint;
import com.codigohasta.addon.utils.alien.AlienRotationUtil;
import com.codigohasta.addon.utils.leaveshack.events.TravelEvent;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * {@code PlayerEntity.travel} 换 yaw —— 等价翻译 AlienV4 {@code MixinPlayerEntity.travel}
 * + {@code RotationManager.travel}。
 *
 * <h2>AlienV4 原实现</h2>
 * <pre>
 * // MixinPlayerEntity.java:52-64
 * &#64;Inject(method = "travel", at = &#64;At("HEAD"), cancellable = true)
 * private void onTravelPre(Vec3d movementInput, CallbackInfo ci) {
 *    PlayerEntity player = (PlayerEntity) PlayerEntity.class.cast(this);
 *    if (player == mc.player) {
 *       TravelEvent event = TravelEvent.get(Event.Stage.Pre, player);
 *       Alien.EVENT_BUS.post(event);
 *       if (event.isCancelled()) { ci.cancel(); ... }
 *    }
 * }
 *
 * // RotationManager.java:202-219
 * &#64;EventListener public void travel(TravelEvent e) {
 *    if (ServerCheckSync.INSTANCE.movementSync()) {
 *       if (!BaritoneUtil.isActive()) {
 *          if (!mc.player.isRiding()) {
 *             if (e.isPre()) {
 *                this.prevYaw = mc.player.getYaw();
 *                this.prevPitch = mc.player.getPitch();
 *                mc.player.setYaw(fixYaw);
 *                mc.player.setPitch(fixPitch);
 *             } else {
 *                mc.player.setYaw(this.prevYaw);
 *                mc.player.setPitch(this.prevPitch);
 *             }
 *          }
 *       }
 *    }
 * }
 * </pre>
 *
 * <h2>1.21.11 调用链（javap 实测）</h2>
 * <pre>
 * LivingEntity.tickMovement()
 *    221  tickMovementInput()
 *    463  jump()                ← MixinLivingEntitySprint 处理
 *    595  travelControlled()
 *          └─ 618 travel(Vec3d) → PlayerEntity.travel   ← 本 mixin
 *              └─ LivingEntity.travel → travelMidAir → applyMovementInput
 *                  └─ Entity.updateVelocity
 *                       └─ getYaw()  ★ yaw 在这里才被读
 * </pre>
 *
 * <p>因此 HEAD 换 yaw、RETURN 还原即可覆盖整段位移计算。
 *
 * <p>⚠️ <b>不与 jump 共用 prevYaw</b>：Alien 原码两者共用同一个 {@code this.prevYaw}，
 * 但 1.21.11 里 {@code jump()} 在 {@code travel()} 之前（463 &lt; 618），
 * 共用会导致 travel 的还原值被 jump 覆盖。这里各用独立字段。
 */
@Mixin(PlayerEntity.class)
public class MixinPlayerEntity {
    /** 对应 Alien 的 {@code private float prevYaw/prevPitch}，但仅用于 travel。 */
    private float alien$travelPrevYaw;
    private float alien$travelPrevPitch;

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void onTravelPre(Vec3d movementInput, CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (player != mc.player) return;

        // ── 等价于 RotationManager.travel：Pre 换 yaw ──
        // Alien 条件：movementSync && !Baritone && !isRiding
        // 1.21.11 无 isRiding()，等价物是 hasVehicle()
        if (AlienRotationUtil.shouldRotate && !player.hasVehicle()) {
            alien$travelPrevYaw = player.getYaw();
            alien$travelPrevPitch = player.getPitch();

            // ⛔ 已撤回：在这里改 player.sidewaysSpeed/forwardSpeed 是【无效】的。
            //   travel(Vec3d) 的参数在调用时已固化成不可变 Vec3d，
            //   改字段影响不到本次参数（日志：TRAVEL-ROT 打印 xxa 已变，
            //   但同一行 mIn 丝毫未变）。留此注释防止再犯。

            // ★ 读 AlienSprint 每 tick 一次性算好的 sendYaw，
            //   与 jump / 发包处【完全同一个数】。
            player.setYaw(AlienRotationUtil.sendYaw);
            player.setPitch(AlienRotationUtil.fixPitch);
            AlienRotationUtil.travelApplied = true;
        }

        TravelEvent event = new TravelEvent(player);
        MeteorClient.EVENT_BUS.post(event);
        if (event.isCancelled()) {
            // ci.cancel() 跳过方法体，RETURN 不触发 ⇒ 必须先还原
            restoreYaw(player);
            ci.cancel();
            event = new TravelEvent(player);
            MeteorClient.EVENT_BUS.post(event);
        }
    }

    @Inject(method = "travel", at = @At("RETURN"))
    private void onTravelPost(Vec3d movementInput, CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (player != mc.player) return;

        // ── Alien 的 travel Post 分支：还原 yaw ──
        restoreYaw(player);

        TravelEvent event = new TravelEvent(player);
        MeteorClient.EVENT_BUS.post(event);
    }

    /** 对应 Alien {@code travel(Post)}：{@code setYaw(prevYaw)} / {@code setPitch(prevPitch)}。 */
    private void restoreYaw(PlayerEntity player) {
        if (!AlienRotationUtil.travelApplied) return;
        AlienRotationUtil.travelApplied = false;
        player.setYaw(alien$travelPrevYaw);
        player.setPitch(alien$travelPrevPitch);
    }
}
