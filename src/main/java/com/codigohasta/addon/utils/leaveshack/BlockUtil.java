package com.codigohasta.addon.utils.leaveshack;

import com.codigohasta.addon.modules.AutoCity;
import com.codigohasta.addon.modules.GlobalSetting;
import com.codigohasta.addon.utils.BlockPosX;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class BlockUtil {
    public static CopyOnWriteArrayList<BlockPos> placeList = new CopyOnWriteArrayList<>();
    public static Direction getClickSideStrict(BlockPos pos) {
        Direction side = null;
        double minDistance = Double.MAX_VALUE;
        for (Direction i : Direction.values()) {
            if (!isGrimDirection(pos, i)) continue;
            double disSq = mc.player.getEyePosition().distanceToSqr(pos.relative(i).getCenter());
            if (disSq > minDistance)
                continue;
            side = i;
            minDistance = disSq;
        }
        return side;
    }
    public static Vec3 getClosestPointToBox(Vec3 pos, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double closestX = Math.max(minX, Math.min(pos.x, maxX));
        double closestY = Math.max(minY, Math.min(pos.y, maxY));
        double closestZ = Math.max(minZ, Math.min(pos.z, maxZ));

        return new Vec3(closestX, closestY, closestZ);
    }

    public static Vec3 getClosestPointToBox(Vec3 eyePos, AABB boundingBox) {
        return getClosestPointToBox(eyePos, boundingBox.minX, boundingBox.minY, boundingBox.minZ, boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ);
    }

    public static Vec3 getClosestPoint(Entity entity) {
        return getClosestPointToBox(mc.player.getEyePosition(), entity.getBoundingBox());
    }
    public static boolean noEntityBlockCrystal(BlockPos pos, boolean ignoreCrystal, boolean ignoreItem) {
        for (Entity entity : getEntities(new AABB(pos))) {
            if (!entity.isAlive() || ignoreItem && entity instanceof ItemEntity || ignoreCrystal && entity instanceof EndCrystal && mc.player.getEyePosition().distanceTo(getClosestPoint(entity)) <= AutoCity.INSTANCE.range.get())
                continue;
            return false;
        }
        return true;
    }
    public static boolean canClick(BlockPos pos) {
        return mc.level.getBlockState(pos).isSolid() && (!(shiftBlocks.contains(getBlock(pos)) || getBlock(pos) instanceof BedBlock) || mc.player.isShiftKeyDown());
    }
    public static boolean canClick(BlockPos pos, boolean ignoreSneak) {
        return mc.level.getBlockState(pos).isSolid() && (!(shiftBlocks.contains(getBlock(pos)) || getBlock(pos) instanceof BedBlock) || (mc.player.isShiftKeyDown() || ignoreSneak));
    }

    public static boolean canPlace(BlockPos pos) {
        return canPlace(pos, null);
    }
    public static boolean hasCrystalPlace(BlockPos pos) {
        for (Entity entity : getEndCrystals(new AABB(pos))) {
            if (!entity.isAlive() || !(entity instanceof EndCrystal crystal))
                continue;
            return crystal.blockPosition().equals(pos);
        }
        return false;
    }
//    public static boolean canPlace(BlockPos pos, boolean ignoreSneak) {
//        return canPlace(pos, ignoreSneak);
//    }
    public static boolean clientCanPlace(BlockPos pos, boolean ignoreCrystal) {
        if (!canReplace(pos)) return false;
        return !hasEntity(pos, ignoreCrystal);
    }
    public static boolean canReplace(BlockPos pos) {
        if (pos.getY() >= 320) return false;
        return mc.level.getBlockState(pos).canBeReplaced();
    }
    public static boolean canPlace(BlockPos pos, Predicate<Direction> directionPredicate) {
        if (getPlaceSide(pos, directionPredicate) == null) return false;
        if (!canReplace(pos)) return false;
        return !hasEntity(pos, false);
    }
    public static boolean hasEntity(BlockPos pos, boolean ignoreCrystal) {
        for (Entity entity : getEntities(new AABB(pos))) {
            if (!entity.isAlive() || entity instanceof ItemEntity || entity instanceof ExperienceOrb || entity instanceof ThrownExperienceBottle || entity instanceof Arrow || ignoreCrystal && entity instanceof EndCrystal)
                continue;
            return true;
        }
        return false;
    }
    public static boolean hasEntity(BlockPos pos, boolean ignoreCrystal, boolean ignorePlayer) {
        for (Entity entity : getEntities(new AABB(pos))) {
            if (!entity.isAlive() || entity instanceof ItemEntity || entity instanceof ExperienceOrb || entity instanceof ThrownExperienceBottle || entity instanceof Arrow || ignoreCrystal && entity instanceof EndCrystal || ignorePlayer && entity instanceof Player)
                continue;
            return true;
        }
        return false;
    }
    public static List<Entity> getEntities(AABB box) {
        List<Entity> list = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == null) continue;
            if (entity.getBoundingBox().intersects(box)) {
                list.add(entity);
            }
        }
        return list;
    }
    public static ArrayList<BlockPos> getSphere(double range) {
        return getSphere(range, mc.player.getEyePosition());
    }
//    public static List<BlockPos> getSphere(int range) {
//        List<BlockPos> list = new ArrayList<>();
//        BlockPos center = mc.player.getBlockPos();
//        for (int x = -range; x <= range; x++) {
//            for (int y = -range; y <= range; y++) {
//                for (int z = -range; z <= range; z++) {
//                    if (x * x + y * y + z * z > range * range) continue;
//                    list.add(center.add(x, y, z));
//                }
//            }
//        }
//        return list;
//    }
    public static ArrayList<BlockPos> getSphere(double range, Vec3 pos) {
        ArrayList<BlockPos> list = new ArrayList<>();
        for (double x = pos.x() - range; x < pos.x() + range; ++x) {
            for (double z = pos.z() - range; z < pos.z() + range; ++z) {
                for (double y = pos.y() - range; y < pos.y() + range; ++y) {
                    BlockPos curPos = new BlockPosX(x, y, z);
                    if (curPos.getCenter().distanceTo(pos) > range) continue;
                    if (!list.contains(curPos)) {
                        list.add(curPos);
                    }
                }
            }
        }
        return list;
    }
    public static boolean hasPlayerEntity(BlockPos pos) {
        for (Entity entity : getEntities(new AABB(pos))) {
            if (entity instanceof Player) return true;
        }
        return false;
    }
    public static boolean hasCrystal(BlockPos pos) {
        for (Entity entity : getEndCrystals(new AABB(pos))) {
            if (!entity.isAlive() || !(entity instanceof EndCrystal))
                continue;
            return true;
        }
        return false;
    }
    public static List<EndCrystal> getEndCrystals(AABB box) {
        List<EndCrystal> list = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof EndCrystal crystal) {
                if (crystal.getBoundingBox().intersects(box)) {
                    list.add(crystal);
                }
            }
        }
        return list;
    }
    public static Direction getPlaceSide(BlockPos pos, Predicate<Direction> directionPredicate) {
        if (pos == null) return null;
        double dis = 114514;
        Direction side = null;
        for (Direction i : Direction.values()) {
            if (directionPredicate != null && !directionPredicate.test(i)) continue;
            if (canClick(pos.relative(i)) && !mc.level.getBlockState(pos.relative(i)).canBeReplaced()) {
                if (!isGrimDirection(pos.relative(i), i.getOpposite()))continue;
                double vecDis = mc.player.getEyePosition().distanceToSqr(pos.getCenter().add(i.getUnitVec3i().getX() * 0.5, i.getUnitVec3i().getY() * 0.5, i.getUnitVec3i().getZ() * 0.5));
                if (side == null || vecDis < dis) {
                    side = i;
                    dis = vecDis;
                }
            }
        }
        return side;
    }
    public static Direction getPlaceSide(BlockPos pos, Predicate<Direction> directionPredicate, boolean ignoreSneak) {
        if (pos == null) return null;
        double dis = 114514;
        Direction side = null;
        for (Direction i : Direction.values()) {
            if (directionPredicate != null && !directionPredicate.test(i)) continue;
            if (canClick(pos.relative(i), ignoreSneak) && !mc.level.getBlockState(pos.relative(i)).canBeReplaced()) {
                if (!isGrimDirection(pos.relative(i), i.getOpposite()))continue;
                double vecDis = mc.player.getEyePosition().distanceToSqr(pos.getCenter().add(i.getUnitVec3i().getX() * 0.5, i.getUnitVec3i().getY() * 0.5, i.getUnitVec3i().getZ() * 0.5));
                if (side == null || vecDis < dis) {
                    side = i;
                    dis = vecDis;
                }
            }
        }
        return side;
    }
    public static ArrayList<Direction> getPlaceSides(BlockPos pos, Predicate<Direction> directionPredicate) {
        ArrayList<Direction> sides = new ArrayList<>();
        if (pos == null) return sides;

        for (Direction i : Direction.values()) {
            if (directionPredicate != null && !directionPredicate.test(i)) continue;

            BlockPos neighbor = pos.relative(i);
            BlockState neighborState = mc.level.getBlockState(neighbor);
            if (canClick(neighbor) && !neighborState.canBeReplaced()) {
                if (!isGrimDirection(neighbor, i.getOpposite())) continue;
                sides.add(i);
            }
        }
        return sides;
    }
    public static ArrayList<Direction> getPlaceSides(BlockPos pos, Predicate<Direction> directionPredicate, boolean ignoreSneak) {
        ArrayList<Direction> sides = new ArrayList<>();
        if (pos == null) return sides;

        for (Direction i : Direction.values()) {
            if (directionPredicate != null && !directionPredicate.test(i)) continue;

            BlockPos neighbor = pos.relative(i);
            BlockState neighborState = mc.level.getBlockState(neighbor);
            if (canClick(neighbor, ignoreSneak) && !neighborState.canBeReplaced()) {
                if (!isGrimDirection(neighbor, i.getOpposite())) continue;
                sides.add(i);
            }
        }
        return sides;
    }
    public static boolean canSee(BlockPos pos, Direction side) {
        Vec3 testVec = pos.getCenter().add(side.getUnitVec3i().getX() * 0.5, side.getUnitVec3i().getY() * 0.5, side.getUnitVec3i().getZ() * 0.5);
        HitResult result = mc.level.clip(new ClipContext(getEyesPos(), testVec, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return result == null || result.getType() == HitResult.Type.MISS;
    }
    public static Vec3 getEyesPos() {
        return mc.player.getEyePosition();
    }
    public static Direction getClickSide(BlockPos pos) {
        Direction side = null;
        double range = 100;
        for (Direction i : Direction.values()) {
            if (!canSee(pos, i)) continue;
            if (Mth.sqrt((float) mc.player.getEyePosition().distanceToSqr(pos.relative(i).getCenter())) > range)
                continue;
            side = i;
            range = Mth.sqrt((float) mc.player.getEyePosition().distanceToSqr(pos.relative(i).getCenter()));
        }
        if (side != null) return side;
        side = Direction.UP;
        for (Direction i : Direction.values()) {
                if (!isGrimDirection(pos, i))continue;
            if (Mth.sqrt((float) mc.player.getEyePosition().distanceToSqr(pos.relative(i).getCenter())) > range)
                continue;
            side = i;
            range = Mth.sqrt((float) mc.player.getEyePosition().distanceToSqr(pos.relative(i).getCenter()));
        }
        return side;
    }
    private static AABB getCombinedBox(BlockPos pos, Level level) {
        VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos).move(pos.getX(), pos.getY(), pos.getZ());
        AABB combined = new AABB(pos);
        for (AABB box : shape.toAabbs()) {
            double minX = Math.max(box.minX, combined.minX);
            double minY = Math.max(box.minY, combined.minY);
            double minZ = Math.max(box.minZ, combined.minZ);
            double maxX = Math.min(box.maxX, combined.maxX);
            double maxY = Math.min(box.maxY, combined.maxY);
            double maxZ = Math.min(box.maxZ, combined.maxZ);
            combined = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }

        return combined;
    }
    private static boolean isIntersected(AABB bb, AABB other) {
        return other.maxX - Shapes.EPSILON > bb.minX
                && other.minX + Shapes.EPSILON < bb.maxX
                && other.maxY - Shapes.EPSILON > bb.minY
                && other.minY + Shapes.EPSILON < bb.maxY
                && other.maxZ - Shapes.EPSILON > bb.minZ
                && other.minZ + Shapes.EPSILON < bb.maxZ;
    }
    private static final double MIN_EYE_HEIGHT = 0.4;
    private static final double MAX_EYE_HEIGHT = 1.62;
    private static final double MOVEMENT_THRESHOLD = 0.0002;
    public static boolean isGrimDirection(BlockPos pos, Direction direction) {
        // see ac.grim.grimac.checks.impl.scaffolding.PositionPlace
        AABB combined = getCombinedBox(pos, mc.level);
        LocalPlayer player = mc.player;
        AABB eyePositions = new AABB(player.getX(), player.getY() + MIN_EYE_HEIGHT, player.getZ(), player.getX(), player.getY() + MAX_EYE_HEIGHT, player.getZ()).inflate(MOVEMENT_THRESHOLD);
        if (isIntersected(eyePositions, combined)) {
            return true;
        }
        return !switch (direction) {
            case NORTH -> eyePositions.minZ > combined.minZ;
            case SOUTH -> eyePositions.maxZ < combined.maxZ;
            case EAST -> eyePositions.maxX < combined.maxX;
            case WEST -> eyePositions.minX > combined.minX;
            case UP -> eyePositions.maxY < combined.maxY;
            case DOWN -> eyePositions.minY > combined.minY;
        };
    }
    public static final List<Block> shiftBlocks = Arrays.asList(
            Blocks.ENDER_CHEST, Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.CRAFTING_TABLE,
            Blocks.BIRCH_TRAPDOOR, Blocks.BAMBOO_TRAPDOOR, Blocks.DARK_OAK_TRAPDOOR, Blocks.CHERRY_TRAPDOOR,
            Blocks.ANVIL, Blocks.BREWING_STAND, Blocks.HOPPER, Blocks.DROPPER, Blocks.DISPENSER,
            Blocks.ACACIA_TRAPDOOR, Blocks.ENCHANTING_TABLE, Blocks.WHITE_SHULKER_BOX, Blocks.ORANGE_SHULKER_BOX,
            Blocks.MAGENTA_SHULKER_BOX, Blocks.LIGHT_BLUE_SHULKER_BOX, Blocks.YELLOW_SHULKER_BOX, Blocks.LIME_SHULKER_BOX,
            Blocks.PINK_SHULKER_BOX, Blocks.GRAY_SHULKER_BOX, Blocks.CYAN_SHULKER_BOX, Blocks.PURPLE_SHULKER_BOX,
            Blocks.BLUE_SHULKER_BOX, Blocks.BROWN_SHULKER_BOX, Blocks.GREEN_SHULKER_BOX, Blocks.RED_SHULKER_BOX, Blocks.BLACK_SHULKER_BOX
    );
    public static void placeBlock(BlockPos pos, Direction side, boolean rotate) {
        clickBlock(pos.relative(side), side.getOpposite(), rotate);
        placeList.add(pos);
    }
    public static void placeSlabBlock(BlockPos pos, Direction side, Direction slabSide, boolean rotate) {
        clickSlabBlock(pos.relative(side), side.getOpposite(), slabSide, rotate);
        placeList.add(pos);
    }
    public static Block getBlock(BlockPos pos) {
        return mc.level.getBlockState(pos).getBlock();
    }
    public static void clickBlock(BlockPos pos, Direction side, boolean rotate) {
        Vec3 directionVec = new Vec3(pos.getX() + 0.5 + side.getUnitVec3i().getX() * 0.5, pos.getY() + 0.5 + side.getUnitVec3i().getY() * 0.5, pos.getZ() + 0.5 + side.getUnitVec3i().getZ() * 0.5);
        if (rotate) Rotation.snapAt(directionVec);
        EntityUtil.placeSwingHand();
        BlockHitResult result = new BlockHitResult(directionVec, side, pos, false);
        if (GlobalSetting.INSTANCE.packetPlace.get()){
            mc.getConnection().send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, result, 0));
        } else {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, result);
        }
        if (rotate) Rotation.snapBack();
    }
    public static boolean needSneak(Block in) {
        return shiftBlocks.contains(in);
    }
    public static void clickSlabBlock(BlockPos pos, Direction side, Direction slabSide, boolean rotate) {
        double yOffset = 0.5;
        if (slabSide == Direction.UP) yOffset += 0.1;
        if (slabSide == Direction.DOWN) yOffset -= 0.1;
        Vec3 directionVec = new Vec3(pos.getX() + 0.5 + side.getUnitVec3i().getX() * 0.5, pos.getY() + yOffset + side.getUnitVec3i().getY() * 0.5, pos.getZ() + 0.5 + side.getUnitVec3i().getZ() * 0.5);
        if (rotate) Rotation.snapAt(directionVec);
        BlockHitResult result = new BlockHitResult(directionVec, side, pos, false);
        if (GlobalSetting.INSTANCE.packetPlace.get()){
            mc.getConnection().send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, result, 0));
            mc.player.swing(InteractionHand.MAIN_HAND);
        } else {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, result);
        }
        if (rotate) Rotation.snapBack();
    }
    public static boolean canPlaceCrystal(BlockPos pos) {
        if (!mc.level.isEmptyBlock(pos)) return false;
        BlockPos obsPos = pos.below();
        BlockPos boost = obsPos.above();
        return (getBlock(obsPos) == Blocks.BEDROCK || getBlock(obsPos) == Blocks.OBSIDIAN)
                && getClickSideStrict(obsPos) != null
                && (mc.level.isEmptyBlock(boost))
                && !hasEntityBlockCrystal(boost, false)
                && !hasEntityBlockCrystal(boost.above(), false);
    }
    public static boolean hasEntityBlockCrystal(BlockPos pos, boolean ignoreCrystal) {
        for (Entity entity : getEntities(new AABB(pos))) {
            if (!entity.isAlive() || ignoreCrystal && entity instanceof EndCrystal)
                continue;
            return true;
        }
        return false;
    }
}
