package com.codigohasta.addon.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Method;

/**
 * Reflective access to Litematica's schematic world.
 *
 * {@code WorldSchematic} extends {@code Level}, but the Litematica jar in
 * {@code libs/} was built for 1.21.11 and still carries intermediary names, so
 * javac cannot load that superclass and every inherited member is unreachable at
 * compile time -- javac cannot even read WorldSchematic.class, because its type
 * annotations reference the intermediary Entity. Nothing here names a Litematica
 * type, so the whole lookup is deferred to whichever build is installed at
 * runtime.
 */
public final class SchematicBridge {
    private SchematicBridge() {}

    private static Method handlerGetter;
    private static Method getBlockState;
    private static Method getBlockEntity;

    /** Litematica's currently loaded schematic world, or null if there is none. */
    public static Object getSchematicWorld() {
        try {
            if (handlerGetter == null) {
                handlerGetter = Class.forName("fi.dy.masa.litematica.world.SchematicWorldHandler")
                    .getMethod("getSchematicWorld");
            }
            return handlerGetter.invoke(null);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    public static BlockState getBlockState(Object schematicWorld, BlockPos pos) {
        try {
            if (getBlockState == null) {
                getBlockState = schematicWorld.getClass().getMethod("getBlockState", BlockPos.class);
            }
            return (BlockState) getBlockState.invoke(schematicWorld, pos);
        } catch (ReflectiveOperationException | LinkageError e) {
            return Blocks.AIR.defaultBlockState();
        }
    }

    public static BlockEntity getBlockEntity(Object schematicWorld, BlockPos pos) {
        try {
            if (getBlockEntity == null) {
                getBlockEntity = schematicWorld.getClass().getMethod("getBlockEntity", BlockPos.class);
            }
            return (BlockEntity) getBlockEntity.invoke(schematicWorld, pos);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }
}
