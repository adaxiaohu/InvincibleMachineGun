package com.codigohasta.addon.mixin;

import com.codigohasta.addon.utils.gcore.MoveSequenceGate;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 抑制原版 {@code sendMovementPackets} —— <b>本项目独立实现</b>。
 *
 * <h2>手法来源</h2>
 * 照 slimefunhelper {@code mixin.slimefunhelper.ClientPlayerMixin} 第 84-85 行的做法
 * （用 {@code ModifyExpressionValue} 改掉 {@code sendMovementPackets} 里那个
 * {@code isCamera()} 判断的返回值，让方法提前返回），
 * 但：
 * <ul>
 *     <li><b>不 import 也不调用</b> slimefunhelper 的任何类（用户明确要求「照抄一个单独的」）；</li>
 *     <li>开关走本项目自己的 {@link MoveSequenceGate}，与 sf 的 {@code sf$cancelMovement} 无关，
 *         两者互不干扰（各改各的表达式，我们只在自己置位时返回 false）。</li>
 * </ul>
 *
 * <h2>为什么改 {@code isCamera()} 就能取消移动包</h2>
 * 1.21.11 的 {@code ClientPlayerEntity.sendMovementPackets()} 大致是：
 * <pre>
 *   if (this.isCamera()) {                    // ← 我们的注入点
 *       ...计算 moved / rotated...
 *       if (moved || rotated) this.networkHandler.sendPacket(...);
 *   }
 * </pre>
 * 把它强制成 {@code false}，本次调用就一个移动包都不发。
 *
 * <h2>★ 为什么是「返回 false」而不是 cancel 整个方法</h2>
 * {@code sendMovementPackets} 里 {@code isCamera()} 之后有可能还有<b>别的副作用</b>需要执行；
 * 只翻转这一个判断，语义最贴近「这一次不发位置包」，
 * 与参考实现的做法完全一致（避免把无关逻辑一起跳过）。
 *
 * <h2>★ 安全</h2>
 * <ul>
 *     <li>只在 {@link MoveSequenceGate#consumeSuppress()} 返回 true 时返回 false ——
 *         其余时间<b>逐字透传</b> {@code original}，行为与原版完全一致；</li>
 *     <li>置位是<b>一次性</b>的：查询即消费，一次 arm 只抑制一条调用；</li>
 *     <li>模块停用时 {@link MoveSequenceGate#disarm()}，不存在「永久停发」的窗口。</li>
 * </ul>
 */
@Mixin(ClientPlayerEntity.class)
public abstract class MixinClientPlayerEntitySuppressMove {

    /**
     * 见类注释。
     *
     * @param original 原版 {@code isCamera()} 的返回值
     * @return {@code false} = 本次不发原版移动包；否则透传原值
     */
    @ModifyExpressionValue(
        method = "sendMovementPackets",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/client/network/ClientPlayerEntity;isCamera()Z")
    )
    private boolean sfimg$suppressMovementPackets(boolean original) {
        if (MoveSequenceGate.consumeSuppress()) {
            return false;
        }
        return original;
    }
}
