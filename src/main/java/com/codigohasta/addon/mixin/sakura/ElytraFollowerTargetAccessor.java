package com.codigohasta.addon.mixin.sakura;

import com.codigohasta.addon.modules.ElytraFollower;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@code ElytraFollower#target}（private PlayerEntity）。
 *
 * <p>目标是本项目自己的模块类，不是 Minecraft 类，必须 {@code remap = false}，
 * 否则 Mixin 会拿这些名字去混淆表里查。
 *
 * <p>返回类型必须与字段声明完全一致（{@code PlayerEntity}）：Mixin 的注解处理器
 * 会在编译期校验 accessor 与目标字段的匹配，写成 {@code Entity} 会报
 * {@code Could not locate @Accessor target}。
 */
@Mixin(value = ElytraFollower.class, remap = false)
public interface ElytraFollowerTargetAccessor {
    @Accessor(value = "target", remap = false)
    PlayerEntity img$target();
}
