package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.AlienSprint;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class MixinKeyboardInputChild {

    /**
     * vanilla {@code KeyboardInput.tick()} 跑完后改写输入。
     *
     * <p>不用 {@code require = 0}：注入失败就该报错，不要藏。
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void onKeyboardTickTail(CallbackInfo ci) {
        if (AlienSprint.INSTANCE == null || !AlienSprint.INSTANCE.isActive()) return;
        if (AlienSprint.INSTANCE.mode.get() != AlienSprint.Mode.Rotation) return;

        AlienSprint.INSTANCE.onInputTick();
    }
}
