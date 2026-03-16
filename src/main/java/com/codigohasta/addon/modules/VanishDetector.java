package com.codigohasta.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket;
import net.minecraft.util.math.Box;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VanishDetector extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("The color of the ghost render.")
        .defaultValue(new SettingColor(255, 0, 0, 150))
        .build()
    );

    private final Setting<Double> timeout = sgGeneral.add(new DoubleSetting.Builder()
        .name("timeout")
        .description("How long to display the location after the last packet received (seconds).")
        .defaultValue(2.0)
        .build()
    );

    private final Map<Integer, GhostData> ghosts = new ConcurrentHashMap<>();

    // 反射缓存字段，避免每秒重复查找导致性能下降
    private Field idField = null;
    private Field xField = null;
    private Field yField = null;
    private Field zField = null;
    private boolean reflectionInit = false;

    public VanishDetector() {
        super(meteordevelopment.meteorclient.systems.modules.Categories.Misc, "vanish-detector", "Shows entities that send position packets but have no spawn packet.");
    }

    @Override
    public void onDeactivate() {
        ghosts.clear();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.world == null) return;

        if (event.packet instanceof EntityPositionSyncS2CPacket packet) {
            try {
                // 初始化反射 (只运行一次)
                if (!reflectionInit) {
                    initializeReflection(packet.getClass());
                    reflectionInit = true;
                }

                // 如果反射初始化失败，直接跳过
                if (idField == null || xField == null) return;

                // 通过反射强制读取 private 字段
                int entityId = idField.getInt(packet);
                double x = xField.getDouble(packet);
                double y = yField.getDouble(packet);
                double z = zField.getDouble(packet);

                // 核心逻辑：ID存在但世界里没这个实体 -> 隐身人
                if (mc.world.getEntityById(entityId) == null) {
                    ghosts.put(entityId, new GhostData(x, y, z, System.currentTimeMillis()));
                }

            } catch (Exception e) {
                // 防止刷屏报错
                if (!reflectionInit) e.printStackTrace();
            }
        }
    }

    // 自动扫描字段，无视混淆名
    private void initializeReflection(Class<?> clazz) {
        try {
            // 1. 查找 int 类型的字段 (通常第一个 int 就是 ID)
            for (Field f : clazz.getDeclaredFields()) {
                if (f.getType() == int.class) {
                    f.setAccessible(true);
                    if (idField == null) idField = f; // 取第一个 int
                }
                // 2. 查找 double 类型的字段 (通常顺序是 x, y, z)
                else if (f.getType() == double.class) {
                    f.setAccessible(true);
                    if (xField == null) xField = f;
                    else if (yField == null) yField = f;
                    else if (zField == null) zField = f;
                }
            }
            
            // 如果没找到，尝试按名字找 'id' (以防它是父类字段或顺序不同)
            if (idField == null) {
                try {
                    idField = clazz.getDeclaredField("id");
                    idField.setAccessible(true);
                } catch (NoSuchFieldException ignored) {}
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        long now = System.currentTimeMillis();
        ghosts.entrySet().removeIf(entry -> (now - entry.getValue().timestamp) > (timeout.get() * 1000));

        for (GhostData ghost : ghosts.values()) {
            double x = ghost.x;
            double y = ghost.y;
            double z = ghost.z;

            Box box = new Box(
                x - 0.3, y, z - 0.3, 
                x + 0.3, y + 1.8, z + 0.3
            );

            event.renderer.box(box, color.get(), color.get(), ShapeMode.Lines, 0);
        }
    }

    private static class GhostData {
        double x, y, z;
        long timestamp;

        public GhostData(double x, double y, double z, long timestamp) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.timestamp = timestamp;
        }
    }
}