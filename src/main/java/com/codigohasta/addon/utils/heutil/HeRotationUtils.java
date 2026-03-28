package com.codigohasta.addon.utils.heutil;

import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class HeRotationUtils {
   /**
    * 旋转视角朝向指定的坐标向量
    * @param vec3d 目标位置
    */
   public static void rotate(Vec3d vec3d) {
      double yaw = Rotations.getYaw(vec3d);
      double pitch = Rotations.getPitch(vec3d);
      Rotations.rotate(yaw, pitch);
   }

   /**
    * 旋转视角朝向指定的方块坐标
    * @param blockPos 目标方块位置
    */
   public static void rotate(BlockPos blockPos) {
      double yaw = Rotations.getYaw(blockPos);
      double pitch = Rotations.getPitch(blockPos);
      Rotations.rotate(yaw, pitch);
   }
}