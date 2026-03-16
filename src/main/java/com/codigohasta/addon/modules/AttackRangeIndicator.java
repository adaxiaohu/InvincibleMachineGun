package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AttackRangeIndicator extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("渲染设置");
    private final SettingGroup sgColors = settings.createGroup("颜色设置");

    // --- 通用设置 ---
    private final Setting<Double> attackRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("攻击距离")
        .description("实际的攻击触达距离（在此范围内变色）。")
        .defaultValue(3.0)
        .min(0)
        .sliderMax(6.0)
        .build()
    );

    private final Setting<Double> warningRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("预警距离")
        .description("开始显示预警的距离。")
        .defaultValue(10.0)
        .min(0)
        .sliderMax(20.0)
        .build()
    );
    
    // --- 渲染设置 ---
    private final Setting<Integer> maxTargets = sgRender.add(new IntSetting.Builder()
        .name("最大渲染数量")
        .description("限制同时渲染框的目标数量，防止在刷怪塔等地卡顿 (0为不限制)。")
        .defaultValue(10)
        .min(0)
        .sliderMax(50)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("形状模式")
        .description("渲染框和圆环的显示方式。")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Boolean> showWarning = sgRender.add(new BoolSetting.Builder()
        .name("显示预警框")
        .description("当目标在预警范围内但超出攻击距离时，是否显示。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> renderSelf = sgRender.add(new BoolSetting.Builder()
        .name("显示自身圆环")
        .description("在自己脚下渲染一个圆环，指示当前的攻击范围。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> renderTargetCircle = sgRender.add(new BoolSetting.Builder()
        .name("显示目标脚底圆环")
        .description("在目标的脚底额外渲染一个圆环。")
        .defaultValue(true)
        .build()
    );

    // --- 颜色设置 ---
    
    // 1. 攻击范围内 (Box)
    private final Setting<SettingColor> inRangeSideColor = sgColors.add(new ColorSetting.Builder()
        .name("攻击范围-方框填充")
        .description("目标在攻击范围内时的方框填充颜色。")
        .defaultValue(new SettingColor(0, 255, 0, 40))
        .build()
    );

    private final Setting<SettingColor> inRangeLineColor = sgColors.add(new ColorSetting.Builder()
        .name("攻击范围-方框边框")
        .description("目标在攻击范围内时的方框线条颜色。")
        .defaultValue(new SettingColor(0, 255, 0, 200))
        .build()
    );

    // 2. 预警范围内 (Box)
    private final Setting<SettingColor> warningSideColor = sgColors.add(new ColorSetting.Builder()
        .name("预警范围-方框填充")
        .description("目标在预警范围内时的方框填充颜色。")
        .defaultValue(new SettingColor(255, 0, 0, 40))
        .build()
    );

    private final Setting<SettingColor> warningLineColor = sgColors.add(new ColorSetting.Builder()
        .name("预警范围-方框边框")
        .description("目标在预警范围内时的方框线条颜色。")
        .defaultValue(new SettingColor(255, 0, 0, 200))
        .build()
    );
    
    // 3. 目标脚底圆环
    private final Setting<SettingColor> targetCircleInRangeColor = sgColors.add(new ColorSetting.Builder()
        .name("目标圆环-可攻击颜色")
        .description("目标在攻击范围内时，脚底圆环的颜色。")
        .defaultValue(new SettingColor(0, 255, 0, 150))
        .build()
    );
    
    private final Setting<SettingColor> targetCircleWarningColor = sgColors.add(new ColorSetting.Builder()
        .name("目标圆环-预警颜色")
        .description("目标在预警范围内时，脚底圆环的颜色。")
        .defaultValue(new SettingColor(255, 0, 0, 150))
        .build()
    );

    // 4. 自身圆环
    private final Setting<SettingColor> selfSideColor = sgColors.add(new ColorSetting.Builder()
        .name("自身圆环-填充颜色")
        .defaultValue(new SettingColor(100, 100, 255, 25)) 
        .build()
    );

    private final Setting<SettingColor> selfLineColor = sgColors.add(new ColorSetting.Builder()
        .name("自身圆环-边框颜色")
        .defaultValue(new SettingColor(100, 100, 255, 150))
        .build()
    );

    public AttackRangeIndicator() {
        super(AddonTemplate.CATEGORY, "攻击范围指示器", "可视化显示攻击范围、预警距离以及自身攻击半径。");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        // 1. 渲染自身圆环
        if (renderSelf.get()) {
            drawSelfCircle(event);
        }

        // 2. 收集并筛选实体
        List<Entity> targets = new ArrayList<>();
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof LivingEntity && entity != mc.player) {
                targets.add(entity);
            }
        }

        // 3. 距离排序
        targets.sort(Comparator.comparingDouble(e -> mc.player.distanceTo(e)));

        // 4. 遍历渲染
        int renderedCount = 0;
        int limit = maxTargets.get();

        for (Entity entity : targets) {
            if (limit > 0 && renderedCount >= limit) break;

            double distance = mc.player.distanceTo(entity);

            if (distance <= attackRange.get()) {
                drawEntityBox(event, entity, inRangeSideColor.get(), inRangeLineColor.get());
                if (renderTargetCircle.get()) {
                    drawTargetFeetCircle(event, entity, targetCircleInRangeColor.get());
                }
                renderedCount++;
            } 
            else if (showWarning.get() && distance <= warningRange.get()) {
                drawEntityBox(event, entity, warningSideColor.get(), warningLineColor.get());
                if (renderTargetCircle.get()) {
                    drawTargetFeetCircle(event, entity, targetCircleWarningColor.get());
                }
                renderedCount++;
            }
        }
    }

    private void drawEntityBox(Render3DEvent event, Entity entity, SettingColor sideColor, SettingColor lineColor) {
        double x = MathHelper.lerp(event.tickDelta, entity.lastRenderX, entity.getX());
        double y = MathHelper.lerp(event.tickDelta, entity.lastRenderY, entity.getY());
        double z = MathHelper.lerp(event.tickDelta, entity.lastRenderZ, entity.getZ());

        float w = entity.getWidth() / 2.0f;
        float h = entity.getHeight();

        net.minecraft.util.math.Box interpolatedBox = new net.minecraft.util.math.Box(
            x - w, y, z - w, 
            x + w, y + h, z + w
        );

        event.renderer.box(
            interpolatedBox, 
            sideColor, 
            lineColor, 
            shapeMode.get(), 
            0
        );
    }
    
    private void drawTargetFeetCircle(Render3DEvent event, Entity entity, SettingColor color) {
        double x = MathHelper.lerp(event.tickDelta, entity.lastRenderX, entity.getX());
        double y = MathHelper.lerp(event.tickDelta, entity.lastRenderY, entity.getY());
        double z = MathHelper.lerp(event.tickDelta, entity.lastRenderZ, entity.getZ());
        double radius = entity.getWidth() * 0.8;
        
        renderCircleManual(event, x, y, z, radius, color, color, shapeMode.get());
    }

    private void drawSelfCircle(Render3DEvent event) {
        double x = MathHelper.lerp(event.tickDelta, mc.player.lastRenderX, mc.player.getX());
        double y = MathHelper.lerp(event.tickDelta, mc.player.lastRenderY, mc.player.getY());
        double z = MathHelper.lerp(event.tickDelta, mc.player.lastRenderZ, mc.player.getZ());

        renderCircleManual(event, x, y, z, attackRange.get(), selfSideColor.get(), selfLineColor.get(), shapeMode.get());
    }

    private void renderCircleManual(Render3DEvent event, double x, double y, double z, double radius, SettingColor sideColor, SettingColor lineColor, ShapeMode mode) {
        int segments = 40;
        double step = (Math.PI * 2) / segments;
        
        double prevX = x + Math.cos(0) * radius;
        double prevZ = z + Math.sin(0) * radius;
        
        for (int i = 1; i <= segments; i++) {
            double angle = i * step;
            double currX = x + Math.cos(angle) * radius;
            double currZ = z + Math.sin(angle) * radius;
            
            // 修复点：使用 quad (四边形) 来模拟三角形
            // 通过重复最后一个点 (currX, y, currZ)，让四边形变成三角形
            if (mode == ShapeMode.Sides || mode == ShapeMode.Both) {
                event.renderer.quad(
                    x, y, z,        // 中心点
                    prevX, y, prevZ, // 上一个点
                    currX, y, currZ, // 当前点
                    currX, y, currZ, // 重复当前点 (闭合为三角形)
                    sideColor
                );
            }
            
            if (mode == ShapeMode.Lines || mode == ShapeMode.Both) {
                event.renderer.line(prevX, y, prevZ, currX, y, currZ, lineColor);
            }
            
            prevX = currX;
            prevZ = currZ;
        }
    }
}