package com.codigohasta.addon.mixin;

import com.codigohasta.addon.utils.CamUtils;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.FreeLook;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Camera.class)
public abstract class MixinCamera {
    @Unique private float tickDelta;

    @Inject(method = "update", at = @At("HEAD"))
    private void onUpdate(World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo info) {
        this.tickDelta = tickDelta;
    }

    @ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"))
    private void onUpdateSetRotationArgs(Args args) {
        if (!Modules.get().isActive(Freecam.class) && !Modules.get().isActive(FreeLook.class)) {
            if (CamUtils.isUsing()) {
                float y = (float) CamUtils.getYaw(this.tickDelta) + (MeteorClient.mc.options.getPerspective() == Perspective.THIRD_PERSON_FRONT ? 180.0F : 0.0F);
                float p = (float) CamUtils.getPitch(this.tickDelta) * (MeteorClient.mc.options.getPerspective() == Perspective.THIRD_PERSON_FRONT ? -1.0F : 1.0F);
                
                if (MeteorClient.mc.currentScreen != null) {
                    y = (float) CamUtils.getYaw(0.0F) + (MeteorClient.mc.options.getPerspective() == Perspective.THIRD_PERSON_FRONT ? 180.0F : 0.0F);
                    p = (float) CamUtils.getPitch(0.0F);
                }
                args.set(0, y);
                args.set(1, p);
            }
        }
    }
}