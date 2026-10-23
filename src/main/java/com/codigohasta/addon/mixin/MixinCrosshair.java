package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.CrosshairHud;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class MixinCrosshair {

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void img$replaceCrosshair(DrawContext context, RenderTickCounter tickCounter,
                                      CallbackInfo ci) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null) return;
            if (mc.options != null && mc.options.hudHidden) return;

            CrosshairHud hud = Modules.get().get(CrosshairHud.class);
            if (hud == null || !hud.isActive()) return;

            // 只取消原版；自绘在 Render2DEvent 里（走 Shader2DUtil，才有抗锯齿/圆角/发光/故障）。
            // ⛔ 曾经误判「Render2DEvent 里画不出来」而改成在此处 DrawContext 直绘；
            //    真因是【坐标】——Meteor 把 screenHeight 填成了 guiWidth。
            //    见 docs/gcore/diag/crosshair-meteor-screenheight-bug.md
            ci.cancel();
        } catch (Throwable ignored) {
            // 准星替换失败绝不能影响渲染主流程
        }
    }
}
