package com.codigohasta.addon.modules;

import com.codigohasta.addon.utils.sakura.render.AbstractRowHud;
import com.codigohasta.addon.utils.sakura.render.SakuraTextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.world.World;

public class CoordHud extends AbstractRowHud {

    private static final float GAP_SCREEN_PX = 8f;

    private final Setting<Boolean> showConverted = sgGeneral.add(new BoolSetting.Builder()
        .name("显示换算坐标").description("在坐标后面用方括号显示另一个维度的对应坐标。")
        .defaultValue(true).build());
    private final Setting<Boolean> onlyNether = sgGeneral.add(new BoolSetting.Builder()
        .name("只在下界").description("只在处于下界时显示，其他维度不显示。")
        .defaultValue(false).build());

    public CoordHud() {
        super("coord-hud", "坐标 HUD（模仿 CatLean 的 coord-widget）。", 812, 438, 0.9);
    }

    @Override
    protected String iconName() {
        return "coord-widget";
    }

    @Override
    protected String displayText() {
        if (mc.player == null || mc.world == null) return null;

        boolean nether = mc.world.getRegistryKey() == World.NETHER;
        if (onlyNether.get() && !nether) return null;

        var p = mc.player;
        int bx = p.getBlockX();
        int by = p.getBlockY();
        int bz = p.getBlockZ();

        String gap = coordinateGap();

        StringBuilder sb = new StringBuilder();
        sb.append(bx).append(gap).append(by).append(gap).append(bz);

        if (showConverted.get()) {
            int cx;
            int cz;
            if (nether) {
                cx = bx * 8;
                cz = bz * 8;
            } else {
                cx = Math.floorDiv(bx, 8);
                cz = Math.floorDiv(bz, 8);
            }
            sb.append(" [").append(cx).append(' ').append(cz).append(']');
        }
        return sb.toString();
    }

    private String coordinateGap() {
        double sf = mc.getWindow().getScaleFactor();
        if (sf <= 0.0) sf = 1.0;
        float fontPx = currentFontPx();
        if (fontPx <= 0f) return " ";

        double wantLogical = GAP_SCREEN_PX / sf;

        final int[] cands = {0x2002, 0x2000, 0x2009, 0x2005, 0x2004, 0x0020};

        String bestGap = null;
        double bestErr = Double.MAX_VALUE;

        for (int cp : cands) {
            String unit = new String(Character.toChars(cp));
            double unitW = SakuraTextRenderer.getWidth(unit, fontPx);
            if (unitW <= 0.0001) continue;

            int n = (int) Math.max(1, Math.round(wantLogical / unitW));
            if (n > 8) continue;
            double gap = n * unitW;
            double err = Math.abs(gap - wantLogical);
            if (err < bestErr) {
                bestErr = err;
                bestGap = unit.repeat(n);
            }
        }

        return bestGap != null ? bestGap : " ";
    }
}
