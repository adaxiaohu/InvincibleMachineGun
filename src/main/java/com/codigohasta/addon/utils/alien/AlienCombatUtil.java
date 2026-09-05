package com.codigohasta.addon.utils.alien;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;

public class AlienCombatUtil {
   private static final Minecraft mc = Minecraft.getInstance();
   private static final AlienTimer breakTimer = new AlienTimer();

   public static void attackCrystal(BlockPos pos, boolean rotate, boolean eatingPause) {
      for (EndCrystal entity : AlienBlockUtil.getEndCrystals(new AABB(pos))) {
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
         mc.getConnection().send(new ServerboundAttackPacket(entity.getId()));
         mc.player.swing(InteractionHand.MAIN_HAND);
      }
   }
}
