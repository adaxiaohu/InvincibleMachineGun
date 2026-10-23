package com.codigohasta.addon.utils.notify;

import meteordevelopment.meteorclient.utils.render.color.Color;

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
