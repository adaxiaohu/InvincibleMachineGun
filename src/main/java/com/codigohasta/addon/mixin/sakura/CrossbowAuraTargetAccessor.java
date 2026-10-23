package com.codigohasta.addon.mixin.sakura;

import com.codigohasta.addon.modules.CrossbowAura;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@code CrossbowAura} 的两个目标字段：优先使用锁定目标 {@code lockedTarget}，
 * 为空时回退到 {@code currentTarget}（由 resolver 决定优先级）。
 */
@Mixin(value = CrossbowAura.class, remap = false)
public interface CrossbowAuraTargetAccessor {
    @Accessor(value = "lockedTarget", remap = false)
    Entity img$lockedTarget();

    @Accessor(value = "currentTarget", remap = false)
    Entity img$currentTarget();
}
