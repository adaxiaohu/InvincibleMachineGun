package com.codigohasta.addon.utils.slimefunhelper.hacks.utils.config;

public record LabelVec2(String xLabel, String yLabel, Vec2 value) {
    public double x() {
        return value.x();
    }

    public double y() {
        return value.y();
    }

    public Vec2 getValue() {
        return value;
    }

    public Vec2 data() {
        return value;
    }

    public LabelVec2 withData(Vec2 next) {
        return new LabelVec2(xLabel, yLabel, next);
    }
}
