package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.CustomFov;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Zoom;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void onGetFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> info) {
        CustomFov module = Modules.get().get(CustomFov.class);
        
        if (module != null && module.isActive()) {
            // changingFov 是关键：
            // true  -> 正在为“世界/地形”计算 FOV
            // false -> 正在为“手持物品/手臂”计算 FOV
            
            if (!changingFov) {
                // --- 手部视角处理 ---
                // 直接返回设置中的 itemFov，强制切断世界 FOV 对手部的影响
                info.setReturnValue(module.itemFov.get().floatValue());
            } 
            else {
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
        }
    }
}