package com.codigohasta.addon.modules;

import net.minecraft.util.math.Vec3d;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class ShieldESP extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("渲染外观");
    private final SettingGroup sgColors = settings.createGroup("颜色设置");

    // --- 通用设置 ---
    private final Setting<Boolean> renderSelf = sgGeneral.add(new BoolSetting.Builder()
        .name("显示自己")
        .description("是否渲染自己的盾牌防御范围。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> radius = sgGeneral.add(new DoubleSetting.Builder()
        .name("半径")
        .description("防御扇形的半径大小。")
        .defaultValue(1.5)
        .min(0.5)
        .sliderMax(3.0)
        .build()
    );

    private final Setting<Double> height = sgGeneral.add(new DoubleSetting.Builder()
        .name("高度偏移")
        .description("扇形渲染的高度位置（相对于脚底）。")
        .defaultValue(0.1)
        .min(0.0)
        .sliderMax(2.0)
        .build()
    );

    // --- 渲染设置 ---
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("渲染模式")
        .description("如何显示扇形区域。")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Integer> segments = sgRender.add(new IntSetting.Builder()
        .name("平滑度")
        .description("扇形边缘的平滑程度（段数）。")
        .defaultValue(30)
        .min(10)
        .max(60)
        .build()
    );

    // --- 颜色设置 (他人) ---
    private final Setting<SettingColor> otherSideColor = sgColors.add(new ColorSetting.Builder()
        .name("他人-填充颜色")
        .description("其他玩家扇形内部的颜色。")
        .defaultValue(new SettingColor(255, 0, 0, 60)) // 默认红色半透明
        .visible(() -> shapeMode.get() != ShapeMode.Lines)
        .build()
    );

    private final Setting<SettingColor> otherLineColor = sgColors.add(new ColorSetting.Builder()
        .name("他人-线条颜色")
        .description("其他玩家扇形边缘的颜色。")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .visible(() -> shapeMode.get() != ShapeMode.Sides)
        .build()
    );

    // --- 颜色设置 (自己) ---
    private final Setting<SettingColor> selfSideColor = sgColors.add(new ColorSetting.Builder()
        .name("自己-填充颜色")
        .description("自己扇形内部的颜色。")
        .defaultValue(new SettingColor(0, 100, 255, 60)) // 默认蓝色半透明
        .visible(() -> renderSelf.get() && shapeMode.get() != ShapeMode.Lines)
        .build()
    );

    private final Setting<SettingColor> selfLineColor = sgColors.add(new ColorSetting.Builder()
        .name("自己-线条颜色")
        .description("自己扇形边缘的颜色。")
        .defaultValue(new SettingColor(0, 100, 255, 255))
        .visible(() -> renderSelf.get() && shapeMode.get() != ShapeMode.Sides)
        .build()
    );

    public ShieldESP() {
        super(AddonTemplate.CATEGORY, "ShieldESP", "渲染敌人（或自己）举盾时的防御范围(180度扇形)。");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        for (PlayerEntity player : mc.world.getPlayers()) {
            // 如果玩家是自己，且“显示自己”未开启，则跳过
            if (player == mc.player && !renderSelf.get()) continue;

            if (player.isBlocking()) {
                renderShieldArc(event, player);
            }
        }
    }

    private void renderShieldArc(Render3DEvent event, PlayerEntity player) {
        // 判断是自己还是别人，选择对应的颜色
        boolean isMe = (player == mc.player);
        SettingColor currentSideColor = isMe ? selfSideColor.get() : otherSideColor.get();
        SettingColor currentLineColor = isMe ? selfLineColor.get() : otherLineColor.get();

        // 坐标计算
        double x = MathHelper.lerp(event.tickDelta, player.lastRenderX, player.getX());
        double y = MathHelper.lerp(event.tickDelta, player.lastRenderY, player.getY()) + height.get();
        double z = MathHelper.lerp(event.tickDelta, player.lastRenderZ, player.getZ());

        float yaw = MathHelper.lerp(event.tickDelta, player.lastBodyYaw, player.bodyYaw);

        List<Vec3d> points = new ArrayList<>();
        Vec3d center = new Vec3d(x, y, z);
        points.add(center);

        int segs = segments.get();
        double r = radius.get();

        // 生成扇形点
        for (int i = 0; i <= segs; i++) {
            double offsetDeg = -90.0 + (180.0 * i / segs);
            double radians = Math.toRadians(yaw + offsetDeg + 180);
            
            double px = x + Math.sin(radians) * r;
            double pz = z - Math.cos(radians) * r;

            points.add(new Vec3d(px, y, pz));
        }

        // 绘制填充
        if (shapeMode.get() != ShapeMode.Lines) {
            for (int i = 1; i < points.size() - 1; i++) {
                Vec3d p1 = points.get(i);
                Vec3d p2 = points.get(i + 1);
                
                // 正面
                event.renderer.quad(
                    center.x, center.y, center.z,
                    p1.x, p1.y, p1.z,
                    p2.x, p2.y, p2.z,
                    center.x, center.y, center.z,
                    currentSideColor
                );
                // 反面
                event.renderer.quad(
                    center.x, center.y, center.z,
                    p2.x, p2.y, p2.z,
                    p1.x, p1.y, p1.z,
                    center.x, center.y, center.z,
                    currentSideColor
                );
            }
        }

        // 绘制线条
        if (shapeMode.get() != ShapeMode.Sides) {
            Vec3d start = points.get(1);
            Vec3d end = points.get(points.size() - 1);
            
            event.renderer.line(center.x, center.y, center.z, start.x, start.y, start.z, currentLineColor);
            event.renderer.line(center.x, center.y, center.z, end.x, end.y, end.z, currentLineColor);

            for (int i = 1; i < points.size() - 1; i++) {
                Vec3d p1 = points.get(i);
                Vec3d p2 = points.get(i + 1);
                event.renderer.line(p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, currentLineColor);
            }
        }
    }
}