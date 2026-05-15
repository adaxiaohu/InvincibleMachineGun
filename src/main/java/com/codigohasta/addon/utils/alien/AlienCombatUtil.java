package com.codigohasta.addon.utils.alien;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public class AlienCombatUtil {
   private static final MinecraftClient mc = MinecraftClient.getInstance();
   private static final AlienTimer breakTimer = new AlienTimer();

   public static void attackCrystal(BlockPos pos, boolean rotate, boolean eatingPause) {
      for (EndCrystalEntity entity : AlienBlockUtil.getEndCrystals(new Box(pos))) {
         attackWithDelay(entity, rotate, eatingPause);
      }
   }

   public static void attackWithDelay(Entity entity, boolean rotate, boolean usingPause) {
      if (breakTimer.passedMs(100)) {
         if (!usingPause || !mc.player.isUsingItem()) {
            attack(entity, rotate);
         }
      }
   }

   public static void attack(Entity entity, boolean rotate) {
      if (entity != null) {
         breakTimer.reset();
         mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(entity, mc.player.isSneaking()));
         mc.player.swingHand(Hand.MAIN_HAND);
      }
   }
}
