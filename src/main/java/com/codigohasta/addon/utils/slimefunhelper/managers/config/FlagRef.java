package com.codigohasta.addon.utils.slimefunhelper.managers.config;

public class FlagRef extends Ref<Boolean> {
    public static final Class<Boolean> TYPE = Boolean.class;
    public FlagRef(boolean v) { super(v); }
    public boolean get() { return getValue(); }
    public void set(boolean v) { setValue(v); }
    public void toggle() { set(!get()); }
}
