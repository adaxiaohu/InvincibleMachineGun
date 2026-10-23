package com.codigohasta.addon.utils.gcore;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * 放置数据：放哪个方块、朝哪个面、目标是哪一格。
 *
 * <p>移植自 {@code dev.godden.core.Utils.ClassUtil.PlaceData}（GCore）。
 *
 * <p>字段名与源一一对应：
 * <ul>
 *     <li>{@code placePos} —— 被点击的（承载面）方块坐标</li>
 *     <li>{@code placeFacing} —— 点击的面朝向</li>
 *     <li>{@code targetPos} —— 方块实际落在哪一格</li>
 * </ul>
 */
public final class GcorePlaceData {

    private final BlockPos placePos;
    private final Direction placeFacing;
    private final BlockPos targetPos;

    public GcorePlaceData(BlockPos placePos, Direction placeFacing, BlockPos targetPos) {
        this.placePos = placePos;
        this.placeFacing = placeFacing;
        this.targetPos = targetPos;
    }

    public BlockPos getPlacePos() {
        return this.placePos;
    }

    public Direction getPlaceFacing() {
        return this.placeFacing;
    }

    public BlockPos getTargetPos() {
        return this.targetPos;
    }

    @Override
    public String toString() {
        return "PlaceData(" + this.placePos + ", " + this.placeFacing + ", " + this.targetPos + ")";
    }
}
