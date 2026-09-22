package com.codigohasta.addon.mixin.slimefunhelper;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

import com.codigohasta.addon.modules.slimefunhelper.ElytraExtra;
import com.codigohasta.addon.utils.slimefunhelper.events.Listener;
import com.codigohasta.addon.utils.slimefunhelper.SlimefunRuntime;
import com.codigohasta.addon.utils.slimefunhelper.utils.EntityUtils;
import net.minecraft.entity.*;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Shadow protected int glidingTicks;
    @Shadow private int jumpingCooldown;
    @Unique private Integer sf$nextJumpCooldown;
    @Inject(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/entity/LivingEntity;isGliding()Z",shift=At.Shift.BEFORE))
    private void sf$glideTicks(CallbackInfo ci){if((Object)this==MinecraftClient.getInstance().player&&SlimefunRuntime.modifying()){var event=Listener.getPlayerFallFlyingTick().fire(glidingTicks+1);glidingTicks=event.isCancelled()?glidingTicks-1:event.context()-1;}}
    @WrapOperation(method="tickMovement",at=@At(value="INVOKE",target="Lnet/minecraft/entity/LivingEntity;jump()V"))
    private void sf$jump(LivingEntity entity,Operation<Void> original){
        if(entity!=MinecraftClient.getInstance().player||!SlimefunRuntime.modifying()){original.call(entity);return;}
        var event=Listener.getPlayerNotFlyJumpPoint().fire(10);
        sf$nextJumpCooldown=event.context();
        if(!event.isCancelled())original.call(entity);
    }
    @Inject(method="tickMovement",at=@At(value="INVOKE",target="Lnet/minecraft/entity/LivingEntity;isGliding()Z",shift=At.Shift.BEFORE))
    private void sf$jumpCooldown(CallbackInfo ci){if(sf$nextJumpCooldown!=null){if(SlimefunRuntime.modifying())jumpingCooldown=sf$nextJumpCooldown;sf$nextJumpCooldown=null;}}
    @Inject(method="travel",at=@At(value="INVOKE",target="Lnet/minecraft/entity/LivingEntity;travelInFluid(Lnet/minecraft/util/math/Vec3d;)V"),cancellable=true)
    private void sf$water(Vec3d input,CallbackInfo ci){
        if((Object)this!=MinecraftClient.getInstance().player||!SlimefunRuntime.extraActive()||com.codigohasta.addon.utils.slimefunhelper.FlightImpulseGuard.preserving())return;
        ClientPlayerEntity player=(ClientPlayerEntity)(Object)this;ElytraExtra extra=ElytraExtra.INSTANCE;
        if(player.isGliding()&&extra.canFireworkControlMotion()&&(extra.fireworksLiquidFly.get()||extra.hasFireworkVelocityOverrides())){ci.cancel();Vec3d velocity=extra.requestNextOverrideVelocity();player.setVelocity(velocity!=null?velocity:EntityUtils.calculateGlidingVelocity(player,player.getVelocity(),player.getRotationVector(),!player.hasNoGravity()));player.move(MovementType.SELF,player.getVelocity());}
    }
    @WrapOperation(method="travelGliding",at=@At(value="INVOKE",target="Lnet/minecraft/entity/LivingEntity;calcGlidingVelocity(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;"))
    private Vec3d sf$velocity(LivingEntity instance,Vec3d old,Operation<Vec3d> original){
        if(instance!=MinecraftClient.getInstance().player||!SlimefunRuntime.extraActive()||com.codigohasta.addon.utils.slimefunhelper.FlightImpulseGuard.preserving())return original.call(instance,old);
        Vec3d velocity=ElytraExtra.INSTANCE.requestNextOverrideVelocity();return ElytraExtra.INSTANCE.clampFireworkSpeedInWeb(velocity==null?original.call(instance,old):velocity);
    }
}
