package com.codigohasta.addon.utils.slimefunhelper.events;

import com.codigohasta.addon.utils.slimefunhelper.events.impl.CombatPlayer;

public final class CombatListener {
    private static final Channel<CombatPlayer> pop=new Channel<>(),death=new Channel<>(),enter=new Channel<>(),leave=new Channel<>();
    public static Channel<CombatPlayer> getPlayerPopTotem(){return pop;}
    public static Channel<CombatPlayer> getPlayerDeathInfo(){return death;}
    public static Channel<CombatPlayer> getPlayerEnterVisualRange(){return enter;}
    public static Channel<CombatPlayer> getPlayerLeaveVisualRange(){return leave;}
}
