package com.codigohasta.addon.utils.slimefunhelper.managers.config;

public class IntRef extends Ref<Integer> {
    public static final Class<Integer> TYPE = Integer.class;
    public IntRef(int v) { super(v); }
    public int get() { return getValue(); }
    public void set(int v) { setValue(v); }
    
}
