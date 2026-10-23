package com.codigohasta.addon.utils.notify;

import meteordevelopment.meteorclient.utils.render.color.Color;

/**
 * 通知类型。
 *
 * <p>✅ 照 CatLean 的 {@code ys} 枚举（字节码确证的 6 个常量）：
 * <pre>
 *   ysArray[0] = SUCCESS;
 *   ysArray[1] = INFO;
 *   ysArray[2] = WARNING;
 *   ysArray[3] = ERROR;
 *   ysArray[4] = ENABLED;
 *   ysArray[5] = DISABLED;
 * </pre>
 *
 * <p>CatLean 的 {@code sg} 为每种类型各持一个图标对象
 * （{@code sg.C()/B()/L()/A()/x()/g()} 六个 {@code bj} 字段），
 * 但这些图标<b>是资源引用、具体文件名在加密字符串里</b>。
 * 本类用<b>颜色</b>区分类型（更直观、且不依赖未知资源），
 * 并在设置里允许覆盖。
 */
public enum ImgNotificationType {
    SUCCESS(0x4CD964),
    INFO(0x4A9EFF),
    WARNING(0xFFB020),
    ERROR(0xFF4D4D),
    ENABLED(0x53D769),
    DISABLED(0x9E9E9E);

    /** 该类型的默认主色（RGB）。 */
    public final int rgb;

    ImgNotificationType(int rgb) {
        this.rgb = rgb;
    }

    /** 转成 render Color（不透明）。 */
    public Color color() {
        return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255);
    }

    /** 供命令解析用（大小写不敏感）。找不到返回 null。 */
    public static ImgNotificationType byName(String name) {
        if (name == null) return null;
        for (ImgNotificationType t : values()) {
            if (t.name().equalsIgnoreCase(name)) return t;
        }
        return null;
    }
}
