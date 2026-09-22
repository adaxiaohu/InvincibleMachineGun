package com.codigohasta.addon.utils.slimefunhelper.utils;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.BiPredicate;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.*;
import net.minecraft.world.BlockView;
public final class WorldUtils {
    public static final int UPDATE_BLOCK_NO_PHYSICS = 2 | 16 | 512;
private static final net.minecraft.client.MinecraftClient mc=net.minecraft.client.MinecraftClient.getInstance();
    public static float calcBlockBreakingDelta(BlockState state, BlockView world, BlockPos pos) {
        var playerBreakSpeed = getPlayerBlockBreakingSpeedAt(state);
        return calcBlockBreakingDelta(state, world, pos, playerBreakSpeed);
    }
    public static float calcBlockBreakingDelta(
            BlockState state, BlockView world, BlockPos pos, float playerBreakSpeed) {
        float f = state.getHardness(world, pos);
        if (f == -1.0F) {
            return 0.0F;
        } else {
            return playerBreakSpeed / f;
        }
    }
    public static float getPlayerBlockBreakingSpeedWithCanMineMultiply(
            PlayerEntity player, BlockState state, ItemStack stack) {
        float f = stack.getMiningSpeedMultiplier(state);
        if (f > 1.0F) {
            AttributeContainer attributeContainer =
                    AttributeUtils.getAttributeWith(player, Map.of(EquipmentSlot.MAINHAND, stack));
            f += attributeContainer.getValue(EntityAttributes.MINING_EFFICIENCY);
        }

        if (StatusEffectUtil.hasHaste(player)) {
            f *= 1.0F + (float) (StatusEffectUtil.getHasteAmplifier(player) + 1) * 0.2F;
        }

        if (player.hasStatusEffect(StatusEffects.MINING_FATIGUE)) {
            float var10000;
            switch (player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier()) {
                case 0 -> var10000 = 0.3F;
                case 1 -> var10000 = 0.09F;
                case 2 -> var10000 = 0.0027F;
                default -> var10000 = 8.1E-4F;
            }

            float g = var10000;
            f *= g;
        }

        f *= (float) player.getAttributeValue(EntityAttributes.BLOCK_BREAK_SPEED);
        if (player.isSubmergedIn(FluidTags.WATER)) {
            f *= (float) player.getAttributeInstance(EntityAttributes.SUBMERGED_MINING_SPEED)
                    .getValue();
        }

        if (!player.isOnGround()) {
            f /= 5.0F;
        }
        int i = canToolHarvest(state, stack) ? 30 : 100;
        return f / i;
    }
    public static boolean isChunkLoaded(BlockPos pos) {
        return isChunkLoaded(ChunkSectionPos.getSectionCoord(pos.getX()), ChunkSectionPos.getSectionCoord(pos.getZ()));
    }
    public static boolean isChunkLoaded(int chunkX, int chunkZ) {
        return mc.world.getChunkManager().isChunkLoaded(chunkX, chunkZ);
    }
    public static float getPlayerBlockBreakingSpeedAt(BlockState state) {
        return getPlayerBlockBreakingSpeedWithCanMineMultiply(mc.player, state, mc.player.getMainHandStack());
    }
    private static boolean canToolHarvest(BlockState state, ItemStack stack) {
        return !state.isToolRequired() || stack.isSuitableFor(state);
    }
public static boolean isChunkLoaded(ChunkPos p){return isChunkLoaded(p.x,p.z);}
public static Map<BlockPos, BlockState> scannChunk(Chunk chunk, BiPredicate<BlockPos, BlockState> predicate) {
        ChunkPos chunkPos = chunk.getPos();
        int minX = chunkPos.getStartX();
        int minY = chunk.getBottomY();
        int minZ = chunkPos.getStartZ();
        int maxX = chunkPos.getEndX();
        int section = chunk.getHighestNonEmptySection();
        int maxY = section == -1
                ? chunk.getBottomY()
                : ChunkSectionPos.getBlockCoord(chunk.sectionIndexToCoord(section + 1));
        int maxZ = chunkPos.getEndZ();
        Map<BlockPos, BlockState> stateMap = new LinkedHashMap<>();

        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = chunk.getBlockState(pos);
                    if (!predicate.test(pos, state)) continue;
                    stateMap.put(pos, state);
                }

        return stateMap;
    }
}
