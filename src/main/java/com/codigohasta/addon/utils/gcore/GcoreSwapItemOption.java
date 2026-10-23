package com.codigohasta.addon.utils.gcore;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

public final class GcoreSwapItemOption {

    private final Setting<GcoreEnumType.ItemMode> slotMode;
    private final Setting<Boolean> print;

    public GcoreSwapItemOption(SettingGroup group) {
        this.slotMode = group.add(new EnumSetting.Builder<GcoreEnumType.ItemMode>()
            .name("切物品模式")
            .description("切物品的方式：HotBar 直接切选中槽，Inv 用背包取物，SwapInv 用点击槽交换。")
            .defaultValue(GcoreEnumType.ItemMode.SwapInv)
            .build());

        this.print = group.add(new BoolSetting.Builder()
            .name("找不到时提示")
            .description("找不到物品时在聊天栏提示。")
            .defaultValue(true)
            .build());
    }

    public GcoreEnumType.ItemMode getSlotMode() {
        return this.slotMode.get();
    }

    public boolean getPrint() {
        return this.print.get();
    }
}
