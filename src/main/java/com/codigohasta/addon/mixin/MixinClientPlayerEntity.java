package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.PearlPhase;
import com.codigohasta.addon.utils.leaveshack.Rotation;
import com.codigohasta.addon.utils.leaveshack.events.MoveEvent;
import com.mojang.authlib.GameProfile;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class MixinClientPlayerEntity extends AbstractClientPlayer {
    public MixinClientPlayerEntity(ClientLevel world, GameProfile profile) {
        super(world, profile);
    }

    @Inject(method = "moveTowardsClosestSpace", at = @At("HEAD"), cancellable = true)
    private void onPushOutOfBlocks(double x, double z, CallbackInfo ci) {
        PearlPhase module = Modules.get().get(PearlPhase.class);
        if (module != null && module.isActive() && module.antiPush.get()) {
            ci.cancel();
        }
    }

    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void onSendMovementPacketsHead(CallbackInfo ci) {
        Rotation.rotationYaw = this.getYRot();
        Rotation.rotationPitch = this.getXRot();
    }

    @Inject(method = "sendPosition", at = @At("TAIL"))
    private void onSendMovementPacketsTail(CallbackInfo ci) {
        Rotation.rotation = false;
    }

    @Redirect(method = "sendPosition",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"))
    private float redirectGetYaw(LocalPlayer entity) {
        if (Rotation.rotation) return Rotation.targetYaw;
        return entity.getYRot();
    }

    @Redirect(method = "sendPosition",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F"))
    private float redirectGetPitch(LocalPlayer entity) {
        if (Rotation.rotation) return Rotation.targetPitch;
        return entity.getXRot();
    }

    @Inject(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"), cancellable = true)
    public void onMoveHook(MoverType movementType, Vec3 movement, CallbackInfo ci) {
        MoveEvent event = new MoveEvent(movement.x, movement.y, movement.z);
        MeteorClient.EVENT_BUS.post(event);
        ci.cancel();
        if (!event.isCancelled()) {
            super.move(movementType, new Vec3(event.getX(), event.getY(), event.getZ()));
        }
    }
}
