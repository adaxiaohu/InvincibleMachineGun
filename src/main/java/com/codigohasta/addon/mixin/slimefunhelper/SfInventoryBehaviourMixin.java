package com.codigohasta.addon.mixin.slimefunhelper;

import baritone.behavior.InventoryBehavior;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.codigohasta.addon.modules.slimefunhelper.BaritoneFix;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Environment(EnvType.CLIENT)
@Mixin(InventoryBehavior.class)
public abstract class SfInventoryBehaviourMixin {
    @ModifyExpressionValue(
            method = "a(ZLjava/util/function/Predicate;)Z",
            at = @At(value = "FIELD", target = "Lbaritone/api/Settings$Setting;value:Ljava/lang/Object;"),
            require = 1,
            remap = false)
    private Object modifyInventoryCheck(Object original) {
        if (BaritoneFix.active() && BaritoneFix.INSTANCE.enableInventoryFireworks.get()) {
            return true;
        }
        return original;
    }
}
