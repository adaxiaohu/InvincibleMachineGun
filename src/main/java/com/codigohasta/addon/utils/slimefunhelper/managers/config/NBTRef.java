package com.codigohasta.addon.utils.slimefunhelper.managers.config;

public class NBTRef<T> extends Ref<T> {
    public NBTRef(T value) { super(value); }
    public T get() { return getValue(); }
    public void set(T value) { setValue(value); }
}
