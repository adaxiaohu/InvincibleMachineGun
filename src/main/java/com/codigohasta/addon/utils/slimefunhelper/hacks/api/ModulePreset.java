package com.codigohasta.addon.utils.slimefunhelper.hacks.api;

public enum ModulePreset {HACKING,VANILLA,AC_COMMON,AC_VULCAN,AC_GRIM_LEGACY,AC_GRIM,AC_MATRIX;
    public boolean hasAC() { return !(this == VANILLA || this == AC_VULCAN); }
}
