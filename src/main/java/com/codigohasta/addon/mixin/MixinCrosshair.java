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

/**
 * 取消原版准星，交给 {@link CrosshairHud} 自绘。
 *
 * <h2>为什么必须用 mixin（✅ 取证）</h2>
 *
 * <p>Meteor 的 {@code events/render} 里<b>没有准星事件</b>（已核对：
 * 只有 {@code Render2DEvent / Render3DEvent / ApplyTransformationEvent …}）。
 * 而 CatLean 是靠它自己的 {@code CrosshairRenderEvent} + {@code GuiMixin.renderCrosshair}：
 * <pre>
 *   // CatLean 的 mixins/render/GuiMixin（字节码确证）
 *   public void renderCrosshair(...) {
 *       if (CrosshairRenderEvent.call()) {   // 有 widget 接管
 *           callbackInfo.cancel();            // ★ 取消原版准星
 *       }
 *   }
 * </pre>
 * 本项目照同一机制，但直接用 {@code Modules.get().get(CrosshairHud.class).isActive()} 判断，
 * 不需要额外的事件类。
 *
 * <h2>注入点</h2>
 * <p>{@code InGameHud.renderCrosshair(DrawContext, RenderTickCounter)} —— 该方法在
 * MC 1.21.11 里是 <b>private</b>（已用 javap 确认），所以方法名要写全：
 * mixin 对 private 方法同样可注入（走 refmap）。
 * {@code cancellable = true} + {@code ci.cancel()} 即跳过原版绘制。
 */
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
