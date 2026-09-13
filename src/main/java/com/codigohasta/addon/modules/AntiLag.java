package com.codigohasta.addon.modules;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;


import com.codigohasta.addon.AddonTemplate;

public class AntiLag extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTp = settings.createGroup("Tp Options");

    // ================== General Settings ==================
    private final Setting<VersionType> version = sgGeneral.add(new EnumSetting.Builder<VersionType>()
        .name("server-version-mode")
        .description("1.16 模式启用 TpUtil 路径拆分算法。")
        .defaultValue(VersionType.MC1_16)
        .build());

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("触发拦截的最大距离。")
        .defaultValue(100.0)
        .range(0.1, 2000.0)
        .build());

    private final Setting<Integer> limitPerSecond = sgGeneral.add(new IntSetting.Builder()
        .name("limit-per-second")
        .description("每秒允许的最大位置包数量（防止被踢）。")
        .defaultValue(100)
        .range(1, 10000)
        .build());

    // ================== Tp Option Settings ==================
    private final Setting<Double> moveDistance = sgTp.add(new DoubleSetting.Builder()
        .name("move-distance")
        .description("将拉回距离拆分为该长度的小步进包")
        .defaultValue(0.5)
        .range(0.01, 1.0)
        .build());

    private final Setting<VClipMode> searchVclipMode = sgTp.add(new EnumSetting.Builder<VClipMode>()
        .name("search-vclip-mode")
        .defaultValue(VClipMode.OnlyUp)
        .build());

    private final Setting<Double> searchFindStep = sgTp.add(new DoubleSetting.Builder()
        .name("search-find-step")
        .defaultValue(1.8)
        .range(0.1, 5.0)
        .build());

    private final Setting<Boolean> back = sgTp.add(new BoolSetting.Builder()
        .name("back")
        .description("关闭就是反拉回 。")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> allowIntoVoid = sgTp.add(new BoolSetting.Builder()
        .name("allow-into-void")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> printWhenTooManyPacket = sgTp.add(new BoolSetting.Builder()
        .name("print-too-many-packets")
        .defaultValue(true)
        .build());

    // ================== 内部状态 ==================
    private int lagCounter = 0;
    private long lastResetTime = System.currentTimeMillis();
    private boolean isRateLimited = false;

    public AntiLag() {
        super(AddonTemplate.CATEGORY, "Anti-Lag", "防拉回，抄袭Gcore。效果不明");
    }

    @Override
    public void onActivate() {
        lagCounter = 0;
        lastResetTime = System.currentTimeMillis();
        isRateLimited = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (System.currentTimeMillis() - lastResetTime >= 1000) {
            lagCounter = 0;
            lastResetTime = System.currentTimeMillis();
            isRateLimited = false;
        }

       
        boolean isMoving = mc.player.input.playerInput.forward() || 
                           mc.player.input.playerInput.backward() || 
                           mc.player.input.playerInput.left() || 
                           mc.player.input.playerInput.right();

        // 自动脱困 (VClip)
        if (isMoving && mc.player.horizontalCollision && !back.get()) {
            if (searchVclipMode.get() == VClipMode.OnlyUp) {
                mc.player.setPosition(mc.player.getX(), mc.player.getY() + searchFindStep.get(), mc.player.getZ());
            }
        }
    }

   
    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null) return;

        if (event.packet instanceof PlayerMoveC2SPacket) {
        
            boolean isFlying = mc.player.isGliding() && 
                mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem().toString().contains("elytra");
            if (isFlying) return;

            lagCounter++;

            if (lagCounter > limitPerSecond.get()) {
                event.cancel();
                isRateLimited = true;
                if (printWhenTooManyPacket.get() && lagCounter == limitPerSecond.get() + 1) {
                    warning("§7限制tp数据包。");
                   
                    mc.particleManager.addParticle(ParticleTypes.CRIT, 
                        mc.player.getX(), mc.player.getY() + 1.0, mc.player.getZ(), 0.0, 0.0, 0.0);
                }
            }
        }
    }

    
    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null || mc.world == null) return;

        if (event.packet instanceof PlayerPositionLookS2CPacket packet) {
        
            if (lagCounter > limitPerSecond.get()) return;

       
            Vec3d serverPos = packet.change().position();
            Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());

            double dist = playerPos.distanceTo(serverPos);
            if (dist > range.get()) return;

            event.cancel();
            
      
            mc.getNetworkHandler().sendPacket(new TeleportConfirmC2SPacket(packet.teleportId()));

            
            if (!back.get()) {
                if (!allowIntoVoid.get() && serverPos.y < mc.world.getBottomY()) return;

                if (version.get() == VersionType.MC1_16) {
                  
                    int steps = (int) Math.ceil(dist / moveDistance.get());
                    
                
                    for (int i = 1; i <= steps; i++) {
                        double ratio = (double) i / steps;
                        double nextX = serverPos.x + (playerPos.x - serverPos.x) * ratio;
                        double nextY = serverPos.y + (playerPos.y - serverPos.y) * ratio;
                        double nextZ = serverPos.z + (playerPos.z - serverPos.z) * ratio;
                        
                  
                        sendFullMovePacket(nextX, nextY, nextZ, mc.player.isOnGround());
                    }
                } else {
              
                    sendFullMovePacket(playerPos.x, playerPos.y, playerPos.z, mc.player.isOnGround());
                }

               
                mc.player.setPosition(playerPos.x, playerPos.y, playerPos.z);
                
              
                lagCounter++;
            }
        }
    }

  
    @EventHandler
    private void onPlayerMove(PlayerMoveEvent event) {
        if (isRateLimited) {
          
            ((IVec3d) event.movement).meteor$set(0.0, 0.0, 0.0);
        }
    }


    private void sendFullMovePacket(double x, double y, double z, boolean onGround) {
        if (mc.getNetworkHandler() == null) return;
        
       
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(
            x, y, z, 
            mc.player.getYaw(), 
            mc.player.getPitch(), 
            onGround, 
            false
        ));
    }

 
    public enum VersionType {
        MC1_16("1.16 (TpUtil Path)"),
        MC1_9("1.9 (Direct)");

        private final String name;
        VersionType(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    public enum VClipMode {
        OnlyUp, Down, Both
    }
}