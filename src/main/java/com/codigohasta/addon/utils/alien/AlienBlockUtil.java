package com.codigohasta.addon.utils.alien;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class AlienBlockUtil {
   private static final MinecraftClient mc = MinecraftClient.getInstance();

   public static Block getBlock(BlockPos pos) {
      return mc.world.getBlockState(pos).getBlock();
   }

   public static boolean canReplace(BlockPos pos) {
      if (pos.getY() >= 320) return false;
      BlockState state = mc.world.getBlockState(pos);
      return state.isReplaceable();
   }

   public static boolean isClickable(Block block) {
      return block instanceof CraftingTableBlock
         || block instanceof AnvilBlock
         || block instanceof LoomBlock
         || block instanceof CartographyTableBlock
         || block instanceof GrindstoneBlock
         || block instanceof StonecutterBlock
         || block instanceof ButtonBlock
         || block instanceof AbstractPressurePlateBlock
         || block instanceof BlockWithEntity
         || block instanceof BedBlock
         || block instanceof FenceGateBlock
         || block instanceof DoorBlock
         || block instanceof NoteBlock
         || block instanceof TrapdoorBlock;
   }

   public static boolean canClick(BlockPos pos) {
      BlockState state = mc.world.getBlockState(pos);
      Block block = state.getBlock();
      return mc.player.isSneaking() || !isClickable(block);
   }

   public static List<Entity> getEntities(Box box) {
      List<Entity> list = new ArrayList<>();
      for (Entity entity : mc.world.getEntities()) {
         if (entity != null && entity.getBoundingBox().intersects(box)) {
            list.add(entity);
         }
      }
      return list;
   }

   public static List<EndCrystalEntity> getEndCrystals(Box box) {
      List<EndCrystalEntity> list = new ArrayList<>();
      for (Entity entity : mc.world.getEntities()) {
         if (entity instanceof EndCrystalEntity crystal && crystal.getBoundingBox().intersects(box)) {
            list.add(crystal);
         }
      }
      return list;
   }

   public static boolean hasEntity(BlockPos pos, boolean ignoreCrystal) {
      return hasEntity(new Box(pos), ignoreCrystal);
   }

   public static boolean hasEntity(Box box, boolean ignoreCrystal) {
      for (Entity entity : getEntities(box)) {
         if (entity.isAlive()
            && !(entity instanceof ItemEntity)
            && !(entity instanceof ArrowEntity)
            && (!ignoreCrystal || !(entity instanceof EndCrystalEntity))) {
            return true;
         }
      }
      return false;
   }

   public static boolean hasCrystal(BlockPos pos) {
      for (Entity entity : getEndCrystals(new Box(pos))) {
         if (entity.isAlive() && entity instanceof EndCrystalEntity) {
            return true;
         }
      }
      return false;
   }

   public static boolean canPlace(BlockPos pos) {
      return canPlace(pos, 1000.0);
   }

   public static boolean canPlace(BlockPos pos, double distance) {
      if (getPlaceSide(pos, distance) == null) return false;
      return canReplace(pos) && !hasEntity(pos, false);
   }

   public static Direction getClickSide(BlockPos pos) {
      Direction side = Direction.UP;
      double minDistance = Double.MAX_VALUE;
      for (Direction i : Direction.values()) {
         if (isStrictDirection(pos, i)) {
            double disSq = mc.player.getEyePos().squaredDistanceTo(pos.offset(i).toCenterPos());
            if (!(disSq > minDistance)) {
               side = i;
               minDistance = disSq;
            }
         }
      }
      return side;
   }

   public static Direction getClickSideStrict(BlockPos pos) {
      Direction side = null;
      double minDistance = Double.MAX_VALUE;
      for (Direction i : Direction.values()) {
         if (isStrictDirection(pos, i)) {
            double disSq = mc.player.getEyePos().squaredDistanceTo(pos.offset(i).toCenterPos());
            if (!(disSq > minDistance)) {
               side = i;
               minDistance = disSq;
            }
         }
      }
      return side;
   }

   public static boolean isStrictDirection(BlockPos pos, Direction side) {
      return true;
   }

   public static Direction getPlaceSide(BlockPos pos) {
      return getPlaceSide(pos, 1000.0);
   }

   public static Direction getPlaceSide(BlockPos pos, double reachDistance) {
      double minDistance = Double.MAX_VALUE;
      Direction side = null;
      for (Direction i : Direction.values()) {
         if (canClick(pos.offset(i)) && !canReplace(pos.offset(i)) && isStrictDirection(pos.offset(i), i.getOpposite())) {
            double vecDis = mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos().add(i.getVector().getX() * 0.5, i.getVector().getY() * 0.5, i.getVector().getZ() * 0.5));
            if (!(Math.sqrt(vecDis) > reachDistance) && !(vecDis > minDistance)) {
               side = i;
               minDistance = vecDis;
            }
         }
      }
      return side;
   }

   public static void placeBlock(BlockPos pos, boolean rotate, boolean packet) {
      Direction side = getPlaceSide(pos);
      if (side != null) {
         clickBlock(pos.offset(side), side.getOpposite(), rotate, Hand.MAIN_HAND, packet);
      }
   }

   public static void placeCrystal(BlockPos pos, boolean rotate) {
      boolean offhand = mc.player.getOffHandStack().getItem() == Items.END_CRYSTAL;
      BlockPos obsPos = pos.down();
      Direction facing = getClickSide(obsPos);
      Vec3d vec = obsPos.toCenterPos().add(facing.getVector().getX() * 0.5, facing.getVector().getY() * 0.5, facing.getVector().getZ() * 0.5);

      clickBlock(obsPos, facing, rotate, offhand ? Hand.OFF_HAND : Hand.MAIN_HAND, true);
   }

   public static void clickBlock(BlockPos pos, Direction side, boolean rotate, Hand hand, boolean packet) {
      Vec3d directionVec = new Vec3d(
         pos.getX() + 0.5 + side.getVector().getX() * 0.5,
         pos.getY() + 0.5 + side.getVector().getY() * 0.5,
         pos.getZ() + 0.5 + side.getVector().getZ() * 0.5
      );

      BlockHitResult result = new BlockHitResult(directionVec, side, pos, false);
      if (packet) {
         mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(hand, result, 0));
      } else {
         mc.interactionManager.interactBlock(mc.player, hand, result);
      }
   }
}
