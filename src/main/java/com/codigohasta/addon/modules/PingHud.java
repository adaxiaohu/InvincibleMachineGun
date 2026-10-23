package com.codigohasta.addon.modules;

import com.codigohasta.addon.utils.sakura.render.AbstractRowHud;
import net.minecraft.client.network.PlayerListEntry;

public class PingHud extends AbstractRowHud {

    private static final int UNKNOWN = -1;

    public PingHud() {
        super("ping-hud", "延迟 HUD（模仿 CatLean 的 ping-widget）。", 815, 474, 0.9);
    }

    @Override
    protected String iconName() {
        return "ping-widget";
    }

    @Override
    protected String displayText() {
        if (mc.player == null) return null;

        int latency = UNKNOWN;
        if (mc.getNetworkHandler() != null) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry != null) latency = entry.getLatency();
        }

        return latency == UNKNOWN ? "--" : latency + " ms";
    }
}
