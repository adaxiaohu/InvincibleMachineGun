package com.codigohasta.addon.utils.gcore;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 反射工具。
 *
 * <p>移植自 {@code dev.godden.core.Utils.ReflectUtil}（GCore）。
 * 源是 Kotlin `object`，Java 里改为静态方法。
 *
 * <p><b>为什么移植它但新代码不调用</b>：GCore 用它反射拿
 * {@code ClientWorld.pendingUpdateManager} 再调 {@code incrementSequence()} /
 * {@code getSequence()}（因为 LiquidBounce 不暴露该字段）。
 *
 * <p>本项目是 **Meteor + Yarn 1.21.11**，这两个方法是**公开的**
 * （`ClientWorld.getPendingUpdateManager()` → `method_41925`），
 * 所以 {@link GcorePlayerActionUtil} 已改成**直调**，不再需要反射。
 *
 * <p>本类仍按「依赖闭包完整」保留（用户要求「所用到的所有工具」），
 * 且带缓存的实现照抄源逻辑。
 */
public final class GcoreReflectUtil {

    private static final ConcurrentHashMap<String, Field> fieldMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Method> methodMap = new ConcurrentHashMap<>();

    private GcoreReflectUtil() {
    }

    /**
     * 调用 {@code field} 所指对象上的无参方法。
     *
     * <p>源实现：先 `field.get(fromObj)` 取出对象，再对它调 `methodName`。
     * 注意**源的缓存 key 只是方法名**（不含类），所以不同类上的同名方法会冲突 ——
     * 这是源的实现，保留。
     */
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
