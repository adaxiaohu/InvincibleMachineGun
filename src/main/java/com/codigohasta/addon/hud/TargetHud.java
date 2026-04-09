//抄袭别人的，不会抄
package com.codigohasta.addon.hud;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

public class TargetHud extends HudElement {
    // 1.21.11 必须显式声明 mc
    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    public static final HudElementInfo<TargetHud> INFO = new HudElementInfo<>(AddonTemplate.HUD_GROUP, "img-target-hud", "1.21.11 兼容版目标面板", TargetHud::new);

    // 贴图路径 (匹配你的目录)
    private static final Identifier TARGET_TEX = Identifier.of("img", "textures/particles/target.png");

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgEsp = settings.createGroup("3D-ESP");

    private final Setting<Double> hudScale = sgGeneral.add(new DoubleSetting.Builder().name("scale").description("缩放").defaultValue(1.0).min(0.5).sliderMax(3.0).build());
    private final Setting<SettingColor> hudColor = sgGeneral.add(new ColorSetting.Builder().name("background").description("背景颜色").defaultValue(new SettingColor(30, 30, 30, 180)).build());
    private final Setting<SettingColor> accentColor = sgGeneral.add(new ColorSetting.Builder().name("accent-color").description("强调色").defaultValue(new SettingColor(255, 100, 100, 255)).build());

    private final Setting<Boolean> espEnabled = sgEsp.add(new BoolSetting.Builder().name("enabled").description("是否在目标处绘制3D旋转纹理").defaultValue(true).build());
    private final Setting<Double> espSize = sgEsp.add(new DoubleSetting.Builder().name("size").description("纹理大小").defaultValue(1.2).min(0.5).sliderMax(3.0).visible(espEnabled::get).build());
    private final Setting<Double> rotationSpeed = sgEsp.add(new DoubleSetting.Builder().name("rotation-speed").description("旋转速度").defaultValue(3.0).min(0.5).sliderMax(10.0).visible(espEnabled::get).build());

    private LivingEntity target;
    private double animatedHealth = 0.0; // 1.21.11 规范: double
    private float rotation = 0f;

    public TargetHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        updateTarget();

        if (target == null) {
            animatedHealth = 0.0;
            return;
        }

        double w = 160 * hudScale.get();
        double h = 50 * hudScale.get();
        box.setSize(w, h);

        // 1. 绘制背景 (纯净 Meteor 原生渲染)
        renderer.quad(x, y, w, h, hudColor.get());
        renderer.quad(x, y, 2 * hudScale.get(), h, accentColor.get());

        // 血条平滑动画 (1.21.11 要求 lerp 参数为 double)
        animatedHealth = MathHelper.lerp(0.1, animatedHealth, (double) target.getHealth());
        
        double avatarSize = 35 * hudScale.get();
        double padding = 7 * hudScale.get();

        // 2. 绘制头像占位符 (规避 InventoryScreen 崩溃的现代解法)
        double avatarX = x + padding;
        double avatarY = y + padding;
        renderer.quad(avatarX, avatarY, avatarSize, avatarSize, new Color(60, 60, 60, 200));
        String initial = target.getName().getString().substring(0, 1).toUpperCase();
        renderer.text(initial, avatarX + avatarSize / 2 - renderer.textWidth(initial) / 2, avatarY + avatarSize / 2 - renderer.textHeight() / 2, Color.WHITE, true);

        // 3. 绘制名字 (Record化)
        String name = target.getName().getString();
        if (name.length() > 14) name = name.substring(0, 14) + "...";
        double textX = avatarX + avatarSize + padding;
        renderer.text(name, textX, y + padding, Color.WHITE, true);

        // 4. 绘制血条背景
        double barY = y + h - padding - 8 * hudScale.get();
        double barW = w - avatarSize - padding * 3;
        double barH = 8 * hudScale.get();
        renderer.quad(textX, barY, barW, barH, new Color(50, 50, 50, 200));

        // 5. 动态血量进度条
        double healthPercent = MathHelper.clamp(animatedHealth / target.getMaxHealth(), 0.0, 1.0);
        Color hpColor = healthPercent > 0.5 ? new Color(100, 255, 100) : (healthPercent > 0.2 ? new Color(255, 200, 50) : new Color(255, 80, 80));
        renderer.quad(textX, barY, barW * healthPercent, barH, hpColor);

        // 血量数字
        String healthText = String.format("%.0f%%", healthPercent * 100);
        double textW = renderer.textWidth(healthText);
        renderer.text(healthText, textX + (barW - textW) / 2, barY - 1, Color.BLACK, false);
    }

    private void updateTarget() {
        KillAura aura = Modules.get().get(KillAura.class);
        
        // 1. 优先获取 KillAura 目标
        if (aura != null && aura.isActive() && aura.getTarget() instanceof LivingEntity living) {
            target = living;
            return;
        } 
        
        // 2. 其次获取准星对准的目标 (完美替代 CrystalAura 报错的获取方式)
        if (mc.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity living) {
            target = living;
            return;
        }

        target = null;
    }

    /**
     * 1.21.11 现代 3D 渲染规范 (彻底抛弃 RenderSystem 和 Tessellator)
     */
   @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!espEnabled.get() || target == null) return;

        rotation -= rotationSpeed.get().floatValue();
        if (rotation <= -360f) rotation += 360f;

        double tickDelta = (double) event.tickDelta;
        
        // 计算相机相对坐标
        double tx = MathHelper.lerp(tickDelta, target.lastRenderX, target.getX()) - event.offsetX;
        double ty = MathHelper.lerp(tickDelta, target.lastRenderY, target.getY()) - event.offsetY;
        double tz = MathHelper.lerp(tickDelta, target.lastRenderZ, target.getZ()) - event.offsetZ;

        MatrixStack matrices = new MatrixStack();
        matrices.push();
        
        // 【修改1】将高度改为 target.getHeight() + 0.8，悬浮在头顶上方，防止被模型挡住
        matrices.translate(tx, ty + target.getHeight() + 0.8, tz);
        
        // 始终面向玩家摄像机
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));
        // Z轴自转动画
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation));

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float size = espSize.get().floatValue() * 0.5f;

        // 获取渲染层
        var layer = net.minecraft.client.render.RenderLayers.entityTranslucent(TARGET_TEX);
        var provider = mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer consumer = provider.getBuffer(layer);

        // 写入顶点
        int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        int overlay = OverlayTexture.DEFAULT_UV;

        consumer.vertex(matrix, -size, -size, 0).color(255, 255, 255, 255).texture(0f, 0f).overlay(overlay).light(light).normal(0, 1, 0);
        consumer.vertex(matrix, -size, size, 0).color(255, 255, 255, 255).texture(0f, 1f).overlay(overlay).light(light).normal(0, 1, 0);
        consumer.vertex(matrix, size, size, 0).color(255, 255, 255, 255).texture(1f, 1f).overlay(overlay).light(light).normal(0, 1, 0);
        consumer.vertex(matrix, size, -size, 0).color(255, 255, 255, 255).texture(1f, 0f).overlay(overlay).light(light).normal(0, 1, 0);

        matrices.pop();

        // 【修改2 核心机制】强制立即渲染这个图层！如果不加这行，它可能不会被画出来！
        provider.draw(layer);
    }
}