package com.codigohasta.addon.modules;

import com.codigohasta.addon.utils.sakura.render.AbstractRowHud;
import com.codigohasta.addon.utils.sakura.render.TpsSampler;

import java.util.Locale;

public class TpsHud extends AbstractRowHud {

    public TpsHud() {
        super("tps-hud", "在你屏幕画个面板，告诉你服务器tps多少", 815, 493, 0.9);
    }

    @Override
    protected String iconName() {
        return "tps-widget";
    }

    @Override
    protected void onTickUpdate() {
        TpsSampler.INSTANCE.onTick();
    }

    @Override
    protected String displayText() {
        TpsSampler s = TpsSampler.INSTANCE;

        if (!s.hasEnoughSamples() || s.sinceLastSampleMs() > 3000L) {
            return "-- (--) t/s";
        }
        return String.format(Locale.ROOT, "%.1f (%.1f) t/s", s.average(), s.instant());
    }
}
