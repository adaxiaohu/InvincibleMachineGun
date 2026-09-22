package com.codigohasta.addon.mixin.slimefunhelper;

import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.process.ElytraProcess;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.codigohasta.addon.utils.slimefunhelper.events.Event;
import com.codigohasta.addon.modules.slimefunhelper.FloatingUtils;
import com.codigohasta.addon.modules.slimefunhelper.BaritoneFix;
import com.codigohasta.addon.utils.slimefunhelper.hooks.BaritoneFixHooks;
import com.codigohasta.addon.utils.slimefunhelper.hooks.impl.baritone.BaritoneFuture;
import com.codigohasta.addon.utils.slimefunhelper.hooks.impl.baritone.BaritoneLanding;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Environment(EnvType.CLIENT)
@Mixin(ElytraProcess.class)
public abstract class SfElytraProcessMixin {

    @Inject(
            method = "a()Z",
            at = @At("HEAD"),
            require = 1,
            cancellable = true,
            remap = false)
    private void hookShouldLandForSafety(CallbackInfoReturnable<Boolean> ci) {
        if (BaritoneFix.active() && BaritoneFix.INSTANCE.disableInventoryCheck.get()
                && BaritoneFixHooks.getInstance().isEnabled()) {
            ci.setReturnValue(!BaritoneFix.INSTANCE.checkCanContinueFlyingCustom());
        }
    }

    @WrapWithCondition(
            method = "onTick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lbaritone/process/ElytraProcess;logDirect(Ljava/lang/String;)V"),
            require = 1,
            remap = false)
    private boolean hookLogDirect(ElytraProcess instance, String string) {
        if (BaritoneFix.active() && BaritoneFix.INSTANCE.enableEmergencyLandingFix.get()) {
            // The actual emergency message is ordinal 3 in Meteor 1.21.11.
            // Match messages instead of silencing whichever call occupies an ordinal.
            return !"Emergency landing - almost out of elytra durability or fireworks".equals(string)
                    && !"almost out of elytra durability or fireworks, but I'm going to continue since elytraAllowEmergencyLand is false".equals(string);
        }
        return true;
    }

    @ModifyExpressionValue(
            method = "a(Lnet/minecraft/util/math/BlockPos;Z)V",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/World;getRegistryKey()Lnet/minecraft/registry/RegistryKey;"),
            require = 1)
    private RegistryKey<World> hookGetRegistryKey(RegistryKey<World> original) {
        if (BaritoneFix.active() && BaritoneFix.INSTANCE.enableDimensionFix.get() && original != World.NETHER) {
            return World.NETHER;
        }
        return original;
    }

    @Unique
    private BaritoneFuture landingFuture;

    @Inject(
            method = "onTick",
            at =
                    @At(
                            value = "FIELD",
                            target = "Lbaritone/api/Settings;elytraAllowEmergencyLand:Lbaritone/api/Settings$Setting;",
                            shift = At.Shift.BEFORE),
            cancellable = true,
            require = 1,
            remap = false)
    private void hookAllowEmergencyLand(boolean par1, boolean par2, CallbackInfoReturnable<PathingCommand> cir) {
        if (!BaritoneFix.active()) return;
        BaritoneFuture future = new BaritoneFuture();
        Event<BaritoneFuture> event = new Event<>(future, true, false, BaritoneLanding.EMERGENCY);
        BaritoneFixHooks.getLandingEvent().dispatch(event);
        if (event.isCancelled()) {
            future.onCancel();
            cir.setReturnValue(new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL));
            return;
        }
        landingFuture = future;
    }

    @Inject(
            method = "onTick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lbaritone/process/ElytraProcess;logDirect(Ljava/lang/String;)V",
                            ordinal = 5),
            cancellable = true,
            require = 1,
            remap = false)
    private void hookLogDirect(boolean par1, boolean par2, CallbackInfoReturnable<PathingCommand> cir) {
        if (!BaritoneFix.active()) return;
        BaritoneFuture future = new BaritoneFuture();
        Event<BaritoneFuture> event = new Event<>(future, true, false, BaritoneLanding.PATH_COMPLETE);
        BaritoneFixHooks.getLandingEvent().dispatch(event);
        if (event.isCancelled()) {
            future.onCancel();
            cir.setReturnValue(new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL));
            return;
        }
        landingFuture = future;
    }

    @WrapWithCondition(
            method = "onTick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lbaritone/process/ElytraProcess;logDirect(Ljava/lang/String;)V",
                            ordinal = 9),
            require = 1,
            remap = false)
    private boolean hookLanding(ElytraProcess instance, String s) {
        if (landingFuture != null) {
            landingFuture.onComplete();
            landingFuture = null;
        }
        return true;
    }

    @Inject(method = "onTick", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void hookPauseElytraProcess(boolean par1, boolean par2, CallbackInfoReturnable<PathingCommand> cir) {
        if (BaritoneFix.active() && BaritoneFix.INSTANCE.shouldPauseBaritoneElytra()) {
            cir.setReturnValue(new PathingCommand(null, PathingCommandType.REQUEST_PAUSE));
        }
    }


    @Inject(
            method = "onTick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lbaritone/process/elytra/ElytraBehavior;a(Ljava/lang/String;)V",
                            ordinal = 2),
            require = 1,
            remap = false)
    private void onNoSolution1(boolean par1, boolean par2, CallbackInfoReturnable<PathingCommand> cir) {
        if (BaritoneFix.active() && BaritoneFix.INSTANCE.freezeWhenFailCalculate.get()) {
            BaritoneFix.INSTANCE.logI18N("message.module.baritone-fix.freeze-all");
            FloatingUtils.INSTANCE.setGrimFloatingTick(true);
        }
    }

    @Inject(
            method = "onTick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lbaritone/process/elytra/ElytraBehavior;a(Ljava/lang/String;)V",
                            ordinal = 3),
            require = 1,
            remap = false)
    private void onNoSolution2(boolean par1, boolean par2, CallbackInfoReturnable<PathingCommand> cir) {
        if (BaritoneFix.active() && BaritoneFix.INSTANCE.freezeWhenFailCalculate.get()) {
            BaritoneFix.INSTANCE.logI18N("message.module.baritone-fix.freeze-pitch");
            FloatingUtils.INSTANCE.setGrimFloatingTick(true);
        }
    }

    @WrapOperation(
            method = "onTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isOnGround()Z"),
            require = 1)
    private boolean onAutoJumpRewrite(ClientPlayerEntity instance, Operation<Boolean> original) {
        if (BaritoneFix.active() && BaritoneFix.INSTANCE.handleAutoJump()) {
            return false;
        }
        return original.call(instance);
    }

}
