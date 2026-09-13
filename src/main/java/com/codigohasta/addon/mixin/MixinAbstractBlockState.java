package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.AlienV4PacketMine;
import net.minecraft.block.AbstractBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.AbstractBlockState.class)
public class MixinAbstractBlockState {

    @Inject(method = "getLuminance", at = @At("HEAD"), cancellable = true)
    private void onGetLuminance(CallbackInfoReturnable<Integer> cir) {
    }

    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void onGetCollisionShape(BlockView world, BlockPos pos, CallbackInfoReturnable<VoxelShape> cir) {
        AlienV4PacketMine pm = AlienV4PacketMine.INSTANCE;
        if (pm != null && pm.isActive() && pm.noCollide.get()
            && AlienV4PacketMine.ghost && pos.equals(AlienV4PacketMine.getBreakPos())) {
            cir.setReturnValue(VoxelShapes.empty());
        }
    }
}