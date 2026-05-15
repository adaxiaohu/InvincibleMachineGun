package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

import java.util.ArrayList;
import java.util.List;

public class Panic extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> restoreOnDisable = sgGeneral.add(new BoolSetting.Builder()
        .name("restore-on-disable")
        .description("关闭模块时恢复之前禁用的功能")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> whitelist = sgGeneral.add(new StringSetting.Builder()
        .name("whitelist")
        .description("白名单模块名, 用逗号分隔")
        .defaultValue("ClickGui,HUD,Offhand")
        .build()
    );

    private final List<Module> disabledModules = new ArrayList<>();

    public Panic() {
        super(AddonTemplate.CATEGORY, "我没有开挂", "紧急停止所有功能, 类似于紧急开关。启用时会关闭除白名单外的所有模块。来自AlienV4的Panic模块。");
    }

    @Override
    public void onActivate() {
        disabledModules.clear();

        String[] whitelistNames = whitelist.get().split(",");
        List<String> whitelistModules = new ArrayList<>();
        for (String name : whitelistNames) {
            whitelistModules.add(name.trim().toLowerCase());
        }

        for (Module module : Modules.get().getAll()) {
            if (module != this && !isWhitelisted(module, whitelistModules) && module.isActive()) {
                disabledModules.add(module);
                module.toggle();
            }
        }

        info("已紧急停止 " + disabledModules.size() + " 个功能");
    }

    @Override
    public void onDeactivate() {
        if (restoreOnDisable.get() && !disabledModules.isEmpty()) {
            int restoredCount = 0;
            for (Module module : disabledModules) {
                if (!module.isActive()) {
                    module.toggle();
                    restoredCount++;
                }
            }
            info("已恢复 " + restoredCount + " 个功能");
        }
        disabledModules.clear();
    }

    @Override
    public String getInfoString() {
        return disabledModules.isEmpty() ? "" : String.valueOf(disabledModules.size());
    }

    public void panicNow() {
        if (!isActive()) {
            toggle();
        }
    }

    public void restoreNow() {
        if (isActive()) {
            toggle();
        }
    }

    private boolean isWhitelisted(Module module, List<String> whitelistModules) {
        if (module == null) return false;
        for (String whitelistName : whitelistModules) {
            if (module.name.equalsIgnoreCase(whitelistName)) {
                return true;
            }
        }
        return false;
    }
}
