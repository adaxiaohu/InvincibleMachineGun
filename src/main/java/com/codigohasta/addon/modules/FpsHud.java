package com.codigohasta.addon.modules;

import com.codigohasta.addon.utils.sakura.render.AbstractRowHud;
import net.minecraft.util.math.MathHelper;

public class FpsHud extends AbstractRowHud {

    private static final float LERP = 0.35f;

    private int sampledFps;
    private float displayFps;

    public FpsHud() {
        super("fps-hud", "帧率 HUD。", 815, 455, 0.9);
    }

    @Override
    protected String iconName() {
        return "fps-widget";
    }

    @Override
    protected void onTickUpdate() {
        sampledFps = mc.getCurrentFps();
    }

    @Override
    protected String displayText() {
        displayFps = MathHelper.lerp(LERP, displayFps, sampledFps);
        return Math.round(displayFps) + " fps";
    }
}

