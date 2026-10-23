package com.codigohasta.addon.modules;

import com.codigohasta.addon.utils.sakura.render.AbstractPanelHud;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.item.ItemStack;

public class InventoryHud extends AbstractPanelHud {

    private final meteordevelopment.meteorclient.settings.Setting<Boolean> smallCountFont =
        sgLook.add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("数量小字号")
            .description("物品数量用更小的字体")
            .defaultValue(true).build());

    private static final float PANEL_W = 145f;
    private static final float TITLE_H = 13f;
    private static final float ROW_PANEL_H = 16f;
    private static final int COLS = 9;
    private static final int ROWS = 3;
    private static final int MAIN_START = 9;
    private static final float CELL_W = PANEL_W / COLS;
    private static final float TITLE_ICON = 10f;
    private static final float TITLE_ICON_X = 2f;
    private static final float TITLE_TEXT_X = 16f;
    private static final String ICON_NAME = "inventory-widget";
    private static final float SINGLE_COUNT_DROP = 0.7f;
    private static final float PANEL_GAP = 2.5f;

    private static final float DIVIDER_W = 0.2f;
    private static final float DIVIDER_H_RATIO = 0.6f;

    private static final float CELL_ICON = 12f;

    private static final Color CELL_LINE = new Color(255, 255, 255, 40);

    public InventoryHud() {
        super("inventory-hud", "在你屏幕上画个面板告诉你背包里有什么", 718, 279, 1.0);
    }

    @Override
    protected void render(float x, float y, float scale) {
        var player = mc.player;
        if (player == null) return;

        float panelW = PANEL_W * scale;
        float titleH = TITLE_H * scale;
        float rowH = ROW_PANEL_H * scale;
        float gap = PANEL_GAP * scale;
        float cellW = CELL_W * scale;

        var inventory = player.getInventory();

        queuePanel(x, y, panelW, titleH, 1f);
        float titleFontPx = currentFontPx();
        float iconPx = TITLE_ICON * scale;
        float iconY = y + (titleH - iconPx) / 2f;
        float textY = y + (titleH - titleFontPx) / 2f;
        queueIcon(ICON_NAME, x + TITLE_ICON_X * scale, iconY, iconPx, 1f);
        queueText(titleText(), x + TITLE_TEXT_X * scale, textY, 1f);

        float rowY = y + titleH + gap;
        for (int r = 0; r < ROWS; r++) {
            queuePanel(x, rowY, panelW, rowH, 1f);

            for (int c = 0; c < COLS; c++) {
                float cx = x + c * cellW;

                if (dividersEnabled() && c < COLS - 1) {
                    float divH = rowH * DIVIDER_H_RATIO;
                    queueShape(cx + cellW, rowY + (rowH - divH) / 2f,
                        DIVIDER_W * scale, divH, 0f, dividerColorSetting(), 1f);
                }

                int slot = MAIN_START + r * COLS + c;
                ItemStack stack = inventory.getStack(slot);
                if (stack == null || stack.isEmpty()) continue;

                float iconPx2 = CELL_ICON * scale;
                float cellIconY = rowY + (rowH - iconPx2) / 2f;
                float iconX = cx + (cellW - iconPx2) / 2f;
                queueItem(stack, iconX, cellIconY, iconPx2, 1f);

                if (stack.getCount() > 1) {
                    String cnt = Integer.toString(stack.getCount());
                    float cntW = overlayTextWidth(cnt);
                    float cntH = mc.textRenderer.fontHeight;

                    float cntX;
                    float cntY;
                    if (stack.getCount() < 10) {
                        cntX = iconX + (iconPx2 - cntW) / 2f;
                        cntY = cellIconY + (iconPx2 - cntH) * SINGLE_COUNT_DROP;
                    } else {
                        cntX = iconX + iconPx2 - cntW;
                        cntY = cellIconY + iconPx2 - cntH;
                    }
                    if (smallCountFont.get()) {
                        queueOverlayTextSmall(cnt, cntX, cntY, 1f);
                    } else {
                        queueOverlayText(cnt, cntX, cntY, 1f);
                    }
                }
            }
            rowY += rowH + gap;
        }
    }

    private String titleText() {
        return com.codigohasta.addon.utils.translation.JsonEnglishTranslationManager
            .moduleText("InventoryHud", "title", "背包");
    }
}
