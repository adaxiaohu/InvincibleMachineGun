package com.codigohasta.addon.utils.slimefunhelper.hacks.api;

import java.util.ArrayList;
import java.util.List;

/**
 * sfIMG config-path migration table.
 *
 * <h2>Why this exists</h2>
 * Meteor persists a setting as the pair {@code (SettingGroup.name, Setting.name)} and
 * looks it up by those exact strings when loading:
 *
 * <pre>
 *   Settings.fromTag    :179  getGroup(groupTag.getString("name"))
 *   SettingGroup.fromTag :83  get(settingTag.getString("name"))
 *   SettingGroup.get     :30  setting.name.equals(name)
 * </pre>
 *
 * IMG derives both halves from a config path in {@code ConfigBuilder.build()}:
 *
 * <pre>
 *   ConfigBuilder.java:28-30
 *     full  = "combat-bot.elytra-bot.mace-height"
 *     group = full minus last segment   -> "combat-bot.elytra-bot"
 *     name  = last segment              -> "mace-height"
 * </pre>
 *
 * Upstream 1.1.7.2 regrouped many settings by inserting a sub-group, e.g.
 * {@code combat-bot.elytra-bot.mace-height} became
 * {@code combat-bot.elytra-bot.mace-height-and-pull-up.mace-height}. That changes the
 * GROUP name, so the saved value can no longer be found and the user silently reverts
 * to the default. Upstream handles this with {@code BaseModule.portConfigs} plus a
 * {@code ConfigManager} load hook; IMG has neither, so the table lives here and
 * {@link BaseModule#fromTag} applies it.
 *
 * <h2>Shape of a move</h2>
 * Entries map a full OLD setting path to a full NEW setting path. From those we derive
 * a (group, name-prefix) pair on each side, because the operation is
 * <em>move a setting between groups</em>, not <em>rename a group</em>: the source group
 * usually still exists and still holds other settings.
 *
 * <p>Matching is by name PREFIX ("name" or "name-suffix") because one config path can
 * expand to several persisted settings: compact optionals store
 * {@code <name>-enabled} and {@code <name>-value} (ConfigBuilder.java:78-79), keybinds add
 * {@code <name>-modifier} (:38), and structured values persist JSON under the same name.
 */
public final class SfConfigMigration {

    private SfConfigMigration() {}

    /** One setting move: old group + old name prefix -> new group + new name prefix. */
    public static final class Move {
        public final String oldGroup;
        public final String oldName;
        public final String newGroup;
        public final String newName;

        Move(String oldFull, String newFull) {
            int d = oldFull.lastIndexOf('.');
            this.oldGroup = d < 0 ? "" : oldFull.substring(0, d);
            this.oldName = d < 0 ? oldFull : oldFull.substring(d + 1);
            int e = newFull.lastIndexOf('.');
            this.newGroup = e < 0 ? "" : newFull.substring(0, e);
            this.newName = e < 0 ? newFull : newFull.substring(e + 1);
        }
    }

    private static final List<Move> MOVES = new ArrayList<>();

    private static void mv(String oldFull, String newFull) {
        MOVES.add(new Move(oldFull, newFull));
    }

    static {
        // ================= ElytraBot sub-grouping (upstream portConfigs) =================
        mv("combat-bot.elytra-bot.mace-combat-use-extra-attack",
           "combat-bot.elytra-bot.mace-attack-settings.mace-combat-use-extra-attack");
        mv("combat-bot.elytra-bot.mace-combat-consider-use",
           "combat-bot.elytra-bot.mace-attack-settings.mace-combat-consider-use");
        // The setting is now declared (with its onPostPlayerTick consumer), so the move is real.
        mv("combat-bot.elytra-bot.mace-post-attack",
           "combat-bot.elytra-bot.mace-attack-settings.mace-post-attack");

        mv("combat-bot.elytra-bot.mace-height",
           "combat-bot.elytra-bot.mace-height-and-pull-up.mace-height");
        mv("combat-bot.elytra-bot.mace-height-ground",
           "combat-bot.elytra-bot.mace-height-and-pull-up.mace-height-ground");
        mv("combat-bot.elytra-bot.mace-max-extra-pull-up-tick",
           "combat-bot.elytra-bot.mace-height-and-pull-up.mace-max-extra-pull-up-tick");
        // NOTE: leaf renames too - the setting itself was renamed on the way in.
        mv("combat-bot.elytra-bot.combat-smooth-flight",
           "combat-bot.elytra-bot.mace-height-and-pull-up.combat-smooth-flight-pullup");
        mv("combat-bot.elytra-bot.combat-smooth-flight-argument-1",
           "combat-bot.elytra-bot.mace-height-and-pull-up.combat-smooth-flight-circle-extra-range");
        mv("combat-bot.elytra-bot.combat-smooth-flight-argument-1-1",
           "combat-bot.elytra-bot.mace-height-and-pull-up.combat-smooth-flight-out-pull-ratio");
        mv("combat-bot.elytra-bot.combat-smooth-flight-argument-1-6",
           "combat-bot.elytra-bot.mace-height-and-pull-up.combat-smooth-flight-out-pull-ratio-enable-distance");
        mv("combat-bot.elytra-bot.combat-smooth-flight-argument-1-5",
           "combat-bot.elytra-bot.mace-height-and-pull-up.combat-smooth-flight-out-pull-ratio-use-horizontal-distance");
        mv("combat-bot.elytra-bot.combat-smooth-flight-argument-1-4",
           "combat-bot.elytra-bot.mace-height-and-pull-up.combat-smooth-flight-dynamic-circle-extra-range");
        mv("combat-bot.elytra-bot.combat-smooth-flight-2",
           "combat-bot.elytra-bot.mace-height-and-pull-up.combat-smooth-flight-pullup-attack");
        mv("combat-bot.elytra-bot.combat-smooth-flight-argument-2-1",
           "combat-bot.elytra-bot.mace-height-and-pull-up.combat-smooth-flight-pull-attack-max-horizontal");
        mv("combat-bot.elytra-bot.combat-smooth-flight-argument-2-2",
           "combat-bot.elytra-bot.mace-height-and-pull-up.combat-smooth-flight-pull-attack-max-relative-y");

        mv("combat-bot.elytra-bot.mace-max-follow-height",
           "combat-bot.elytra-bot.mace-follow-and-attack.mace-max-follow-height");
        mv("combat-bot.elytra-bot.mace-y-level-lerp",
           "combat-bot.elytra-bot.mace-follow-and-attack.mace-y-level-lerp");
        // Upstream moved the setting but registered no portConfigs for it (one of the gaps
        // found during the audit). Migrated anyway so users keep the value.
        mv("combat-bot.elytra-bot.follow-on-ground-height-extra",
           "combat-bot.elytra-bot.mace-follow-and-attack.follow-on-ground-height-extra");

        mv("combat-bot.elytra-bot.combat-angle-optimize",
           "combat-bot.elytra-bot.angle-optimizing-flight.combat-angle-optimize-pullup");
        mv("combat-bot.elytra-bot.combat-angle-optimize-follow",
           "combat-bot.elytra-bot.angle-optimizing-flight.combat-angle-optimize-follow");
        mv("combat-bot.elytra-bot.combat-angle-optimize-range",
           "combat-bot.elytra-bot.angle-optimizing-flight.combat-angle-optimize-range");
        mv("combat-bot.elytra-bot.combat-angle-optimize-radical",
           "combat-bot.elytra-bot.angle-optimizing-flight.combat-angle-optimize-radical-pullup");
        mv("combat-bot.elytra-bot.combat-angle-optimize-radical-pull-up-optimize-range",
           "combat-bot.elytra-bot.angle-optimizing-flight.combat-angle-optimize-radical-pull-up-optimize-range");
        mv("combat-bot.elytra-bot.combat-angle-optimize-radical-follow",
           "combat-bot.elytra-bot.angle-optimizing-flight.combat-angle-optimize-follow-pitch-limit");
        mv("combat-bot.elytra-bot.combat-pull-up-angle-optimize",
           "combat-bot.elytra-bot.angle-optimizing-flight.combat-angle-optimize-pullup-persistent-direction");

        mv("combat-bot.elytra-bot.fly-anti-spear",
           "combat-bot.elytra-bot.anti-spear-settings.fly-anti-spear");
        mv("combat-bot.elytra-bot.fly-anti-spear-when-pull-up",
           "combat-bot.elytra-bot.anti-spear-settings.fly-anti-spear-when-pull-up");
        mv("combat-bot.elytra-bot.fly-anti-spear-disable-when-using-spear",
           "combat-bot.elytra-bot.anti-spear-settings.fly-anti-spear-disable-when-using-spear");
        mv("combat-bot.elytra-bot.fly-anti-spear-after-angle",
           "combat-bot.elytra-bot.anti-spear-settings.fly-anti-spear-after-angle");
        mv("combat-bot.elytra-bot.fly-anti-spear-use-spear-reset-when-follow",
           "combat-bot.elytra-bot.anti-spear-settings.fly-anti-spear-use-spear-reset-when-follow");

        // ================= Velocity: push options moved under client-velocity-control =================
        // Upstream migrated only no-block-push. The other three were moved without a
        // registration; migrated here so their values survive.
        mv("velocity-management.antikb.no-block-push",
           "velocity-management.antikb.client-velocity-control.no-block-push");
        mv("velocity-management.antikb.no-entity-push",
           "velocity-management.antikb.client-velocity-control.no-entity-push");
        mv("velocity-management.antikb.no-liquid-flow-push",
           "velocity-management.antikb.client-velocity-control.no-liquid-flow-push");
        mv("velocity-management.antikb.no-climbing",
           "velocity-management.antikb.client-velocity-control.no-climbing");

        // ================= pre-existing 1.1.6.3 registrations (survival regrouping) =================
        mv("aaxray.simple", "survival-mine-utils.aaxray-simple");
        mv("mine-utils.block-farm", "survival-mine-utils.block-farm");
        mv("aaxray.seed-ore", "survival-mine-utils.aaxray-seed-ore");
        mv("travelling-control.search-control", "travelling-control");
        // baritone.fix -> baritone.fix is a namespace move only upstream
        // (MOV_CONFIG -> SURVIVAL_CONFIG). IMG keeps a single namespace, so it is a no-op.
    }

    /** All registered moves, in registration order. */
    public static List<Move> moves() {
        return MOVES;
    }

    /** Number of registered moves; used by verification tooling. */
    public static int size() {
        return MOVES.size();
    }

    /**
     * Find the move whose source is this {@code (group, name)} pair, or {@code null}.
     *
     * <p>The name is matched exactly, or as a prefix followed by '-' so that companion
     * settings ({@code -enabled}, {@code -value}, {@code -modifier}) move with their
     * parent. Longest name match wins so a more specific move beats a shorter one.
     */
    public static Move find(String group, String name) {
        Move best = null;
        for (Move m : MOVES) {
            if (!m.oldGroup.equals(group)) continue;
            boolean exact = m.oldName.equals(name);
            boolean prefixed = name.length() > m.oldName.length()
                    && name.startsWith(m.oldName)
                    && name.charAt(m.oldName.length()) == '-';
            if (!exact && !prefixed) continue;
            if (best == null || m.oldName.length() > best.oldName.length()) best = m;
        }
        return best;
    }

    /** Rewrite a setting name to the target name, keeping any {@code -suffix}. */
    public static String rename(Move m, String name) {
        if (name.equals(m.oldName)) return m.newName;
        return m.newName + name.substring(m.oldName.length());
    }
}
