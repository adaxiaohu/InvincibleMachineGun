package com.codigohasta.addon.mixin.slimefunhelper;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Optional;
import com.codigohasta.addon.utils.slimefunhelper.accessors.access.PendingUpdateManagerAccess;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * Ported from upstream 1.1.7.2 {@code me.matl114.mixins.access.PendingUpdateManagerMixin}.
 *
 * <p>Required by {@code SequencedActionManager.getBeforeBreakPredictionState}: the interface
 * method has no other implementation, so calling it without this mixin throws
 * {@code AbstractMethodError}.
 */
@Mixin(PendingUpdateManager.class)
public abstract class PendingUpdateManagerMixin implements PendingUpdateManagerAccess {

    @Shadow
    @Final
    private Long2ObjectOpenHashMap<PendingUpdateManager.PendingUpdate> blockPosToPendingUpdate;

    @Unique
    @Override
    public Optional<BlockState> getPendingBlockState(int sequenceNumber, BlockPos pos) {
        var pending = blockPosToPendingUpdate.get(pos.asLong());
        return (pending == null || pending.sequence < sequenceNumber)
                ? Optional.empty()
                : Optional.of(pending.blockState);
    }
}
