package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.gcore.GcoreServerEquivMove;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;

import java.util.LinkedHashSet;
import java.util.Set;
public class TPServerEquivalent extends Module {
    private static volatile TPServerEquivalent INSTANCE;
    private final SettingGroup sgModules = settings.createGroup("通用");
    private final Setting<String> registry = sgModules.add(new StringSetting.Builder()
        .name("生效模块")
        .description("有服务端等价模式的 TP 模块内部名。")
        .defaultValue("TpAnchorAura,GLightTpAnchorAura,GAntiLag,GcoreGTrap,GcoreWebAura,GcoreTpCrystalAura,GAntiExplosion,GSuperVClip,GTpbot,GTpaura,TpAura")
        .build());
    private volatile Set<String> registered = Set.of();
    private volatile String cachedRegistryRaw = null;
    private final Setting<Boolean> logDecisions = sgModules.add(new BoolSetting.Builder()
        .name("判定日志")
        .description("诊断日志把每次可达性判定的中间量写入 gcore-servequiv.log。 开发用的")
        .defaultValue(false)
        .onChanged(v -> GcoreServerEquivMove.setDiagEnabled(v))
        .build());

    public TPServerEquivalent() {
        super(AddonTemplate.CATEGORY, "TP 服务端等价",
            "开了就不回弹哈哈。");
        INSTANCE = this;
        GcoreServerEquivMove.setGate(TPServerEquivalent::isEnabledFor);
    }

    @Override
    public void onActivate() {
        INSTANCE = this;
        this.refreshRegistry();
        GcoreServerEquivMove.setDiagEnabled(this.logDecisions.get());
        this.info("已启用；生效模块 = %s；诊断日志 = %b",
            this.registry.get(), this.logDecisions.get());
    }

    @Override
    public void onDeactivate() {

        GcoreServerEquivMove.setDiagEnabled(false);
    }
    public static boolean isEnabledFor(String moduleName) {
        TPServerEquivalent inst = INSTANCE;
        if (inst == null || !inst.isActive() || moduleName == null) {
            return false;
        }
        return inst.resolveRegistered().contains(moduleName);
    }

    private Set<String> resolveRegistered() {
        String raw = this.registry.get();
        if (!raw.equals(this.cachedRegistryRaw)) {
            Set<String> set = new LinkedHashSet<>();
            for (String s : raw.split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) {
                    set.add(t);
                }
            }
            this.registered = Set.copyOf(set);
            this.cachedRegistryRaw = raw;
        }
        return this.registered;
    }

    private void refreshRegistry() {
        this.cachedRegistryRaw = null;
        this.resolveRegistered();
    }
}
