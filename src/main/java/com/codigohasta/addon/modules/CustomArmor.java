package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;

public class CustomArmor extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Boolean> hideArmor = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-armor")
        .description("Stops armor from being rendered on entities.")
        .defaultValue(true)
        .build()
    );

    // 暂时移除材质替换相关的设置，因为 1.21.4 的材质系统过于复杂
    // 如果只需要隐藏铠甲，上面的代码就足够了

    public CustomArmor() {
        super(AddonTemplate.CATEGORY, "custom-armor", "Modify player armor rendering.");
    }
}