package com.codigohasta.addon.mixin.slimefunhelper;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Set;
import com.codigohasta.addon.utils.slimefunhelper.accessors.access.ClientPlayerAccess;
import com.codigohasta.addon.modules.slimefunhelper.AutoResync;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPosition;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayNetworkHandler.class)
public abstract class InventoryNetworkMixin {

    @WrapOperation(
            method = "onPlayerPositionLook",
            at = @At(value = "INVOKE", target =
                    "Lnet/minecraft/client/network/ClientPlayNetworkHandler;setPosition(Lnet/minecraft/entity/EntityPosition;Ljava/util/Set;Lnet/minecraft/entity/Entity;Z)Z"))
    private boolean wrapSetPositionLook(
            EntityPosition pos, Set<PositionFlag> flags, Entity entity, boolean bl, Operation<Boolean> original) {
        if (AutoResync.INSTANCE != null && AutoResync.INSTANCE.isActive()
                && AutoResync.INSTANCE.noVelocitySetback.get()) {
            Vec3d currentVelocity = entity.getVelocity();
            boolean re = original.call(pos, flags, entity, bl);
            entity.setVelocity(currentVelocity);
            return re;
        } else {
            return original.call(pos, flags, entity, bl);
        }
    }

    @Inject(
            method = "onCloseScreen",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/network/ClientPlayerEntity;closeScreen()V",
                            shift = At.Shift.BEFORE),
            cancellable = true)
    private void onCloseScreenClearKeepedInv(CloseScreenS2CPacket packet, CallbackInfo ci) {
        if (!com.codigohasta.addon.utils.slimefunhelper.SlimefunRuntime.modifying()) return;
        ClientPlayerAccess access = ClientPlayerAccess.of(MinecraftClient.getInstance().player);
        if (access.getKeepedInvHandler() != null && access.getKeepedInvHandler().syncId == packet.getSyncId()) {
            access.clearKeepedInventory(true);
        }
        if (MinecraftClient.getInstance().player.currentScreenHandler.syncId != packet.getSyncId()) {
            ci.cancel();
        }
    }

    @Inject(method = "onScreenHandlerSlotUpdate", at = @At(value = "RETURN"), locals = LocalCapture.CAPTURE_FAILSOFT)
    private void onScreenHandlerSlotUpdateSyncToKeeped(ScreenHandlerSlotUpdateS2CPacket packet, CallbackInfo ci) {
        if (!com.codigohasta.addon.utils.slimefunhelper.SlimefunRuntime.modifying()) return;
        // Debug.info("Received screen handler slot update packet
        // ",packet.getSyncId(),packet.getSlot(),packet.getItemStack());
        if (MinecraftClient.getInstance().player != null) {
            ClientPlayerAccess access = ClientPlayerAccess.of(MinecraftClient.getInstance().player);
            if (access.getKeepedInvHandler() != null && packet.getSyncId() == access.getKeepedInvHandler().syncId) {
                access.getKeepedInvHandler().setStackInSlot(packet.getSlot(), packet.getRevision(), packet.getStack());
            }
        }
    }

    @Inject(method = "onInventory", at = @At(value = "RETURN"), locals = LocalCapture.CAPTURE_FAILSOFT)
    private void onInventorySyncToKeeped(InventoryS2CPacket packet, CallbackInfo ci) {
        if (!com.codigohasta.addon.utils.slimefunhelper.SlimefunRuntime.modifying()) return;
        if (MinecraftClient.getInstance().player != null) {
            ClientPlayerAccess access = ClientPlayerAccess.of(MinecraftClient.getInstance().player);
            if (access.getKeepedInvHandler() != null && packet.syncId() == access.getKeepedInvHandler().syncId) {
                access.getKeepedInvHandler()
                        .updateSlotStacks(packet.revision(), packet.contents(), packet.cursorStack());
            }
        }
    }

    @Inject(
            method = "onOpenScreen",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/screen/ingame/HandledScreens;open(Lnet/minecraft/screen/ScreenHandlerType;Lnet/minecraft/client/MinecraftClient;ILnet/minecraft/text/Text;)V",
                            shift = At.Shift.BEFORE))
    private void onInventoryOpenCloseKeepInventory(OpenScreenS2CPacket packet, CallbackInfo ci) {
        if (!com.codigohasta.addon.utils.slimefunhelper.SlimefunRuntime.modifying()) return;
        // for keepInv
        if (MinecraftClient.getInstance().player != null) {
            ClientPlayerAccess access = ClientPlayerAccess.of(MinecraftClient.getInstance().player);
            access.clearKeepedInventory(false);
        }
    }

}
