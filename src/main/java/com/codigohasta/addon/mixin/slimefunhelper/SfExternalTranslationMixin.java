package com.codigohasta.addon.mixin.slimefunhelper;

import com.codigohasta.addon.utils.slimefunhelper.SfTranslations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional compatibility: the translation addon rebuilds unknown titles from English IDs. */
@Pseudo
@Mixin(targets = "com.nippaku_zanmu.trans_addon.modules.Translation", remap = false)
public abstract class SfExternalTranslationMixin {
    @Inject(method = "tran(Lcom/nippaku_zanmu/trans_addon/util/trans_engine/AbstractTransEngine;)V", at = @At("TAIL"), require = 0)
    private void sfimg$restoreOwnedTranslations(CallbackInfo ci) {
        SfTranslations.refreshFromModule();
    }
}
