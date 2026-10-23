package com.codigohasta.addon.utils.gcore;

import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;

@FunctionalInterface
public interface GcoreStatePredicate {
    boolean test(BlockState state, FluidState fluid, BlockPos pos);
}
