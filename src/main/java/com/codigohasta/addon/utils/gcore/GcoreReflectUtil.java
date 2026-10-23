package com.codigohasta.addon.utils.gcore;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

public final class GcoreReflectUtil {

    private static final ConcurrentHashMap<String, Field> fieldMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Method> methodMap = new ConcurrentHashMap<>();

    private GcoreReflectUtil() {
    }

    public static Object invokeMethod(String methodName, Field field, Object fromObj) {
        try {
            Object obj = field.get(fromObj);
            Method method = obj.getClass().getMethod(methodName);
            Object result = method.invoke(obj);
            methodMap.put(methodName, method);
            return result;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 取（并缓存）{@code fromObj} 所在类的声明字段，强制可访问。
     *
     * <p>缓存 key 同样只是字段名。
     */
    public static Field getField(String fieldName, Object fromObj) {
        if (fieldMap.containsKey(fieldName)) {
            return fieldMap.get(fieldName);
        }
        try {
            Field field = fromObj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            fieldMap.put(fieldName, field);
            return field;
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}
