package com.codigohasta.addon.utils.slimefunhelper.managers.config;

public interface ConfigEnum {
    default String getConfigEnumType() { return getClass().getSimpleName(); }
    default boolean isIn(ConfigEnum... values) { for (var v: values) if (this == v) return true; return false; }
    default boolean isNotIn(ConfigEnum... values) { return !isIn(values); }
    default net.minecraft.text.Text getDisplay() { return net.minecraft.text.Text.literal(toString()); }
    @SuppressWarnings("unchecked") default <T> T cast() { return (T)this; }
}
