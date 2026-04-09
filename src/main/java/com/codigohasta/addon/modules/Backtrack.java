package com.codigohasta.addon.modules; // 请修改为你的实际包名

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.entity.Entity;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class Backtrack extends Module {

    public enum Mode {
        LagBehind,
        Freeze
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDelays = settings.createGroup("Delays");
    private final SettingGroup sgPackets = settings.createGroup("Target Packets");
    private final SettingGroup sgRender = settings.createGroup("Render"); // 新增渲染设置组

    // --- General Settings ---
    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>().name("mode").defaultValue(Mode.LagBehind).build());
    private final Setting<Boolean> smart = sgGeneral.add(new BoolSetting.Builder().name("smart").defaultValue(true).build());
    private final Setting<Double> minRange = sgGeneral.add(new DoubleSetting.Builder().name("min-range").defaultValue(2.5).range(0.5, 10.0).sliderRange(0.5, 10.0).build());
    private final Setting<Double> maxRange = sgGeneral.add(new DoubleSetting.Builder().name("max-range").defaultValue(7.5).range(0.5, 10.0).sliderRange(0.5, 10.0).build());
    private final Setting<Double> maxReach = sgGeneral.add(new DoubleSetting.Builder().name("max-reach").defaultValue(4.0).range(0.05, 6.0).sliderRange(0.05, 6.0).build());

    // --- Delay Settings ---
    private final Setting<Integer> minLagMs = sgDelays.add(new IntSetting.Builder().name("min-lag-ms").defaultValue(125).range(5, 5000).sliderRange(5, 1000).build());
    private final Setting<Integer> maxLagMs = sgDelays.add(new IntSetting.Builder().name("max-lag-ms").defaultValue(275).range(5, 5000).sliderRange(5, 1000).build());
    private final Setting<Integer> maxPacketData = sgDelays.add(new IntSetting.Builder().name("max-packet-data").defaultValue(7).range(1, 100).sliderRange(1, 20).build());

    // --- Target Packets Settings ---
    private final Setting<Boolean> targetMovements = sgPackets.add(new BoolSetting.Builder().name("movements").defaultValue(true).build());
    private final Setting<Boolean> targetSwings = sgPackets.add(new BoolSetting.Builder().name("swings").defaultValue(true).build());
    private final Setting<Boolean> targetAttacks = sgPackets.add(new BoolSetting.Builder().name("attacks").defaultValue(true).build());
    private final Setting<Boolean> targetActions = sgPackets.add(new BoolSetting.Builder().name("entity-actions").defaultValue(true).build());
    private final Setting<Boolean> targetDigs = sgPackets.add(new BoolSetting.Builder().name("digs").defaultValue(true).build());
    private final Setting<Boolean> targetPlacements = sgPackets.add(new BoolSetting.Builder().name("placements").defaultValue(true).build());
    private final Setting<Boolean> targetTransactions = sgPackets.add(new BoolSetting.Builder().name("transactions").defaultValue(true).build());
    private final Setting<Boolean> targetKeepAlives = sgPackets.add(new BoolSetting.Builder().name("keep-alives").defaultValue(true).build());

    // --- Render Settings (1.21 Meteor ESP) ---
    private final Setting<Boolean> renderEsp = sgRender.add(new BoolSetting.Builder()
        .name("render-esp")
        .description("Renders a box at the entity's real server position.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the box is rendered.")
        .defaultValue(ShapeMode.Both)
        .visible(renderEsp::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("The side color of the bounding box.")
        .defaultValue(new SettingColor(255, 0, 0, 50))
        .visible(() -> renderEsp.get() && (shapeMode.get() == ShapeMode.Sides || shapeMode.get() == ShapeMode.Both))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("The line color of the bounding box.")
        .defaultValue(new SettingColor(255, 0, 0, 200))
        .visible(() -> renderEsp.get() && (shapeMode.get() == ShapeMode.Lines || shapeMode.get() == ShapeMode.Both))
        .build()
    );

    // --- State Variables ---
    private final List<BacktrackData> packetData = new CopyOnWriteArrayList<>();
    private final List<TimedPacket> inboundPackets = new CopyOnWriteArrayList<>();
    private final List<TimedPacket> outboundPackets = new CopyOnWriteArrayList<>();

    private int randomizedMilliseconds = 0;
    private long lastResetTime = 0;
    private boolean shouldBacktrackSmart = false;
    private boolean isSending = false;

    // Reflection Cache for S2C Records (Prevents 1.21+ API mapping issues)
    private static final Map<Class<?>, Method> idMethodCache = new HashMap<>();

    public Backtrack() {
        super(meteordevelopment.meteorclient.systems.modules.Categories.Combat, "backtrack", "Simulates lag for a reach advantage with real position ESP.");
    }

    @Override
    public void onActivate() {
        resetTimer();
        packetData.clear();
        inboundPackets.clear();
        outboundPackets.clear();
        shouldBacktrackSmart = false;
    }

    @Override
    public void onDeactivate() {
        flushAll();
    }

    private void resetTimer() {
        lastResetTime = System.currentTimeMillis();
        randomizedMilliseconds = minLagMs.get() + (int) (Math.random() * ((maxLagMs.get() - minLagMs.get()) + 1));
    }

    // ================== [ 3D Rendering (Meteor 1.21.11 API) ] ==================
    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!renderEsp.get() || mc.world == null) return;

        for (BacktrackData data : packetData) {
            // 如果数据里没有缓存包，或者位置为 null 则不渲染
            if (data.actualPosition == null || data.movements.isEmpty()) continue;

            Entity entity = mc.world.getEntityById(data.entityId);
            if (entity == null) continue;

            double x = data.actualPosition.x;
            double y = data.actualPosition.y;
            double z = data.actualPosition.z;

            // 获取实体在 1.21 中的真实宽高
            double width = entity.getWidth() / 2.0;
            double height = entity.getHeight();

            // 使用 Meteor 自带的 Renderer3D 绘制 Box，完美兼容光影和管线
            event.renderer.box(
                x - width, y, z - width,      // 最小坐标 minX, minY, minZ
                x + width, y + height, z + width, // 最大坐标 maxX, maxY, maxZ
                sideColor.get(), lineColor.get(), shapeMode.get(), 0
            );
        }
    }
    // ===========================================================================

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        long currentTime = System.currentTimeMillis();

        if (smart.get()) {
            double closestDist = 999999.0;
            for (BacktrackData data : packetData) {
                Entity entity = mc.world.getEntityById(data.entityId);
                if (entity != null) {
                    double dist = mc.player.distanceTo(entity);
                    if (dist < closestDist) closestDist = dist;
                }
            }
            if (closestDist >= maxRange.get()) shouldBacktrackSmart = false;

            if (!shouldBacktrackSmart) {
                flushAll();
                return;
            }
        }

        if (currentTime - lastResetTime >= randomizedMilliseconds) {
            inboundPackets.removeIf(p -> {
                if (currentTime - p.time >= randomizedMilliseconds) {
                    applyInbound(p.packet);
                    return true;
                }
                return false;
            });

            outboundPackets.removeIf(p -> {
                if (currentTime - p.time >= randomizedMilliseconds) {
                    String name = p.packet.getClass().getSimpleName();
                    if (name.contains("PlayerInteractEntity")) {
                        boolean outOfReach = false;
                        for (BacktrackData data : packetData) {
                            if (data.actualPosition == null) continue;
                            Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                            if (data.actualPosition.distanceTo(playerPos) >= maxReach.get()) {
                                outOfReach = true;
                                break;
                            }
                        }
                        if (!outOfReach) sendOutbound(p.packet);
                    } else {
                        sendOutbound(p.packet);
                    }
                    return true;
                }
                return false;
            });

            if (mode.get() == Mode.Freeze) {
                for (BacktrackData data : packetData) {
                    for (Packet<?> packet : data.movements) applyInbound(packet);
                    data.movements.clear();
                }
            }
            resetTimer();
        }

        if (mode.get() == Mode.LagBehind) {
            for (BacktrackData data : packetData) {
                if (data.movements.size() >= (randomizedMilliseconds / 50)) {
                    if (!data.movements.isEmpty()) applyInbound(data.movements.remove(0));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onPacketSend(PacketEvent.Send event) {
        if (isSending) return;

        Packet<?> packet = event.packet;
        String name = packet.getClass().getSimpleName();
        boolean intercept = false;

        if (targetMovements.get() && name.contains("PlayerMove")) intercept = true;
        else if (targetSwings.get() && name.contains("HandSwing")) intercept = true;
        else if (targetActions.get() && name.contains("ClientCommand")) intercept = true;
        else if (targetDigs.get() && name.contains("PlayerAction")) intercept = true;
        else if (targetPlacements.get() && (name.contains("PlayerInteractBlock") || name.contains("PlayerInteractItem"))) intercept = true;
        else if (targetTransactions.get() && name.contains("Pong")) intercept = true;
        else if (targetKeepAlives.get() && name.contains("KeepAlive")) intercept = true;
        else if (targetAttacks.get() && name.contains("PlayerInteractEntity")) {
            intercept = true;
            if (!shouldBacktrackSmart) shouldBacktrackSmart = true;
        }

        if (intercept) {
            outboundPackets.add(new TimedPacket(packet));
            event.cancel();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null || mc.world == null) return;

        Packet<?> packet = event.packet;
        String name = packet.getClass().getSimpleName();

        if (name.contains("EntityPositionS2CPacket") || name.contains("EntityS2CPacket") || name.contains("EntityTrackerUpdateS2CPacket")) {
            int entityId = extractEntityIdSafely(packet);
            if (entityId == -1 || entityId == mc.player.getId()) return;

            Entity entity = mc.world.getEntityById(entityId);
            if (entity == null) return;

            double dist = mc.player.distanceTo(entity);
            if (dist < minRange.get() || dist > maxRange.get()) return;

            BacktrackData data = retrieveData(entityId);
            data.actualPosition = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
            
            if (data.movements.size() > maxPacketData.get()) return;

            data.movements.add(packet);
            event.cancel();
        }
    }

    private void flushAll() {
        for (BacktrackData data : packetData) {
            for (Packet<?> packet : data.movements) applyInbound(packet);
        }
        packetData.clear();

        for (TimedPacket p : inboundPackets) applyInbound(p.packet);
        inboundPackets.clear();

        for (TimedPacket p : outboundPackets) sendOutbound(p.packet);
        outboundPackets.clear();
    }

    @SuppressWarnings("unchecked")
    private void applyInbound(Packet<?> packet) {
        if (mc.getNetworkHandler() != null) {
            try { ((Packet<ClientPlayPacketListener>) packet).apply(mc.getNetworkHandler()); } catch (Exception ignored) { }
        }
    }

    private void sendOutbound(Packet<?> packet) {
        if (mc.getNetworkHandler() != null) {
            isSending = true;
            mc.getNetworkHandler().sendPacket(packet);
            isSending = false;
        }
    }

    private BacktrackData retrieveData(int entityId) {
        for (BacktrackData data : packetData) {
            if (data.entityId == entityId) return data;
        }
        BacktrackData newData = new BacktrackData(entityId);
        packetData.add(newData);
        return newData;
    }

    private int extractEntityIdSafely(Packet<?> packet) {
        Class<?> clazz = packet.getClass();
        
        // 1. 从缓存中快速读取，避免重复反射损耗性能
        if (idMethodCache.containsKey(clazz)) {
            Method m = idMethodCache.get(clazz);
            if (m == null) return -1;
            try { 
                return (int) m.invoke(packet); 
            } catch (Exception e) { 
                return -1; 
            }
        }
        
        // 2. 如果没有缓存，则动态查找对应的方法
        try {
            Method m;
            try {
                m = clazz.getMethod("id"); // 尝试 1.21+ Record 新格式
            } catch (NoSuchMethodException e) {
                m = clazz.getMethod("getEntityId"); // 尝试旧版 Getter 格式
            }
            m.setAccessible(true);
            idMethodCache.put(clazz, m);
            return (int) m.invoke(packet); // 此时外部的 catch(Exception) 会接管所有 IllegalAccessException
        } catch (Exception e) {
            // 彻底找不到该方法，或强转失败，标记为 null
            idMethodCache.put(clazz, null);
            return -1;
        }
    }

    private static class TimedPacket {
        public final Packet<?> packet;
        public final long time;
        public TimedPacket(Packet<?> packet) {
            this.packet = packet;
            this.time = System.currentTimeMillis();
        }
    }

    private static class BacktrackData {
        public final int entityId;
        public Vec3d actualPosition = null; // 修正初始值
        public final List<Packet<?>> movements = new CopyOnWriteArrayList<>();
        public BacktrackData(int entityId) { this.entityId = entityId; }
    }
}