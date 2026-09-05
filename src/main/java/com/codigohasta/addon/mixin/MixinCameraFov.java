package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.CustomFov;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Zoom;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.1.2 把 GameRenderer.getFov(camera, tickDelta, changingFov) 拆成了
 * Camera 上的两个方法，原来的 changingFov 参数变成了两个不同的注入点：
 *   changingFov = true  -> calculateFov    ：世界/地形 FOV
 *   changingFov = false -> calculateHudFov ：手持物品/手臂 FOV
 */
@Mixin(Camera.class)
public class MixinCameraFov {

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void onCalculateFov(float tickDelta, CallbackInfoReturnable<Float> info) {
        CustomFov module = Modules.get().get(CustomFov.class);
        if (module == null || !module.isActive()) return;

        // --- 世界视角处理 ---
        float fov = module.fov.get().floatValue();

        // 保持对 Meteor Zoom 的兼容
        Zoom zoom = Modules.get().get(Zoom.class);
        if (zoom != null) {
            double scaling = zoom.getScaling();
            if (scaling > 1.0) {
                fov /= (float) scaling;
            }
        }

        info.setReturnValue(fov);
    }

    @Inject(method = "calculateHudFov", at = @At("RETURN"), cancellable = true)
    private void onCalculateHudFov(float tickDelta, CallbackInfoReturnable<Float> info) {
        CustomFov module = Modules.get().get(CustomFov.class);
        if (module == null || !module.isActive()) return;

        // --- 手部视角处理 ---
        // 直接返回设置中的 itemFov，强制切断世界 FOV 对手部的影响
        info.setReturnValue(module.itemFov.get().floatValue());
    }
}
