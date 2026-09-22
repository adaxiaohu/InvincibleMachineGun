package com.codigohasta.addon.utils.slimefunhelper.hacks.utils.config;

public final class NBTTypes {
    public static final Type<Integer> INT_TYPE=new Type<>();
    public static final Type<Double> DOUBLE_TYPE=new Type<>();
    public static final Type<Boolean> BOOLEAN_TYPE=new Type<>();
    public static final Type<String> STRING_TYPE=new Type<>();
    public static final Type<Object> CONFIG_ENUM_TYPE=new Type<>();
    public static final Type<Vec3> VEC3_TYPE=new Type<>();
    public static class Type<T> { @SuppressWarnings("unchecked") public <R> Type<R> cast() {return (Type<R>)this;} }
}
