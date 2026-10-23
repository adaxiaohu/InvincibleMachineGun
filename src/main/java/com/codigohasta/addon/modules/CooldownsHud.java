package com.codigohasta.addon.modules;

import com.codigohasta.addon.mixin.CooldownInstanceAccessor;
import com.codigohasta.addon.mixin.ItemCooldownManagerAccessor;
import com.codigohasta.addon.utils.sakura.render.AbstractPanelHud;
import com.codigohasta.addon.utils.sakura.render.CatProgress;
import com.codigohasta.addon.utils.sakura.render.SakuraTextRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CooldownsHud extends AbstractPanelHud {

    private static final float TITLE_ANIM_MS = 200f;
    private static final float ROW_ANIM_MS = 140f;
    private static final float ROW_STAGGER_MS = 55f;
    private static final float ITEM_SHOW_AT = 0.30f;
    private static final float CONTENT_SHOW_AT = 0.25f;

    private static final float T_ICON = 10f;
    private static final float T_ICON_X = 2f;
    private static final float T_DIV_X = 14f;
    private static final float T_DIV_H = 8.5f;
    private static final float T_DIV_W = 0.2f;
    private static final float T_TEXT_X = 18f;
    private static final float T_PAD = 24f;

    private final CatProgress titleAnim = new CatProgress(TITLE_ANIM_MS);
    private final List<CatProgress> rowAnims = new ArrayList<>();
    private final List<Row> rowCache = new ArrayList<>();

    private static final class Row {
        ItemStack stack;
        String name;
        int remainingTicks;
    }

    private List<Row> rows = List.of();

    public CooldownsHud() {
        super("cooldowns-hud", "物品冷却 HUD（模仿 CatLean 的 cooldowns-widget）。", 20, 300, 1.0);
    }

    @Override
    protected void onTickUpdate() {
        rebuild();
    }

    private void rebuild() {
        if (mc.player == null) {
            rows = List.of();
            return;
        }
        var manager = mc.player.getItemCooldownManager();
        if (!(manager instanceof ItemCooldownManagerAccessor acc)) {
            rows = List.of();
            return;
        }

        var inventory = mc.player.getInventory();
        int now = acc.getTick();

        Map<Identifier, Row> byGroup = new LinkedHashMap<>();

        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty()) continue;
            if (!manager.isCoolingDown(stack)) continue;

            Identifier group = manager.getGroup(stack);
            if (byGroup.containsKey(group)) continue;

            Row row = new Row();
            row.stack = stack;
            row.name = stack.getName().getString();
            row.remainingTicks = remainingTicks(acc, group, now);
            byGroup.put(group, row);
        }

        rows = List.copyOf(byGroup.values());
    }

    private static int remainingTicks(ItemCooldownManagerAccessor acc, Identifier group, int now) {
        Object entry = acc.getEntries().get(group);
        if (entry == null) {
            diagOnce("entries 里没有 group=" + group);
            return 0;
        }
        if (!(entry instanceof CooldownInstanceAccessor cia)) {
            diagOnce("entry 类型不符: " + entry.getClass());
            return 0;
        }
        int end = cia.getEndTime();
        int rem = Math.max(0, end - now);
        diagOnce("OK group=" + group + " endTick=" + end + " tick=" + now + " remaining=" + rem);
        return rem;
    }

    private static boolean diagDone;
    private static void diagOnce(String msg) {
        if (diagDone) return;
        diagDone = true;
        com.codigohasta.addon.AddonTemplate.LOG.info("[CooldownsHud diag] {}", msg);
    }

    @Override
    protected void render(float x, float y, float scale) {
        float fontPx = currentFontPx();
        float titleH = 13f * scale;
        float rowH = 16f * scale;
        float gap = 2.5f * scale;

        float screenW = (float) mc.getWindow().getScaledWidth();
        boolean alignRight = x > screenW / 2f;

        float dt = frameDelta();
        titleAnim.animateTo(rows.isEmpty() ? 0f : 1f);
        titleAnim.update(dt);

        int rowCount = rows.size();
        while (rowAnims.size() < rowCount) {
            rowAnims.add(new CatProgress(ROW_ANIM_MS, rowAnims.size() * ROW_STAGGER_MS));
            rowCache.add(null);
        }
        for (int i = 0; i < rowCount; i++) rowCache.set(i, rows.get(i));

        int total = rowAnims.size();
        for (int i = 0; i < total; i++) {
            boolean live = i < rowCount;
            CatProgress a = rowAnims.get(i);
            if (!live && a.value() > 0.01f) {
                a.setDelay((total - 1 - i) * ROW_STAGGER_MS / 1000f);
            }
            a.animateTo(live ? 1f : 0f);
            a.update(dt);
        }
        while (!rowAnims.isEmpty() && rowAnims.size() > rowCount
               && rowAnims.get(rowAnims.size() - 1).value() <= 0.001f) {
            rowAnims.remove(rowAnims.size() - 1);
            rowCache.remove(rowCache.size() - 1);
        }

        total = rowAnims.size();

        float tRaw = titleAnim.value();
        if (tRaw > 0.01f) {
            String title = titleText();
            float titleTextW = (float) SakuraTextRenderer.getWidth(title, fontPx);
            float titlePanelW = titleTextW + T_PAD * scale;

            float e = Math.max(0f, Math.min(1.15f, CatProgress.outBack(tRaw)));
            float w = titlePanelW * Math.min(1f, e);
            float h = titleH * Math.min(1f, e);
            float panelX = alignRight ? (x - w) : x;
            float panelY = y + (titleH - h) / 2f;
            float alpha = Math.min(1f, tRaw * 2f);

            queuePanel(panelX, panelY, w, h, alpha);

            if (tRaw > CONTENT_SHOW_AT) {
                float k = Math.min(1f, e);
                float cx = alignRight ? (x - titlePanelW / 2f) : (x + titlePanelW / 2f);
                float cy = y + titleH / 2f;
                float iconPx = T_ICON * scale * k;
                queueIcon("cooldowns-widget",
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

        float rowY = y + titleH + gap;
        for (int i = 0; i < total; i++) {
            boolean live = i < rowCount;
            float raw = Math.max(0f, Math.min(1f, rowAnims.get(i).value()));

            Row row = live ? rows.get(i) : rowCache.get(i);
            if (row == null) { rowY += rowH + gap; continue; }
            if (raw <= 0.01f) { rowY += rowH + gap; continue; }

            float rt = CatProgress.outQuint(raw);
            float alpha = Math.min(1f, raw * 2f);

            float iconW = rowH;
            String timeText = formatTime(row.remainingTicks);
            float timeW = (float) SakuraTextRenderer.getWidth(timeText, fontPx) + 10f * scale;
            float nameW = (float) SakuraTextRenderer.getWidth(row.name, fontPx) + 12f * scale;
            float rowW = iconW + nameW + timeW;

            float slide = (1f - rt) * rowW;
            float rowLeft = alignRight ? (x - rowW + slide) : (x + slide);

            queuePanel(rowLeft, rowY, iconW, rowH, alpha);
            queuePanel(rowLeft + iconW, rowY, nameW, rowH, alpha);
            queuePanel(rowLeft + iconW + nameW, rowY, timeW, rowH, alpha);

            if (raw >= ITEM_SHOW_AT) {
                float itemPx = 12f * scale;
                queueItem(row.stack,
                    rowLeft + (iconW - itemPx) / 2f,
                    rowY + (rowH - itemPx) / 2f,
                    itemPx, alpha);
            }
            if (raw > CONTENT_SHOW_AT) {
                queueText(row.name,
                    rowLeft + iconW + 6f * scale,
                    rowY + (rowH - fontPx) / 2f, alpha);
            }
            if (raw > CONTENT_SHOW_AT) {
                float timeTextW = (float) SakuraTextRenderer.getWidth(timeText, fontPx);
                queueText(timeText,
                    rowLeft + iconW + nameW + (timeW - timeTextW) / 2f,
                    rowY + (rowH - fontPx) / 2f, alpha);
            }

            rowY += rowH + gap;
        }
    }

    private static String formatTime(int ticks) {
        int seconds = Math.max(0, ticks) / 20;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private String titleText() {
        return com.codigohasta.addon.utils.translation.JsonEnglishTranslationManager
            .moduleText("CooldownsHud", "title", "冷却");
    }
}
