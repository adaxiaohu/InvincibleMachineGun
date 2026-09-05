package com.codigohasta.addon.utils.heutil;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Vec3i;

public class HeBlockUtils {
   public static final Minecraft mc = Minecraft.getInstance();

   public static void open(BlockPos pos) {
      Direction clickSide = BlockUtils.getDirection(pos);
      open(pos, clickSide);
   }

   public static void open(BlockPos pos, Direction side) {
      Vec3i vector = side.getUnitVec3i();
      double offset = 0.45;
      Vec3 directionVec = new Vec3(
         pos.getX() + 0.5 + vector.getX() * offset,
         pos.getY() + 0.5 + vector.getY() * offset,
         pos.getZ() + 0.5 + vector.getZ() * offset
      );
      HeRotationUtils.rotate(directionVec);
      BlockHitResult result = new BlockHitResult(directionVec, side, pos, false);
      mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, result);
   }

   public static boolean place(BlockPos blockPos, int slot, boolean checkEntities, Direction side, Vec3 hitPos) {
      if (slot >= 0 && slot <= 8) {
         Block toPlace = Blocks.OBSIDIAN;
         ItemStack i = mc.player.getInventory().getItem(slot);
         if (i.getItem() instanceof BlockItem blockItem) {
            toPlace = blockItem.getBlock();
         }

         if (!BlockUtils.canPlaceBlock(blockPos, checkEntities, toPlace)) {
            return false;
         } else {
            BlockPos neighbour = blockPos.relative(side);
            BlockHitResult bhr = new BlockHitResult(hitPos, side.getOpposite(), neighbour, false);
            Rotations.rotate(Rotations.getYaw(hitPos), Rotations.getPitch(hitPos), 0, () -> {
               InvUtils.swap(slot, false);
               BlockUtils.interact(bhr, InteractionHand.MAIN_HAND, true);
            });
            return true;
         }
      } else {
         return false;
      }
   }

   public static List<BlockPos> listPosInSphere(int range, BlockPos pos) {
      Vec3 centerPos = pos.getCenter();
      List<BlockPos> list = new ArrayList<>();

      for (int x = pos.getX() - range; x < pos.getX() + range; x++) {
         for (int z = pos.getZ() - range; z < pos.getZ() + range; z++) {
            for (int y = pos.getY() - range; y < pos.getY() + range; y++) {
               BlockPos curPos = new BlockPos(x, y, z);
               if (!(curPos.getCenter().distanceTo(centerPos) > range) && !list.contains(curPos)) {
                  list.add(curPos);
               }
            }
         }
      }

      return list;
   }

   public static List<BlockPos> listPosInSphere(int range, int height, BlockPos pos) {
      Vec3 centerPos = pos.getCenter();
      List<BlockPos> list = new ArrayList<>();

      for (int x = pos.getX() - range; x < pos.getX() + range; x++) {
         for (int z = pos.getZ() - range; z < pos.getZ() + range; z++) {
            for (int y = pos.getY(); y < pos.getY() + height; y++) {
               BlockPos curPos = new BlockPos(x, y, z);
               if (!(curPos.getCenter().distanceTo(centerPos) > range) && !list.contains(curPos)) {
                  list.add(curPos);
               }
            }
         }
      }

      return list;
   }

   public static Direction getBlockFacingDirection(BlockState state) {
      if (state.hasProperty(BlockStateProperties.FACING)) {
         return (Direction)state.getValue(BlockStateProperties.FACING);
      } else if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
         return (Direction)state.getValue(BlockStateProperties.HORIZONTAL_FACING);
      } else if (state.hasProperty(BlockStateProperties.AXIS)) {
         Direction.Axis axis = (Direction.Axis)state.getValue(BlockStateProperties.AXIS);
         return Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
      } else {
         return null;
      }
   }

   public static Vec3 getFaceCenter(BlockPos pos, Direction direction) {
      AABB box = mc.level.getBlockState(pos).getShape(mc.level, pos).bounds();
      double x = pos.getX() + box.minX + (box.maxX - box.minX) * 0.5;
      double y = pos.getY() + box.minY + (box.maxY - box.minY) * 0.5;
      double z = pos.getZ() + box.minZ + (box.maxZ - box.minZ) * 0.5;
      return new Vec3(
         x + direction.getStepX() * (box.maxX - box.minX) * 0.5,
         y + direction.getStepY() * (box.maxY - box.minY) * 0.5,
         z + direction.getStepZ() * (box.maxZ - box.minZ) * 0.5
      );
   }
}