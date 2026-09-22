package com.codigohasta.addon.mixin.slimefunhelper;

import baritone.cache.ChunkPacker;
import com.codigohasta.addon.modules.slimefunhelper.BaritoneFix;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(ChunkPacker.class)
@Environment(EnvType.CLIENT)
public abstract class SfBaritoneChunkPackerFixMixin {
    @Unique
    private static final BlockState a = Blocks.AIR.getDefaultState();

    @WrapOperation(
            method = "a(Lnet/minecraft/world/chunk/WorldChunk;)Lbaritone/cache/CachedChunk;",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lbaritone/utils/BlockStateInterface;a(Lnet/minecraft/world/chunk/WorldChunk;III)Lnet/minecraft/block/BlockState;"),
            require = 1)
    private static BlockState fixWorldAccessIndexOutOfBound(
            WorldChunk chunk, int x, int y, int z, Operation<BlockState> original) {
        if (BaritoneFix.active() && (y < 0 || y >= (chunk.getSectionArray().length << 4))) {
            return a;
        }
        return original.call(chunk, x, y, z);
    }
}
