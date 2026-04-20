package com.codigohasta.addon.mixin;

import net.minecraft.client.ClientBrandRetriever;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import meteordevelopment.meteorclient.systems.modules.Modules;
import com.codigohasta.addon.modules.SonarBypass;

@Mixin(value = ClientBrandRetriever.class, remap = false)
public class MixinClientBrandRetriever {

    @Inject(method = "getClientModName", at = @At("HEAD"), cancellable = true)
    private static void onGetClientModName(CallbackInfoReturnable<String> cir) {
        // 【核心修复】检查 Meteor 模块系统是否已初始化
        // 如果 Modules 还没加载，或者系统还没准备好，直接返回，不执行后续逻辑
        if (Modules.get() == null) return;

        try {
            // 安全地获取模块
            SonarBypass module = Modules.get().get(SonarBypass.class);
            
            // 只有模块存在且开启时，才修改返回值
            if (module != null && module.isActive()) {
                cir.setReturnValue(module.brandName.get());
            }
        } catch (Exception ignored) {
            // 兜底：防止由于类加载顺序导致的任何其他潜在异常
        }
    }
}