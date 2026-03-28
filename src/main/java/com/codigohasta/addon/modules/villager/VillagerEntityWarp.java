package com.codigohasta.addon.modules.villager;

import java.util.UUID;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class VillagerEntityWarp {
   private static final MinecraftClient mc = MinecraftClient.getInstance();
   private final UUID uuid;
   private final BlockPos operatePos;
   private final Vec3d operatePosCenter;
   private long lastTradeTime;
   private long tradeTimes;

   public VillagerEntityWarp(UUID uuid, BlockPos operatePos) {
      this.uuid = uuid;
      this.operatePos = operatePos;
      this.operatePosCenter = operatePos.toCenterPos();
   }

   public UUID getUuid() {
      return this.uuid;
   }

   public VillagerEntity getVillager() {
      try {
         for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof VillagerEntity && entity.getUuid().equals(this.uuid)) {
               return (VillagerEntity) entity;
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

   public Vec3d getOperatePosCenter() {
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