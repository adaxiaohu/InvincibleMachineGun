package com.codigohasta.addon.utils.slimefunhelper.versioned.api;

import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.math.Vec3d;
public final class VPacket {
    public static PlayerMoveC2SPacket.OnGroundOnly newOnGroundOnly(boolean ground,boolean collision){return new PlayerMoveC2SPacket.OnGroundOnly(ground,collision);}
    public static boolean getCollisionFlag(PlayerMoveC2SPacket packet){return packet.horizontalCollision();}
    public static PlayerMoveC2SPacket.Full newFull(double x,double y,double z,float yaw,float pitch,boolean ground,boolean collision){return new PlayerMoveC2SPacket.Full(x,y,z,yaw,pitch,ground,collision);}
    public static PlayerMoveC2SPacket.PositionAndOnGround newPositionAndOnGround(double x,double y,double z,boolean ground,boolean collision){return new PlayerMoveC2SPacket.PositionAndOnGround(x,y,z,ground,collision);}
    public static PlayerMoveC2SPacket.LookAndOnGround newLookAndOnGround(float yaw,float pitch,boolean ground,boolean collision){return new PlayerMoveC2SPacket.LookAndOnGround(yaw,pitch,ground,collision);}
    public static Vec3d getVelocity(EntityVelocityUpdateS2CPacket packet){return packet.getVelocity();}
public static net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket newVehicleMove(net.minecraft.entity.Entity entity){return net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket.fromVehicle(entity);}
}