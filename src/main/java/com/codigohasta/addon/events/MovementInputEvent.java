package com.codigohasta.addon.events;

import net.minecraft.client.player.ClientInput;

public class MovementInputEvent {
    private static final MovementInputEvent INSTANCE = new MovementInputEvent();

    public ClientInput input;

    public static MovementInputEvent get(ClientInput input) {
        INSTANCE.input = input;
        return INSTANCE;
    }
}