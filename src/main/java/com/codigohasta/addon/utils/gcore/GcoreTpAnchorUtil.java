package com.codigohasta.addon.utils.gcore;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * TP 锚的核心搜索算法。
 *
 * <p>移植自 {@code dev.godden.core.Utils.TpAnchorUtil}（GCore，859 行）。
 * 源是 Kotlin `object`，Java 里改为静态方法。
 *
 * <p><b>四层搜索</b>：
 * <ol>
 *     <li>{@link #getAnchorPosList} —— 在目标周围 13³ 网格找放锚点，算对目标的伤害，
 *         <b>按伤害降序</b></li>
 *     <li>{@link #getTpAnchorData} —— 找「我传送到哪」，算自伤，<b>按自伤升序</b>；
 *         核心判据 {@code damageToSelf < anchorData.getDamage()}（换血划算）</li>
 *     <li>{@link #touchCheck} —— 眼到方块中心 ≤ 6（{@code 36.0} 硬编码）</li>
 *     <li>{@link #getTpAnchorProtectPos} —— 只扫玩家周围 10 个格子，找放保护方块</li>
 * </ol>
 *
 * <p><b>★ 两套算法（务必分清）</b>：
 * <pre>
 * getTpAnchorDataFinal      → getTpAnchorData       （完整版，ModuleGTpAnchorAura 用）
 * getLightTpAnchorDataFinal → getLightTpAnchorData  （Light 版，ModuleGLightTpAnchorAura 用）
 * </pre>
 * 两者**共用** {@link #getAnchorPosList}（找锚位）与
 * {@link #getTpAnchorProtectPos}（找保护方块），但**第 2 层「找 TP 点」完全不同**：
 *
 * <table border="1">
 *   <caption>字节码特征（javap 逐方法统计）</caption>
 *   <tr><th></th><th>HashMap</th><th>checkCanMove</th><th>能否穿墙</th></tr>
 *   <tr><td>{@code getTpAnchorData}</td><td>0</td><td>0</td><td>✅（模块用 doTpMove）</td></tr>
 *   <tr><td>{@code getLightTpAnchorData}</td><td>8</td><td>6</td><td>❌（要求有通路）</td></tr>
 * </table>
 *
 * <p><b>★ 关于 {@code colliedBlockMap}</b>：**只有 Light 版有**这个局部
 * {@code HashMap}，用于「放锚/放保护方块后，检查玩家还能不能走到 TP 点」。
 * 完整版没有可达性检查 —— 因为它的立意就是「瞬移过去」，要求「能走过去」自相矛盾。
 * 每次检查前 put、检查后 clear（源就是这么写的），保持原样。
 */
public final class GcoreTpAnchorUtil {

    /** 眼高常量（源硬编码）。 */
    private static final double EYE_Y = 1.62;

    /** 交互距离上限的平方：6.0² （源硬编码）。 */
    private static final double MAX_TOUCH_SQ = 36.0;

    private GcoreTpAnchorUtil() {
    }

    // ═══════════════════ 第 0 层：入口 ═══════════════════

    /**
     * 主入口：先找锚位置，再为第一个（伤害最高的）锚找 TP 点。
     *
     * <p>对应源 {@code getTpAnchorDataFinal}。
     */
    public static GcoreTpAnchorPlaceData getTpAnchorDataFinal(
            GcoreEnumType.BlockPlaceVersion version, Entity target, Vec3d targetVec,
            Entity placer, int scanBorder, double clickRange,
            float minDamageToTarget, float maxDamageToSelf,
            boolean allowPlaceAnchor, boolean allowClickAfterPlace,
            boolean allowPlaceProtect, boolean onlyPlaceProtect) {

        ArrayList<GcoreAnchorPlaceData> anchorPosList =
            getAnchorPosList(target, targetVec, scanBorder, minDamageToTarget, allowPlaceAnchor);
        if (anchorPosList.isEmpty()) {
            return null;
        }

        // ★ 只取第一个 —— 列表已按伤害降序，所以就是「伤害最高」的那个锚位
        GcoreAnchorPlaceData anchorPlaceData = anchorPosList.get(0);

        return getTpAnchorData(anchorPlaceData, version, target, targetVec, placer,
            scanBorder, clickRange, maxDamageToSelf,
            allowClickAfterPlace, allowPlaceProtect, onlyPlaceProtect);
    }

    /**
     * Light 版主入口（{@code ModuleGLightTpAnchorAura} 用）。
     *
     * <p>对应源 {@code getLightTpAnchorDataFinal}。
     *
     * <p><b>与 {@link #getTpAnchorDataFinal} 的唯一区别</b>：多一个
     * {@code placerVec} 参数，并且委托给
     * {@link #getLightTpAnchorData}（而不是 {@link #getTpAnchorData}）。
     *
     * <p><b>★ 为什么要显式传 {@code placerVec}</b>：Light 版要用它做
     * {@code checkCanMove(placerVec, tpVec)} 双向可达性检查 ——
     * 而那个检查用的是**上一 tick 的位置**（模块传的是
     * {@code player.getPos()}，不是 {@code getPrevPos()}）。
     * 所以它必须是参数，不能用 {@code placer.getEntityPos()} 代替。
     */
    public static GcoreTpAnchorPlaceData getLightTpAnchorDataFinal(
            GcoreEnumType.BlockPlaceVersion version, Entity target, Vec3d targetVec,
            Entity placer, Vec3d placerVec, int scanBorder, double clickRange,
            float minDamageToTarget, float maxDamageToSelf,
            boolean allowPlaceAnchor, boolean allowClickAfterPlace,
            boolean allowPlaceProtect, boolean onlyPlaceProtect) {

        ArrayList<GcoreAnchorPlaceData> anchorPosList =
            getAnchorPosList(target, targetVec, scanBorder, minDamageToTarget, allowPlaceAnchor);
        if (anchorPosList.isEmpty()) {
            return null;
        }

        GcoreAnchorPlaceData anchorPlaceData = anchorPosList.get(0);

        return getLightTpAnchorData(anchorPlaceData, version, target, targetVec, placer, placerVec,
            scanBorder, clickRange, maxDamageToSelf,
            allowClickAfterPlace, allowPlaceProtect, onlyPlaceProtect);
    }

    // ═══════════════════ 第 1 层：找锚位置 ═══════════════════

    /**
     * 在目标周围 {@code ±scanBorder} 网格里找所有可放锚（或已有点火）的位置。
     *
     * <p>逐行对应源 {@code getAnchorPosList}：
     * <pre>
     * entities = TickUpdate.getWorldEntitiesList(); entities.add(target)
     * 遍历 ±scanBorder：
     *   anchorPos = (x, y, z); expVec = (x+0.5, y+0.5, z+0.5)
     *   情况 A: allowPlaceAnchor &amp;&amp; canPlaceAtPos(anchorPos, entities)
     *           dmg = getExpDamage(expVec, target, targetVec, null, null, AnchorUtils.getExplosion())
     *           if (dmg >= minDamageToTarget) 加 Type.Place
     *           continue
     *   情况 B: 该格已是重生锚
     *           dmg = getExpDamage(expVec, target, targetVec, provider, null, AnchorUtils.getExplosion())
     *           provider = pos -&gt; pos == anchorPos ? AIR : world.getBlockState(pos)
     *           if (dmg >= minDamageToTarget) 加 Type.Ignite
     * 按 damage 降序排序
     * </pre>
     *
     * <p><b>排序方向来自 jar 字节码</b>（{@code getAnchorPosList$2}）：
     * {@code compareValues(-a.damage, -b.damage)} —— 取负即降序。
     */
    public static ArrayList<GcoreAnchorPlaceData> getAnchorPosList(
            Entity target, Vec3d targetVec, int scanBorder,
            float minDamageToTarget, boolean allowPlaceAnchor) {

        int centerX = (int) Math.floor(targetVec.x);
        int centerY = (int) Math.floor(targetVec.y);
        int centerZ = (int) Math.floor(targetVec.z);

        ArrayList<Entity> entities = GcoreTickUpdate.getWorldEntitiesList();
        entities.add(target);

        ArrayList<GcoreAnchorPlaceData> posDataList = new ArrayList<>();
        var world = MinecraftClient.getInstance().world;

        for (int x = centerX - scanBorder; x <= centerX + scanBorder; x++) {
            for (int y = centerY - scanBorder; y <= centerY + scanBorder; y++) {
                for (int z = centerZ - scanBorder; z <= centerZ + scanBorder; z++) {

                    BlockPos anchorPos = new BlockPos(x, y, z);
                    Vec3d expVec = new Vec3d(x + 0.5, y + 0.5, z + 0.5);

                    // ── 情况 A：空地放新锚 ──
                    if (allowPlaceAnchor && GcoreBlockUtil.canPlaceAtPos(anchorPos, entities)) {
                        float damageToTarget = GcoreExplosionUtils.getExpDamage(
                            expVec, target, targetVec, null, GcoreAnchorUtils.getExplosion());
                        if (damageToTarget < minDamageToTarget) {
                            continue;
                        }
                        posDataList.add(new GcoreAnchorPlaceData(
                            GcoreAnchorPlaceData.Type.Place,
                            new GcoreAnchorData(anchorPos, expVec, damageToTarget)));
                        continue;
                    }

                    // ── 情况 B：已有锚 → 点火 ──
                    if (world.getBlockState(anchorPos).getBlock() != Blocks.RESPAWN_ANCHOR) {
                        continue;
                    }

                    Function<BlockPos, BlockState> provider = pos ->
                        pos.equals(anchorPos) ? Blocks.AIR.getDefaultState() : world.getBlockState(pos);

                    float damageToTarget = GcoreExplosionUtils.getExpDamage(
                        expVec, target, targetVec, provider, GcoreAnchorUtils.getExplosion());
                    if (damageToTarget < minDamageToTarget) {
                        continue;
                    }
                    posDataList.add(new GcoreAnchorPlaceData(
                        GcoreAnchorPlaceData.Type.Ignite,
                        new GcoreAnchorData(anchorPos, expVec, damageToTarget)));
                }
            }
        }

        // ★ 伤害降序（字节码证据：compareValues(-a, -b)）
        posDataList.sort(Comparator.comparingDouble(
            (GcoreAnchorPlaceData d) -> -d.getAnchorData().getDamage()));
        return posDataList;
    }

    // ═══════════════════ 第 2 层：找 TP 点 ═══════════════════

    /**
     * 为给定锚方案找「我传送到哪」。
     *
     * <p>逐行对应源 {@code getTpAnchorData}（309-458 行）。核心判据：
     * <pre>
     * if (damageToSelf &gt;= anchorData.getDamage()) continue;   // 换血划算
     * </pre>
     * 以及两个性能优化：
     * <ul>
     *     <li>{@code damageToSelf == 0} → 立刻返回（不搜完 2197 个点）</li>
     *     <li>{@code damageToSelf <= maxDamageToSelf} 才收集</li>
     * </ul>
     *
     * <p><b>排序方向来自 jar 字节码</b>（{@code getTpAnchorData$2}）：自伤升序。
     */
    public static GcoreTpAnchorPlaceData getTpAnchorData(
            GcoreAnchorPlaceData anchorData, GcoreEnumType.BlockPlaceVersion version,
            Entity target, Vec3d targetVec, Entity placer, int scanBorder,
            double clickRange, float maxDamageToSelf,
            boolean allowClickAfterPlace, boolean allowPlaceProtect, boolean onlyPlaceProtect) {

        int centerX = (int) Math.floor(targetVec.x);
        int centerY = (int) Math.floor(targetVec.y);
        int centerZ = (int) Math.floor(targetVec.z);
        double clickRangeSq = clickRange * clickRange;

        ArrayList<GcoreTpAnchorPlaceData> findDataList = new ArrayList<>();

        BlockPos anchorPos = anchorData.getAnchorData().getAnchorPos();
        Box anchorBox = new Box(
            anchorPos.getX(), anchorPos.getY(), anchorPos.getZ(),
            anchorPos.getX() + 1.0, anchorPos.getY() + 1.0, anchorPos.getZ() + 1.0);

        var world = MinecraftClient.getInstance().world;

        for (int x = centerX - scanBorder; x <= centerX + scanBorder; x++) {
            for (int y = centerY - scanBorder; y <= centerY + scanBorder; y++) {
                for (int z = centerZ - scanBorder; z <= centerZ + scanBorder; z++) {

                    // ★ tpVec 的 y 不加 0.5（脚部坐标）
                    Vec3d tpVec = new Vec3d(x + 0.5, y + 0.0, z + 0.5);

                    // 不能站在锚的方块里
                    if (y <= -64
                        || GcorePlayerUtil.PlayerState.Normal.getBBox(tpVec).intersects(anchorBox)) {
                        continue;
                    }

                    boolean canTouch = touchCheck(tpVec, clickRangeSq, anchorPos);

                    // 若要求「放完能点到」或位置卡方块 → 跳过
                    if (allowClickAfterPlace && !canTouch || !GcoreBlockUtil.checkNoPosCollie(tpVec)) {
                        continue;
                    }

                    // ── 确定放置面 ──
                    GcorePlaceData placeData = null;
                    if (anchorData.getType() == GcoreAnchorPlaceData.Type.Place) {
                        if (version == GcoreEnumType.BlockPlaceVersion.MC1_14 && canTouch) {
                            placeData = new GcorePlaceData(anchorPos, Direction.DOWN, anchorPos);
                        } else {
                            for (Direction dir : Direction.values()) {
                                BlockPos pos = anchorPos.offset(dir);
                                boolean ok = world.getBlockState(pos).isOpaque()
                                    && touchCheck(tpVec, clickRangeSq, pos);
                                if (!ok) {
                                    continue;
                                }
                                placeData = new GcorePlaceData(pos, dir.getOpposite(), anchorPos);
                                break;
                            }
                        }
                    } else if ((anchorData.getType() == GcoreAnchorPlaceData.Type.Ignite
                        || anchorData.getType() == GcoreAnchorPlaceData.Type.Power) && canTouch) {
                        placeData = new GcorePlaceData(anchorPos, Direction.DOWN, anchorPos);
                    }

                    if (placeData == null) {
                        continue;
                    }

                    // ── 算自伤（假装锚已消失）──
                    Function<BlockPos, BlockState> provider = pos ->
                        pos.equals(anchorPos) ? Blocks.AIR.getDefaultState() : world.getBlockState(pos);

                    float damageToSelf = GcoreExplosionUtils.getExpDamage(
                        anchorData.getAnchorData().getExpVec(), placer, tpVec,
                        provider, GcoreAnchorUtils.getExplosion());

                    // ★★ 核心判据：自己吃的伤害必须小于目标吃的伤害
                    if (damageToSelf >= anchorData.getAnchorData().getDamage()) {
                        continue;
                    }

                    if (!onlyPlaceProtect) {
                        if (damageToSelf == 0.0f) {
                            // ★ 零自伤 → 完美，立刻返回
                            GcoreAnchorPlaceData data = new GcoreAnchorPlaceData(
                                anchorData.getType(), anchorData.getAnchorData(), placeData, null, 0.0f);
                            return new GcoreTpAnchorPlaceData(tpVec, data);
                        }
                        if (damageToSelf <= maxDamageToSelf) {
                            GcoreAnchorPlaceData data = new GcoreAnchorPlaceData(
                                anchorData.getType(), anchorData.getAnchorData(), placeData, null, damageToSelf);
                            findDataList.add(new GcoreTpAnchorPlaceData(tpVec, data));
                        }
                    }

                    // ── 可选：再找保护方块 ──
                    if (!onlyPlaceProtect && !allowPlaceProtect) {
                        continue;
                    }
                    GcoreAnchorProtectData protectData = getTpAnchorProtectPos(
                        anchorData.getAnchorData(), version, target, targetVec, placer, tpVec,
                        clickRange, maxDamageToSelf);
                    if (protectData == null) {
                        continue;
                    }

                    GcoreAnchorPlaceData data = new GcoreAnchorPlaceData(
                        anchorData.getType(), anchorData.getAnchorData(), placeData, protectData,
                        protectData.getDamageToSelf());

                    if (protectData.getDamageToSelf() == 0.0f) {
                        return new GcoreTpAnchorPlaceData(tpVec, data);
                    }
                    findDataList.add(new GcoreTpAnchorPlaceData(tpVec, data));
                }
            }
        }

        // ★ 自伤升序（字节码证据：compareValues(a, b) 不取负）
        findDataList.sort(Comparator.comparingDouble(GcoreTpAnchorPlaceData::selfDamage));
        return findDataList.isEmpty() ? null : findDataList.get(0);
    }

    // ═══════════════════ Light 版：第 2 层 ═══════════════════

    /**
     * Light 版「找 TP 点」。
     *
     * <p>移植自源 {@code getLightTpAnchorData}（110-303 行）。
     *
     * <p><b>★ 与 {@link #getTpAnchorData} 的本质区别（字节码证据）</b>：
     * 用 javap 逐方法统计特征得：
     * <pre>
     * getLightTpAnchorData  HashMap=8  checkCanMove=6   ← 有可达性检查
     * getTpAnchorData       HashMap=0  checkCanMove=0   ← 没有
     * </pre>
     * 也就是说：
     * <ul>
     *     <li>Light 版**多了**「放锚后还能不能双向走到 TP 点」的检查
     *         （{@code checkCanMove(placerVec, tpVec)} 与反向）——
     *         用 {@code colliedBlockMap} 模拟「锚/保护方块已放上去」</li>
     *     <li>因为要检查，它必须**显式接收 {@code placerVec}** ——
     *         源 {@code getTpAnchorData} 的签名里根本没有这个参数，
     *         物理上不可能调用 {@code checkCanMove}</li>
     *     <li>代价：要求两点间有**可行走通路** → 不能穿墙，
     *         所以 Light 版只能用于「能走过去」的场景</li>
     * </ul>
     *
     * <p>三处检查的位置与源逐一对应：
     * <ol>
     *     <li>{@code damageToSelf == 0} 分支 → 检查通过才 return</li>
     *     <li>{@code damageToSelf <= maxDamageToSelf} 分支 → 检查通过才收集</li>
     *     <li>带保护方块的分支 → 检查通过才收集</li>
     * </ol>
     * 每次检查后 {@code clear()} —— 源就是这样（先 put 后清）。
     */
    public static GcoreTpAnchorPlaceData getLightTpAnchorData(
            GcoreAnchorPlaceData anchorData, GcoreEnumType.BlockPlaceVersion version,
            Entity target, Vec3d targetVec, Entity placer, Vec3d placerVec,
            int scanBorder, double clickRange, float maxDamageToSelf,
            boolean allowClickAfterPlace, boolean allowPlaceProtect, boolean onlyPlaceProtect) {

        int centerX = (int) Math.floor(targetVec.x);
        int centerY = (int) Math.floor(targetVec.y);
        int centerZ = (int) Math.floor(targetVec.z);
        double clickRangeSq = clickRange * clickRange;

        ArrayList<GcoreTpAnchorPlaceData> findDataList = new ArrayList<>();

        BlockPos anchorPos = anchorData.getAnchorData().getAnchorPos();
        Box anchorBox = new Box(
            anchorPos.getX(), anchorPos.getY(), anchorPos.getZ(),
            anchorPos.getX() + 1.0, anchorPos.getY() + 1.0, anchorPos.getZ() + 1.0);

        // ★ Light 版特有的虚拟方块图（模拟「锚/保护方块已放上」）
        HashMap<BlockPos, BlockState> colliedBlockMap = new HashMap<>();

        var world = MinecraftClient.getInstance().world;

        for (int x = centerX - scanBorder; x <= centerX + scanBorder; x++) {
            for (int y = centerY - scanBorder; y <= centerY + scanBorder; y++) {
                for (int z = centerZ - scanBorder; z <= centerZ + scanBorder; z++) {

                    Vec3d tpVec = new Vec3d(x + 0.5, y + 0.0, z + 0.5);

                    if (y <= -64
                        || GcorePlayerUtil.PlayerState.Normal.getBBox(tpVec).intersects(anchorBox)) {
                        continue;
                    }

                    boolean canTouch = touchCheck(tpVec, clickRangeSq, anchorPos);

                    if (allowClickAfterPlace && !canTouch || !GcoreBlockUtil.checkNoPosCollie(tpVec)) {
                        continue;
                    }

                    // ── 确定放置面 ──
                    GcorePlaceData placeData = null;
                    if (anchorData.getType() == GcoreAnchorPlaceData.Type.Place) {
                        if (version == GcoreEnumType.BlockPlaceVersion.MC1_14 && canTouch) {
                            placeData = new GcorePlaceData(anchorPos, Direction.DOWN, anchorPos);
                        } else {
                            for (Direction dir : Direction.values()) {
                                BlockPos pos = anchorPos.offset(dir);
                                boolean ok = world.getBlockState(pos).isOpaque()
                                    && touchCheck(tpVec, clickRangeSq, pos);
                                if (!ok) {
                                    continue;
                                }
                                placeData = new GcorePlaceData(pos, dir.getOpposite(), anchorPos);
                                break;
                            }
                        }
                    } else if ((anchorData.getType() == GcoreAnchorPlaceData.Type.Ignite
                        || anchorData.getType() == GcoreAnchorPlaceData.Type.Power) && canTouch) {
                        placeData = new GcorePlaceData(anchorPos, Direction.DOWN, anchorPos);
                    }

                    if (placeData == null) {
                        continue;
                    }

                    Function<BlockPos, BlockState> provider = pos ->
                        pos.equals(anchorPos) ? Blocks.AIR.getDefaultState() : world.getBlockState(pos);

                    float damageToSelf = GcoreExplosionUtils.getExpDamage(
                        anchorData.getAnchorData().getExpVec(), placer, tpVec,
                        provider, GcoreAnchorUtils.getExplosion());

                    if (damageToSelf >= anchorData.getAnchorData().getDamage()) {
                        continue;
                    }

                    if (!onlyPlaceProtect) {
                        if (damageToSelf == 0.0f) {
                            GcoreAnchorPlaceData data = new GcoreAnchorPlaceData(
                                anchorData.getType(), anchorData.getAnchorData(), placeData, null, 0.0f);
                            // ★ Light 特有：检查通过才返回
                            if (canReachBothWays(placerVec, tpVec, anchorPos,
                                allowClickAfterPlace, colliedBlockMap)) {
                                return new GcoreTpAnchorPlaceData(tpVec, data);
                            }
                            continue;
                        }
                        if (damageToSelf <= maxDamageToSelf) {
                            GcoreAnchorPlaceData data = new GcoreAnchorPlaceData(
                                anchorData.getType(), anchorData.getAnchorData(), placeData, null, damageToSelf);
                            if (canReachBothWays(placerVec, tpVec, anchorPos,
                                allowClickAfterPlace, colliedBlockMap)) {
                                findDataList.add(new GcoreTpAnchorPlaceData(tpVec, data));
                            }
                        }
                    }

                    if (!onlyPlaceProtect && !allowPlaceProtect) {
                        continue;
                    }
                    GcoreAnchorProtectData protectData = getTpAnchorProtectPos(
                        anchorData.getAnchorData(), version, target, targetVec, placer, tpVec,
                        clickRange, maxDamageToSelf);
                    if (protectData == null) {
                        continue;
                    }

                    GcoreAnchorPlaceData data = new GcoreAnchorPlaceData(
                        anchorData.getType(), anchorData.getAnchorData(), placeData, protectData,
                        protectData.getDamageToSelf());

                    colliedBlockMap.put(protectData.getProtectPos(), Blocks.OBSIDIAN.getDefaultState());
                    if (!allowClickAfterPlace) {
                        colliedBlockMap.put(anchorPos, Blocks.RESPAWN_ANCHOR.getDefaultState());
                    }
                    boolean reachable = GcoreBlockUtil.checkCanMove(placerVec, tpVec, colliedBlockMap,
                        GcorePlayerUtil.PlayerState.Normal)
                        && GcoreBlockUtil.checkCanMove(tpVec, placerVec, colliedBlockMap,
                        GcorePlayerUtil.PlayerState.Normal);
                    colliedBlockMap.clear();
                    if (!reachable) {
                        continue;
                    }

                    if (protectData.getDamageToSelf() == 0.0f) {
                        return new GcoreTpAnchorPlaceData(tpVec, data);
                    }
                    findDataList.add(new GcoreTpAnchorPlaceData(tpVec, data));
                }
            }
        }

        findDataList.sort(Comparator.comparingDouble(GcoreTpAnchorPlaceData::selfDamage));
        return findDataList.isEmpty() ? null : findDataList.get(0);
    }

    /**
     * 「放锚后还能不能双向走到 TP 点」检查（**Light 版专用**）。
     *
     * <p>源在 {@code getLightTpAnchorData} 的 {@code damageToSelf == 0} 与
     * {@code <= maxDamageToSelf} 两个分支里都做了同样的检查：
     * <pre>
     * if (!allowClickAfterPlace) colliedBlockMap.put(anchorPos, RESPAWN_ANCHOR)
     * if (!checkCanMove(placerVec, tpVec, map) || !checkCanMove(tpVec, placerVec, map)) { map.clear(); continue; }
     * map.clear();
     * </pre>
     * 这里抽成方法以避免重复（行为完全一致）。
     *
     * <p><b>参数是 {@code placerVec}（Vec3d）而不是 {@code placer}（Entity）</b> ——
     * 因为源就是这么传的：它用的是模块显式传入的
     * {@code player.getPos()}，而不是 {@code placer.getEntityPos()}。
     */
    private static boolean canReachBothWays(Vec3d placerVec, Vec3d tpVec, BlockPos anchorPos,
                                            boolean allowClickAfterPlace,
                                            Map<BlockPos, BlockState> colliedBlockMap) {
        if (!allowClickAfterPlace) {
            colliedBlockMap.put(anchorPos, Blocks.RESPAWN_ANCHOR.getDefaultState());
        }
        boolean reachable = GcoreBlockUtil.checkCanMove(placerVec, tpVec, colliedBlockMap,
            GcorePlayerUtil.PlayerState.Normal)
            && GcoreBlockUtil.checkCanMove(tpVec, placerVec, colliedBlockMap,
            GcorePlayerUtil.PlayerState.Normal);
        colliedBlockMap.clear();
        return reachable;
    }

    /**
     * 能否从 {@code tpVec} 打到 {@code pos}。
     *
     * <p>源 {@code getTpAnchorData$lambda$3$touchCheck$2} 与
     * {@code getLightTpAnchorData$lambda$0$touchCheck} 实现完全相同：
     * <pre>
     * disToPosCenter = tpVec.squaredDistanceTo(pos + (0.5, 0.5, 0.5))
     * return Vec3d.ofCenter(pos).squaredDistanceTo(tpVec + (0, 1.62, 0)) &lt;= 36.0
     *     &amp;&amp; disToPosCenter &lt; clickRangeSq
     * </pre>
     * {@code 36.0} 是 6 格上限的平方，{@code 1.62} 是眼高。
     */
    public static boolean touchCheck(Vec3d tpVec, double clickRangeSq, BlockPos pos) {
        double disToPosCenter = tpVec.squaredDistanceTo(
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        return Vec3d.ofCenter(new Vec3i(pos.getX(), pos.getY(), pos.getZ()))
            .squaredDistanceTo(tpVec.add(0.0, EYE_Y, 0.0)) <= MAX_TOUCH_SQ
            && disToPosCenter < clickRangeSq;
    }

    // ═══════════════════ 第 4 层：保护方块 ═══════════════════

    /**
     * 找一个放保护方块（黑曜石）的位置。
     *
     * <p>逐行对应源 {@code getTpAnchorProtectPos}（562-722 行）。
     *
     * <p><b>候选只有 10 个格子</b>（玩家周围，不是 13³）：
     * <pre>
     * (x,y-1,z) (x,y+2,z) (x+1,y,z) (x-1,y,z) (x+1,y+1,z)
     * (x-1,y+1,z) (x,y,z+1) (x,y,z-1) (x,y+1,z+1) (x,y+1,z-1)
     * </pre>
     * 其中 {@code x = floor(clickerVec.x)} 等。
     *
     * <p>对每个候选：跳过与锚重合的 → {@code touchCheck} 通过 →
     * {@code canPlaceAtPos} 通过 → 找放置面 →
     * 检查「放了保护方块后目标还吃多少伤害」（要 ≥ 原伤害，即保护方块没把伤害也挡掉）→
     * 再检查自己吃多少伤害。
     *
     * <p><b>排序</b>：源用 {@code minWithOrNull} + 自伤升序比较器 ——
     * 即取自伤最小的（{@code dataList.sort(...); get(0)} 等价）。
     */
    public static GcoreAnchorProtectData getTpAnchorProtectPos(
            GcoreAnchorData anchorData, GcoreEnumType.BlockPlaceVersion version,
            Entity target, Vec3d targetVec, Entity clicker, Vec3d clickerVec,
            double clickRange, float maxDamageToSelf) {

        int x = (int) Math.floor(clickerVec.x);
        int y = (int) Math.floor(clickerVec.y);
        int z = (int) Math.floor(clickerVec.z);

        List<BlockPos> posList = List.of(
            new BlockPos(x, y - 1, z), new BlockPos(x, y + 2, z),
            new BlockPos(x + 1, y, z), new BlockPos(x - 1, y, z),
            new BlockPos(x + 1, y + 1, z), new BlockPos(x - 1, y + 1, z),
            new BlockPos(x, y, z + 1), new BlockPos(x, y, z - 1),
            new BlockPos(x, y + 1, z + 1), new BlockPos(x, y + 1, z - 1));

        double clickRangeSq = clickRange * clickRange;
        Vec3d clickerEyeVec = clickerVec.add(0.0, EYE_Y, 0.0);

        // 源里这个 touchCheck 用 clickerVec（不是 tpVec）
        java.util.function.Predicate<BlockPos> touchCheck = pos -> {
            double disToPosCenter = clickerVec.squaredDistanceTo(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            return Vec3d.ofCenter(new Vec3i(pos.getX(), pos.getY(), pos.getZ()))
                .squaredDistanceTo(clickerEyeVec) <= MAX_TOUCH_SQ
                && disToPosCenter < clickRangeSq;
        };

        ArrayList<GcoreAnchorProtectData> dataList = new ArrayList<>();
        ArrayList<Entity> entities = GcoreTickUpdate.getWorldEntitiesList();
        var world = MinecraftClient.getInstance().world;

        for (BlockPos protectPos : posList) {
            if (protectPos.equals(anchorData.getAnchorPos())) {
                continue;
            }
            if (!GcoreBlockUtil.canPlaceAtPos(protectPos, entities)) {
                continue;
            }

            boolean canTouch = touchCheck.test(protectPos);

            // ── 找放置面 ──
            GcorePlaceData placeData;
            if (version == GcoreEnumType.BlockPlaceVersion.MC1_14 && canTouch) {
                placeData = new GcorePlaceData(protectPos, Direction.DOWN, protectPos);
            } else {
                placeData = null;
                for (Direction dir : Direction.values()) {
                    BlockPos pos = protectPos.offset(dir);
                    boolean ok = world.getBlockState(pos).isOpaque() && touchCheck.test(pos);
                    if (!ok) {
                        continue;
                    }
                    placeData = new GcorePlaceData(pos, dir.getOpposite(), protectPos);
                    break;
                }
            }
            if (placeData == null) {
                continue;
            }

            // ── 目标还吃多少伤害（假装锚已消失 + 保护方块已放）──
            Function<BlockPos, BlockState> targetProvider = pos ->
                pos.equals(anchorData.getAnchorPos()) ? Blocks.AIR.getDefaultState()
                    : pos.equals(protectPos) ? Blocks.OBSIDIAN.getDefaultState()
                    : world.getBlockState(pos);

            float damageToTarget = GcoreExplosionUtils.getExpDamage(
                anchorData.getExpVec(), target, targetVec, targetProvider,
                GcoreAnchorUtils.getExplosion());
            if (damageToTarget < anchorData.getDamage()) {
                continue;
            }

            // ── 自己吃多少伤害 ──
            float damageToSelf = GcoreExplosionUtils.getExpDamage(
                anchorData.getExpVec(), clicker, clickerVec, targetProvider,
                GcoreAnchorUtils.getExplosion());

            if (damageToSelf == 0.0f) {
                return new GcoreAnchorProtectData(protectPos, placeData, damageToSelf);
            }
            if (damageToSelf > maxDamageToSelf) {
                continue;
            }
            dataList.add(new GcoreAnchorProtectData(protectPos, placeData, damageToSelf));
        }

        dataList.sort(Comparator.comparingDouble(GcoreAnchorProtectData::getDamageToSelf));
        return dataList.isEmpty() ? null : dataList.get(0);
    }

    // ═══════════════════ 点火安全点（供将来扩展）═══════════════════

    /**
     * 找一个「安全点燃锚」的位置。
     *
     * <p>移植自源 {@code getSafePosToIgniteAnchor}（728-808 行）。
     * TP 锚主路径**不调用它**（那是 {@code ModuleGLightTpAnchorAura} 用的），
     * 但为保持算法完整一并移植。
     */
    public static GcoreAnchorData getSafePosToIgniteAnchor(BlockPos anchorPos, Entity target,
                                                           int scanBorder, double clickRange) {
        double clickRangeSq = clickRange * clickRange;
        Vec3d expVec = anchorPos.toCenterPos();
        ArrayList<GcoreAnchorData> vecList = new ArrayList<>();

        var world = MinecraftClient.getInstance().world;

        for (int x = anchorPos.getX() - scanBorder; x <= anchorPos.getX() + scanBorder; x++) {
            for (int y = anchorPos.getY() - scanBorder; y <= anchorPos.getY() + scanBorder; y++) {
                for (int z = anchorPos.getZ() - scanBorder; z <= anchorPos.getZ() + scanBorder; z++) {
                    Vec3d tpVec = new Vec3d(x + 0.5, y + 0.0, z + 0.5);

                    if (y <= -64 || !touchCheck(tpVec, clickRangeSq, anchorPos)
                        || !GcoreBlockUtil.checkNoPosCollie(tpVec)) {
                        continue;
                    }

                    Function<BlockPos, BlockState> provider = pos ->
                        pos.equals(anchorPos) ? Blocks.AIR.getDefaultState() : world.getBlockState(pos);

                    // ★ 注意 explosion 用的是 **水晶**（威力 6.0），不是锚（5.0）！
                    //   源此处调用是 `getExpDamage$default(..., null, null, 48, null)`，
                    //   mask=48（=16|32）表示 statePredicate 与 explosion **都用默认值**，
                    //   而 explosion 的默认是 `CrystalUtils.getExplosion()`（威力 6.0）。
                    //   （其余 6 处调用都显式传了 AnchorUtils.getExplosion()，威力 5.0。）
                    float damageToSelf = GcoreExplosionUtils.getExpDamage(
                        expVec, target, tpVec, provider, GcoreCrystalUtils.getExplosion());

                    if (damageToSelf == 0.0f) {
                        return new GcoreAnchorData(anchorPos, tpVec, damageToSelf);
                    }
                    vecList.add(new GcoreAnchorData(anchorPos, tpVec, damageToSelf));
                }
            }
        }

        vecList.sort(Comparator.comparingDouble(GcoreAnchorData::getDamage));
        return vecList.isEmpty() ? null : vecList.get(0);
    }
}
