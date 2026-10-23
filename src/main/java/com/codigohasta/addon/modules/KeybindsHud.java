package com.codigohasta.addon.modules;

import com.codigohasta.addon.utils.sakura.render.AbstractPanelHud;
import com.codigohasta.addon.utils.sakura.render.CatProgress;
import com.codigohasta.addon.utils.sakura.render.SakuraTextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

import java.util.ArrayList;
import java.util.List;

public class KeybindsHud extends AbstractPanelHud {

    private static final float TITLE_H = 17f;
    private static final float ROW_H = 13f;
    private static final float ROW_STEP = 14f;
    private static final float NAME_PAD = 6f;
    private static final float KEY_PAD = 8f;
    private static final float TITLE_EXTRA = 13f;
    private static final float TITLE_PAD = 24f;

    private static final float T_ICON = 10f;
    private static final float T_ICON_X = 2f;
    private static final float T_DIV_X = 14f;
    private static final float T_DIV_H = 8.5f;
    private static final float T_DIV_W = 0.2f;
    private static final float T_TEXT_X = 18f;

    private static final float CONTENT_SHOW_AT = 0.25f;

    private final SettingGroup sgKeybinds = settings.createGroup("快捷键");

    private final Setting<Boolean> onlyActive = sgKeybinds.add(new BoolSetting.Builder()
        .name("只显示已启用")
        .description("只列出当前已开启的模块（照用户说明）。")
        .defaultValue(true).build());

    private static final class Row {
        final String name;
        final String key;
        float alpha;
        boolean alive = true;
        Row(String n, String k) { this.name = n; this.key = k; }
    }

    private final java.util.Map<String, Row> rows = new java.util.LinkedHashMap<>();

    private float lastFrameDelta = 1f / 60f;

    private static final float FADE_SPEED = 6f;

    public KeybindsHud() {
        super("keybinds-hud", "在你屏幕上画一个显示快捷键的模块的面板。",
            700, 340, 1.0);
    }

    private void updateRows() {
        float dt = lastFrameDelta;

        List<Row> now = new ArrayList<>();
        for (Module m : Modules.get().getAll()) {
            if (onlyActive.get() && !m.isActive()) continue;
            var kb = m.keybind;
            if (kb == null || !kb.isSet()) continue;
            String keyName = kb.toString();
            if (keyName == null || keyName.isEmpty() || "None".equals(keyName)) continue;
            now.add(new Row(m.title, keyName));
        }
        now.sort((a, b) -> a.name.compareToIgnoreCase(b.name));

        java.util.Set<String> present = new java.util.HashSet<>();
        for (Row r : now) present.add(r.name);

        java.util.Iterator<java.util.Map.Entry<String, Row>> it = rows.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            Row row = e.getValue();
            if (present.contains(e.getKey())) {
                row.alive = true;
                row.alpha = Math.min(1f, row.alpha + dt * FADE_SPEED);
            } else {
                row.alive = false;
                row.alpha = Math.max(0f, row.alpha - dt * FADE_SPEED);
                if (row.alpha <= 0f) it.remove();
            }
        }
        for (Row r : now) {
            Row row = rows.get(r.name);
            if (row == null) {
                r.alpha = 0f;
                rows.put(r.name, r);
            }
        }

        titleAlpha = rows.isEmpty()
            ? Math.max(0f, titleAlpha - dt * FADE_SPEED)
            : Math.min(1f, titleAlpha + dt * FADE_SPEED);
    }

    private float titleAlpha;

    @Override
    protected void render(float x, float y, float scale) {
        if (mc.player == null) return;

        float dt = frameDelta();
        lastFrameDelta = (dt > 0f && dt < 0.5f) ? dt : 1f / 60f;
        updateRows();

        float fontPx = currentFontPx();
        float titleH = TITLE_H * scale;
        float rowH = ROW_H * scale;

        float screenW = (float) mc.getWindow().getScaledWidth();
        boolean alignRight = x > screenW / 2f;

        List<Row> visible = new ArrayList<>(rows.values());

        if (titleAlpha > 0.01f) {
            String title = titleText();
            float titleTextW = (float) SakuraTextRenderer.getWidth(title, fontPx);
            Row refRow = null;
            for (Row r : visible) { if (r.alive) { refRow = r; break; } }
            float titlePanelW = (refRow == null)
                ? titleTextW + TITLE_PAD * scale
                : (measureNameW(refRow, fontPx) + measureKeyW(refRow, fontPx)
                   + TITLE_EXTRA * scale);

            float e = Math.max(0f, Math.min(1.15f, CatProgress.outBack(titleAlpha)));
            float w = titlePanelW * Math.min(1f, e);
            float h = titleH * Math.min(1f, e);
            float panelX = alignRight ? (x - w) : x;
            float panelY = y + (titleH - h) / 2f;
            float alpha = titleAlpha;
            queuePanel(panelX, panelY, w, h, alpha);

            if (titleAlpha > CONTENT_SHOW_AT) {
                float k = Math.min(1f, e);
                float cx = alignRight ? (x - titlePanelW / 2f) : (x + titlePanelW / 2f);
                float cy = y + titleH / 2f;

                float iconPx = T_ICON * scale * k;
                queueIcon("keybinds-widget",
                    cx + (T_ICON_X * scale - titlePanelW / 2f) * k,
                    cy - iconPx / 2f, iconPx, alpha);

                var dc = dividerColorSetting();
                queueShape(
                    cx + (T_DIV_X * scale - titlePanelW / 2f) * k,
                    cy - T_DIV_H * scale * k / 2f,
                    T_DIV_W * scale, T_DIV_H * scale * k, 0f, dc, alpha);

                queueText(title,
                    cx + (T_TEXT_X * scale - titlePanelW / 2f) * k,
                    cy - fontPx * k / 2f, alpha);
            }
        }

        float rowY = y + titleH;
        for (Row row : visible) {
            float a = Math.max(0f, Math.min(1f, row.alpha));
            if (a <= 0.01f) { rowY += ROW_STEP * scale * a; continue; }

            float nameTextW = (float) SakuraTextRenderer.getWidth(row.name, fontPx);
            float keyTextW = (float) SakuraTextRenderer.getWidth(row.key, fontPx);
            float nameW = nameTextW + NAME_PAD * scale;
            float keyW = keyTextW + KEY_PAD * scale;
            float rowW = nameW + keyW;

            float slide = (1f - a) * rowW;
            float rowLeft = alignRight ? (x - rowW + slide) : (x + slide);

            queuePanel(rowLeft, rowY, nameW, rowH, a);
            queuePanel(rowLeft + nameW, rowY, keyW, rowH, a);

            if (a > CONTENT_SHOW_AT) {
                queueText(row.name,
                    rowLeft + (nameW - nameTextW) / 2f,
                    rowY + (rowH - fontPx) / 2f, a);

                queueText(row.key,
                    rowLeft + nameW + (keyW - keyTextW) / 2f,
                    rowY + (rowH - fontPx) / 2f, a);
            }

            rowY += ROW_STEP * scale * a;
        }
    }

    private static float measureNameW(Row r, float fontPx) {
        return (float) SakuraTextRenderer.getWidth(r.name, fontPx) + NAME_PAD;
    }

    private static float measureKeyW(Row r, float fontPx) {
        return (float) SakuraTextRenderer.getWidth(r.key, fontPx) + KEY_PAD;
    }

    private String titleText() {
        return com.codigohasta.addon.utils.translation.JsonEnglishTranslationManager
            .moduleText("KeybindsHud", "title", "快捷键");
    }
}
