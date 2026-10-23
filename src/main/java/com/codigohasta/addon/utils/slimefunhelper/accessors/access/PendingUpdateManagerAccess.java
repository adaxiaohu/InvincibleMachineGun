package com.codigohasta.addon.utils.slimefunhelper.accessors.access;

import java.util.Optional;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.util.math.BlockPos;

/** Ported from upstream 1.1.7.2 {@code me.matl114.accessors.access.PendingUpdateManagerAccess}. */
public interface PendingUpdateManagerAccess {
    static PendingUpdateManagerAccess of(PendingUpdateManager manager) {
        return (PendingUpdateManagerAccess) manager;
    }

    Optional<BlockState> getPendingBlockState(int sequenceNumber, BlockPos pos);
}
