package com.codigohasta.addon.utils;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Detection for the standalone SlimefunHelper mod.
 *
 * <p>SlimefunHelper ships its own copy of the sfIMG subsystem (mining, packet, soulbound and
 * elytra helpers) and puts the same accessor interfaces on the same Minecraft classes. Loading
 * both copies at once makes the JVM reject the target classes with
 * {@code IncompatibleClassChangeError: Conflicting default methods}, so one side has to stand
 * down. IMG yields: when SlimefunHelper is present the sfIMG mixin package is skipped and its
 * modules are not activated, and SlimefunHelper provides the functionality instead.
 *
 * <p>This class deliberately references nothing but the Fabric loader so it stays loadable from
 * {@code IMixinConfigPlugin}, which runs before Minecraft classes exist.
 */
public final class SlimefunHelperCompat {
    /** Fabric mod id of the standalone SlimefunHelper build. */
    public static final String MOD_ID = "slimefunhelper";

    /** Mixin package holding IMG's ported sfIMG mixins; disabled whenever SlimefunHelper is present. */
    public static final String SFIMG_MIXIN_PACKAGE = "com.codigohasta.addon.mixin.slimefunhelper.";

    private static Boolean loaded;

    private SlimefunHelperCompat() {}

    /** Whether the standalone SlimefunHelper mod is installed. Result is cached after first call. */
    public static boolean isSlimefunHelperLoaded() {
        if (loaded == null) {
            boolean present;
            try {
                present = FabricLoader.getInstance().isModLoaded(MOD_ID);
            } catch (Throwable ignored) {
                // Very early loader states can throw; treat as "not present" so IMG's own
                // implementation stays available rather than silently disabling both sides.
                present = false;
            }
            loaded = present;
        }
        return loaded;
    }
}
