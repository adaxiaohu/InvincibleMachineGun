package com.codigohasta.addon.utils.slimefunhelper.accessors.hacks;

import net.minecraft.client.option.KeyBinding;

public interface KeyBindAccess {
    public void resetKeyState();

    static KeyBindAccess of(KeyBinding keyBinding) {
        return (KeyBindAccess) keyBinding;
    }
}
