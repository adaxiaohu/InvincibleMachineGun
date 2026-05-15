package com.codigohasta.addon.events;

import java.awt.Color;

public class TotemParticleEvent {
    private static final TotemParticleEvent INSTANCE = new TotemParticleEvent();

    public double velocityX;
    public double velocityY;
    public double velocityZ;
    public Color color;
    public boolean cancelled;

    public static TotemParticleEvent get(double velocityX, double velocityY, double velocityZ) {
        INSTANCE.velocityX = velocityX;
        INSTANCE.velocityY = velocityY;
        INSTANCE.velocityZ = velocityZ;
        INSTANCE.color = null;
        INSTANCE.cancelled = false;
        return INSTANCE;
    }

    public void cancel() {
        this.cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
