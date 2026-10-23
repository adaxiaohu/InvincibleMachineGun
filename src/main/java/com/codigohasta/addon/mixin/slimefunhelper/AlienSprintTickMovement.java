package com.codigohasta.addon.mixin.slimefunhelper;

import com.codigohasta.addon.modules.AlienSprint;
import com.codigohasta.addon.utils.alien.AlienMovementUtil;
import com.codigohasta.addon.utils.alien.AlienRotationUtil;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * 等价翻译 AlienV4 {@code TickMovementEvent}（{@code MixinClientPlayerEntity.java:222-225}）：
 * <pre>
 * &#64;Inject(method = "tickMovement", at = &#64;At("HEAD"))
 * private void tickMovement(CallbackInfo ci) {
 *    Alien.EVENT_BUS.post(TickMovementEvent.INSTANCE);
 * }
 * </pre>
 *
 * <h2>为什么必须注入 tickMovement 而不是 TickEvent.Pre</h2>
 * Alien 的时间线（{@code Sprint.onMove} 监听 {@code TickMovementEvent}）：
 * <pre>
 * ClientPlayerEntity.tick() HEAD        → TickEvent.Pre
 *   └─ LivingEntity.tick()
 *        └─ tickMovement() HEAD         → ★ TickMovementEvent：算 fixYaw + setSprinting
 *             tickMovementInput()   (221)
 *             jump()                (463)  ← 读 fixYaw
 *             travelControlled()    (595)
 *                  travel()         (618)  ← 读 fixYaw
 * ClientPlayerEntity.tick() RETURN      → TickEvent.Post
 * </pre>
 * 放在 {@code TickEvent.Pre} 会<b>早半个 tick</b>：
 * <ul>
 *   <li>{@code PlayerPositionLookS2CPacket} 后置的 {@code pause} 还没被 TickEvent.Post 清掉</li>
 *   <li>{@code pitch/yaw} 可能被上一 tick 的发包还原动作影响</li>
 *   <li>Alien 的 {@code TickMovementEvent} 是「每次 tickMovement 都重算」，
 *       而 tickMovement 在 {@code rideTick}/{@code travelControlled} 等路径下可能同 tick 多次</li>
 * </ul>
 */
@Mixin(ClientPlayerEntity.class)
public class AlienSprintTickMovement {
    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void onTickMovementHead(CallbackInfo ci) {
        if (AlienSprint.INSTANCE == null || !AlienSprint.INSTANCE.isActive()) return;
        if (mc.player == null) return;
        AlienSprint.INSTANCE.alienTickMovement();
    }
}
