package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.sakura.render.SakuraSkinRenderer;
import com.codigohasta.addon.utils.sakura.render.SakuraSvgIcons;
import com.codigohasta.addon.utils.sakura.render.SakuraTextRenderer;
import com.codigohasta.addon.utils.sakura.render.Shader2DUtil;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerListHud extends Module {
    
    private static final class Row {
        AbstractClientPlayerEntity player;
        boolean alive;
        float alpha;
        float marqueeOffset;
        float marqueeDir = 1f;
        float marqueePause;
    }

    // ═══════════ 设置 ═══════════

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgLayout = settings.createGroup("布局");
    private final SettingGroup sgLook = settings.createGroup("外观");

    private final Setting<Boolean> hudEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("启用").description("显示玩家列表 HUD。").defaultValue(true).build());
    private final Setting<Integer> hudX = sgGeneral.add(new IntSetting.Builder()
        .name("X坐标").description("锚点 X（逻辑坐标；左右自适应决定贴左缘还是右缘）。")
        .defaultValue(805).sliderRange(0, 2000).build());
    private final Setting<Integer> hudY = sgGeneral.add(new IntSetting.Builder()
        .name("Y坐标").description("锚点 Y（逻辑坐标）。")
        .defaultValue(8).sliderRange(0, 2000).build());
    private final Setting<Double> hudScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("缩放").description("整体缩放。").defaultValue(0.9)
        .min(0.5).max(2.0).sliderRange(0.5, 2.0).build());

    // ── 布局 ──
    private final Setting<Integer> maxRows = sgLayout.add(new IntSetting.Builder()
        .name("最大行数").description("最多显示多少名玩家。").defaultValue(15)
        .min(1).max(100).sliderRange(1, 100).build());
    private final Setting<Double> maxNameWidth = sgLayout.add(new DoubleSetting.Builder()
        .name("名字最大宽度").description("名字列的最长显示宽度，超出后来回滚动。")
        .defaultValue(90.0).min(30.0).max(300.0).sliderRange(30.0, 300.0).build());
    private final Setting<SortMode> sortMode = sgLayout.add(new EnumSetting.Builder<SortMode>()
        .name("排序方式").description("玩家行的排序。")
        .defaultValue(SortMode.Name).build());
    private final Setting<Double> marqueeSpeed = sgLayout.add(new DoubleSetting.Builder()
        .name("滚动速度").description("名字滚动速度（逻辑像素/秒）。")
        .defaultValue(30.0).min(5.0).max(120.0).sliderRange(5.0, 120.0).build());
    private final Setting<Double> marqueeGap = sgLayout.add(new DoubleSetting.Builder()
        .name("两端停顿").description("滚到两端后各停留多久（秒）。")
        .defaultValue(0.8).min(0.0).max(3.0).sliderRange(0.0, 3.0).build());

    public enum SortMode {
        Name,
        Distance,
        Health,
        None
    }

    // ── 显示列开关 ──
    private final Setting<Boolean> showHp = sgLayout.add(new BoolSetting.Builder()
        .name("血量").description("显示血量列（含吸收）。").defaultValue(true).build());
    private final Setting<Boolean> showDist = sgLayout.add(new BoolSetting.Builder()
        .name("距离").description("显示距离列。").defaultValue(true).build());
    private final Setting<Boolean> showPing = sgLayout.add(new BoolSetting.Builder()
        .name("延迟").description("显示延迟列。").defaultValue(true).build());

    // ── 外观（照水印的默认）──
    private final Setting<SettingColor> panelColor = sgLook.add(new ColorSetting.Builder()
        .name("面板颜色").description("标题与每行面板的背景色。")
        .defaultValue(new SettingColor(0, 0, 0, 120)).build());
    private final Setting<Boolean> panelBlur = sgLook.add(new BoolSetting.Builder()
        .name("背景模糊").description("面板后方的圆角背景模糊。").defaultValue(true).build());
    private final Setting<Double> blurStrength = sgLook.add(new DoubleSetting.Builder()
        .name("模糊强度").description("越大越糊。").defaultValue(20.0).min(1.0).max(20.0)
        .sliderRange(1.0, 20.0).visible(panelBlur::get).build());
    private final Setting<Double> panelRadius = sgLook.add(new DoubleSetting.Builder()
        .name("面板圆角").description("圆角半径。").defaultValue(3.051470588235289)
        .min(0.0).max(20.0).sliderRange(0.0, 20.0).build());
    private final Setting<Double> panelHeight = sgLook.add(new DoubleSetting.Builder()
        .name("行面板高度").description("每行面板的高度。").defaultValue(13.0)
        .min(8.0).max(30.0).sliderRange(8.0, 30.0).build());
    private final Setting<Double> titleHeight = sgLook.add(new DoubleSetting.Builder()
        .name("标题面板高度")
        .description("标题面板的高度。。")
        .defaultValue(16.0).min(8.0).max(40.0).sliderRange(8.0, 40.0).build());
    private final Setting<Double> fontSize = sgLook.add(new DoubleSetting.Builder()
        .name("字号").description("面板里的文字大小。").defaultValue(9.0)
        .min(5.0).max(20.0).sliderRange(5.0, 20.0).build());
    private final Setting<Double> iconSize = sgLook.add(new DoubleSetting.Builder()
        .name("图标大小").description("标题里小人图标的边长。")
        .defaultValue(10.0).min(4.0).max(24.0).sliderRange(4.0, 24.0).build());
    private final Setting<Boolean> dividers = sgLook.add(new BoolSetting.Builder()
        .name("分隔竖线").description("在图标与文字之间、数值与头像之间画细竖线。")
        .defaultValue(true).build());
    private final Setting<Double> dividerWidth = sgLook.add(new DoubleSetting.Builder()
        .name("竖线粗细").description("分隔竖线的宽度（ 0.5）。")
        .defaultValue(0.2).min(0.2).max(3.0).sliderRange(0.2, 3.0)
        .visible(dividers::get).build());
    private final Setting<Double> dividerHeight = sgLook.add(new DoubleSetting.Builder()
        .name("竖线高度").description("分隔竖线的高度（ 8.5）。")
        .defaultValue(8.5).min(2.0).max(24.0).sliderRange(2.0, 24.0)
        .visible(dividers::get).build());
    private final Setting<SettingColor> dividerColor = sgLook.add(new ColorSetting.Builder()
        .name("竖线颜色").description("分隔竖线的颜色。")
        .defaultValue(new SettingColor(255, 255, 255, 70))
        .visible(dividers::get).build());

    private final Setting<Boolean> panelBorder = sgLook.add(new BoolSetting.Builder()
        .name("面板描边")
        .description("在面板边缘画一圈细描边")
        .defaultValue(true).build());

    
    private static final float BORDER_WIDTH = 0.5f;
    
    private static final int BORDER_DELTA = 55;

    private final Setting<Boolean> shadowEnabled = sgLook.add(new BoolSetting.Builder()
        .name("阴影").description("面板边缘的羽化投影。").defaultValue(false).build());
    private final Setting<SettingColor> shadowColor = sgLook.add(new ColorSetting.Builder()
        .name("阴影颜色").defaultValue(new SettingColor(255, 0, 182, 255))
        .visible(shadowEnabled::get).build());
    private final Setting<Double> shadowFeather = sgLook.add(new DoubleSetting.Builder()
        .name("阴影羽化")
        .description("面板阴影的羽化半径。")
        .defaultValue(2.0).min(0.0).max(40.0).sliderRange(0.0, 40.0)
        .visible(shadowEnabled::get).build());

    // ═══════════ 状态 ═══════════

    
    private final Map<UUID, Row> rows = new HashMap<>();
    private float lastFrameDelta = 1f / 60f;

    
    private String titleText() {
        return com.codigohasta.addon.utils.translation.JsonEnglishTranslationManager
            .moduleText("PlayerListHud", "title", "玩家列表");
    }

    public PlayerListHud() {
        super(AddonTemplate.IMG_HUD_CATEGORY, "player-list-hud", "在你屏幕上画一个玩家列表，随着身边玩家更新。");
    }

    // ═══════════ 渲染 ═══════════

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!hudEnabled.get()) return;
        if (mc.player == null || mc.world == null) return;

        float dt = (float) event.frameTime;
        lastFrameDelta = (dt > 0f && dt < 0.5f) ? dt : 1f / 60f;

        float scale = hudScale.get().floatValue();
        float anchorX = hudX.get();
        float y = hudY.get();

        // ── 1. 收集本帧玩家──
        List<AbstractClientPlayerEntity> players = new ArrayList<>();
        for (var p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            if (p instanceof AbstractClientPlayerEntity acp) players.add(acp);
        }

        // ── 2. 排序）──
        SortMode sort = sortMode.get();
        if (sort == SortMode.Name) {
            players.sort(Comparator.comparing(p -> p.getName().getString(), String.CASE_INSENSITIVE_ORDER));
        } else if (sort == SortMode.Distance) {
            players.sort(Comparator.comparingDouble(p -> mc.player.distanceTo(p)));
        } else if (sort == SortMode.Health) {
            players.sort(Comparator.comparingDouble(p -> p.getHealth() + p.getAbsorptionAmount()));
        }
        if (players.size() > maxRows.get()) players = players.subList(0, maxRows.get());

        // ── 3. 进行 alpha（含出场）与名字滚动 ──
        updateRows(players);

        // ── 4. 尺寸 ──
        float fontPx = fontSize.get().floatValue() * scale;
        float panelH = panelHeight.get().floatValue() * scale;
        float titleH = titleHeight.get().floatValue() * scale;
        float rowRadius = Math.max(0f, Math.min(panelRadius.get().floatValue() * scale, panelH / 2f));
        float titleRadius = Math.max(0f, Math.min(panelRadius.get().floatValue() * scale, titleH / 2f));
        final float ROW_GAP = 2.5f;
        float rowStep = panelH + ROW_GAP * scale;
        float firstRowY = y + titleH + ROW_GAP * scale;
        float titlePad = 24f * scale;       
        float avatarOuter = 10f * scale;    
        float iconPx = iconSize.get().floatValue() * scale;  
        float divW = dividerWidth.get().floatValue() * scale;   
        float divH = dividerHeight.get().floatValue() * scale; 
        var dc = dividerColor.get();
        Color divColor = new Color(dc.r, dc.g, dc.b, dc.a);

        String title = titleText();
        float titleW = (float) SakuraTextRenderer.getWidth(title, fontPx);

        // ── 5. 本帧要画的行存活玩家 正在出场的行。
        List<Row> drawRows = new ArrayList<>();
        Set<UUID> present = new HashSet<>();
        for (AbstractClientPlayerEntity p : players) {
            present.add(p.getUuid());
            Row row = rows.get(p.getUuid());
            if (row != null) drawRows.add(row);
        }
        for (Row row : rows.values()) {
            if (!row.alive) drawRows.add(row);   // 出场中的行排在存活行之后
        }

        // ── 6. 逐行算文本与宽度 ──
        int n = drawRows.size();
        String[] statTexts = new String[n];
        float[] nameWidths = new float[n];
        float[] rowTextWidths = new float[n]; 
        float nameColW = maxNameWidth.get().floatValue() * scale;
        final float TEXT_OFF_X   = 3f;    
        final float AVATAR_OFF_X = 9f;    
        final float ROW_PAD      = 21f;  
        final float RIGHT_PAD    = 2f;    

        // 单位口径
        //   {@link SakuraTextRenderer#getWidth} 返回【逻辑像素】（源码 `width / scaleFactor`），
        //   而 Shader2DUtil / SakuraTextRenderer 的绘制坐标收的也是逻辑像素。规则：
        //     · getWidth() 的结果（rowTextWidths…）= 逻辑像素 → 【不乘 scale】
        //     · 源码常量（3 / 9 / 21 / 10…）是设计单位 → 【必须乘 scale】
        //   一个表达式里所有加数必须同口径；混用偏差 = textW×(1−scale)，
        //   scale=1 时完全看不出来。所以用的时候就知道逻辑像素和实际看的像素尺寸要换算的啊。

        float avOffXpx = AVATAR_OFF_X * scale;   
        float layoutW  = ROW_PAD * scale;      
        float panelPad = avOffXpx + avatarOuter + RIGHT_PAD * scale;
        final float ICON_DIV_GAP = 3f;    
        final float DIV_TEXT_GAP = 3f;   

        for (int i = 0; i < n; i++) {
            Row row = drawRows.get(i);
            AbstractClientPlayerEntity p = row.player;

            StringBuilder sb = new StringBuilder();
            if (showHp.get()) {
                int hp = Math.round(p.getHealth() + p.getAbsorptionAmount()); 
                sb.append(" | ").append(hp).append(" hp");
            }
            if (showDist.get()) {
                sb.append(" | ").append(Math.round(mc.player.distanceTo(p))).append(" m");
            }
            if (showPing.get()) {
                sb.append(" | ").append(pingOf(p)).append(" ms");
            }
            statTexts[i] = sb.toString();

            String name = p.getName().getString();
            float nameW = (float) SakuraTextRenderer.getWidth(name, fontPx);
            nameWidths[i] = nameW;
            float thisNameCol = Math.min(nameW, nameColW);

            float statW = (float) SakuraTextRenderer.getWidth(statTexts[i], fontPx);
            rowTextWidths[i] = thisNameCol + statW;
        }

        // ── 7. 左右自适应
        float screenW = (float) mc.getWindow().getScaledWidth();
        boolean alignRight = anchorX > screenW / 2f;

        var pc = panelColor.get();
        Color bg = new Color(pc.r, pc.g, pc.b, pc.a);
        Color white = new Color(255, 255, 255, 255);
        Color shadow = new Color(shadowColor.get().r, shadowColor.get().g,
            shadowColor.get().b, shadowColor.get().a);

        float titlePanelW = titleW + titlePad;
        float groupW = iconPx + ICON_DIV_GAP * scale + divW + DIV_TEXT_GAP * scale + titleW;
        float groupX = (titlePanelW - groupW) / 2f;   // 整组在面板内的起始偏移（水平居中）
        float titleX = alignRight ? anchorX - titlePanelW : anchorX;
        drawPanel(titleX, y, titlePanelW, titleH, titleRadius, bg, 1f, shadow);
        float rowY = firstRowY;
        for (int i = 0; i < n; i++) {
            Row row = drawRows.get(i);
            float a = row.alpha;
            if (a <= 0.004f) continue;
            float rowW = rowTextWidths[i] + layoutW;
            float rowX = alignRight ? anchorX - rowW : anchorX;
            float drawW = rowTextWidths[i] + panelPad;

            Color rowBg = new Color(pc.r, pc.g, pc.b, Math.round(pc.a * a));
            drawPanel(rowX, rowY, drawW, panelH, rowRadius, rowBg, a,
                new Color(shadow.r, shadow.g, shadow.b, Math.round(shadow.a * a)));

            rowY += rowStep * a; 
        }

        // ═══ 第二遍：所有文字与图标 ═══
        SakuraTextRenderer.begin();
        try {
            float iconX = titleX + groupX;
            float divX = iconX + iconPx + ICON_DIV_GAP * scale;
            float titleTextX = divX + divW + DIV_TEXT_GAP * scale;
            float titleIconY = y + (titleH - iconPx) / 2f;
            float titleDivY = y + (titleH - divH) / 2f;
            float titleTextY = y + (titleH - fontPx) / 2f;
            SakuraSvgIcons.draw("player-list-widget", iconX, titleIconY, iconPx, white);
            if (dividers.get()) {
                drawDivider(divX, titleDivY, divW, divH, divColor, 1f);
            }
            SakuraTextRenderer.draw(title, titleTextX, titleTextY, fontPx, 0xFFFFFFFF);

            // 行
            rowY = firstRowY;
            for (int i = 0; i < n; i++) {
                Row row = drawRows.get(i);
                float a = row.alpha;
                if (a <= 0.004f) continue;

                AbstractClientPlayerEntity p = row.player;
                float rowW = rowTextWidths[i] + layoutW;
                float rowX = alignRight ? anchorX - rowW : anchorX;
                float textW = rowTextWidths[i];
                float ty = rowY + (panelH - fontPx) / 2f;
                int argb = (Math.round(255 * a) << 24) | 0xFFFFFF;
                String name = p.getName().getString();
                float nameX = rowX + TEXT_OFF_X * scale;
                float thisNameCol = Math.min(nameWidths[i], nameColW);
                if (nameWidths[i] <= nameColW) {
                    SakuraTextRenderer.draw(name, nameX, ty, fontPx, argb);
                } else {
                    SakuraTextRenderer.drawClipped(name, nameX - row.marqueeOffset, ty, fontPx, argb,
                        nameX, nameX + nameColW);
                }

                SakuraTextRenderer.draw(statTexts[i], nameX + thisNameCol, ty, fontPx, argb);
                float avatarX = rowX + textW + avOffXpx;
                if (dividers.get()) {
                    float textRight = rowX + textW + TEXT_OFF_X * scale;
                    float midX = (textRight + avatarX) / 2f;
                    drawDivider(midX - divW / 2f, rowY + (panelH - divH) / 2f,
                        divW, divH, divColor, a);
                }

                float avatarY = rowY + (panelH - avatarOuter) / 2f;

                Identifier skin = null;
                try {
                    skin = p.getSkin().body().texturePath();
                } catch (Throwable ignored) {
                }
                boolean drawn = skin != null && SakuraSkinRenderer
                    .drawPlayerHeadLayered(skin, avatarX, avatarY, avatarOuter, 0f, a);
                if (!drawn) {
                    Shader2DUtil.drawRoundedRect(avatarX, avatarY, avatarOuter, avatarOuter,
                        2f * scale, new Color(70, 70, 70, Math.round(255 * a)), 0f, null);
                }

                rowY += rowStep * a;
            }
        } finally {
            SakuraTextRenderer.end();
        }
    }

    
    private void drawDivider(float x, float y, float w, float h, Color color, float alpha) {
        Color c = new Color(color.r, color.g, color.b,
            Math.round(color.a * Math.max(0f, Math.min(1f, alpha))));
        Shader2DUtil.drawRoundedRect(snapX(x), y, w, h, 0f, c, 0f, null);
    }

    
    private float snapX(float logical) {
        float sf = (float) mc.getWindow().getScaleFactor();
        if (sf <= 0f) return logical;
        return Math.round(logical * sf) / sf;
    }

    
    private void drawPanel(float x, float y, float w, float h, float radius,
                           Color bg, float alpha, Color shadow) {
        if (panelBlur.get()) {
            Shader2DUtil.drawRoundedBlur(x, y, w, h, radius,
                new java.awt.Color(bg.r, bg.g, bg.b, bg.a),
                blurStrength.get().floatValue(), 0.9f * alpha, true);
        }
        if (shadowEnabled.get() && shadow.a > 0) {
            Shader2DUtil.drawRoundedShadow(x, y, w, h, radius, shadow,
                shadowFeather.get().floatValue(), 0f, 0f);
        }
        Color borderCol = null;
        float bw = 0f;
        if (panelBorder.get()) {
            int lum = (bg.r * 299 + bg.g * 587 + bg.b * 114) / 1000;
            int d = lum < 128 ? BORDER_DELTA : -BORDER_DELTA;
            borderCol = new Color(
                Math.max(0, Math.min(255, bg.r + d)),
                Math.max(0, Math.min(255, bg.g + d)),
                Math.max(0, Math.min(255, bg.b + d)), bg.a);
            bw = BORDER_WIDTH * hudScale.get().floatValue();
        }
        Shader2DUtil.drawRoundedRect(x, y, w, h, radius, bg, bw, borderCol);
    }

    // ═══════════ 行动画 ═══════════

    
    private void updateRows(List<AbstractClientPlayerEntity> players) {
        float speed = 6f; 

        Set<UUID> present = new HashSet<>();
        for (AbstractClientPlayerEntity p : players) present.add(p.getUuid());

        Iterator<Map.Entry<UUID, Row>> it = rows.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Row> e = it.next();
            Row row = e.getValue();
            if (present.contains(e.getKey())) {
                row.alive = true;
                row.alpha = Math.min(1f, row.alpha + lastFrameDelta * speed);
            } else {
                row.alive = false;   
                row.alpha = Math.max(0f, row.alpha - lastFrameDelta * speed);
                if (row.alpha <= 0f) it.remove();
            }
        }

        // 新行 + 推进滚动
        float marqueeSpeed = this.marqueeSpeed.get().floatValue();
        float gapTime = marqueeGap.get().floatValue();
        float nameColW = maxNameWidth.get().floatValue() * hudScale.get().floatValue();
        float fontPx = fontSize.get().floatValue() * hudScale.get().floatValue();

        for (AbstractClientPlayerEntity p : players) {
            Row row = rows.get(p.getUuid());
            if (row == null) {
                row = new Row();
                row.player = p;
                row.alive = true;
                row.alpha = 0f;
                rows.put(p.getUuid(), row);
            } else {
                row.player = p;   
            }
            updateMarquee(row, p, nameColW, fontPx, marqueeSpeed, gapTime);
        }
    }

    
    private void updateMarquee(Row row, AbstractClientPlayerEntity p,
                               float nameColW, float fontPx, float speed, float gapTime) {
        float nameW = (float) SakuraTextRenderer.getWidth(p.getName().getString(), fontPx);
        float overflow = nameW - nameColW;
        if (overflow <= 0.5f) {
            row.marqueeOffset = 0f;
            row.marqueeDir = 1f;
            row.marqueePause = 0f;
            return;
        }

        if (row.marqueePause > 0f) {
            row.marqueePause = Math.max(0f, row.marqueePause - lastFrameDelta);
            return;
        }

        row.marqueeOffset += row.marqueeDir * speed * lastFrameDelta;
        if (row.marqueeOffset >= overflow) {
            row.marqueeOffset = overflow;
            row.marqueeDir = -1f;
            row.marqueePause = gapTime;
        } else if (row.marqueeOffset <= 0f) {
            row.marqueeOffset = 0f;
            row.marqueeDir = 1f;
            row.marqueePause = gapTime;
        }
    }

    
    private int pingOf(AbstractClientPlayerEntity p) {
        PlayerListEntry entry = mc.getNetworkHandler() != null
            ? mc.getNetworkHandler().getPlayerListEntry(p.getUuid()) : null;
        return entry != null ? entry.getLatency() : 0;
    }
}
