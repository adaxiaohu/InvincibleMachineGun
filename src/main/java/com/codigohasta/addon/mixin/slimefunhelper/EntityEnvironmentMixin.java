package com.codigohasta.addon.mixin.slimefunhelper;
import com.codigohasta.addon.utils.slimefunhelper.events.Listener;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.fluid.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
@Mixin(Entity.class) public abstract class EntityEnvironmentMixin {
@WrapOperation(method="updateMovementInFluid",at=@At(value="INVOKE",target="Lnet/minecraft/util/math/Vec3d;add(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;",ordinal=1))
private Vec3d sf$fluid(Vec3d current,Vec3d delta,Operation<Vec3d> original,@Local(argsOnly=true)TagKey<Fluid> tag){if((Object)this!=MinecraftClient.getInstance().player)return original.call(current,delta);var e=Listener.getPlayerFluidVelocityPoint().fire(delta,tag);return e.isCancelled()?current:original.call(current,e.context());}
}