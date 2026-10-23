package com.codigohasta.addon.utils.gcore;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public final class GcoreTpCrystalUtil {

    public static final double DEFAULT_PLACER_EYE_Y = 1.62;

    private static final int FIND_TP_VEC_SCAN_BORDER = 6;

    /** {@link #findAndSetTpVec} 里玩家碰撞箱的半宽（源 161/164 行）。 */
    private static final double PLACER_HALF_WIDTH = 0.3;

    /** {@link #findAndSetTpVec} 里玩家碰撞箱的高度（源 165 行）。 */
    private static final double PLACER_HEIGHT = 1.8;

    /** {@link #getSafePosToAttackCrystal} 里的虚空 Y 门槛（源 494 行）。 */
    private static final double VOID_Y = -64.0;

    public static final float DEFAULT_MAX_DAMAGE_TO_SELF = 1.0f;

    private GcoreTpCrystalUtil() {
    }

    // ═══════════════════════════ 入口 ═══════════════════════════

    /**
     * 见 {@link #getTpPlaceCrystalData(GcoreEnumType.CrystalPlaceVersion, Entity, Vec3d, double, double, int, Vec3d, int, float, boolean, boolean)}，
     * {@code placerEyeY} 取源默认值 {@code 1.62}。
     */
    public static GcoreTpCrystalAndObsidianPlaceData getTpPlaceCrystalData(
        GcoreEnumType.CrystalPlaceVersion version, Entity target, Vec3d targetVec,
        double placeRange, int placeAmount, Vec3d centerPos, int scanBorder,
        float minDamageToTarget, boolean allowPlaceObsidian, boolean allowPlaceCrystalSame) {
        return getTpPlaceCrystalData(version, target, targetVec, DEFAULT_PLACER_EYE_Y,
            placeRange, placeAmount, centerPos, scanBorder,
            minDamageToTarget, allowPlaceObsidian, allowPlaceCrystalSame);
    }

    public static GcoreTpCrystalAndObsidianPlaceData getTpPlaceCrystalData(
        GcoreEnumType.CrystalPlaceVersion version, Entity target, Vec3d targetVec,
        double placerEyeY, double placeRange, int placeAmount, Vec3d centerPos, int scanBorder,
        float minDamageToTarget, boolean allowPlaceObsidian, boolean allowPlaceCrystalSame) {
        return getTpPlaceCrystalData(version, target, targetVec, placerEyeY, placeRange, placeAmount,
            centerPos, scanBorder, minDamageToTarget, allowPlaceObsidian, allowPlaceCrystalSame, false);
    }

    /**
     * 见上一个重载；{@code fixMissingPlaceData} 为 <b>修复 A</b> 的开关
     * （{@code true} = 让每个被选中的方块都拿到 {@code placeData}）。
     */
    public static GcoreTpCrystalAndObsidianPlaceData getTpPlaceCrystalData(
        GcoreEnumType.CrystalPlaceVersion version, Entity target, Vec3d targetVec,
        double placerEyeY, double placeRange, int placeAmount, Vec3d centerPos, int scanBorder,
        float minDamageToTarget, boolean allowPlaceObsidian, boolean allowPlaceCrystalSame,
        boolean fixMissingPlaceData) {
        return getPlacePosDataListFinal(
            version,
            getPlacePosDataList(version, target, targetVec, centerPos, scanBorder,
                minDamageToTarget, allowPlaceObsidian),
            placerEyeY, placeRange, placeAmount,
            allowPlaceObsidian, allowPlaceCrystalSame, fixMissingPlaceData);
    }

    // ═══════════════════════════ ② 挑几个 ═══════════════════════════

    /** 见 {@link #getPlacePosDataListFinal(GcoreEnumType.CrystalPlaceVersion, List, double, double, int, boolean, boolean, boolean)}，{@code placerEyeY} 取默认。 */
    public static GcoreTpCrystalAndObsidianPlaceData getPlacePosDataListFinal(
        GcoreEnumType.CrystalPlaceVersion version,
        List<GcoreCrystalAndObsidianPlaceData> placeDataList,
        double placeRange, int placeAmount,
        boolean allowPlaceObsidian, boolean allowPlaceCrystalSame) {
        return getPlacePosDataListFinal(version, placeDataList, DEFAULT_PLACER_EYE_Y,
            placeRange, placeAmount, allowPlaceObsidian, allowPlaceCrystalSame, false);
    }

    public static GcoreTpCrystalAndObsidianPlaceData getPlacePosDataListFinal(
        GcoreEnumType.CrystalPlaceVersion version,
        List<GcoreCrystalAndObsidianPlaceData> placeDataList,
        double placerEyeY, double placeRange, int placeAmount,
        boolean allowPlaceObsidian, boolean allowPlaceCrystalSame,
        boolean fixMissingPlaceData) {

        int amount = 0;
        ArrayList<Box> collieBoxs = new ArrayList<>();
        ArrayList<GcoreCrystalAndObsidianPlaceData> finalPosDataList = new ArrayList<>();

        if (placeDataList.isEmpty()) {
            return null;
        }

        GcoreCrystalAndObsidianPlaceData dataFirst = placeDataList.get(0);
        finalPosDataList.add(dataFirst);
        Vec3d finalTpVec = null;

        for (GcoreCrystalAndObsidianPlaceData data : placeDataList) {
            if (amount >= placeAmount) {
                break;
            }

            // ★ 引用比较（源 Kotlin 的 !==）
            if (data != dataFirst) {
                Vec3d expVec = data.getExpVec();
                Box box = new Box(
                    expVec.x - 1.0, expVec.y, expVec.z - 1.0,
                    expVec.x + 1.0, expVec.y + 2.0, expVec.z + 1.0);

                if (!checkCollie(collieBoxs, box)) {
                    Vec3d tpVec;

                    if (fixMissingPlaceData) {
                        // ═══ 修复 A：先入列，再找站位 ═══
                        //   而 findAndSetTpVec 只给【已在列表里】的方块写 placeData
                        //   → 刚 add 的这个方块永远拿不到落点（placePos=null）
                        //   → 执行段跳过铺黑曜石，却仍然发水晶包 → 服务端必拒。
                        //   改成「先 add → 再 findAndSetTpVec」后，本方块也会被写回；
                        //   而且返回的 tpVec 是对【含本方块】的完整列表求得的，更自洽。
                        finalPosDataList.add(data);
                        tpVec = findAndSetTpVec(
                            finalPosDataList, version, dataFirst.getObsidianPos(),
                            allowPlaceObsidian, allowPlaceCrystalSame, placeRange, placerEyeY);

                        if (tpVec != null) {
                            collieBoxs.add(box);
                            amount++;
                            finalTpVec = tpVec;
                        } else {
                            // 找不到共同站位 → 回滚，恢复到「入列前」的状态
                            finalPosDataList.remove(data);
                        }
                    } else {
                        tpVec = findAndSetTpVec(
                            finalPosDataList, version, dataFirst.getObsidianPos(),
                            allowPlaceObsidian, allowPlaceCrystalSame, placeRange, placerEyeY);

                        if (tpVec != null) {
                            finalPosDataList.add(data);
                            collieBoxs.add(box);
                            amount++;
                            finalTpVec = tpVec;
                        }
                    }
                }
            }
        }

        // 【诊断】阶段③：选站位的最终结果（只读，不影响判定）
        if (finalTpVec == null) {
            GcoreWebTrapDiag.logf("站位", "★找不到可站立点 → 放弃 (候选=%d placeAmount=%d allowSame=%b)",
                placeDataList.size(), placeAmount, allowPlaceCrystalSame);
        } else {
            StringBuilder sb = new StringBuilder();
            for (GcoreCrystalAndObsidianPlaceData d : finalPosDataList) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(d.getType()).append('@').append(d.getObsidianPos());
            }
            GcoreWebTrapDiag.logf("站位", "OK tpVec=[%.2f,%.2f,%.2f] 选中=%d [%s]",
                finalTpVec.x, finalTpVec.y, finalTpVec.z, finalPosDataList.size(), sb);
        }

        return (finalTpVec == null)
            ? null
            : new GcoreTpCrystalAndObsidianPlaceData(finalTpVec, finalPosDataList);
    }

    private static boolean checkCollie(ArrayList<Box> collieBoxs, Box box) {
        for (Box bbox : collieBoxs) {
            if (box.intersects(bbox)) {
                return true;
            }
        }
        return false;
    }

    // ═══════════════════════════ ③ 找站哪 ═══════════════════════════

    /** 见 {@link #findAndSetTpVec(ArrayList, GcoreEnumType.CrystalPlaceVersion, BlockPos, boolean, boolean, double, double)}，{@code placerEyeY} 取默认。 */
    public static Vec3d findAndSetTpVec(
        ArrayList<GcoreCrystalAndObsidianPlaceData> placeDataList,
        GcoreEnumType.CrystalPlaceVersion version, BlockPos centerPos,
        boolean allowPlaceObsidian, boolean allowPlaceCrystalSame, double placeRange) {
        return findAndSetTpVec(placeDataList, version, centerPos,
            allowPlaceObsidian, allowPlaceCrystalSame, placeRange, DEFAULT_PLACER_EYE_Y);
    }

    public static Vec3d findAndSetTpVec(
        ArrayList<GcoreCrystalAndObsidianPlaceData> placeDataList,
        GcoreEnumType.CrystalPlaceVersion version, BlockPos centerPos,
        boolean allowPlaceObsidian, boolean allowPlaceCrystalSame,
        double placeRange, double placerEyeY) {

        MinecraftClient mc = MinecraftClient.getInstance();

        int centerX = centerPos.getX();
        int centerY = centerPos.getY();
        int centerZ = centerPos.getZ();
        int scanBorder = FIND_TP_VEC_SCAN_BORDER;

        for (int xf = centerX - scanBorder; xf <= centerX + scanBorder; xf++) {
            for (int yf = centerY - scanBorder; yf <= centerY + scanBorder; yf++) {
                for (int zf = centerZ - scanBorder; zf <= centerZ + scanBorder; zf++) {

                    if (yf > VOID_Y) {
                        // ★ 站位取「格子底面」：y + 0.0
                        Vec3d placeVec = new Vec3d(xf + 0.5, yf + 0.0, zf + 0.5);

                        Box placerBox = new Box(
                            placeVec.x - PLACER_HALF_WIDTH, placeVec.y, placeVec.z - PLACER_HALF_WIDTH,
                            placeVec.x + PLACER_HALF_WIDTH, placeVec.y + PLACER_HEIGHT, placeVec.z + PLACER_HALF_WIDTH);

                        if (GcoreBlockUtil.checkNoPosCollie(placeVec)) {
                            boolean flag = true;

                            // touchCheck 闭包（源 180-188 行）
                            //   pos.toCenterPos().distanceTo(placeVec + (0, placerEyeY, 0)) <= 36.0
                            //   && placeVec.squaredDistanceTo(pos + 0.5) < placeRange²
                            Vec3d placerEyeVec = placeVec.add(0.0, placerEyeY, 0.0);

                            // placeDataCheck 闭包（源 191-203 行）
                            //   world.getBlockState(pos).canOcclude() && touchCheck(pos)
                            for (GcoreCrystalAndObsidianPlaceData data : placeDataList) {
                                BlockPos obsPos = data.getObsidianPos();

                                // ★ 1×3×1（黑曜石 1 + 水晶 2）
                                Box obsBox = new Box(
                                    obsPos.getX(), obsPos.getY(), obsPos.getZ(),
                                    obsPos.getX() + 1.0, obsPos.getY() + 3.0, obsPos.getZ() + 1.0);

                                if (obsBox.intersects(placerBox)) {
                                    flag = false;
                                } else {
                                    boolean canTouchObs = touchCheck(obsPos, placerEyeVec, placeVec, placeRange);

                                    if (data.getType() == GcoreCrystalAndObsidianPlaceData.Type.PlaceCrystal) {
                                        if (canTouchObs) {
                                            data.setPlaceData(new GcorePlaceData(obsPos, Direction.DOWN, obsPos));
                                            continue;
                                        }
                                        flag = false;
                                    } else if (data.getType() == GcoreCrystalAndObsidianPlaceData.Type.PlaceObsidian) {
                                        if (!allowPlaceObsidian) {
                                            // ★ 源是 continue（跳过这个方块），不是 flag = false
                                            continue;
                                        }

                                        GcorePlaceData placeData;
                                        if (version == GcoreEnumType.CrystalPlaceVersion.MC1_14 && canTouchObs) {
                                            placeData = new GcorePlaceData(obsPos, Direction.DOWN, obsPos);
                                        } else {
                                            placeData = null;
                                            for (Direction dir : Direction.values()) {
                                                BlockPos pos = obsPos.offset(dir);
                                                if (placeDataCheck(pos, dir, placerEyeVec, placeVec, placeRange)) {
                                                    placeData = new GcorePlaceData(pos, dir.getOpposite(), obsPos);
                                                    break;
                                                }
                                            }
                                        }

                                        if (placeData == null) {
                                            flag = false;
                                        } else if (!allowPlaceCrystalSame || canTouchObs) {
                                            data.setPlaceData(placeData);
                                            continue;
                                        } else {
                                            flag = false;
                                        }
                                    }
                                }
                            }

                            if (flag) {
                                return placeVec;
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    private static boolean touchCheck(BlockPos pos, Vec3d placerEyeVec, Vec3d placeVec, double placeRange) {
        return pos.toCenterPos().distanceTo(placerEyeVec) <= 36.0
            && placeVec.squaredDistanceTo(
                   pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < placeRange * placeRange;
    }

    private static boolean placeDataCheck(BlockPos pos, Direction dir, Vec3d placerEyeVec,
                                          Vec3d placeVec, double placeRange) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.world.getBlockState(pos).isOpaque()
            && touchCheck(pos, placerEyeVec, placeVec, placeRange);
    }

    // ═══════════════════════════ ① 扫描候选 ═══════════════════════════

    /** 见 {@link #getPlacePosDataList(GcoreEnumType.CrystalPlaceVersion, Entity, Vec3d, Vec3d, int, float, boolean)}，{@code scanBorder} 由调用方给。 */
    public static List<GcoreCrystalAndObsidianPlaceData> getPlacePosDataList(
        GcoreEnumType.CrystalPlaceVersion version, Entity target, Vec3d targetVec,
        Vec3d centerPos, int scanBorder, float minDamageToTarget, boolean allowPlaceObsidian) {

        MinecraftClient mc = MinecraftClient.getInstance();

        int centerX = (int) Math.ceil(centerPos.x);
        int centerY = (int) Math.ceil(centerPos.y);
        int centerZ = (int) Math.ceil(centerPos.z);

        ArrayList<GcoreCrystalAndObsidianPlaceData> posList = new ArrayList<>();
        ArrayList<Entity> entities = GcoreTickUpdate.getWorldEntitiesList();

        for (int xf = centerX - scanBorder; xf <= centerX + scanBorder; xf++) {
            for (int yf = centerY - scanBorder; yf <= centerY + scanBorder; yf++) {
                for (int zf = centerZ - scanBorder; zf <= centerZ + scanBorder; zf++) {

                    BlockPos obsPos = new BlockPos(xf, yf, zf);
                    // ★ 爆炸中心 = 底座上方 1 格的中心
                    Vec3d expVec = new Vec3d(xf + 0.5, yf + 1.0, zf + 0.5);

                    if (GcoreCrystalUtil.canPlaceCheck(version, obsPos, true, true, entities)) {
                        if (GcoreCrystalUtil.isCrystaBase(obsPos)) {
                            // ── 情况 A：底座已存在 → 只放水晶 ──
                            float damageToTarget = GcoreExplosionUtils.getExpDamage(
                                expVec, target, targetVec, null, GcoreCrystalUtils.getExplosion());
                            if (damageToTarget >= minDamageToTarget) {
                                posList.add(new GcoreCrystalAndObsidianPlaceData(
                                    GcoreCrystalAndObsidianPlaceData.Type.PlaceCrystal,
                                    null, expVec, damageToTarget, obsPos));
                            }
                        } else if (allowPlaceObsidian && GcoreBlockUtil.canPlaceAtPos(obsPos, entities)) {
                            // ── 情况 B：要自己放黑曜石 → 假装已放，再算伤害 ──
                            BlockPos finalObsPos = obsPos;
                            Function<BlockPos, BlockState> stateProvider =
                                it -> it.equals(finalObsPos)
                                    ? Blocks.OBSIDIAN.getDefaultState()
                                    : mc.world.getBlockState(it);

                            float damageToTarget = GcoreExplosionUtils.getExpDamage(
                                expVec, target, targetVec, stateProvider, GcoreCrystalUtils.getExplosion());
                            if (damageToTarget >= minDamageToTarget) {
                                posList.add(new GcoreCrystalAndObsidianPlaceData(
                                    GcoreCrystalAndObsidianPlaceData.Type.PlaceObsidian,
                                    null, expVec, damageToTarget, obsPos));
                            }
                        }
                    }
                }
            }
        }

        // ★ 源：sortedWith(posList, getPlacePosDataList$$inlined$sortedBy$1)
        //   比较器 = compareValues(-a.getDamage(), -b.getDamage()) → 伤害**降序**
        posList.sort(Comparator.comparingDouble(
            (GcoreCrystalAndObsidianPlaceData d) -> -d.getDamage()));

        // 【诊断】阶段②：候选扫描结果（只读，不影响判定）
        int nCrystal = 0;
        for (GcoreCrystalAndObsidianPlaceData d : posList) {
            if (d.getType() == GcoreCrystalAndObsidianPlaceData.Type.PlaceCrystal) {
                nCrystal++;
            }
        }
        if (posList.isEmpty()) {
            GcoreWebTrapDiag.logf("候选", "★无候选 → 放弃 (minDmg=%.2f allowObs=%b)",
                minDamageToTarget, allowPlaceObsidian);
        } else {
            GcoreWebTrapDiag.logf("候选",
                "候选=%d (水晶=%d 黑曜石=%d) minDmg=%.2f allowObs=%b top=[%s dmg=%.4f]",
                posList.size(), nCrystal, posList.size() - nCrystal,
                minDamageToTarget, allowPlaceObsidian,
                posList.get(0).getObsidianPos(), posList.get(0).getDamage());
        }

        return posList;
    }

    // ═══════════════════════════ 打水晶时的安全站位 ═══════════════════════════

    /** 见 {@link #getSafePosToAttackCrystal(Vec3d, int, double, double, float)}，{@code maxDamageToSelf} 取源默认 {@code 1.0f}。 */
    public static Vec3d getSafePosToAttackCrystal(Vec3d crystalVec, int scanBorder,
                                                  double hitRange, double throughWallRange) {
        return getSafePosToAttackCrystal(crystalVec, scanBorder, hitRange, throughWallRange,
            DEFAULT_MAX_DAMAGE_TO_SELF);
    }

    public static Vec3d getSafePosToAttackCrystal(Vec3d crystalVec, int scanBorder,
                                                  double hitRange, double throughWallRange,
                                                  float maxDamageToSelf) {

        MinecraftClient mc = MinecraftClient.getInstance();
        Entity player = mc.player;

        Vec3d expVec = crystalVec;
        ArrayList<GcoreCrystalVecAndDamage> vecList = new ArrayList<>();
        Vec3d bestVec = null;
        boolean success = false;

        int cx = (int) Math.ceil(crystalVec.x);
        int cy = (int) Math.ceil(crystalVec.y);
        int cz = (int) Math.ceil(crystalVec.z);

        for (int xf = cx - scanBorder; xf <= cx + scanBorder; xf++) {
            for (int yf = cy - scanBorder; yf <= cy + scanBorder; yf++) {
                for (int zf = cz - scanBorder; zf <= cz + scanBorder; zf++) {

                    if (success) {
                        return bestVec;
                    }

                    Vec3d[] candidates = new Vec3d[] {
                        new Vec3d(xf + 0.5, yf + 0.0, zf + 0.5),
                        new Vec3d(xf + 0.5, yf + 0.2, zf + 0.5)
                    };

                    for (Vec3d vec : candidates) {
                        if (vec.y > VOID_Y
                            && GcoreBlockUtil.checkNoPosCollie(vec)
                            && GcoreAimUtil.checkCanHit(vec, DEFAULT_PLACER_EYE_Y, expVec, 1.8,
                                   hitRange, throughWallRange)) {

                            float damageToSelf = GcoreExplosionUtils.getExpDamage(
                                expVec, player, vec, null, GcoreCrystalUtils.getExplosion());

                            if (damageToSelf == 0.0f) {
                                success = true;
                                bestVec = vec;
                            } else if (damageToSelf <= maxDamageToSelf) {
                                vecList.add(new GcoreCrystalVecAndDamage(vec, damageToSelf, null));
                            }
                        }
                    }
                }
            }
        }

        // ★ 自伤升序（比较器直接返回 getDamage()，不取负）
        vecList.sort(Comparator.comparingDouble(GcoreCrystalVecAndDamage::getDamage));

        return vecList.isEmpty() ? null : vecList.get(0).getVec();
    }
}
