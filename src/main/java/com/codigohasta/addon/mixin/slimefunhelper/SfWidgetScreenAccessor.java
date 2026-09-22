package com.codigohasta.addon.mixin.slimefunhelper;

import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value=WidgetScreen.class,remap=false)
public interface SfWidgetScreenAccessor {
    @Accessor("root") WContainer sf$getRoot();
}
