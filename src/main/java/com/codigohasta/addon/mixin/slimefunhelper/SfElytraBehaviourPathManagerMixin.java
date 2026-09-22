package com.codigohasta.addon.mixin.slimefunhelper;

import baritone.api.utils.BetterBlockPos;
import baritone.process.elytra.ElytraBehavior;
import baritone.process.elytra.UnpackedSegment;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;
import com.codigohasta.addon.modules.slimefunhelper.BaritoneFix;
import com.codigohasta.addon.utils.slimefunhelper.hooks.BaritoneFixHooks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Environment(EnvType.CLIENT)
@Mixin(ElytraBehavior.PathManager.class)
public abstract class SfElytraBehaviourPathManagerMixin {

    @WrapOperation(
            method = "b()V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lbaritone/process/elytra/ElytraBehavior;a(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;Z)Z",
                            ordinal = 2),
            require = 1)
    private boolean b(ElytraBehavior instance, Vec3d start, Vec3d to, boolean b, Operation<Boolean> original) {
        if (!b && BaritoneFix.active() && BaritoneFix.INSTANCE.baritoneExperimental1.get()) {
            if (to.y < BaritoneFix.INSTANCE.baritoneExperimentHeight.get()) {
                return false;
            }
        }
        return original.call(instance, start, to, b);
    }

}
