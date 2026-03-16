package com.codigohasta.addon.modules;

import net.minecraft.util.math.Vec3d;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class ChatPrefixCustom extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgStyle = settings.createGroup("文字样式");

    public enum ColorMode {
        固定,
        渐变
    }

    // --- 基础设置 ---
    private final Setting<String> prefixText = sgGeneral.add(new StringSetting.Builder()
        .name("前缀文字")
        .defaultValue("IMG")
        .build()
    );

    private final Setting<ColorMode> colorMode = sgGeneral.add(new EnumSetting.Builder<ColorMode>()
        .name("颜色模式")
        .defaultValue(ColorMode.固定)
        .build()
    );

    private final Setting<SettingColor> prefixColor = sgGeneral.add(new ColorSetting.Builder()
        .name("前缀颜色 (起)")
        .defaultValue(new SettingColor(255, 170, 0, 255))
        .build()
    );

    private final Setting<SettingColor> prefixColorEnd = sgGeneral.add(new ColorSetting.Builder()
        .name("前缀颜色 (终)")
        .defaultValue(new SettingColor(255, 0, 255, 255))
        .visible(() -> colorMode.get() == ColorMode.渐变)
        .build()
    );

    // --- 样式设置 ---
    private final Setting<Boolean> bold = sgStyle.add(new BoolSetting.Builder().name("加粗").defaultValue(true).build());
    private final Setting<Boolean> italic = sgStyle.add(new BoolSetting.Builder().name("斜体").defaultValue(false).build());
    private final Setting<Boolean> underline = sgStyle.add(new BoolSetting.Builder().name("下划线").defaultValue(false).build());
    private final Setting<Boolean> strikethrough = sgStyle.add(new BoolSetting.Builder().name("删除线").defaultValue(false).build());

    // --- 纯净的开关变量，不包含复杂的内部逻辑 ---
    private final Setting<Boolean> forceBtn = sgGeneral.add(new BoolSetting.Builder()
        .name("强制暴力覆盖")
        .description("点击后会强行修改内存中的meteor前缀。")
        .defaultValue(false)
        .build()
    );

    public ChatPrefixCustom() {
        super(AddonTemplate.CATEGORY, "改前缀术", "修改并美化全端前缀，支持渐变和多种样式。就是把聊天框关于meteor的功能反馈的紫色meteor字样改成你设置的文字，装B用");
    }

    // 在每一 Tick 里去检查开关状态，完美避开自引用报错
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (forceBtn.get()) {
            runForceUpdate();
            forceBtn.set(false); // 执行完后弹回开关
        }
    }

    @SuppressWarnings("unchecked")
    private void runForceUpdate() {
        // 修改原生 Config
        try {
            Setting<String> pText = (Setting<String>) Config.get().settings.get("prefix");
            if (pText != null) pText.set(prefixText.get());

            Setting<SettingColor> pColor = (Setting<SettingColor>) Config.get().settings.get("prefix-color");
            if (pColor != null) pColor.set(prefixColor.get());
        } catch (Exception ignored) {}

        // 构造复杂的富文本对象
        MutableText customPrefix = Text.literal("[").formatted(Formatting.GRAY);
        
        String text = prefixText.get();
        if (text.isEmpty()) text = "IMG";

        // 逐字处理渐变和样式
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            MutableText charText = Text.literal(String.valueOf(c));
            
            // 计算颜色
            int color;
            if (colorMode.get() == ColorMode.渐变 && text.length() > 1) {
                float progress = (float) i / (text.length() - 1);
                color = getGradientColor(prefixColor.get(), prefixColorEnd.get(), progress);
            } else {
                color = prefixColor.get().getPacked();
            }

            // 应用样式和颜色
            Style style = Style.EMPTY.withColor(color)
                .withBold(bold.get())
                .withItalic(italic.get())
                .withUnderline(underline.get())
                .withStrikethrough(strikethrough.get());
            
            charText.setStyle(style);
            customPrefix.append(charText);
        }

        customPrefix.append(Text.literal("] ").formatted(Formatting.GRAY));

        // 反射修改 Lotus
        String[] chatUtilClasses = {
            "xiaohe66.meteor.lotus.utils.ChatUtils",
            "meteordevelopment.meteorclient.utils.player.ChatUtils",
            "meteordevelopment.meteorclient.utils.ChatUtils"
        };

        boolean success = false;
        for (String className : chatUtilClasses) {
            try {
                Class<?> clazz = Class.forName(className);
                if (modifyPrefixField(clazz, customPrefix)) success = true;
            } catch (Exception ignored) {}
        }

        if (success) info("§a[√] 炫彩前缀已强制覆盖！");
        else warning("§6[!] 未找到字段，常规修改已尝试。");
    }

    // 计算渐变插值
    private int getGradientColor(SettingColor start, SettingColor end, float progress) {
        int r = interpolate(start.r, end.r, progress);
        int g = interpolate(start.g, end.g, progress);
        int b = interpolate(start.b, end.b, progress);
        int a = interpolate(start.a, end.a, progress);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int interpolate(int start, int end, float progress) {
        return (int) (start + (end - start) * progress);
    }

    private boolean modifyPrefixField(Class<?> clazz, Text newPrefix) {
        try {
            for (Field field : clazz.getDeclaredFields()) {
                if ((field.getName().equalsIgnoreCase("PREFIX") || field.getName().equalsIgnoreCase("prefix")) 
                    && Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    try {
                        Field modifiersField = Field.class.getDeclaredField("modifiers");
                        modifiersField.setAccessible(true);
                        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
                    } catch (Exception ignored) {}
                    field.set(null, newPrefix);
                    return true;
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    @Override
    public void onActivate() {
        runForceUpdate();
    }
}