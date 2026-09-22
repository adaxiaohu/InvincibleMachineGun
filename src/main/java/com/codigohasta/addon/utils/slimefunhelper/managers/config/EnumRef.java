package com.codigohasta.addon.utils.slimefunhelper.managers.config;

public class EnumRef<T> extends NBTRef<T> {
    public EnumRef(T value) { super(value); }
    @SuppressWarnings("unchecked") public void next() { Enum<?> v=(Enum<?>)get(); Object[] values=v.getDeclaringClass().getEnumConstants(); set((T)values[(v.ordinal()+1)%values.length]); }
}
