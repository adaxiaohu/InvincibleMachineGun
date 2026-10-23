package com.codigohasta.addon.utils.gcore;

import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class GcoreSimpleRaycastContext extends RaycastContext {

    public GcoreSimpleRaycastContext(ShapeType shapeType, FluidHandling fluidHandling,
                                     ShapeContext shapeContext) {
        super(Vec3d.ZERO, Vec3d.ZERO, shapeType, fluidHandling, shapeContext);
    }

    public GcoreSimpleRaycastContext(ShapeType shapeType, FluidHandling fluidHandling,
                                     Entity entity) {
        this(shapeType, fluidHandling, ShapeContext.of(entity));
    }
}
