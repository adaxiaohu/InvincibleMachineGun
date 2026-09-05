package com.codigohasta.addon.modules.villager;

import java.util.UUID;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class VillagerEntityWarp {
   private static final Minecraft mc = Minecraft.getInstance();
   private final UUID uuid;
   private final BlockPos operatePos;
   private final Vec3 operatePosCenter;
   private long lastTradeTime;
   private long tradeTimes;

   public VillagerEntityWarp(UUID uuid, BlockPos operatePos) {
      this.uuid = uuid;
      this.operatePos = operatePos;
      this.operatePosCenter = operatePos.getCenter();
   }

   public UUID getUuid() {
      return this.uuid;
   }

   public Villager getVillager() {
      try {
         for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Villager && entity.getUUID().equals(this.uuid)) {
               return (Villager) entity;
            }
         }
         return null;
      } catch (Exception var2) {
         ChatUtils.error("获取村民状态异常 : " + var2.getMessage());
         var2.printStackTrace();
         return null;
      }
   }

   public BlockPos getOperatePos() {
      return this.operatePos;
   }

   public Vec3 getOperatePosCenter() {
      return this.operatePosCenter;
   }

   public long getLastTradeTime() {
      return this.lastTradeTime;
   }

   public void setLastTradeTime(long lastTradeTime) {
      this.lastTradeTime = lastTradeTime;
   }

   public long getTradeTimes() {
      return this.tradeTimes;
   }

   public void setTradeTimes(long tradeTimes) {
      this.tradeTimes = tradeTimes;
   }
}