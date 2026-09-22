package com.codigohasta.addon.utils.slimefunhelper.hacks;

import net.minecraft.util.PlayerInput;

/** One jump, a released input tick, then one airborne jump handled by vanilla. */
public final class ElytraBotTakeoff {
    private enum Phase { READY, RELEASE, OPEN, WAIT_FOR_LANDING }

    private Object player;
    private Phase phase = Phase.READY;
    private int retryTicks;

    public PlayerInput tick(Object currentPlayer, PlayerInput input, boolean allowed, boolean onGround, boolean gliding) {
        if (player != currentPlayer) {
            reset();
            player = currentPlayer;
        }
        if (!allowed || gliding || input.jump() || input.sneak()) {
            reset();
            return input;
        }
        if (retryTicks > 0) {
            retryTicks--;
            return input;
        }
        boolean jump = false;
        switch (phase) {
            case READY -> {
                if (onGround) {
                    jump = true;
                    phase = Phase.RELEASE;
                }
            }
            case RELEASE -> phase = Phase.OPEN;
            case OPEN -> {
                if (onGround) {
                    phase = Phase.READY;
                    retryTicks = 10;
                } else {
                    jump = true;
                    phase = Phase.WAIT_FOR_LANDING;
                }
            }
            case WAIT_FOR_LANDING -> {
                if (onGround) {
                    phase = Phase.READY;
                    retryTicks = 10;
                }
            }
        }
        return jump ? new PlayerInput(input.forward(), input.backward(), input.left(), input.right(),
            true, input.sneak(), input.sprint()) : input;
    }

    public void reset() {
        player = null;
        phase = Phase.READY;
        retryTicks = 0;
    }
}
