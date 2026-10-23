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

        posDataList.sort(Comparator.comparingDouble(
            (GcoreAnchorPlaceData d) -> -d.getAnchorData().getDamage()));
        return posDataList;
    }

    // ═══════════════════ 第 2 层：找 TP 点 ═══════════════════

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

        findDataList.sort(Comparator.comparingDouble(GcoreTpAnchorPlaceData::selfDamage));
        return findDataList.isEmpty() ? null : findDataList.get(0);
    }

    // ═══════════════════ Light 版：第 2 层 ═══════════════════

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
