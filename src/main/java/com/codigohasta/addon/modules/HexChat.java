package com.codigohasta.addon.modules;

import net.minecraft.util.math.Vec3d;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

import java.awt.Color;

public class HexChat extends Module {
    
    public enum Mode {
        Static("单色"),
        Gradient("双色渐变"),
        Quad("四色渐变"),   // 新增：满足你截图里那种多种颜色的需求
        Rainbow("彩虹模式"); // 新增：自动全彩

        private final String title;

        Mode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgStyle = settings.createGroup("样式设置");

    // --- 模式设置 ---
    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("模式")
        .description("选择字体颜色的生成模式。")
        .defaultValue(Mode.Quad) // 默认改成四色，方便你体验截图效果
        .build()
    );

    // --- 颜色设置 (单色) ---
    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("单色颜色")
        .description("单色模式的颜色。")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .visible(() -> mode.get() == Mode.Static)
        .build()
    );

    // --- 颜色设置 (双色/四色) ---
    // 颜色 1
    private final Setting<SettingColor> color1 = sgGeneral.add(new ColorSetting.Builder()
        .name("颜色 1 (起始)")
        .description("渐变的起始颜色 (例如金色)。")
        .defaultValue(new SettingColor(255, 215, 0, 255)) // 金色
        .visible(() -> mode.get() == Mode.Gradient || mode.get() == Mode.Quad)
        .build()
    );

    // 颜色 2
    private final Setting<SettingColor> color2 = sgGeneral.add(new ColorSetting.Builder()
        .name("颜色 2")
        .description("渐变的第二种颜色 (例如绿色)。")
        .defaultValue(new SettingColor(17, 255, 0, 255)) // 绿色
        .visible(() -> mode.get() == Mode.Gradient || mode.get() == Mode.Quad)
        .build()
    );

    // 颜色 3 (仅四色模式)
    private final Setting<SettingColor> color3 = sgGeneral.add(new ColorSetting.Builder()
        .name("颜色 3")
        .description("渐变的第三种颜色 (例如蓝色)。")
        .defaultValue(new SettingColor(0, 191, 255, 255)) // 深天蓝
        .visible(() -> mode.get() == Mode.Quad)
        .build()
    );

    // 颜色 4 (仅四色模式)
    private final Setting<SettingColor> color4 = sgGeneral.add(new ColorSetting.Builder()
        .name("颜色 4 (结束)")
        .description("渐变的结束颜色 (例如粉色)。")
        .defaultValue(new SettingColor(255, 105, 180, 255)) // 热粉色
        .visible(() -> mode.get() == Mode.Quad)
        .build()
    );

    // --- 彩虹设置 ---
    private final Setting<Double> rainbowSpread = sgGeneral.add(new DoubleSetting.Builder()
        .name("彩虹跨度")
        .description("彩虹色在句子中变化的频率。数值越小颜色变化越慢。")
        .defaultValue(0.1)
        .min(0.01)
        .sliderMax(1.0)
        .visible(() -> mode.get() == Mode.Rainbow)
        .build()
    );

    // --- 样式设置 ---
    private final Setting<Boolean> bold = sgStyle.add(new BoolSetting.Builder()
        .name("粗体")
        .description("加粗 (&l)。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> italic = sgStyle.add(new BoolSetting.Builder()
        .name("斜体")
        .description("斜体 (&o)。")
        .defaultValue(false)
        .build()
    );
    
    private final Setting<Boolean> underline = sgStyle.add(new BoolSetting.Builder()
        .name("下划线")
        .description("下划线 (&n)。")
        .defaultValue(false)
        .build()
    );

    // --- 保护设置 ---
    private final Setting<Boolean> ignoreCommands = sgGeneral.add(new BoolSetting.Builder()
        .name("忽略指令")
        .description("忽略以 / 开头的指令 (防止指令失效)。")
        .defaultValue(true)
        .build()
    );

    public HexChat() {
        super(AddonTemplate.CATEGORY, "彩字聊天者", "让你的聊天变成炫酷的渐变色 (Hex)。当服务器有CMI插件支持&类彩字时候可以生效");
    }

    @EventHandler
    private void onMessageSend(SendMessageEvent event) {
        String message = event.message;

        // 防止指令被破坏
        if (ignoreCommands.get() && message.startsWith("/")) {
            return;
        }

        // 构建样式字符串
        StringBuilder styleBuilder = new StringBuilder();
        if (bold.get()) styleBuilder.append("&l");
        if (italic.get()) styleBuilder.append("&o");
        if (underline.get()) styleBuilder.append("&n");
        String styleSuffix = styleBuilder.toString();

        String finalMessage;

        switch (mode.get()) {
            case Static:
                finalMessage = getHexCode(color.get()) + styleSuffix + message;
                break;
            case Gradient:
                finalMessage = applyGradient(message, styleSuffix, false);
                break;
            case Quad:
                finalMessage = applyGradient(message, styleSuffix, true);
                break;
            case Rainbow:
                finalMessage = applyRainbow(message, styleSuffix);
                break;
            default:
                finalMessage = message;
        }

        // 替换原始消息
        event.message = finalMessage;
    }

    // 处理 双色 和 四色 渐变
    private String applyGradient(String text, String style, boolean isQuad) {
        StringBuilder builder = new StringBuilder();
        int length = text.length();

        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            
            // 计算当前字符在整个句子中的进度 (0.0 到 1.0)
            double progress = (length <= 1) ? 0 : (double) i / (length - 1);
            
            int r, g, b;

            if (!isQuad) {
                // --- 双色逻辑 (颜色1 -> 颜色2) ---
                r = interpolate(color1.get().r, color2.get().r, progress);
                g = interpolate(color1.get().g, color2.get().g, progress);
                b = interpolate(color1.get().b, color2.get().b, progress);
            } else {
                // --- 四色逻辑 (颜色1 -> 2 -> 3 -> 4) ---
                // 将进度分为三段: 0-0.33, 0.33-0.66, 0.66-1.0
                if (progress < 0.333) {
                    // 第一段: 颜色1 -> 颜色2
                    double subProgress = progress * 3; // 映射到 0-1
                    r = interpolate(color1.get().r, color2.get().r, subProgress);
                    g = interpolate(color1.get().g, color2.get().g, subProgress);
                    b = interpolate(color1.get().b, color2.get().b, subProgress);
                } else if (progress < 0.666) {
                    // 第二段: 颜色2 -> 颜色3
                    double subProgress = (progress - 0.333) * 3;
                    r = interpolate(color2.get().r, color3.get().r, subProgress);
                    g = interpolate(color2.get().g, color3.get().g, subProgress);
                    b = interpolate(color2.get().b, color3.get().b, subProgress);
                } else {
                    // 第三段: 颜色3 -> 颜色4
                    double subProgress = (progress - 0.666) * 3;
                    // 防止浮点数溢出导致 subProgress > 1
                    if (subProgress > 1) subProgress = 1;
                    
                    r = interpolate(color3.get().r, color4.get().r, subProgress);
                    g = interpolate(color3.get().g, color4.get().g, subProgress);
                    b = interpolate(color3.get().b, color4.get().b, subProgress);
                }
            }

            builder.append(String.format("&#%02X%02X%02X", r, g, b));
            builder.append(style);
            builder.append(c);
        }

        return builder.toString();
    }

    // 处理彩虹模式
    private String applyRainbow(String text, String style) {
        StringBuilder builder = new StringBuilder();
        int length = text.length();
        double spread = rainbowSpread.get();

        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            
            // HSB 颜色模式: 色相(H)随字符位置变化
            // 色相从 0.0 循环到 1.0
            float hue = (float) ((i * spread) % 1.0); 
            
            // 转换为 RGB
            int rgb = Color.HSBtoRGB(hue, 1.0f, 1.0f);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;

            builder.append(String.format("&#%02X%02X%02X", r, g, b));
            builder.append(style);
            builder.append(c);
        }
        return builder.toString();
    }

    // 线性插值辅助方法
    private int interpolate(int start, int end, double factor) {
        return (int) (start + (end - start) * factor);
    }

    private String getHexCode(SettingColor color) {
        return String.format("&#%02X%02X%02X", color.r, color.g, color.b);
    }
}