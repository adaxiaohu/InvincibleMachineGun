package com.codigohasta.addon.utils;

import net.minecraft.block.BlockState;

/** Preserves the legacy block flags used by mining and placement logic. */
public final class BlockStateCompat {
    private BlockStateCompat() {}

    // Fluid presence also includes waterlogged blocks; it is not equivalent to this flag.
    @SuppressWarnings("deprecation")
    public static boolean isLiquid(BlockState state) {
        return state.isLiquid();
    }

    // Collision shape and side solidity are not equivalent to the legacy solid flag.
    @SuppressWarnings("deprecation")
    public static boolean isSolid(BlockState state) {
        return state.isSolid();
    }
}
