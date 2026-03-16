package com.codigohasta.addon.mixins;

import com.codigohasta.addon.modules.PearlPhase;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class MixinClientPlayerEntity {

    // 保留你原来的珍珠穿墙逻辑
    @Inject(method = "pushOutOfBlocks", at = @At("HEAD"), cancellable = true)
    private void onPushOutOfBlocks(double x, double z, CallbackInfo ci) {
        PearlPhase module = Modules.get().get(PearlPhase.class);
        if (module != null && module.isActive() && module.antiPush.get()) {
            ci.cancel();
        }
    }
}