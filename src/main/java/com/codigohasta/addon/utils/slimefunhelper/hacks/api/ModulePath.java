package com.codigohasta.addon.utils.slimefunhelper.hacks.api;

public record ModulePath(String path) {
    public ModulePath add(String child) { return new ModulePath(path + "." + child); }
    public ModulePath addEnable() { return add("enable"); }
    public ModulePath addHotkey() { return add("hotkey"); }
    public String[] toPath() { return path.split("\\."); }
}
