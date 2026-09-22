package com.codigohasta.addon.utils.slimefunhelper.hacks.utils;

import com.codigohasta.addon.utils.slimefunhelper.managers.config.FlagRef;
public final class HotKeyUtils {
    public static Runnable wrapAsHandler(Runnable action){return action;}
    public static Runnable wrapFlagAsToggle(String[] path,FlagRef flag){return flag::toggle;}
}
