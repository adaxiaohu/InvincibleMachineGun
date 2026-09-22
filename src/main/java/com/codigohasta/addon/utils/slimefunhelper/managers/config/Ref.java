package com.codigohasta.addon.utils.slimefunhelper.managers.config;

import java.util.function.*;
public class Ref<T> {
    protected T value;
    private Supplier<T> reader;
    private Consumer<T> writer;
    public Ref(T value) { this.value = value; }
    public T getValue() { return reader == null ? value : reader.get(); }
    public void setValue(T v) { if (writer == null) value = v; else writer.accept(v); }
    public void connect(Supplier<T> reader, Consumer<T> writer) { this.reader = reader; this.writer = writer; }
}
