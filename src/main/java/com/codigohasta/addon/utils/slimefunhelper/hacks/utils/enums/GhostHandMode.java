package com.codigohasta.addon.utils.slimefunhelper.hacks.utils.enums;

import com.codigohasta.addon.utils.slimefunhelper.managers.config.ConfigEnum;
import com.codigohasta.addon.utils.slimefunhelper.utils.InventoryUtils;

public enum GhostHandMode implements ConfigEnum {
    INV_SWAP(InventoryUtils.getPlayerInvSize()),
    HOT_BAR_ONLY(9),
    INV_CLICK(InventoryUtils.getPlayerInvSize());
    ;

    final int searchSize;

    GhostHandMode(int size) {
        searchSize = size;
    }

    @Override
    public String getConfigEnumType() {
        return "ghost_hand_mode";
    }

    public int getSearchSize(boolean offHand) {
        return offHand ? InventoryUtils.getPlayerInvSize() : searchSize;
    }
}
