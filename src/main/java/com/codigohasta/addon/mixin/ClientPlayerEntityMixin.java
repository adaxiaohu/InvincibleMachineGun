package com.codigohasta.addon.mixin;

import com.codigohasta.addon.events.MovementInputEvent;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.ClientInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class ClientPlayerEntityMixin {
    @Shadow public ClientInput input;

    /**
     * 1.21.11 兼容版注入
     * 使用 TAIL 而不是 INVOKE，以避开 LiquidBounce 等 200+ Mod 的重定向冲突
     */
    @Inject(method = "aiStep", at = @At("TAIL"))
    private void onAfterTickMovement(CallbackInfo ci) {
        // 确保在物理计算前，input 已经被更新
        if (this.input != null) {
            MeteorClient.EVENT_BUS.post(MovementInputEvent.get(this.input));
        }
    }
}