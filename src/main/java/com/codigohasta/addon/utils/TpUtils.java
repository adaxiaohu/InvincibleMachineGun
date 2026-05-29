package com.codigohasta.addon.utils;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class TpUtils {

    public static boolean hasBlockCollision(Vec3d pos) {
        if (mc.world == null || mc.player == null) return true;
        Vec3d offset = pos.subtract(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        Box box = mc.player.getBoundingBox().offset(offset);
        return checkBoxCollision(box);
    }

    private static boolean checkBoxCollision(Box box) {
        if (mc.world == null) return true;
        for (BlockPos b : BlockPos.iterate(BlockPos.ofFloored(box.minX, box.minY, box.minZ), BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ))) {
            BlockState state = mc.world.getBlockState(b);
            if (!state.getCollisionShape(mc.world, b).isEmpty()) return true;
        }
        return false;
    }

    public static boolean isPositionSafe(Vec3d pos, boolean checkHazards) {
        if (mc.world == null || mc.player == null) return false;
        BlockPos bp = BlockPos.ofFloored(pos);
        if (!mc.world.isChunkLoaded(bp)) return false;
        Vec3d offset = pos.subtract(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        Box box = mc.player.getBoundingBox().offset(offset);
        if (checkBoxCollision(box)) return false;
        if (checkHazards) {
            for (BlockPos b : BlockPos.iterate(BlockPos.ofFloored(box.minX, box.minY, box.minZ), BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ))) {
                Block block = mc.world.getBlockState(b).getBlock();
                if (block == Blocks.LAVA || block == Blocks.FIRE || block == Blocks.SOUL_FIRE) return false;
            }
        }
        return true;
    }

    public static Vec3d findNearestSafePos(Vec3d desired, int searchRadius, boolean checkHazards) {
        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int y = -searchRadius; y <= searchRadius; y++) {
                for (int z = -searchRadius; z <= searchRadius; z++) {
                    Vec3d test = desired.add(x, y, z);
                    if (isPositionSafe(test, checkHazards)) return test;
                }
            }
        }
        return null;
    }

    public static double searchVerticalSpace(Vec3d origin, int maxUp) {
        if (mc.player == null || mc.world == null) return 0;
        Vec3d offset = origin.subtract(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        Box baseBox = mc.player.getBoundingBox().offset(offset);
        Box searchBox = new Box(baseBox.minX, origin.y, baseBox.minZ, baseBox.maxX, origin.y + maxUp, baseBox.maxZ);
        if (!checkBoxCollision(searchBox)) return maxUp;
        for (double y = maxUp; y > 0; y -= 0.5) {
            Vec3d test = origin.add(0, y, 0);
            if (!hasBlockCollision(test)) return y;
        }
        return 0;
    }

    public static boolean isInWater(Vec3d pos) {
        if (mc.world == null) return false;
        return !mc.world.getFluidState(BlockPos.ofFloored(pos)).isEmpty();
    }

    public static void applyFallProtection() {
        if (mc.player == null) return;
        mc.player.fallDistance = 0;
        mc.player.setOnGround(false);
    }

    public static double estimateExplosionDamage(Vec3d explosionPos, Entity target, float power) {
        if (mc.world == null) return 0;
        double size = power * 2.0;
        double dist = Math.sqrt(explosionPos.squaredDistanceTo(target.getX(), target.getY(), target.getZ()));
        if (dist > size) return 0;
        double exposure = calculateExposure(explosionPos, target.getBoundingBox());
        double impact = (1.0 - dist / size) * exposure;
        return (impact * impact + impact) / 2.0 * 7.0 * power + 1.0;
    }

    public static double calculateExposure(Vec3d origin, Box box) {
        if (mc.world == null) return 1.0;
        double xStep = 1.0 / Math.max(1, (int) Math.ceil((box.maxX - box.minX) * 2.0));
        double yStep = 1.0 / Math.max(1, (int) Math.ceil((box.maxY - box.minY) * 2.0));
        double zStep = 1.0 / Math.max(1, (int) Math.ceil((box.maxZ - box.minZ) * 2.0));
        int hit = 0, total = 0;
        for (double x = box.minX; x <= box.maxX; x += xStep) {
            for (double y = box.minY; y <= box.maxY; y += yStep) {
                for (double z = box.minZ; z <= box.maxZ; z += zStep) {
                    total++;
                    Vec3d target = new Vec3d(x, y, z);
                    if (mc.world.raycast(new RaycastContext(origin, target, ShapeType.COLLIDER, FluidHandling.NONE, mc.player)).getType() == net.minecraft.util.hit.HitResult.Type.MISS) {
                        hit++;
                    }
                }
            }
        }
        return total > 0 ? (double) hit / total : 0;
    }

    public static Vec3d predictPosition(Entity entity, int ticksAhead) {
        Vec3d vel = entity.getVelocity();
        double gravity = entity.isOnGround() ? 0 : -0.5 * ticksAhead * ticksAhead;
        return new Vec3d(
            entity.getX() + vel.x * ticksAhead,
            entity.getY() + vel.y * ticksAhead * 0.5 + gravity,
            entity.getZ() + vel.z * ticksAhead
        );
    }

    private static boolean hasHazardBlocks(Box box) {
        if (mc.world == null) return false;
        for (BlockPos b : BlockPos.iterate(BlockPos.ofFloored(box.minX, box.minY, box.minZ), BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ))) {
            Block block = mc.world.getBlockState(b).getBlock();
            if (block == Blocks.LAVA || block == Blocks.FIRE || block == Blocks.SOUL_FIRE) return true;
        }
        return false;
    }
}
