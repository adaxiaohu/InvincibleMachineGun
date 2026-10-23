package com.codigohasta.addon.mixin.sakura;

import com.codigohasta.addon.modules.slimefunhelper.ElytraBot;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 sfIMG {@code ElytraBot#target}（package-private Entity）。
 *
 * <p>这是 sfIMG 分类下的「鞘翅追人」模块（{@code ElytraBot}），与主分类的
 * {@code ElytraFollower} 是两个不同模块，都要接入 TargetHud。
 *
 * <p>目标是本项目自己的模块类，不是 Minecraft 类，必须 {@code remap = false}，
 * 否则 Mixin 会拿这些名字去混淆表里查。
 *
 * <p>返回类型必须与字段声明完全一致（{@code Entity}），否则注解处理器会报
 * {@code Could not locate @Accessor}。
 */
@Mixin(value = ElytraBot.class, remap = false)
public interface ElytraBotTargetAccessor {
    @Accessor(value = "target", remap = false)
    Entity img$target();
}
