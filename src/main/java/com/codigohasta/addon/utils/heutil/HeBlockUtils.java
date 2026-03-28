package com.codigohasta.addon.utils.heutil;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

public class HeBlockUtils {
   public static final MinecraftClient mc = MinecraftClient.getInstance();

   public static void open(BlockPos pos) {
      Direction clickSide = BlockUtils.getDirection(pos);
      open(pos, clickSide);
   }

   public static void open(BlockPos pos, Direction side) {
      Vec3i vector = side.getVector();
      double offset = 0.45;
      Vec3d directionVec = new Vec3d(
         pos.getX() + 0.5 + vector.getX() * offset,
         pos.getY() + 0.5 + vector.getY() * offset,
         pos.getZ() + 0.5 + vector.getZ() * offset
      );
      HeRotationUtils.rotate(directionVec);
      BlockHitResult result = new BlockHitResult(directionVec, side, pos, false);
      mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, result);
   }

   public static boolean place(BlockPos blockPos, int slot, boolean checkEntities, Direction side, Vec3d hitPos) {
      if (slot >= 0 && slot <= 8) {
         Block toPlace = Blocks.OBSIDIAN;
         ItemStack i = mc.player.getInventory().getStack(slot);
         if (i.getItem() instanceof BlockItem blockItem) {
            toPlace = blockItem.getBlock();
         }

         if (!BlockUtils.canPlaceBlock(blockPos, checkEntities, toPlace)) {
            return false;
         } else {
            BlockPos neighbour = blockPos.offset(side);
            BlockHitResult bhr = new BlockHitResult(hitPos, side.getOpposite(), neighbour, false);
            Rotations.rotate(Rotations.getYaw(hitPos), Rotations.getPitch(hitPos), 0, () -> {
               InvUtils.swap(slot, false);
               BlockUtils.interact(bhr, Hand.MAIN_HAND, true);
            });
            return true;
         }
      } else {
         return false;
      }
   }

   public static List<BlockPos> listPosInSphere(int range, BlockPos pos) {
      Vec3d centerPos = pos.toCenterPos();
      List<BlockPos> list = new ArrayList<>();

      for (int x = pos.getX() - range; x < pos.getX() + range; x++) {
         for (int z = pos.getZ() - range; z < pos.getZ() + range; z++) {
            for (int y = pos.getY() - range; y < pos.getY() + range; y++) {
               BlockPos curPos = new BlockPos(x, y, z);
               if (!(curPos.toCenterPos().distanceTo(centerPos) > range) && !list.contains(curPos)) {
                  list.add(curPos);
               }
            }
         }
      }

      return list;
   }

   public static List<BlockPos> listPosInSphere(int range, int height, BlockPos pos) {
      Vec3d centerPos = pos.toCenterPos();
      List<BlockPos> list = new ArrayList<>();

      for (int x = pos.getX() - range; x < pos.getX() + range; x++) {
         for (int z = pos.getZ() - range; z < pos.getZ() + range; z++) {
            for (int y = pos.getY(); y < pos.getY() + height; y++) {
               BlockPos curPos = new BlockPos(x, y, z);
               if (!(curPos.toCenterPos().distanceTo(centerPos) > range) && !list.contains(curPos)) {
                  list.add(curPos);
               }
            }
         }
      }

      return list;
   }

   public static Direction getBlockFacingDirection(BlockState state) {
      if (state.contains(Properties.FACING)) {
         return (Direction)state.get(Properties.FACING);
      } else if (state.contains(Properties.HORIZONTAL_FACING)) {
         return (Direction)state.get(Properties.HORIZONTAL_FACING);
      } else if (state.contains(Properties.AXIS)) {
         Direction.Axis axis = (Direction.Axis)state.get(Properties.AXIS);
         return Direction.from(axis, Direction.AxisDirection.POSITIVE);
      } else {
         return null;
      }
   }

   public static Vec3d getFaceCenter(BlockPos pos, Direction direction) {
      Box box = mc.world.getBlockState(pos).getOutlineShape(mc.world, pos).getBoundingBox();
      double x = pos.getX() + box.minX + (box.maxX - box.minX) * 0.5;
      double y = pos.getY() + box.minY + (box.maxY - box.minY) * 0.5;
      double z = pos.getZ() + box.minZ + (box.maxZ - box.minZ) * 0.5;
      return new Vec3d(
         x + direction.getOffsetX() * (box.maxX - box.minX) * 0.5,
         y + direction.getOffsetY() * (box.maxY - box.minY) * 0.5,
         z + direction.getOffsetZ() * (box.maxZ - box.minZ) * 0.5
      );
   }
}