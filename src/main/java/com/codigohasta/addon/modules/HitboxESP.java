package com.codigohasta.addon.modules;

import net.minecraft.util.math.Vec3d;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

public class HitboxESP extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("颜色设置");

    // --- 通用设置 ---
    private final Setting<Boolean> ignoreSelf = sgGeneral.add(new BoolSetting.Builder()
        .name("忽略自己")
        .description("不渲染自己的碰撞箱。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> lineWidth = sgGeneral.add(new DoubleSetting.Builder()
        .name("线条宽度")
        .defaultValue(2.5)
        .min(0.1)
        .max(5.0)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("渲染模式")
        .description("选择线框、填充或两者都显示。")
        .defaultValue(ShapeMode.Lines)
        .build()
    );

    // --- 颜色设置 ---
    private final Setting<SettingColor> sideColor = sgColors.add(new ColorSetting.Builder()
        .name("填充颜色")
        .description("碰撞箱内部的填充颜色。")
        .defaultValue(new SettingColor(255, 255, 255, 30))
        .visible(() -> shapeMode.get() != ShapeMode.Lines)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgColors.add(new ColorSetting.Builder()
        .name("线条颜色")
        .description("碰撞箱边缘线条的颜色。")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .visible(() -> shapeMode.get() != ShapeMode.Sides)
        .build()
    );

    public HitboxESP() {
        super(AddonTemplate.CATEGORY, "HitboxESP", "显示玩家的碰撞箱(Hitbox)。");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.world == null) return;

        for (PlayerEntity player : mc.world.getPlayers()) {
            // 过滤自己
            if (ignoreSelf.get() && player == mc.player) continue;
            
            // 过滤观察者模式玩家 (通常不需要看他们的碰撞箱)
            if (player.isSpectator()) continue;

            renderHitbox(event, player);
        }
    }

    private void renderHitbox(Render3DEvent event, PlayerEntity entity) {
        // --- 核心逻辑：插值计算 ---
        // Minecraft 的逻辑运行在 20 TPS (每0.05秒一次)，但渲染FPS通常远高于此。
        // 直接用 entity.getX() 会导致画面看起来一卡一卡的。
        // 我们必须使用 lastRenderX 和 当前X 结合 tickDelta 进行插值，算出当前帧的“平滑位置”。
        
        double x = MathHelper.lerp(event.tickDelta, entity.lastRenderX, entity.getX());
        double y = MathHelper.lerp(event.tickDelta, entity.lastRenderY, entity.getY());
        double z = MathHelper.lerp(event.tickDelta, entity.lastRenderZ, entity.getZ());

        // 获取实体的长宽
        // getBoundingBox() 获取的是 Tick 结束时的静态框，我们需要自己根据长宽构造一个动态框
        float width = entity.getWidth();
        float height = entity.getHeight();

        // 构造碰撞箱坐标
        // 实体坐标通常是脚底中心，所以 x 和 z 需要减去宽度的一半
        double minX = x - width / 2.0;
        double minY = y;
        double minZ = z - width / 2.0;
        double maxX = x + width / 2.0;
        double maxY = y + height;
        double maxZ = z + width / 2.0;

        // 渲染
        event.renderer.box(
            minX, minY, minZ,
            maxX, maxY, maxZ,
            sideColor.get(),
            lineColor.get(),
            shapeMode.get(),
            0
        );
    }
}