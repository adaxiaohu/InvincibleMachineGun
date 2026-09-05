package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.alien.AlienRender3DUtil;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.tags.FluidTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class LavaESP extends Module {
    public static LavaESP INSTANCE;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSphere = settings.createGroup("Sphere");
    private final SettingGroup sgArrow = settings.createGroup("Arrow");
    private final SettingGroup sgText = settings.createGroup("Text");

    // ── General ──

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("检测范围（格）")
        .defaultValue(8)
        .min(1)
        .max(32)
        .sliderRange(1, 32)
        .build()
    );

    private final Setting<Boolean> dangerColors = sgGeneral.add(new BoolSetting.Builder()
        .name("danger-colors")
        .description("根据危险等级自动变色（关则用下方统一颜色）")
        .defaultValue(true)
        .build()
    );

    // ── Sphere ──

    private final Setting<Boolean> sphere = sgSphere.add(new BoolSetting.Builder()
        .name("sphere")
        .description("显示3D方块边框")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> sphereColor = sgSphere.add(new ColorSetting.Builder()
        .name("sphere-color")
        .description("方块边框颜色（告警着色关闭时使用）")
        .defaultValue(new SettingColor(255, 50, 50, 200))
        .build()
    );

    // ── Arrow ──

    private final Setting<Boolean> arrow = sgArrow.add(new BoolSetting.Builder()
        .name("arrow")
        .description("显示指向岩浆来源方向的箭头")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> arrowColor = sgArrow.add(new ColorSetting.Builder()
        .name("arrow-color")
        .description("箭头线条颜色")
        .defaultValue(new SettingColor(255, 100, 0, 255))
        .build()
    );

    // ── Text ──

    private final Setting<Boolean> text = sgText.add(new BoolSetting.Builder()
        .name("text")
        .description("在方块上方显示危险提示文字")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> textColor = sgText.add(new ColorSetting.Builder()
        .name("text-color")
        .description("提示文字颜色（告警着色关闭时使用）")
        .defaultValue(new SettingColor(255, 50, 50, 255))
        .build()
    );

    // ── 数据缓存 ──

    private final Map<BlockPos, LavaDanger> dangerCache = new HashMap<>();
    private int tickCounter = 0;

    // ── 等级颜色 ──
    private static final Color COLOR_DANGER  = new Color(255, 0, 0, 200);
    private static final Color COLOR_WARNING = new Color(255, 100, 0, 200);
    private static final Color COLOR_CAUTION = new Color(255, 180, 0, 180);
    private static final Color COLOR_SAFE    = new Color(255, 220, 50, 160);

    // ── 标签文本 ──
    private static final String LABEL_DANGER  = "§c§l岩浆!";
    private static final String LABEL_WARNING = "§6岩浆";
    private static final String LABEL_SOURCE  = "§e岩浆源";
    private static final String LABEL_FLOW    = "§7岩浆流";

    public LavaESP() {
        super(AddonTemplate.CATEGORY, "岩浆泄露预警", "预测方块挖开会流出岩浆的位置，绘制3D球体和方向箭头");
        INSTANCE = this;
    }

    @Override
    public void onDeactivate() {
        dangerCache.clear();
    }

    // ══════════════════════════════════════════════
    //  扫描（Tick级别，每2 tick一次）
    // ══════════════════════════════════════════════

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) return;

        tickCounter++;
        if (tickCounter % 2 != 0) return; // 每2 tick扫描一次

        int r = range.get();
        BlockPos playerPos = mc.player.blockPosition();
        dangerCache.clear();

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dist > r) continue;

                    BlockPos checkPos = playerPos.offset(dx, dy, dz);

                    // 跳过空气和岩浆方块本身
                    if (mc.level.isEmptyBlock(checkPos)) continue;
                    if (mc.level.getFluidState(checkPos).is(FluidTags.LAVA)) continue;

                    Set<Direction> lavaDirs = new HashSet<>();
                    int sourceCount = 0;

                    for (Direction dir : Direction.values()) {
                        FluidState fluid = mc.level.getFluidState(checkPos.relative(dir));
                        if (!fluid.isEmpty() && fluid.is(FluidTags.LAVA)) {
                            lavaDirs.add(dir);
                            if (fluid.isSource()) sourceCount++;
                        }
                    }

                    if (!lavaDirs.isEmpty()) {
                        dangerCache.put(checkPos, new LavaDanger(lavaDirs, sourceCount, dist));
                    }
                }
            }
        }
    }

    // ══════════════════════════════════════════════
    //  渲染（3D）
    // ══════════════════════════════════════════════

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (dangerCache.isEmpty()) return;

        for (Map.Entry<BlockPos, LavaDanger> entry : dangerCache.entrySet()) {
            BlockPos pos = entry.getKey();
            LavaDanger danger = entry.getValue();

            Color sphereCol = getSphereColor(danger);
            Color arrowCol = toMeteor(arrowColor.get());

            // ── 3D 方块边框 ──
            if (sphere.get()) {
                drawBlockOutline(event, pos, sphereCol);
            }

            // ── 方向箭头 ──
            if (arrow.get()) {
                for (Direction dir : danger.lavaDirections) {
                    drawArrow(event, pos, dir, arrowCol);
                }
            }

            // ── 文字提示 ──
            if (text.get()) {
                String label = getDangerLabel(danger);
                int labelColor = dangerColors.get() ? getDangerTextColor(danger) : textColor.get().getPacked();
                AlienRender3DUtil.drawText3D(
                    label,
                    Vec3.atCenterOf(pos).add(0, 0.85, 0),
                    2.0, 0.5, Double.MAX_VALUE,
                    labelColor
                );
            }
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        AlienRender3DUtil.renderDeferred();
    }

    // ══════════════════════════════════════════════
    //  3D 八面体（水晶/钻石形状）— 8条棱，醒目且别致
    // ══════════════════════════════════════════════

    private void drawBlockOutline(Render3DEvent event, BlockPos pos, Color color) {
        Vec3 c = Vec3.atCenterOf(pos);

        // 6个顶点：6个面的中心
        Vec3 top    = c.add(0,   0.5, 0);
        Vec3 bottom = c.add(0,  -0.5, 0);
        Vec3 north  = c.add(0,   0,  -0.5);
        Vec3 south  = c.add(0,   0,   0.5);
        Vec3 east   = c.add( 0.5, 0,   0);
        Vec3 west   = c.add(-0.5, 0,   0);

        // 上顶点 → 4个侧顶点
        event.renderer.line(top.x, top.y, top.z, north.x, north.y, north.z, color);
        event.renderer.line(top.x, top.y, top.z, south.x, south.y, south.z, color);
        event.renderer.line(top.x, top.y, top.z, east.x,  east.y,  east.z,  color);
        event.renderer.line(top.x, top.y, top.z, west.x,  west.y,  west.z,  color);

        // 下顶点 → 4个侧顶点
        event.renderer.line(bottom.x, bottom.y, bottom.z, north.x, north.y, north.z, color);
        event.renderer.line(bottom.x, bottom.y, bottom.z, south.x, south.y, south.z, color);
        event.renderer.line(bottom.x, bottom.y, bottom.z, east.x,  east.y,  east.z,  color);
        event.renderer.line(bottom.x, bottom.y, bottom.z, west.x,  west.y,  west.z,  color);
    }

    // ══════════════════════════════════════════════
    //  方向箭头
    // ══════════════════════════════════════════════

    private void drawArrow(Render3DEvent event, BlockPos pos, Direction dir, Color color) {
        Vec3 center = Vec3.atCenterOf(pos);
        double dx = dir.getStepX();
        double dy = dir.getStepY();
        double dz = dir.getStepZ();

        double shaftLen = 0.5;
        double headLen = 0.2;
        double headW = 0.12;

        double mx = center.x + dx * shaftLen;
        double my = center.y + dy * shaftLen;
        double mz = center.z + dz * shaftLen;

        double tx = center.x + dx * (shaftLen + headLen);
        double ty = center.y + dy * (shaftLen + headLen);
        double tz = center.z + dz * (shaftLen + headLen);

        // 箭杆
        event.renderer.line(center.x, center.y, center.z, mx, my, mz, color);

        // 计算垂直于箭杆方向的基向量（用于箭头锥体）
        Vec3 dirVec = new Vec3(dx, dy, dz);
        Vec3 up;
        if (Math.abs(dy) < 0.9) {
            up = new Vec3(0, 1, 0).cross(dirVec).normalize();
        } else {
            up = new Vec3(1, 0, 0).cross(dirVec).normalize();
        }
        Vec3 right = dirVec.cross(up).normalize();

        // 箭头底部四角
        double hx = tx - dx * headLen;
        double hy = ty - dy * headLen;
        double hz = tz - dz * headLen;

        Vec3[] base = new Vec3[]{
            new Vec3(hx + right.x * headW + up.x * headW, hy + right.y * headW + up.y * headW, hz + right.z * headW + up.z * headW),
            new Vec3(hx - right.x * headW + up.x * headW, hy - right.y * headW + up.y * headW, hz - right.z * headW + up.z * headW),
            new Vec3(hx - right.x * headW - up.x * headW, hy - right.y * headW - up.y * headW, hz - right.z * headW - up.z * headW),
            new Vec3(hx + right.x * headW - up.x * headW, hy + right.y * headW - up.y * headW, hz + right.z * headW - up.z * headW),
        };

        // 箭头顶端 → 底部四角
        for (Vec3 b : base) {
            event.renderer.line(tx, ty, tz, b.x, b.y, b.z, color);
        }
        // 底部四角连线（形成菱形底面）
        for (int i = 0; i < 4; i++) {
            Vec3 a = base[i];
            Vec3 b = base[(i + 1) % 4];
            event.renderer.line(a.x, a.y, a.z, b.x, b.y, b.z, color);
        }
    }

    // ══════════════════════════════════════════════
    //  颜色 & 文字
    // ══════════════════════════════════════════════

    private Color getSphereColor(LavaDanger danger) {
        if (!dangerColors.get()) return toMeteor(sphereColor.get());
        return getLevelColor(danger);
    }

    private Color getLevelColor(LavaDanger danger) {
        double d = danger.distance;
        boolean hasSource = danger.lavaSourceCount > 0;

        if (hasSource && d <= 4) return COLOR_DANGER;
        if (d <= 4) return COLOR_WARNING;
        if (hasSource) return COLOR_CAUTION;
        return COLOR_SAFE;
    }

    private int getDangerTextColor(LavaDanger danger) {
        Color c = getLevelColor(danger);
        return c.getPacked();
    }

    private String getDangerLabel(LavaDanger danger) {
        if (!dangerColors.get()) return "岩浆!";

        double d = danger.distance;
        boolean hasSource = danger.lavaSourceCount > 0;

        if (hasSource && d <= 4) return LABEL_DANGER;
        if (d <= 4) return LABEL_WARNING;
        if (hasSource) return LABEL_SOURCE;
        return LABEL_FLOW;
    }

    // ── 工具 ──

    private static Color toMeteor(SettingColor c) {
        return new Color(c.r, c.g, c.b, c.a);
    }

    // ══════════════════════════════════════════════
    //  内部数据类
    // ══════════════════════════════════════════════

    private static class LavaDanger {
        final Set<Direction> lavaDirections;
        final int lavaSourceCount;
        final double distance;

        LavaDanger(Set<Direction> lavaDirections, int lavaSourceCount, double distance) {
            this.lavaDirections = lavaDirections;
            this.lavaSourceCount = lavaSourceCount;
            this.distance = distance;
        }
    }
}
