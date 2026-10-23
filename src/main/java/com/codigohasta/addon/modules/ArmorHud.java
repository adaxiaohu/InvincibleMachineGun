package com.codigohasta.addon.modules;

import com.codigohasta.addon.utils.sakura.render.AbstractPanelHud;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

public class ArmorHud extends AbstractPanelHud {

    private static final float PANEL_W = 81f;
    private static final float PANEL_H = 22f;
    private static final float ICON = 16f;
    private static final float BAR_W = 15f;
    private static final float BAR_H = 2f;
    private static final float BAR_RADIUS = 1f;
    private static final float PAD_X = 1.5f;
    private static final float GAP = (PANEL_W - PAD_X * 2f - ICON * 4f) / 3f;
    private static final float ICON_DY = 1.5f;
    private static final float BAR_DY = 18.5f;

    private static final float DIVIDER_W = 0.2f;
    private static final float DIVIDER_H_RATIO = 0.6f;

    private static final EquipmentSlot[] SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final SettingGroup sgArmor = settings.createGroup("耐久条");

    private final Setting<SettingColor> lowColor = sgArmor.add(new ColorSetting.Builder()
        .name("低耐久颜色")
        .description("耐久接近 0 时的条色。")
        .defaultValue(new SettingColor(255, 255, 255, 255)).build());
    private final Setting<SettingColor> highColor = sgArmor.add(new ColorSetting.Builder()
        .name("高耐久颜色")
        .description("耐久满时的条色。中间耐久在「低/高」两色之间平滑插值。")
        .defaultValue(new SettingColor(220, 120, 169, 255)).build());
    private final Setting<Boolean> breathe = sgArmor.add(new BoolSetting.Builder()
        .name("呼吸效果")
        .description("照 CatLean 的观感，让耐久条随时间轻微明暗呼吸。"
            + "（源码是「两个主题色循环插值」；这里改做在亮度上，"
            + "以免盖掉「绿=好、红=差」的耐久语义。）")
        .defaultValue(true).build());
    private final Setting<Double> breatheSpeed = sgArmor.add(new DoubleSetting.Builder()
        .name("呼吸速度")
        .description("呼吸的快慢。源码的相位是 (时间/14) mod 854，这里换算成每秒周期数。")
        .defaultValue(0.6).min(0.1).max(3.0).sliderRange(0.1, 3.0)
        .visible(breathe::get).build());
    private final Setting<Double> breatheDepth = sgArmor.add(new DoubleSetting.Builder()
        .name("呼吸幅度")
        .description("明暗变化的强度。0 = 不呼吸，1 = 最强。")
        .defaultValue(0.35).min(0.0).max(1.0).sliderRange(0.0, 1.0)
        .visible(breathe::get).build());
    private final Setting<SettingColor> barBg = sgArmor.add(new ColorSetting.Builder()
        .name("条底颜色")
        .description("耐久条未填充部分的底色。")
        .defaultValue(new SettingColor(40, 40, 48, 200)).build());
    private final Setting<Boolean> showDividersArmor = sgArmor.add(new BoolSetting.Builder()
        .name("图标分隔线")
        .description("在 4 个装备图标之间画细竖线（样式同背包）。")
        .defaultValue(true).build());

    public ArmorHud() {
        super("armor-hud", "装备耐久 HUD（模仿 CatLean 的 armor-widget）。", 582, 483, 0.8);
    }

    @Override
    protected void render(float x, float y, float scale) {
        var player = mc.player;
        if (player == null) return;

        float panelW = PANEL_W * scale;
        float panelH = PANEL_H * scale;
        float icon = ICON * scale;
        float gap = GAP * scale;
        float barW = BAR_W * scale;
        float barH = BAR_H * scale;
        float barRadius = Math.min(BAR_RADIUS * scale, barH / 2f);

        queuePanel(x, y, panelW, panelH, 1f);

        float breatheT = 0f;
        if (breathe.get()) {
            double ms = System.currentTimeMillis();
            double hz = breatheSpeed.get();
            breatheT = (float) ((ms / 1000.0 * hz) % 1.0);
        }
        float pulse = (float) Math.sin(breatheT * Math.PI * 2.0) * 0.5f + 0.5f;
        float depth = breathe.get() ? breatheDepth.get().floatValue() : 0f;
        float bright = 1f - depth * 0.5f + depth * pulse;

        for (int i = 0; i < SLOTS.length; i++) {
            ItemStack stack = player.getEquippedStack(SLOTS[i]);
            boolean empty = stack == null || stack.isEmpty();

            float ix = x + PAD_X * scale + i * (icon + gap);
            float iy = y + ICON_DY * scale;

            if (!empty) {
                queueItem(stack, ix, iy, icon, 1f);
            }

            float ratio = empty ? 0f : durabilityRatio(stack);
            queueShape(ix, y + BAR_DY * scale, barW, barH, barRadius,
                colorOf(barBg.get(), bright, 1f), 1f);
            if (ratio > 0f) {
                queueShape(ix, y + BAR_DY * scale, barW * ratio, barH, barRadius,
                    durabilityColor(ratio, bright), 1f);
            }

            if (showDividersArmor.get() && i < SLOTS.length - 1) {
                float divX = ix + icon + gap / 2f;
                float blockTop = y + ICON_DY * scale;
                float blockH = (BAR_DY + BAR_H - ICON_DY) * scale;
                float divH = blockH * DIVIDER_H_RATIO;
                queueShape(divX - DIVIDER_W * scale / 2f,
                    blockTop + (blockH - divH) / 2f,
                    DIVIDER_W * scale, divH, 0f, dividerColorSetting(), 1f);
            }
        }
    }

    private static float durabilityRatio(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0f;
        if (stack.getMaxDamage() <= 0) return 1f;
        int remaining = stack.getMaxDamage() - stack.getDamage();
        return Math.max(0f, Math.min(1f, remaining / (float) stack.getMaxDamage()));
    }

    private Color durabilityColor(float ratio, float bright) {
        var lo = lowColor.get();
        var hi = highColor.get();
        int r = Math.round(lo.r + (hi.r - lo.r) * ratio);
        int g = Math.round(lo.g + (hi.g - lo.g) * ratio);
        int b = Math.round(lo.b + (hi.b - lo.b) * ratio);
        int a = Math.round(lo.a + (hi.a - lo.a) * ratio);
        return colorOf(new SettingColor(r, g, b, a), bright, 1f);
    }

    private static Color colorOf(SettingColor c, float bright, float alphaMul) {
        return new Color(
            clamp(c.r * bright),
            clamp(c.g * bright),
            clamp(c.b * bright),
            clamp(c.a * alphaMul));
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }
}
