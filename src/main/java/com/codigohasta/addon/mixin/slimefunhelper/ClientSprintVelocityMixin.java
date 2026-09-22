package com.codigohasta.addon.mixin.slimefunhelper;

import com.codigohasta.addon.utils.slimefunhelper.SlimefunRuntime;
import com.codigohasta.addon.utils.slimefunhelper.hacks.MovTasks;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientSprintVelocityMixin {
    @Shadow public Input input;
    @Unique private boolean sf$directional(){
        if(!SlimefunRuntime.modifying())return false;
        var sprint=MovTasks.getSprint();
        return sprint.isActive()&&sprint.directionalSprint.get()&&input.playerInput.backward()&&!input.playerInput.forward()&&sprint.enableSprintDirectionalThisTick;
    }
    @ModifyExpressionValue(method={"shouldStopSprinting","canStartSprinting","tickMovement","shouldStopSwimSprinting"},at=@At(value="INVOKE",target="Lnet/minecraft/client/input/Input;hasForwardMovement()Z"))
    private boolean sf$forward(boolean original){return sf$directional()||original;}
    @Inject(method="pushOutOfBlocks",at=@At("HEAD"),cancellable=true)
    private void sf$blockPush(double x,double z,CallbackInfo ci){if(SlimefunRuntime.modifying()&&MovTasks.getVelocity().isActive()&&MovTasks.getVelocity().noBlock.get())ci.cancel();}
}
