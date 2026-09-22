package com.codigohasta.addon.utils.slimefunhelper.managers.config;

public class DoubleRef extends Ref<Double> {
    public static final Class<Double> TYPE = Double.class;
    public DoubleRef(double v) { super(v); }
    public double get() { return getValue(); }
    public void set(double v) { setValue(v); }
    public void set(float v) { set((double)v); }
}
