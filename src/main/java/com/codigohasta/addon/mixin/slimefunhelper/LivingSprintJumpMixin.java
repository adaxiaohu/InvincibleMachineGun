package com.codigohasta.addon.mixin.slimefunhelper;
import com.codigohasta.addon.utils.slimefunhelper.SlimefunRuntime;
import com.codigohasta.addon.utils.slimefunhelper.hacks.MovTasks;
import com.codigohasta.addon.utils.slimefunhelper.utils.EntityUtils;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LivingEntity.class)
public abstract class LivingSprintJumpMixin {
    @Inject(method="jump",at=@At(value="INVOKE",target="Lnet/minecraft/entity/LivingEntity;addVelocityInternal(Lnet/minecraft/util/math/Vec3d;)V",shift=At.Shift.BEFORE),cancellable=true)
    private void sf$backwardJump(CallbackInfo ci,@Local Vec3d vector){
        if(SlimefunRuntime.modifying()&&MovTasks.getSprint().isActive()&&MovTasks.getSprint().directionalSprint.get()){
            var entity=(LivingEntity)(Object)this;
            Vec3d rotation=EntityUtils.pitchYawToRotation(0,entity.getYaw());
            if(rotation.x*vector.x+rotation.z*vector.z<0){entity.addVelocityInternal(rotation.normalize().multiply(-0.2));entity.velocityDirty=true;ci.cancel();}
        }
    }
}
