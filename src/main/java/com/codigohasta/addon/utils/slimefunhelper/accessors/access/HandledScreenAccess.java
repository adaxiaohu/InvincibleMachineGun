package com.codigohasta.addon.utils.slimefunhelper.accessors.access;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;

public interface HandledScreenAccess {
    static HandledScreenAccess of(HandledScreen<?> screen) { return (HandledScreenAccess) screen; }
    Slot reallyGetSlotAt(double x, double y);
}
