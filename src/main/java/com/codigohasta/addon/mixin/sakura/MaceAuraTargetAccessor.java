package com.codigohasta.addon.mixin.sakura;

import com.codigohasta.addon.modules.MaceAura;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@code MaceAura#currentTarget}（private Entity）。
 *
 * <p>目标是本项目自己的模块类，不是 Minecraft 类，必须 {@code remap = false}，
 * 否则 Mixin 会拿这些名字去混淆表里查。
 */
@Mixin(value = MaceAura.class, remap = false)
public interface MaceAuraTargetAccessor {
    @Accessor(value = "currentTarget", remap = false)
    Entity img$currentTarget();
}
