package com.codigohasta.addon.utils;

/**
 * Applies a temporary screen-space transform to Meteor 2D mesh vertices.
 *
 * <p>The transform is thread-local because HUD rendering and mesh construction happen on the
 * client render thread, and keeping the state local prevents accidental cross-thread leakage.</p>
 */
public final class ScreenVertexTransform {
    private static final ThreadLocal<State> STATE = new ThreadLocal<>();

    private ScreenVertexTransform() {
    }

    public static void beginClockwise90(double originX, double originY) {
        STATE.set(new State(originX, originY));
    }

    public static void end() {
        STATE.remove();
    }

    /**
     * Meteor's font mesh writes two vec2 attributes for every vertex: position, then UV.
     * Only the position attribute belongs in screen space and may be rotated.
     */
    public static boolean shouldTransformNextVec2() {
        State state = STATE.get();
        return state != null && state.consumePositionAttribute();
    }

    public static double transformX(double x, double y) {
        State state = STATE.get();
        return state == null ? x : state.originX - y;
    }

    public static double transformY(double x, double y) {
        State state = STATE.get();
        return state == null ? y : state.originY + x;
    }

    private static final class State {
        private final double originX;
        private final double originY;
        private boolean positionAttribute = true;

        private State(double originX, double originY) {
            this.originX = originX;
            this.originY = originY;
        }

        private boolean consumePositionAttribute() {
            boolean result = positionAttribute;
            positionAttribute = !positionAttribute;
            return result;
        }
    }
}
