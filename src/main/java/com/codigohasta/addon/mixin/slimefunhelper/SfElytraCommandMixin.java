package com.codigohasta.addon.mixin.slimefunhelper;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.command.defaults.ElytraCommand;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.codigohasta.addon.modules.slimefunhelper.BaritoneFix;
import com.codigohasta.addon.utils.slimefunhelper.SfTranslations;
import com.codigohasta.addon.utils.slimefunhelper.hooks.impl.baritone.BaritoneSeedMemory;
import com.codigohasta.addon.utils.slimefunhelper.utils.ChatUtils;
import com.codigohasta.addon.utils.slimefunhelper.utils.Debug;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Environment(EnvType.CLIENT)
@Mixin(ElytraCommand.class)
public abstract class SfElytraCommandMixin extends Command {
    public SfElytraCommandMixin(IBaritone iBaritone, String... strings) {
        super(iBaritone, strings);
    }

    @ModifyExpressionValue(
            method = "execute",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/World;getRegistryKey()Lnet/minecraft/registry/RegistryKey;"),
            require = 1)
    private RegistryKey<World> onExecuteNetherSupport(RegistryKey<World> original) throws CommandInvalidStateException {
        if (BaritoneFix.active() && BaritoneFix.INSTANCE.enableDimensionFix.get() && original != World.NETHER) {
            int y = ctx.playerFeet().getY();
            // Lifting the dimension gate does not extend the native Nether octree.
            // Reject invalid starts before creating an asynchronous pathfinding task.
            if (y < 0 || y >= 128) {
                throw new CommandInvalidStateException(SfTranslations.text("ui.baritone-height-unsupported",
                        "Baritone Elytra requires a starting Y of 0-127 and a goal Y of 1-127; current Y: %s.").formatted(y));
            }
            BaritoneFix.INSTANCE.logI18N("message.module.baritone-fix.ignore-dimension-limit");
            return World.NETHER;
        }
        return original;
    }

    @Inject(method = "execute", at = @At("HEAD"), require = 1, remap = false)
    private void onAutoImportSeedValue(String par1, IArgConsumer par2, CallbackInfo ci) {
        if (BaritoneFix.active() && BaritoneFix.INSTANCE.enableSeedAutoImport.get()) {
            BaritoneSeedMemory seedOre = BaritoneFix.INSTANCE.seedCache.get();
            if (seedOre.hasCurrentSeed()) {
                long seed = seedOre.getCurrentSeed();
                if (seed != BaritoneAPI.getSettings().elytraNetherSeed.value && BaritoneSeedMemory.isSeedValid(seed)) {
                    Debug.chat(
                            Text.literal("[BaritoneFix]").formatted(Formatting.RED),
                            "Auto import the cached world seed",
                            Text.literal(Long.toString(seed)));
                    BaritoneAPI.getSettings().elytraNetherSeed.value = (Long) seed;
                }
            }
            Debug.chat("[BaritoneFix] Using seed", BaritoneAPI.getSettings().elytraNetherSeed.value);
        }
    }
}
