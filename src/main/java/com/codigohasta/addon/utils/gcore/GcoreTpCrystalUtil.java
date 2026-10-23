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

/**
 * TP 水晶的核心算法（找放哪 / 找站哪 / 打水晶时的安全站位）。
 *
 * <p>移植自 {@code dev.godden.core.Utils.TpCrystalUtil}（GCore，源 559 行）。
 *
 * <p><b>裁剪说明</b>：源有 7 个公开方法，TP 水晶路径**只用 5 个**
 * （另有 2 个是死代码，见下）：
 * <ul>
 *     <li>{@link #getTpPlaceCrystalData} —— 入口（= 下面两步串联）</li>
 *     <li>{@link #getPlacePosDataListFinal} —— 从候选里挑 {@code PlaceAmount} 个</li>
 *     <li>{@link #findAndSetTpVec} —— 找一个能服务所有方块的站位</li>
 *     <li>{@link #getPlacePosDataList} —— 扫描候选格 + 算伤害 + 按伤害降序</li>
 *     <li>{@link #getSafePosToAttackCrystal} —— 打已有水晶时的安全站位</li>
 * </ul>
 *
 * <p><b>★ 两个死代码（全树无调用者，不移植）</b>：
 * <ul>
 *     <li>{@code getLightTpPlaceCrystalData}（源 33-55）——
 *         实现体与 {@link #getTpPlaceCrystalData} <b>完全相同</b>，疑似重构残留</li>
 *     <li>{@code getDamagePosList}（源 405-464）——
 *         用 {@code DamageUtils.calculateReducedDamage}（<b>减伤后</b>伤害，考虑护甲）。
 *         模块实际用的是 <b>裸伤害</b>（{@link GcoreExplosionUtils#getExpDamage}），
 *         所以它没被启用。**用户已确认不移植**</li>
 * </ul>
 *
 * <h2>★ 与锚模块的关键差异</h2>
 * <p>水晶需要<b>先有底座</b>，所以「放黑曜石」时要<b>假装那一格已经是黑曜石</b>
 * 来算伤害（因为伤害要按「放完之后」的世界算）。这就是
 * {@code blockStateProvider} 参数的用途：
 * <pre>
 * pos -&gt; pos.equals(obsPos) ? Blocks.OBSIDIAN.getDefaultState() : world.getBlockState(pos)
 * </pre>
 */
public final class GcoreTpCrystalUtil {

    /** 源里 {@code placerEyeY} 的默认参数。 */
    public static final double DEFAULT_PLACER_EYE_Y = 1.62;

    /**
     * {@link #findAndSetTpVec} 里硬编码的搜索半径。
     *
     * <p><b>★ 这是硬编码，不是设置项</b>（源 145 行 {@code val scanBorder$iv: Int = 6}）——
     * 与模块的 {@code ScanBorder} 设置**无关**。照抄不改。
     */
    private static final int FIND_TP_VEC_SCAN_BORDER = 6;

    /** {@link #findAndSetTpVec} 里玩家碰撞箱的半宽（源 161/164 行）。 */
    private static final double PLACER_HALF_WIDTH = 0.3;

    /** {@link #findAndSetTpVec} 里玩家碰撞箱的高度（源 165 行）。 */
    private static final double PLACER_HEIGHT = 1.8;

    /** {@link #getSafePosToAttackCrystal} 里的虚空 Y 门槛（源 494 行）。 */
    private static final double VOID_Y = -64.0;

    /** 源的 {@code getSafePosToAttackCrystal} 参数 {@code maxDamageToSelf} 默认值。 */
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

    /**
     * 算一整套「放水晶」方案。
     *
     * <p>逐行对应源的 {@code TpCrystalUtil.getTpPlaceCrystalData}（源 57-79 行）——
     * 它只是把 {@link #getPlacePosDataList} 的结果交给
     * {@link #getPlacePosDataListFinal}：
     * <pre>
     * return getPlacePosDataListFinal(
     *     version,
     *     getPlacePosDataList(version, target, targetVec, centerPos, scanBorder,
     *                         minDamageToTarget, allowPlaceObsidian),
     *     placerEyeY, placeRange, placeAmount,
     *     allowPlaceObsidian, allowPlaceCrystalSame);
     * </pre>
     *
     * @return 方案；无解时 {@code null}
     */
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

    /**
     * 从候选列表里挑出 {@code placeAmount} 个不互相挡的方块，并为它们找一个共同站位。
     *
     * <p>逐行对应源的 {@code TpCrystalUtil.getPlacePosDataListFinal}（源 81-130 行）。
     *
     * <pre>
     * amount = 0; collieBoxs = []; finalPosDataList = []
     * if (placeDataList.isEmpty()) return null;
     * dataFirst = placeDataList.first();        // ★ 伤害最高的那个（列表已降序）
     * finalPosDataList.add(dataFirst);
     * finalTpVec = null;
     * for (data : placeDataList) {
     *     if (amount &gt;= placeAmount) break;
     *     if (data != dataFirst) {                              // ★ 引用比较
     *         box = Box(expVec.x-1, expVec.y, expVec.z-1, expVec.x+1, expVec.y+2, expVec.z+1);
     *         if (!checkCollie(collieBoxs, box)) {              // 与已选的爆炸范围不重叠
     *             tpVec = findAndSetTpVec(finalPosDataList, version, dataFirst.obsidianPos, ...);
     *             if (tpVec != null) {
     *                 finalPosDataList.add(data); collieBoxs.add(box);
     *                 amount++; finalTpVec = tpVec;
     *             }
     *         }
     *     }
     * }
     * return finalTpVec == null ? null : new TpCrystalAndObsidianPlaceData(finalTpVec, finalPosDataList);
     * </pre>
     *
     * <p><b>★ 三个要点</b>：
     * <ol>
     *     <li>{@code data != dataFirst} 是 <b>引用比较</b>（Kotlin 的 {@code !==}，
     *         字节码 {@code if_acmpeq}）。{@code dataFirst} 已被无条件加入列表，
     *         所以循环里要跳过它。**不能写成 {@code equals}** ——
     *         源类没有覆写 {@code equals}，行为上等价，但语义要照抄。</li>
     *     <li><b>每次都要重新调 {@code findAndSetTpVec}</b>，且传的是
     *         <b>不断增长的</b> {@code finalPosDataList} ——
     *         意思是「加进这个方块后，还找得到共同站位吗」。
     *         找不到就**不加**它，继续试下一个。</li>
     *     <li>{@code amount} 从 0 开始，但 {@code dataFirst} 已经占了 1 个 ——
     *         所以实际最多选 {@code placeAmount + 1} 个。**这是源的写法，照抄**。</li>
     * </ol>
     */
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
                        //   源的顺序是「先 findAndSetTpVec → 再 add」，
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
                        // ═══ 源行为（逐字）═══
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

    /**
     * 源的 {@code getPlacePosDataListFinal$checkCollie}（源 544-553 行）——
     * 新盒子与已有盒子列表里任一相交则返回 {@code true}。
     */
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

    /**
     * 找一个「能同时够到列表里每一个方块」的站位，并**顺便写回**每个方块的放置面。
     *
     * <p>逐行对应源的 {@code TpCrystalUtil.findAndSetTpVec}（源 132-304 行）——
     * 本类最复杂的一段。
     *
     * <pre>
     * for (x,y,z 在 centerPos ± 6 内) {                      // ★ scanBorder 硬编码 6
     *     if (y &lt;= -64) continue;                            // 不进虚空
     *     placeVec = new Vec3d(x + 0.5, y + 0.0, z + 0.5);   // ★ y + 0.0（不是 +0.5）
     *     placerBox = Box(placeVec.x±0.3, placeVec.y ~ +1.8, placeVec.z±0.3);
     *     if (checkNoPosCollie(placeVec)) {
     *         flag = true;
     *         for (data : placeDataList) {
     *             obsBox = Box(obs.x, obs.y, obs.z, obs.x+1, obs.y+3, obs.z+1);   // ★ 1×3×1
     *             if (obsBox.intersects(placerBox)) { flag = false; }             // 不能站进方块
     *             else {
     *                 canTouchObs = touchCheck(obsPos);
     *                 if (data.type == PlaceCrystal) {
     *                     if (canTouchObs) data.setPlaceData(new PlaceData(obsPos, DOWN, obsPos));
     *                     else flag = false;
     *                 } else if (data.type == PlaceObsidian) {
     *                     if (!allowPlaceObsidian) continue;
     *                     if (version == MC1_14 &amp;&amp; canTouchObs) {
     *                         placeData = new PlaceData(obsPos, DOWN, obsPos);
     *                     } else {
     *                         for (dir : Direction.values()) {                     // ★ 6 个方向
     *                             pos = obsPos.offset(dir);
     *                             if (placeDataCheck(pos, dir)) {
     *                                 placeData = new PlaceData(pos, dir.getOpposite(), obsPos);
     *                                 break;
     *                             }
     *                         }
     *                     }
     *                     if (placeData == null) flag = false;
     *                     else if (!allowPlaceCrystalSame || canTouchObs) data.setPlaceData(placeData);
     *                     else flag = false;
     *                 }
     *             }
     *         }
     *         if (flag) return placeVec;                     // ★ 全部通过 → 就是它
     *     }
     * }
     * return null;
     * </pre>
     *
     * <p><b>★ 五个容易抄错的点（都已用字节码核实）</b>：
     * <ol>
     *     <li><b>{@code placeVec.y = y + 0.0}</b>，不是 {@code y + 0.5} ——
     *         站位取<b>格子底面</b>（玩家脚的位置）。
     *         对比 {@link #getSafePosToAttackCrystal} 用的是 {@code y + 0.5}。</li>
     *     <li><b>{@code obsBox} 高 <code>3</code> 格</b>（{@code obs.y} 到 {@code obs.y+3}）——
     *         黑曜石 1 格 + 水晶 2 格。抄成 {@code +1} 或 {@code +2} 都会让
     *         「玩家站进水晶空间」被漏判。</li>
     *     <li><b>{@code version == MC1_14 && canTouchObs} 才直接朝下放</b>；
     *         否则（含 MC1_13）要遍历 6 个方向找可放的面。</li>
     *     <li><b>方向顺序用 {@code Direction.values()}</b>（源是
     *         {@code BlockUtil.EntriesMappings}，展开后就是 {@code Direction.values()}
     *         —— 已对照 {@code GcoreBlockUtil.getPlaceDataToPos} 的既有实现）。</li>
     *     <li><b>{@code continue} 与 {@code flag = false} 语义不同</b>：
     *         {@code !allowPlaceObsidian} 时是 <b>{@code continue}</b>
     *         （跳过这个方块，不否决站位）；其他失败是 <b>{@code flag = false}</b>
     *         （否决这个站位）。**抄反会导致要么永远找不到、要么放下会卡住**。</li>
     * </ol>
     */
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

    /**
     * 源的 {@code findAndSetTpVec} 里的 {@code touchCheck} 闭包（源 180-188 行）。
     *
     * <pre>
     * pos.toCenterPos().distanceTo(placeVec + (0, placerEyeY, 0)) &lt;= 36.0   // ★ 6 格²
     *     &amp;&amp; placeVec.squaredDistanceTo(pos + 0.5) &lt; placeRange²
     * </pre>
     *
     * <p><b>★ {@code 36.0} 是硬编码</b>（原版方块交互距离 6 格的平方），
     * 与 {@code placeRange} 设置无关。
     */
    private static boolean touchCheck(BlockPos pos, Vec3d placerEyeVec, Vec3d placeVec, double placeRange) {
        return pos.toCenterPos().distanceTo(placerEyeVec) <= 36.0
            && placeVec.squaredDistanceTo(
                   pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < placeRange * placeRange;
    }

    /**
     * 源的 {@code findAndSetTpVec} 里的 {@code placeDataCheck} 闭包（源 191-203 行）。
     *
     * <pre>
     * world.getBlockState(pos).canOcclude() &amp;&amp; touchCheck(pos)
     * </pre>
     *
     * <p>{@code isOpaque()} 对应源的 {@code method_26225} ——
     * 在项目实际使用的 Yarn 映射（{@code 1.21.11+build.4}）里就叫
     * <b>{@code isOpaque}</b>（映射表已核）。
     */
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

    /**
     * 找「我传过去能打到水晶、但自己不吃炸」的站位。
     *
     * <p>逐行对应源的 {@code TpCrystalUtil.getSafePosToAttackCrystal}（源 466-542 行）。
     *
     * <pre>
     * vecList = []; bestVec = null; success = false;
     * for (x,y,z 在 crystalVec ± scanBorder 内) {
     *     if (success) return bestVec;                       // ★ 已找到零伤点 → 立即返回
     *     for (vec : [ (x+0.5, y+0.0, z+0.5),               // ★ 每个格子试**两个高度**
     *                  (x+0.5, y+0.2, z+0.5) ]) {
     *         if (vec.y &gt; -64
     *             &amp;&amp; checkNoPosCollie(vec)
     *             &amp;&amp; AimUtil.checkCanHit(vec, 1.62, crystalVec, 1.8, hitRange, throughWallRange)) {
     *             dmgToSelf = getExpDamage(crystalVec, player, vec);
     *             if (dmgToSelf == 0.0f) { success = true; bestVec = vec; }   // 优先级 1
     *             else if (dmgToSelf &lt;= maxDamageToSelf) vecList.add(...);     // 优先级 2
     *         }
     *     }
     * }
     * sortWith(vecList, comparingDouble(CrystalVecAndDamage::getDamage));       // ★ 自伤升序
     * return vecList.isEmpty() ? null : vecList.get(0).getVec();
     * </pre>
     *
     * <p><b>★ 三个要点（已用字节码核实）</b>：
     * <ol>
     *     <li><b>每个格子试两个高度：{@code y + 0.0} 和 {@code y + 0.2}</b>
     *         （字节码偏移 173 是 {@code ldc2_w 0.5d}，偏移 198 是 {@code ldc2_w 0.2d}）。
     *         反编译产物把这两元素数组完全丢掉了，只留一个「未初始化的 Iterable」——
     *         **必须回字节码才看得到**。这是本函数最容易漏的地方。</li>
     *     <li><b>零伤立即返回</b>（{@code damageToSelf == 0.0f}）——
     *         零伤是理论最优，继续扫只是浪费。注意是 <b>{@code ==}</b> 浮点精确比较，
     *         因为 {@code getExpDamage} 对「完全遮挡」返回的正是精确 {@code 0.0f}。</li>
     *     <li><b>排序是自伤升序</b>（{@code getSafePosToAttackCrystal$2} 直接返回
     *         {@code getDamage()}，不取负）—— 与 {@link #getPlacePosDataList}
     *         的降序**方向相反**。</li>
     * </ol>
     *
     * <p><b>{@code 1.62} / {@code 1.8}</b>：玩家眼高 / 水晶靶心高。
     * 射线从「眼睛」发出打到水晶上沿。
     *
     * @param scanBorder 搜索半径。模块传 <b>6</b>（硬编码，不是设置项）
     */
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

                    // ★ 每个格子试两个高度（字节码：0.5d 与 0.2d）
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
