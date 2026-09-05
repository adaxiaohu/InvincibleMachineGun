package com.codigohasta.addon.utils.alien;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

public class AlienEntityUtil {
   private static final Minecraft mc = Minecraft.getInstance();

   public static boolean inInventory() {
      if (mc.player == null) return false;
      if (!(mc.player.containerMenu instanceof net.minecraft.world.inventory.InventoryMenu)) {
         return false;
      }
      return mc.screen == null
         || mc.screen instanceof OptionsSubScreen
         || mc.screen instanceof OptionsScreen
         || mc.screen instanceof ChatScreen
         || mc.screen instanceof InventoryScreen
         || mc.screen instanceof PauseScreen;
   }

   public static float getHealth(Entity entity) {
      if (entity instanceof LivingEntity living) {
         return living.getHealth() + living.getAbsorptionAmount();
      }
      return 0.0F;
   }

   public static BlockPos getPlayerPos(boolean fix) {
      if (fix) {
         return BlockPos.containing(mc.player.getX(), mc.player.getY() + 0.3, mc.player.getZ());
      }
      return BlockPos.containing(mc.player.getX(), mc.player.getY(), mc.player.getZ());
   }

   public static boolean canSee(BlockPos pos, Direction side) {
      Vec3 testVec = pos.getCenter().add(side.getUnitVec3i().getX() * 0.5, side.getUnitVec3i().getY() * 0.5, side.getUnitVec3i().getZ() * 0.5);
      HitResult result = mc.level.clip(new ClipContext(mc.player.getEyePosition(), testVec, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
      return result == null || result.getType() == HitResult.Type.MISS;
   }

   public static void swingHand(InteractionHand hand) {
      mc.player.swing(hand);
   }

   public static void swingHandServer(InteractionHand hand) {
      mc.getConnection().send(new ServerboundSwingPacket(hand));
   }

   public static void swingHandClient(InteractionHand hand) {
      mc.player.swing(hand, false);
   }

   public static void swingHand(InteractionHand hand, boolean server) {
      if (server) {
         mc.getConnection().send(new ServerboundSwingPacket(hand));
      } else {
         mc.player.swing(hand);
      }
   }

   public static void syncInventory() {
      mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
   }
}
