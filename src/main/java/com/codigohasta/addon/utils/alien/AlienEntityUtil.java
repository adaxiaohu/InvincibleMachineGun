package com.codigohasta.addon.utils.alien;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class AlienEntityUtil {
   private static final MinecraftClient mc = MinecraftClient.getInstance();

   public static boolean inInventory() {
      if (mc.player == null) return false;
      if (!(mc.player.currentScreenHandler instanceof net.minecraft.screen.PlayerScreenHandler)) {
         return false;
      }
      return mc.currentScreen == null
         || mc.currentScreen instanceof GameOptionsScreen
         || mc.currentScreen instanceof OptionsScreen
         || mc.currentScreen instanceof ChatScreen
         || mc.currentScreen instanceof InventoryScreen
         || mc.currentScreen instanceof GameMenuScreen;
   }

   public static float getHealth(Entity entity) {
      if (entity instanceof LivingEntity living) {
         return living.getHealth() + living.getAbsorptionAmount();
      }
      return 0.0F;
   }

   public static BlockPos getPlayerPos(boolean fix) {
      if (fix) {
         return BlockPos.ofFloored(mc.player.getX(), mc.player.getY() + 0.3, mc.player.getZ());
      }
      return BlockPos.ofFloored(mc.player.getX(), mc.player.getY(), mc.player.getZ());
   }

   public static boolean canSee(BlockPos pos, Direction side) {
      Vec3d testVec = pos.toCenterPos().add(side.getVector().getX() * 0.5, side.getVector().getY() * 0.5, side.getVector().getZ() * 0.5);
      HitResult result = mc.world.raycast(new RaycastContext(mc.player.getEyePos(), testVec, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
      return result == null || result.getType() == HitResult.Type.MISS;
   }

   public static void swingHand(Hand hand) {
      mc.player.swingHand(hand);
   }

   public static void swingHandServer(Hand hand) {
      mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
   }

   public static void swingHandClient(Hand hand) {
      mc.player.swingHand(hand, false);
   }

   public static void swingHand(Hand hand, boolean server) {
      if (server) {
         mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
      } else {
         mc.player.swingHand(hand);
      }
   }

   public static void syncInventory() {
      mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
   }
}
