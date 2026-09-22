package com.codigohasta.addon.utils.slimefunhelper.hacks.utils.config;

import java.util.function.Predicate;
public class OptionalPrimitive<T> {
    public static final Class<OptionalPrimitive<Double>> DOUBLE_TYPE=type(Double.class);
    public static final Class<OptionalPrimitive<Integer>> INT_TYPE=type(Integer.class);
    @SuppressWarnings("unchecked") public static <T> Class<OptionalPrimitive<WrapEnum<T>>> configEnum(Class<T> c) {return (Class)OptionalPrimitive.class;}
    @SuppressWarnings("unchecked") public static <T> Class<OptionalPrimitive<T>> type(Class<T> c) {return (Class)OptionalPrimitive.class;}
    public boolean present;
    public T value;
    public OptionalPrimitive(boolean present, NBTTypes.Type<T> type, T value) {this.present=present; this.value=value;}
    public boolean isPresent(){return present;}
    public T getValue(){return value;}
    public boolean test(Predicate<T> p){return present && p.test(value);}
    public T orElse(T other){return present?value:other;}
    public boolean positive(){return present && value instanceof Number n && n.doubleValue()>1E-7;}
    public OptionalPrimitive<T> withValue(T v){return new OptionalPrimitive<>(present,null,v);}
}
