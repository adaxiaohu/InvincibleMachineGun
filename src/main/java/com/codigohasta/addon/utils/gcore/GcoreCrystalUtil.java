package com.codigohasta.addon.utils.gcore;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public final class GcoreCrystalUtil {

    private GcoreCrystalUtil() {
    }

    // ─────────────────────────── canPlaceCheck ───────────────────────────

    /** 见 {@link #canPlaceCheck(GcoreEnumType.CrystalPlaceVersion, BlockPos, boolean, boolean, List)}，全默认。 */
    public static boolean canPlaceCheck(GcoreEnumType.CrystalPlaceVersion version, BlockPos placePos,
                                        boolean ignoreCrystal) {
        return canPlaceCheck(version, placePos, ignoreCrystal, false,
            GcoreTickUpdate.getWorldEntitiesList());
    }

    public static boolean canPlaceCheck(GcoreEnumType.CrystalPlaceVersion version, BlockPos placePos,
                                        boolean ignoreCrystal, boolean ignoreBase, List<Entity> list) {
        World world = MinecraftClient.getInstance().world;

        // ① 底座上方必须「空」
        if (world.isAir(placePos.up())) {
            // ② MC1_12 的水晶是 2 格高，要求上方 2 格都空
            if (version == GcoreEnumType.CrystalPlaceVersion.MC1_12) {
                if (!world.isAir(placePos.up(2))) {
                    return false;
                }
            }

            // ③ 底座合法 + ④ 1×2×1 空间内没有实体
            if ((ignoreBase || isCrystaBase(placePos))
                && !GcoreEntityUtil.checkAnyEntitiesCollie(
                    new Box(
                        placePos.getX() + 0.0, placePos.getY() + 1.0, placePos.getZ() + 0.0,
                        placePos.getX() + 1.0, placePos.getY() + 3.0, placePos.getZ() + 1.0),
                    ignoreCrystal,
                    list)) {
                return true;
            }
        }

        return false;
    }

    // ─────────────────────────── isCrystaBase ───────────────────────────

    public static boolean isCrystaBase(BlockPos placePos) {
        World world = MinecraftClient.getInstance().world;
        var block = world.getBlockState(placePos).getBlock();
        return block == Blocks.OBSIDIAN || block == Blocks.BEDROCK;
    }

    // ─────────────────────────── getAttackCrystalFromTarget ───────────────────────────

    /** 见 {@link #getAttackCrystalFromTarget(Entity, Vec3d, Entity, Vec3d, float, float, float, Predicate)}，全默认。 */
    public static Entity getAttackCrystalFromTarget(Entity target, Vec3d targetVec,
                                                    Entity attacker, Vec3d attackerVec) {
        return getAttackCrystalFromTarget(target, targetVec, attacker, attackerVec,
            0.0f, 1.0f, 12.0f, crystal -> true);
    }

    public static net.minecraft.entity.Entity getAttackCrystalFromTarget(
        Entity target, Vec3d targetVec,
        Entity attacker, Vec3d attackerVec,
        float minDamageToTarget, float maxDamageToSelf, float expSizeDistance,
        Predicate<net.minecraft.entity.Entity> filterFunction) {

        ArrayList<GcoreCrystalVecAndDamage> crystalList = new ArrayList<>();
        float sqExpDis = expSizeDistance * expSizeDistance;

        for (Entity entity : GcoreTickUpdate.getWorldEntitiesList()) {
            if (entity instanceof net.minecraft.entity.decoration.EndCrystalEntity
                && target.squaredDistanceTo(entity) < (double) sqExpDis
                && filterFunction.test(entity)) {

                Vec3d crystalPos = entity.getEntityPos();
                float damageToTarget = GcoreExplosionUtils.getExpDamage(
                    crystalPos, target, targetVec, null, GcoreCrystalUtils.getExplosion());

                if (!(damageToTarget < minDamageToTarget)) {
                    float damageToSelf;
                    if (maxDamageToSelf >= 1.0f) {
                        damageToSelf = -999.0f;
                    } else {
                        damageToSelf = GcoreExplosionUtils.getExpDamage(
                            crystalPos, attacker, attackerVec, null, GcoreCrystalUtils.getExplosion());
                    }

                    if (!(damageToSelf > maxDamageToSelf)) {
                        crystalList.add(new GcoreCrystalVecAndDamage(null, damageToTarget, entity));
                    }
                }
            }
        }

        // 源：maxWithOrNull(crystalList, comparingDouble(CrystalVecAndDamage::getDamage))
        GcoreCrystalVecAndDamage best = crystalList.stream()
            .max(Comparator.comparingDouble(GcoreCrystalVecAndDamage::getDamage))
            .orElse(null);

        return (best == null) ? null : best.getCrystalEntity();
    }
}
