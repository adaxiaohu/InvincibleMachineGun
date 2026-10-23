package com.codigohasta.addon.utils.gcore;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

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
