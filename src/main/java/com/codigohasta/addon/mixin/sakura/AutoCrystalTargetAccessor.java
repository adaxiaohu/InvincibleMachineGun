package com.codigohasta.addon.mixin.sakura;

import com.codigohasta.addon.modules.AutoCrystal;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 返回类型必须与字段类型（PlayerEntity）完全一致，否则 Mixin 会拒绝应用。 */
@Mixin(value = AutoCrystal.class, remap = false)
public interface AutoCrystalTargetAccessor {
    @Accessor(value = "target", remap = false)
    PlayerEntity img$target();
}
