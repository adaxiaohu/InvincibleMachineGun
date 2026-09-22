package com.codigohasta.addon.utils.slimefunhelper.hacks.utils.config;
public final class EntrySet<T> {
public final Regex regex;private transient net.minecraft.registry.Registry<T> registry;
public EntrySet(Regex regex,net.minecraft.registry.Registry<T> registry){this.regex=regex;this.registry=registry;}
@SuppressWarnings("unchecked") public static <T> Class<EntrySet<T>> parameter(){return (Class)EntrySet.class;}
public boolean test(T value){return regex.test(net.minecraft.registry.Registries.BLOCK.getId((net.minecraft.block.Block)value).getPath());}
}