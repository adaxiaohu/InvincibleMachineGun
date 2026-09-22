package com.codigohasta.addon.mixin.slimefunhelper;
import com.codigohasta.addon.utils.slimefunhelper.SlimefunRuntime;
import com.codigohasta.addon.utils.slimefunhelper.hacks.MovTasks;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(Entity.class)
public abstract class EntityPushMixin {
    @WrapWithCondition(method="pushAwayFrom",at=@At(value="INVOKE",target="Lnet/minecraft/entity/Entity;addVelocity(DDD)V"))
    private boolean sf$entityPush(Entity entity,double x,double y,double z){return !SlimefunRuntime.modifying()||!MovTasks.getVelocity().isActive()||!MovTasks.getVelocity().noEntityPush.get();}
}
