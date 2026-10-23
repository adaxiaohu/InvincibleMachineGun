package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.notify.ImgNotification;
import com.codigohasta.addon.utils.notify.ImgNotifications;
import com.codigohasta.addon.utils.notify.ImgNotificationType;
import com.codigohasta.addon.utils.sakura.render.Shader2DUtil;
import com.codigohasta.addon.utils.sakura.render.SakuraTextRenderer;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

import java.util.List;

public class NotificationsWidget extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgLook = settings.createGroup("外观");
    private static final float ROW_H = 13f;
    private static final float ROW_STEP = 16f;
    
    private static final float TYPE_ICON = 9f;
    private final Setting<Integer> hudY = sgGeneral.add(new IntSetting.Builder()
        .name("Y坐标").description("通知区域的锚点")
        .defaultValue(350).sliderRange(0, 2000).build());
    private final Setting<Double> hudScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("缩放").description("整体缩放。").defaultValue(1.0)
        .min(0.5).max(2.0).sliderRange(0.5, 2.0).build());
    private final Setting<Boolean> stackUp = sgGeneral.add(new BoolSetting.Builder()
        .name("向上堆叠")
        .description("堆积")
        .defaultValue(true).build());
    private final Setting<Integer> maxVisible = sgGeneral.add(new IntSetting.Builder()
        .name("最大条数").description("同时最多显示几条。")
        .defaultValue(4).min(1).max(20).sliderRange(1, 20).build());

    private final Setting<Double> fontSize = sgLook.add(new DoubleSetting.Builder()
        .name("字号").description("文字大小。").defaultValue(9.0)
        .min(5.0).max(20.0).sliderRange(5.0, 20.0).build());

    private final Setting<SettingColor> panelColor = sgLook.add(new ColorSetting.Builder()
        .name("面板颜色").description("面板背景色")
        .defaultValue(new SettingColor(0, 0, 0, 120)).build());
    private final Setting<Boolean> panelBlur = sgLook.add(new BoolSetting.Builder()
        .name("背景模糊").description("面板后方的圆角背景模糊。").defaultValue(true).build());
    private final Setting<Double> blurStrength = sgLook.add(new DoubleSetting.Builder()
        .name("模糊强度").description("越大越糊。").defaultValue(6.0).min(1.0).max(20.0)
        .sliderRange(1.0, 20.0).visible(panelBlur::get).build());
    private final Setting<Double> panelRadius = sgLook.add(new DoubleSetting.Builder()
        .name("面板圆角").description("圆角半径。").defaultValue(3.5)
        .min(0.0).max(20.0).sliderRange(0.0, 20.0).build());
    
    private final Setting<Boolean> panelBorder = sgLook.add(new BoolSetting.Builder()
        .name("面板描边").description("在面板边缘画一圈细描边。")
        .defaultValue(true).build());

    
    private static final float BORDER_WIDTH = 0.5f;
    
    private static final int BORDER_DELTA = 55;

    private final Setting<SettingColor> titleColor = sgLook.add(new ColorSetting.Builder()
        .name("文字颜色").defaultValue(new SettingColor(255, 255, 255, 255)).build());
    private final Setting<Boolean> showTypeColor = sgLook.add(new BoolSetting.Builder()
        .name("按类型着色")
        .description("用通知类型的颜色给面板描边。")
        .defaultValue(false).build());
    private final Setting<Boolean> showDivider = sgLook.add(new BoolSetting.Builder()
        .name("图标分隔线")
        .description("图标与文字之间的细竖线（截图里有）。")
        .defaultValue(true).build());

    // ── 动画2026-10-07做的动画速度要可自定义可调了~──
    
    private final Setting<Double> animSpeedMs = sgLook.add(new DoubleSetting.Builder()
        .name("入场时长")
        .description("面板从中心弹出的毫秒数。越小越快")
        .defaultValue(340.0).min(80.0).max(1200.0).sliderRange(80.0, 1200.0).build());
    
    private final Setting<Double> exitSpeedMs = sgLook.add(new DoubleSetting.Builder()
        .name("出场时长")
        .description("面板缩回中心的毫秒数。")
        .defaultValue(260.0).min(60.0).max(1200.0).sliderRange(60.0, 1200.0).build());
    
    private final Setting<Double> animElastic = sgLook.add(new DoubleSetting.Builder()
        .name("弹性强度")
        .description("入场弹出的过冲幅度")
        .defaultValue(1.0).min(0.0).max(2.0).sliderRange(0.0, 2.0).build());

    
    private final Setting<Double> notifySeconds = sgGeneral.add(new DoubleSetting.Builder()
        .name("驻留时长")
        .description("每条通知在屏幕上停留多少秒。")
        .defaultValue(1.0).min(0.5).max(15.0).sliderRange(0.5, 15.0).build());

    
    private final Setting<Boolean> chineseText = sgLook.add(new BoolSetting.Builder()
        .name("中文文案")
        .description("中文")
        .defaultValue(true).build());

    
    private final Setting<Boolean> sounds = sgLook.add(new BoolSetting.Builder()
        .name("音效")
        .description("模块开/关时播放音效。")
        .defaultValue(true).build());
    private final Setting<Double> soundVolume = sgLook.add(new DoubleSetting.Builder()
        .name("音效音量").description("0 = 静音，1 = 最大。")
        .defaultValue(1.0).min(0.0).max(1.0).sliderRange(0.0, 1.0)
        .visible(sounds::get).build());

    
    public boolean soundEnabled() {
        return sounds.get();
    }

    
    public float soundVolume() {
        return soundVolume.get().floatValue();
    }

    
    public boolean useChineseText() {
        return chineseText.get();
    }

    public NotificationsWidget() {
        super(AddonTemplate.IMG_HUD_CATEGORY, "notifications-widget",
            "在你的屏幕上画出开关模块的提示弹窗。");
    }

    @Override
    public void onDeactivate() {
        // 关掉时清队列，避免下次开启时弹出一堆陈旧通知
        ImgNotifications.INSTANCE.clear();
    }

    
    private static String iconFor(ImgNotificationType type) {
        return switch (type) {
            case ENABLED -> "on";
            case DISABLED -> "off";
            case SUCCESS -> "success";
            case WARNING -> "warning";
            case ERROR -> "error";
            case INFO -> "info";
        };
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!isActive()) return;

        ImgNotifications mgr = ImgNotifications.INSTANCE;
        mgr.setSoundVolume(sounds.get() ? soundVolume.get().floatValue() : 0f);
        com.codigohasta.addon.utils.notify.ImgNotification.ANIM_MS =
            animSpeedMs.get().floatValue();
        com.codigohasta.addon.utils.notify.ImgNotification.EXIT_MS =
            exitSpeedMs.get().floatValue();
        mgr.defaultSeconds = Math.max(1, (int) Math.round(notifySeconds.get()));
        if (mgr.isEmpty()) return;

        float scale = hudScale.get().floatValue();
        float dt = (float) event.frameTime;
        if (dt <= 0f || dt > 0.5f) dt = 1f / 60f;
        mgr.update(dt);

        List<ImgNotification> list = mgr.snapshot();
        if (list.isEmpty()) return;

        float rowH = ROW_H * scale;
        float step = ROW_STEP * scale;
        float fontPx = fontSize.get().floatValue() * scale;
        int visible = Math.min(list.size(), maxVisible.get());
        int startIdx = list.size() - visible;  

        var tc = titleColor.get();

        float panelW = 0f;

        for (int k = 0; k < visible; k++) {
            ImgNotification n = list.get(startIdx + k);
            float a = n.anim.value();
            if (a <= 0.01f) continue;

            float anim = a;  
            String title = n.title;
            String content = n.content;
            String line = title.isEmpty() ? content : title;
            float lineW = (float) SakuraTextRenderer.getWidth(line, fontPx);

            float iconW = TYPE_ICON * scale;
            float gapIcon = 3f * scale;     
            float divW = showDivider.get() ? 0.2f * scale : 0f;
            float gapText = 4f * scale;     
            float padL = 3f * scale;
            float padR = 4f * scale;
            panelW = padL + iconW + gapIcon + divW + gapText + lineW + padR;

            int order = stackUp.get() ? (visible - 1 - k) : k;
            float axisX = event.screenWidth / 2f;
            float px = axisX - panelW / 2f;
            float py = hudY.get() + order * step;
            float exit = n.exitProgress();
            float easeK;         
            if (exit < 1f) {
                easeK = 1f - com.codigohasta.addon.utils.sakura.render.CatProgress.inBack(1f - exit, 2.2f);
            } else {
                easeK = com.codigohasta.addon.utils.sakura.render.CatProgress.outElastic(
                    anim, animElastic.get().floatValue());
            }
            easeK = Math.max(0f, easeK);

            float cx = px + panelW / 2f;
            float cy = py + rowH / 2f;
            float aw = panelW * Math.min(1.15f, easeK);
            float ah = rowH * Math.min(1.15f, easeK);
            float ax = cx - aw / 2f;
            float ay = cy - ah / 2f;

    
            float enterAlpha = Math.min(1f, a * 3f);
            float exitAlpha = (exit >= 1f) ? 1f : Math.max(0f, exit * 1.6f);
            float alpha = Math.min(1f, Math.min(enterAlpha, exitAlpha));

        
            var pc = panelColor.get();
            Color bg = new Color(pc.r, pc.g, pc.b, Math.round(pc.a * alpha));
            float radius = Math.max(0f, Math.min(panelRadius.get().floatValue() * scale, ah / 2f));

            Color borderCol = null;
            float bw = 0f;
            if (panelBorder.get()) {
                if (showTypeColor.get()) {
                 
                    borderCol = new Color(
                        (n.type.rgb >> 16) & 0xFF, (n.type.rgb >> 8) & 0xFF, n.type.rgb & 0xFF,
                        Math.round(200 * alpha));
                } else {
                    int lum = (pc.r * 299 + pc.g * 587 + pc.b * 114) / 1000;
                    int d = lum < 128 ? BORDER_DELTA : -BORDER_DELTA;
                    borderCol = new Color(
                        Math.max(0, Math.min(255, pc.r + d)),
                        Math.max(0, Math.min(255, pc.g + d)),
                        Math.max(0, Math.min(255, pc.b + d)),
                        Math.round(pc.a * alpha));
                }
                bw = BORDER_WIDTH * scale * Math.min(1f, easeK);
            }

            if (panelBlur.get()) {
                Shader2DUtil.drawRoundedBlur(ax, ay, aw, ah, radius,
                    new java.awt.Color(pc.r, pc.g, pc.b, Math.max(0, Math.round(pc.a * alpha))),
                    blurStrength.get().floatValue(), 0.9f * alpha, true);
            }
            Shader2DUtil.drawRoundedRect(ax, ay, aw, ah, radius, bg, bw, borderCol);

            if (easeK < 0.12f || alpha <= 0.01f) continue;
            float k2 = easeK;
            float cFontPx = fontPx * k2;
            float cIconW = iconW * k2;
            float cGapIcon = gapIcon * k2;
            float cDivW = divW * k2;
            float cGapText = gapText * k2;
            float cPadL = padL * k2;

            float cCenterY = ay + ah / 2f;
            float contentL = ax + cPadL;

            // 3) 物品图标(可以学下怎么画出的图标这里
            if (n.icon != null) {
                float itemSize = 9f * scale * k2;           
                float itemScale = itemSize / 16f;           
                meteordevelopment.meteorclient.utils.render.RenderUtils.drawItem(
                    event.drawContext, n.icon,
                    Math.round(contentL), Math.round(cCenterY - itemSize / 2f),
                    itemScale, false, null, false);
            } else if (n.spritePath != null) {
                // 药水效果等 sprite 图标(可以学下怎么画出的图标这里
                drawSpriteIcon(event.drawContext, n.spritePath,
                    contentL, cCenterY - (TYPE_ICON * scale * k2) / 2f,
                    TYPE_ICON * scale * k2, alpha);
            }

            SakuraTextRenderer.begin();
            try {
                // 类型图标
                if (n.icon == null && n.spritePath == null) {
                    int ia = Math.round(255 * alpha);
                    Color iconTint = new Color(255, 255, 255, ia);
                    com.codigohasta.addon.utils.sakura.render.SakuraSvgIcons.draw(
                        iconFor(n.type), contentL + 0.5f * scale * k2,
                        cCenterY - cIconW / 2f, cIconW,
                        iconW,        
                        iconTint);
                }

                // 竖线
                float divX = contentL + cIconW + cGapIcon;
                if (showDivider.get()) {
                    float lineH = 8.5f * scale * k2;
                    Shader2DUtil.drawRoundedRect(divX, cCenterY - lineH / 2f,
                        Math.max(0.05f, cDivW), lineH, 0f,
                        new Color(255, 255, 255, Math.round(70 * alpha)), 0f, null);
                }

                int argb = (Math.round(tc.a * alpha) << 24) | (tc.r << 16) | (tc.g << 8) | tc.b;
                SakuraTextRenderer.draw(line, divX + cGapText,
                    cCenterY - cFontPx / 2f, cFontPx, argb);
            } finally {
                SakuraTextRenderer.end();
            }
        }
    }

    
    private void drawSpriteIcon(net.minecraft.client.gui.DrawContext ctx, String path,
                                float x, float y, float size, float alpha) {
        try {
            var atlas = mc.getAtlasManager().getAtlasTexture(net.minecraft.util.Atlases.GUI);
            if (atlas == null) return;
            var sprite = atlas.getSprite(net.minecraft.util.Identifier.of("minecraft", path));
            if (sprite == null) return;
            int a8 = Math.round(255 * Math.max(0f, Math.min(1f, alpha)));
            if (a8 <= 2) return;
            ctx.drawSpriteStretched(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,
                sprite, Math.round(x), Math.round(y),
                Math.round(size), Math.round(size), (a8 << 24) | 0xFFFFFF);
        } catch (Throwable ignored) {
        }
    }
}
