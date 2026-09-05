package com.codigohasta.addon.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftClientAccessor {
    // 1.21.11 依然使用这个字段名来控制物品使用冷却
    @Accessor("rightClickDelay")
    void setItemUseCooldown(int itemUseCooldown);

    @Accessor("rightClickDelay")
    int getItemUseCooldown();
}