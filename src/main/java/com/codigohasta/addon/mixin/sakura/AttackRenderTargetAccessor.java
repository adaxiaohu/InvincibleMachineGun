package com.codigohasta.addon.mixin.sakura;

import com.codigohasta.addon.modules.slimefunhelper.Attack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 sfIMG「a攻击辅助」({@link Attack}) 的 {@code private Entity lastTickTarget}。
 *
 * <p><b>为什么需要它</b>：这个模块有两条不同的目标获取路径——
 * <ul>
 *     <li>{@code getCurrentRangeEntities()} —— 范围内<b>所有</b>可攻击实体，<b>没有排序</b>；
 *         TargetHud 原先用它，取到的是「列表里碰巧第一个」，与模块实际锁定的目标不一致。</li>
 *     <li>{@code lastTickTarget} —— {@code onRenderTarget()} 每 tick 缓存的<b>渲染目标</b>，
 *         由 {@code searchAttackEntity} 按「准星优先 + 权重排序」选出，就是屏幕上画框的那个。</li>
 * </ul>
 * 用户要求 TargetHud 以「渲染目标」为准，因此这里直接读该缓存字段，而不是重算一遍：
 * 重算既多一次开销，也忽略了渲染的前置条件（未持武器 / 举盾 / 用弓时不更新），
 * 结果可能与屏幕上的框不一致。
 *
 * <p>字段在 {@code Attack} 里只写不读（仅 {@code onRenderTarget} 用），所以读它是安全的。
 *
 * <p>{@code Attack} 是本项目自己的类，不是 Minecraft 类，因此 {@code remap = false}。
 */
@Mixin(value = Attack.class, remap = false)
public interface AttackRenderTargetAccessor {
    @Accessor(value = "lastTickTarget", remap = false)
    Entity img$lastTickTarget();
}
