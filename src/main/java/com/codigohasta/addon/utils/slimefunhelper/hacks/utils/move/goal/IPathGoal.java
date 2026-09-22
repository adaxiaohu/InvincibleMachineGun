package com.codigohasta.addon.utils.slimefunhelper.hacks.utils.move.goal;

import net.minecraft.util.math.Vec3d;

public sealed interface IPathGoal
        permits GoalNearBlockPos {
    public Vec3d sample();

    public boolean isInGoal(Vec3d playerPos);
}
