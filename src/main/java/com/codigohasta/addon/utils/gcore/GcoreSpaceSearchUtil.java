package com.codigohasta.addon.utils.gcore;

import com.codigohasta.addon.utils.slimefunhelper.utils.CollisionUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 「头顶可用空间」搜索 —— 重锤模拟高度的动态计算。
 *
 * <p><b>移植自 {@code slimefunhelper} 的 ＠{@code Attack.processMaceAttack} 依赖链</b>：
 * <pre>
 *   Attack.processMaceAttack
 *     └─ MovTasks.searchFirstNoCollisionSpaceYHeight(origin, min, max, upOrDown)
 *          ├─ mc.player.dimensions.getBoxAt(origin)          // 玩家在该处的碰撞箱
 *          ├─ CollisionUtil.resetY(box, y+tmin, y+tmax)      // 沿 Y 拉伸成搜索柱
 *          ├─ CollisionUtil.getCollisions(...)               // 采集柱内所有碰撞体
 *          └─ MovTasks.searchFirstNoYConflictYHeight(...)    // 求第一个不冲突的高度
 * </pre>
 *
 * <p>这是 slimefunhelper 重锤 TP「动态模拟高度」的真正核心：
 * 它不是死用一个高度，而是先问「从我头顶往上/往下，第一个能不卡住的位置在哪」。
 *
 * <p><b>为什么必须用它</b>：固定高度在室内会撞天花板（服务端判
 * {@code moved wrongly} / {@code clipped into block} 而拉回），在露天又浪费高度。
 * slimefunhelper 的做法是「以设定值为起点，向下找第一个可行高度」。
 */
public final class GcoreSpaceSearchUtil {

    private GcoreSpaceSearchUtil() {
    }

    /** 玩家碰撞箱高度，多给 2E-7 的余量（照抄 slimefunhelper）。 */
    private static final double BOX_EPSILON = 2.0E-7;

    /**
     * 在 {@code origin} 处沿 Y 方向找「第一个不与任何碰撞体冲突」的高度差。
     *
     * <p>逐行对应 {@code MovTasks.searchFirstNoCollisionSpaceYHeight}
     * （源 157-183 行）＋ {@code searchFirstNoYConflictYHeight}（源 606-668 行）。
     *
     * @param origin   搜索起点（一般传 {@code playerPos.add(0, maxMace, 0)}）
     * @param min      最小高度差（{@code upOrDown=true} 时向下界）
     * @param max      最大高度差
     * @param upOrDown {@code true} = 向上搜；{@code false} = 向下搜
     * @return 相对 {@code origin.y} 的高度差；原方法可能返回越界值（源里标注 fixme），这里照抄
     */
    public static double searchFirstNoCollisionSpaceYHeight(Vec3d origin, double min, double max,
                                                           boolean upOrDown) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null) {
            return 0.0;
        }

        Box boundingBox = player.dimensions.getBoxAt(origin);
        double tmin = upOrDown ? min : -max;
        double tmax = upOrDown ? max : -min;

        // 沿 Y 拉伸成「搜索柱」：XZ 保持玩家碰撞箱，Y 覆盖 [origin.y+tmin, origin.y+tmax]
        Box involved = CollisionUtil.resetY(boundingBox, origin.y + tmin, origin.y + tmax);

        List<Box> collisionsBB = new ArrayList<>();
        List<VoxelShape> collisionsVoxel = new ArrayList<>();
        // flags 照抄 slimefunhelper：COLLISION_FLAG_CHECK_BORDER
        CollisionUtil.getCollisions(
            mc.world,
            player,
            involved,
            collisionsVoxel,
            collisionsBB,
            CollisionUtil.COLLISION_FLAG_CHECK_BORDER,
            null,
            null,
            null);

        return searchFirstNoYConflictYHeight(origin.y, collisionsBB, collisionsVoxel, min, max, upOrDown);
    }

    /**
     * 从碰撞体列表中求「第一个 Y 方向不冲突的高度」。
     *
     * <p>逐行对应 {@code MovTasks.searchFirstNoYConflictYHeight}（源 606-668 行）：
     * <pre>
     *   把 AABB 与 VoxelShape 的包围盒合并成 allBoxes
     *   向上：按 minY 升序；从 levelY = originY + min 起，
     *         若 [levelY, levelY+玩家高] 与某盒重叠 → levelY 推到该盒 maxY（若没越界）
     *         一旦 levelY+玩家高 <= 某盒 minY → 此后再无冲突，结束
     *   向下：按 minY 降序；从 levelY = originY - min 起，
     *         若重叠 → levelY 推到该盒 minY - 玩家高（若没越界）
     * </pre>
     *
     * @return {@code levelY - originY}
     */
    public static double searchFirstNoYConflictYHeight(double originY,
                                                       List<Box> collisionsBB,
                                                       List<VoxelShape> collisionShapes,
                                                       double min,
                                                       double max,
                                                       boolean upOrDown) {
        List<Box> allBoxes = new ArrayList<>(collisionsBB);
        for (VoxelShape shape : collisionShapes) {
            allBoxes.addAll(shape.getBoundingBoxes());
        }
        allBoxes.sort(Comparator.comparingDouble(b -> b.minY * (upOrDown ? 1.0D : -1.0D)));

        MinecraftClient mc = MinecraftClient.getInstance();
        double playerBoxHeight = (mc.player == null ? 1.8 : mc.player.dimensions.height()) + BOX_EPSILON;

        if (upOrDown) {
            double levelY = originY + min;
            for (Box re : allBoxes) {
                // 该高度区间已与这个盒子冲突 → 抬到盒子顶部
                if (levelY + playerBoxHeight > re.minY && levelY < re.maxY) {
                    if (re.maxY < originY + max) {
                        levelY = re.maxY;
                    } else {
                        break;   // 超出搜索上界
                    }
                } else if (levelY + playerBoxHeight <= re.minY) {
                    // 已按 minY 升序 → 这个位置不会再和后面任何盒子冲突
                    break;
                }
            }
            return levelY - originY;
        } else {
            double levelY = originY - min;
            for (Box re : allBoxes) {
                if (levelY < re.maxY && levelY + playerBoxHeight > re.minY) {
                    double newLevelY = re.minY - playerBoxHeight;
                    if (newLevelY > originY - max) {
                        levelY = newLevelY;
                    } else {
                        break;   // 超出搜索下界
                    }
                } else if (levelY >= re.maxY) {
                    break;
                }
            }
            return levelY - originY;
        }
    }

    // ═══════════ ★ 2026-10-13：找「头顶有足够空间」的柱子 ═══════════

    /**
     * ★★★ 在 {@code around} 附近找一根「站得住、且头顶有 {@code needY} 格空间」的柱子。
     *
     * <h3>为什么要这个（用户理想情况 ① ~ ③）</h3>
     * 服务端 {@code ServerGamePacketListenerImpl}：
     * <pre>
     * 1574: this.player.move(MoverType.PLAYER, new Vec3(d3,d4,d5));   // ★ 真的移动
     * 1610: allowMovement = !hasNewCollision(oldBox, newBox);         // ★ 目标位置不能卡方块
     * </pre>
     * ⇒ **每个包的目标位置都必须不卡方块**。
     *
     * <p>而 `fallDistance` 只能由「上升 → 下降」累积：
     * <pre>
     * 1545: flag = (packet.y - lastGoodY) > 0;  if (flag) resetFallDistance();
     * 1689: doCheckFallDamage(实际位移, onGround);   // 下降量 → fallDistance
     * </pre>
     * ⇒ 想伪造 {@code reach} 格，就**必须真的抬到 reach 高**（服务端不接受「凭空累积」）。
     *
     * <h3>关键洞察</h3>
     * 服务端只检查【目标位置】是否卡方块，**不检查路径**（`hasNewCollision(oldBox, newBox)`）。
     * ⇒ **「抬升的柱子」和「落下的柱子」可以不是同一根！**
     *
     * <pre>
     *   现状（同柱）：升 = start + reach  ← 两格高空间里升不上去 ⇒ CLIPPED ⇒ 回弹
     *   本方案（两柱）：升 = riseCol + reach   （riseCol 头顶够高 ⇒ 目标位置合法 ✓）
     *                   降 = landPos           （landPos 已验证站得住 ⇒ 合法 ✓）
     * </pre>
     * ⇒ 两个包都被接受 ⇒ 不回弹；下降量 ≈ reach ⇒ 满伤害。
     *
     * @param around  搜索中心（一般传玩家当前位置，或落点）
     * @param radius  水平搜索半径（格）
     * @param needY   需要的头顶空间（= 想伪造的高度 + 一点余量）
     * @return 找到的柱子底部坐标（y 与 {@code around.y} 取整对齐）；找不到返回 {@code null}
     */
    public static Vec3d findColumnWithHeadroom(Vec3d around, int radius, double needY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || around == null) {
            return null;
        }

        double baseY = around.y;
        int cx = (int) Math.floor(around.x);
        int cz = (int) Math.floor(around.z);

        // ★ 按「水平距离由近到远」扫描：抬升柱越近，位移越小、越容易被服务端接受。
        //   所以用环形（按平方距离排序）而不是简单的三层循环。
        List<int[]> candidates = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                candidates.add(new int[]{dx, dz, dx * dx + dz * dz});
            }
        }
        candidates.sort(Comparator.comparingInt(c -> c[2]));

        double need = Math.max(2.0, needY);
        for (int[] c : candidates) {
            double x = cx + c[0] + 0.5;
            double z = cz + c[1] + 0.5;

            // ① 柱底必须站得住（玩家 1.8 碰撞箱不卡方块）
            Vec3d foot = new Vec3d(x, baseY, z);
            if (!GcoreBlockUtil.checkNoPosCollie(foot)) {
                continue;
            }

            // ② ★ 拱顶位置（= 伪造高度的目标）也必须不卡方块。
            //   服务端 :1610 `hasNewCollision(oldBox, newBox)` 只检查【目标位置】，
            //   所以这里直接对目标位置做同源碰撞检测 —— 比依赖搜索语义更可靠。
            Vec3d top = new Vec3d(x, baseY + need, z);
            if (!GcoreBlockUtil.checkNoPosCollie(top)) {
                continue;
            }

            // ③ 中间也抽查几个点，避免「上下都空、中间有薄天花板」的漏判。
            //   （服务端不查路径，但我们的抬升包若落在中间高度会撞 —— 抽查能降低风险）
            boolean pathOk = true;
            for (double dy = 1.0; dy < need; dy += 1.0) {
                if (!GcoreBlockUtil.checkNoPosCollie(new Vec3d(x, baseY + dy, z))) {
                    pathOk = false;
                    break;
                }
            }
            if (pathOk) {
                return foot;
            }
        }
        return null;
    }
}
