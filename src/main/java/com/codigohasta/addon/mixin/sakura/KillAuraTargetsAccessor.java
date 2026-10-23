package com.codigohasta.addon.mixin.sakura;

import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * 暴露 Meteor {@code KillAura#targets}（private final List&lt;Entity&gt;）。
 *
 * <p><b>为什么需要它</b>：KillAura 有 {@code max-targets} 设置，会同时攻击多个目标，
 * 但它对外只提供了 {@code getTarget()}（返回列表第一个）。Sakura TargetHud 要显示
 * 全部被攻击的目标，就必须读到整个列表。
 *
 * <p>目标是 Minecraft 类（{@code class_1297}），字段名走 Yarn 映射，
 * 因此这里用默认的 {@code remap = true}（不能像本项目自己的模块那样写 false）。
 */
@Mixin(value = KillAura.class, remap = false)
public interface KillAuraTargetsAccessor {
    @Accessor(value = "targets", remap = false)
    List<Entity> img$targets();
}
