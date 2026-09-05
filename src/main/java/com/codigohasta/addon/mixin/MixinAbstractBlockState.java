package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.AlienV4PacketMine;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public class MixinAbstractBlockState {

    @Inject(method = "getLightEmission", at = @At("HEAD"), cancellable = true)
    private void onGetLuminance(CallbackInfoReturnable<Integer> cir) {
    }

    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void onGetCollisionShape(BlockGetter world, BlockPos pos, CallbackInfoReturnable<VoxelShape> cir) {
        AlienV4PacketMine pm = AlienV4PacketMine.INSTANCE;
        if (pm != null && pm.isActive() && pm.noCollide.get()
            && AlienV4PacketMine.ghost && pos.equals(AlienV4PacketMine.getBreakPos())) {
            cir.setReturnValue(Shapes.empty());
        }
    }
}