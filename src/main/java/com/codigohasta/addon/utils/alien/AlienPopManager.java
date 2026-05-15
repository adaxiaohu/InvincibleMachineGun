package com.codigohasta.addon.utils.alien;

import java.util.HashMap;

public class AlienPopManager {
    public final HashMap<String, Integer> popContainer = new HashMap<>();

    public int getPop(String name) {
        return popContainer.getOrDefault(name, 0);
    }

    public void onTotemPop(String playerName) {
        int count = popContainer.getOrDefault(playerName, 0) + 1;
        popContainer.put(playerName, count);
    }

    public void onDeath(String playerName) {
        popContainer.remove(playerName);
    }
}
