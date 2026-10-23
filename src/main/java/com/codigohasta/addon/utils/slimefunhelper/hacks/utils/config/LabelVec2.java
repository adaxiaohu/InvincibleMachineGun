package com.codigohasta.addon.utils.slimefunhelper.hacks.utils.config;

/**
 * A {@link Vec2} whose two components carry translation keys, so each half can be labelled
 * independently in the settings UI.
 *
 * <p>Ported from SlimefunHelper 1.1.7.2 {@code me.matl114.hacks.utils.config.LabelVec2}.
 * Upstream builds a two-field editor from {@code SubScreenWidget}/{@code ButtonElement};
 * IMG renders structured settings as a single JSON field via
 * {@code ConfigBuilder.ObjectSetting}, so this keeps the data and the labels and matches the
 * existing {@link LabelVec3} shape.
 *
 * <p>The label fields are the keys used for the per-field tooltips, e.g.
 * {@code widget.elytra-bot.mace-fix.extra-bonus} and
 * {@code widget.elytra-bot.mace-fix.predict-multiply}.
 */
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
