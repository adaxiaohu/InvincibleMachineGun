package com.codigohasta.addon.utils.alien;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CartographyTableBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.LoomBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public class AlienBlockUtil {
   private static final Minecraft mc = Minecraft.getInstance();

   public static Block getBlock(BlockPos pos) {
      return mc.level.getBlockState(pos).getBlock();
   }

   public static boolean canReplace(BlockPos pos) {
      if (pos.getY() >= 320) return false;
      BlockState state = mc.level.getBlockState(pos);
      return state.canBeReplaced();
   }

   public static boolean isClickable(Block block) {
      return block instanceof CraftingTableBlock
         || block instanceof AnvilBlock
         || block instanceof LoomBlock
         || block instanceof CartographyTableBlock
         || block instanceof GrindstoneBlock
         || block instanceof StonecutterBlock
         || block instanceof ButtonBlock
         || block instanceof BasePressurePlateBlock
         || block instanceof BaseEntityBlock
         || block instanceof BedBlock
         || block instanceof FenceGateBlock
         || block instanceof DoorBlock
         || block instanceof NoteBlock
         || block instanceof TrapDoorBlock;
   }

   public static boolean canClick(BlockPos pos) {
      BlockState state = mc.level.getBlockState(pos);
      Block block = state.getBlock();
      return mc.player.isShiftKeyDown() || !isClickable(block);
   }

   public static List<Entity> getEntities(AABB box) {
      List<Entity> list = new ArrayList<>();
      for (Entity entity : mc.level.entitiesForRendering()) {
         if (entity != null && entity.getBoundingBox().intersects(box)) {
            list.add(entity);
         }
      }
      return list;
   }

   public static List<EndCrystal> getEndCrystals(AABB box) {
      List<EndCrystal> list = new ArrayList<>();
      for (Entity entity : mc.level.entitiesForRendering()) {
         if (entity instanceof EndCrystal crystal && crystal.getBoundingBox().intersects(box)) {
            list.add(crystal);
         }
      }
      return list;
   }

   public static boolean hasEntity(BlockPos pos, boolean ignoreCrystal) {
      return hasEntity(new AABB(pos), ignoreCrystal);
   }

   public static boolean hasEntity(AABB box, boolean ignoreCrystal) {
      for (Entity entity : getEntities(box)) {
         if (entity.isAlive()
            && !(entity instanceof ItemEntity)
            && !(entity instanceof Arrow)
            && (!ignoreCrystal || !(entity instanceof EndCrystal))) {
            return true;
         }
      }
      return false;
   }

   public static boolean hasCrystal(BlockPos pos) {
      for (Entity entity : getEndCrystals(new AABB(pos))) {
         if (entity.isAlive() && entity instanceof EndCrystal) {
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
            double disSq = mc.player.getEyePosition().distanceToSqr(pos.relative(i).getCenter());
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
            double disSq = mc.player.getEyePosition().distanceToSqr(pos.relative(i).getCenter());
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
         if (canClick(pos.relative(i)) && !canReplace(pos.relative(i)) && isStrictDirection(pos.relative(i), i.getOpposite())) {
            double vecDis = mc.player.getEyePosition().distanceToSqr(pos.getCenter().add(i.getUnitVec3i().getX() * 0.5, i.getUnitVec3i().getY() * 0.5, i.getUnitVec3i().getZ() * 0.5));
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
         clickBlock(pos.relative(side), side.getOpposite(), rotate, InteractionHand.MAIN_HAND, packet);
      }
   }

   public static void placeCrystal(BlockPos pos, boolean rotate) {
      boolean offhand = mc.player.getOffhandItem().getItem() == Items.END_CRYSTAL;
      BlockPos obsPos = pos.below();
      Direction facing = getClickSide(obsPos);
      Vec3 vec = obsPos.getCenter().add(facing.getUnitVec3i().getX() * 0.5, facing.getUnitVec3i().getY() * 0.5, facing.getUnitVec3i().getZ() * 0.5);

      clickBlock(obsPos, facing, rotate, offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, true);
   }

   public static void clickBlock(BlockPos pos, Direction side, boolean rotate, InteractionHand hand, boolean packet) {
      Vec3 directionVec = new Vec3(
         pos.getX() + 0.5 + side.getUnitVec3i().getX() * 0.5,
         pos.getY() + 0.5 + side.getUnitVec3i().getY() * 0.5,
         pos.getZ() + 0.5 + side.getUnitVec3i().getZ() * 0.5
      );

      BlockHitResult result = new BlockHitResult(directionVec, side, pos, false);
      if (packet) {
         mc.getConnection().send(new ServerboundUseItemOnPacket(hand, result, 0));
      } else {
         mc.gameMode.useItemOn(mc.player, hand, result);
      }
   }
}
