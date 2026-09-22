package com.codigohasta.addon.mixin.slimefunhelper;

import com.codigohasta.addon.utils.slimefunhelper.accessors.access.HandledScreenAccess;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(HandledScreen.class)
public interface HandledScreenAccessor extends HandledScreenAccess {
    @Invoker("getSlotAt") Slot reallyGetSlotAt(double x, double y);
}
