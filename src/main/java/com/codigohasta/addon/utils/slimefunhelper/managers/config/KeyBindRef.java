package com.codigohasta.addon.utils.slimefunhelper.managers.config;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.misc.Keybind;
public class KeyBindRef extends Ref<Keybind> {
    public final Setting<Keybind> setting;
    public final Runnable callback;
    private boolean pressed;
    public Setting<Keybind> modifier;
    public boolean allowInScreen;
    public KeyBindRef(Setting<Keybind> setting, Runnable callback) { super(setting.get()); this.setting=setting; this.callback=callback; }
    public void poll() {
        var mc=net.minecraft.client.MinecraftClient.getInstance();
        boolean permitted=mc.currentScreen==null||allowInScreen;
        boolean down=permitted&&setting.get().isSet()&&setting.get().isPressed()
            &&(modifier==null||!modifier.get().isSet()||modifier.get().isPressed());
        if(down&&!pressed&&callback!=null)callback.run();
        pressed=down;
    }
}
