package com.codigohasta.addon.modules.slimefunhelper;

/**
 * Standalone sfIMG entry for SlimefunHelper's MineExtra/fast-break implementation.
 * The inherited module ID stays sf-mine-extra so existing settings remain loadable.
 * Register this instance in place of MineExtra, never alongside a second instance.
 */
public final class FastMine extends MineExtra {
    public FastMine() {
        super();
        bindFlag(quickMine);
    }
}
