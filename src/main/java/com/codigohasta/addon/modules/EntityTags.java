package com.codigohasta.addon.modules;

import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d; 
import org.joml.Vector3d;

import com.codigohasta.addon.AddonTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class EntityTags extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("渲染");
    private final SettingGroup sgCluster = settings.createGroup("聚合");

    // --- 通用设置 ---
    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("目标实体")
        .description("选择要显示标签的实体类型。")
        .defaultValue(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER)
        .build()
    );

    private final Setting<Boolean> displayHealth = sgGeneral.add(new BoolSetting.Builder()
        .name("显示血量")
        .description("显示实体的生命值。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> displayDistance = sgGeneral.add(new BoolSetting.Builder()
        .name("显示距离")
        .description("显示到实体的距离。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> displayItems = sgGeneral.add(new BoolSetting.Builder()
        .name("显示装备")
        .description("在标签上方显示装备信息。")
        .defaultValue(true)
        .build()
    );

    // --- 渲染设置 ---
    private final Setting<Double> scale = sgRender.add(new DoubleSetting.Builder()
        .name("标签缩放")
        .description("标签的大小缩放。")
        .defaultValue(1.0)
        .min(0.1)
        .build()
    );

    private final Setting<Double> yOffset = sgRender.add(new DoubleSetting.Builder()
        .name("Y轴偏移")
        .description("标签在实体上方的偏移量。")
        .defaultValue(0.5)
        .sliderRange(0.0, 2.0)
        .build()
    );

    private final Setting<SettingColor> bgColor = sgRender.add(new ColorSetting.Builder()
        .name("背景颜色")
        .description("标签的背景颜色。")
        .defaultValue(new SettingColor(0, 0, 0, 75))
        .build()
    );

    // --- 聚合设置 ---
    private final Setting<Boolean> enableClustering = sgCluster.add(new BoolSetting.Builder()
        .name("启用合并")
        .description("远距离时合并相同实体的标签。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> clusterPlayerDistance = sgCluster.add(new DoubleSetting.Builder()
        .name("触发聚合距离")
        .description("距离玩家多远时开始合并标签。")
        .defaultValue(15.0)
        .min(0.0)
        .sliderMax(50.0)
        .visible(enableClustering::get)
        .build()
    );

    private final Setting<Double> clusterEntityDistance = sgCluster.add(new DoubleSetting.Builder()
        .name("实体间距阈值")
        .description("实体间距离多近才能合并。")
        .defaultValue(3.0)
        .min(0.1)
        .sliderMax(10.0)
        .visible(enableClustering::get)
        .build()
    );

    private final Setting<Integer> clusterMinCount = sgCluster.add(new IntSetting.Builder()
        .name("最小聚合数量")
        .description("达到多少数量才进行合并。")
        .defaultValue(2)
        .min(2)
        .sliderMax(10)
        .visible(enableClustering::get)
        .build()
    );

    private final Vector3d pos = new Vector3d();
    private final Color WHITE = new Color(255, 255, 255);
    private final Color GREEN = new Color(25, 252, 25);
    private final Color AMBER = new Color(255, 105, 25);
    private final Color RED = new Color(255, 25, 25);

    public EntityTags() {
        super(AddonTemplate.CATEGORY, "实体标签+", "在实体头顶显示标签，看他的血量距离和实体名称");
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.world == null || mc.player == null) return;

        List<EntityCluster> clusters = new ArrayList<>();

        for (Entity entity : mc.world.getEntities()) {
            if (entity.getType() == EntityType.PLAYER) continue;
            if (!entities.get().contains(entity.getType())) continue;

            double distToPlayer = PlayerUtils.distanceToCamera(entity);
            boolean shouldCluster = enableClustering.get() && distToPlayer > clusterPlayerDistance.get();
            boolean added = false;

            if (shouldCluster) {
                for (EntityCluster cluster : clusters) {
                    Vec3d entityPos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
                    if (cluster.type == entity.getType() && cluster.center.distanceTo(entityPos) <= clusterEntityDistance.get()) {
                        cluster.add(entity);
                        added = true;
                        break;
                    }
                }
            }

            if (!added) {
                clusters.add(new EntityCluster(entity));
            }
        }

        for (EntityCluster cluster : clusters) {
            if (enableClustering.get() && cluster.entities.size() >= clusterMinCount.get()) {
                renderMergedTag(event, cluster);
            } else {
                for (Entity entity : cluster.entities) {
                    renderIndividualTag(event, entity);
                }
            }
        }
    }

    private void renderMergedTag(Render2DEvent event, EntityCluster cluster) {
        Entity first = cluster.entities.get(0);
        pos.set(cluster.center.getX(), cluster.center.getY() + first.getEyeHeight(first.getPose()) + yOffset.get(), cluster.center.getZ());

        if (!NametagUtils.to2D(pos, scale.get())) return;

        TextRenderer text = TextRenderer.get();
        NametagUtils.begin(pos, event.drawContext);

        String nameText = first.getType().getName().getString() + " x" + cluster.entities.size();
        if (displayDistance.get()) {
            double dist = Math.round(PlayerUtils.distanceToCamera(cluster.center.x, cluster.center.y, cluster.center.z) * 10.0) / 10.0;
            nameText += " (" + dist + "m)";
        }

        double width = text.getWidth(nameText, true);
        double height = text.getHeight(true);
        double widthHalf = width / 2;

        drawBg(-widthHalf, -height, width, height);

        text.beginBig();
        text.render(nameText, -widthHalf, -height, WHITE, true);
        text.end();

        NametagUtils.end(event.drawContext);
    }

    private void renderIndividualTag(Render2DEvent event, Entity entity) {
        Utils.set(pos, entity, event.tickDelta);
        pos.add(0.0, entity.getEyeHeight(entity.getPose()) + yOffset.get(), 0.0);

        if (!NametagUtils.to2D(pos, scale.get())) return;

        TextRenderer text = TextRenderer.get();
        NametagUtils.begin(pos, event.drawContext);

        // 1. 准备文字
        String nameText = entity.getType().getName().getString();
        String healthText = "";
        String distText = "";
        Color healthColor = WHITE;

        double totalWidth = text.getWidth(nameText, true);

        if (displayHealth.get() && entity instanceof LivingEntity living) {
            float health = living.getHealth() + living.getAbsorptionAmount();
            float maxHealth = living.getMaxHealth() + living.getAbsorptionAmount();
            healthText = " " + Math.round(health);

            double hpPercent = health / maxHealth;
            if (hpPercent <= 0.333) healthColor = RED;
            else if (hpPercent <= 0.666) healthColor = AMBER;
            else healthColor = GREEN;

            totalWidth += text.getWidth(healthText, true);
        }

        if (displayDistance.get()) {
            double dist = Math.round(PlayerUtils.distanceToCamera(entity) * 10.0) / 10.0;
            distText = " " + dist + "m";
            totalWidth += text.getWidth(distText, true);
        }

        double height = text.getHeight(true);
        double widthHalf = totalWidth / 2;

        // 2. 绘制背景
        drawBg(-widthHalf, -height, totalWidth, height);

        // 3. 绘制文字 (核心修复部分)
        text.beginBig();
        double hX = -widthHalf;
        
        // 渲染名称
        hX = text.render(nameText, hX, -height, WHITE, true);
        
        // 渲染血量 (独立判断，并更新 hX)
        if (displayHealth.get() && !healthText.isEmpty()) {
            hX = text.render(healthText, hX, -height, healthColor, true);
        }
        
        // 渲染距离 (独立判断，使用最新的 hX)
        if (displayDistance.get() && !distText.isEmpty()) {
            text.render(distText, hX, -height, WHITE, true);
        }
        
        text.end();

        // 4. 绘制装备
        if (displayItems.get() && entity instanceof LivingEntity living) {
            drawItems(event, living, -height, totalWidth);
        }

        NametagUtils.end(event.drawContext);
    }

    private void drawItems(Render2DEvent event, LivingEntity entity, double currentYOffset, double tagWidth) {
        List<ItemStack> equipment = new ArrayList<>();
        // 1.21.11 必须使用 getEquippedStack
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = entity.getEquippedStack(slot);
            if (!stack.isEmpty()) equipment.add(stack);
        }

        if (equipment.isEmpty()) return;

        double itemSpacing = 2.0;
        double itemSize = 32.0; 
        double totalItemWidth = (equipment.size() * itemSize) + ((equipment.size() - 1) * itemSpacing);
        double startX = -(totalItemWidth / 2);
        double startY = currentYOffset - itemSize - 5;

        for (int i = 0; i < equipment.size(); i++) {
            ItemStack stack = equipment.get(i);
            double x = startX + i * (itemSize + itemSpacing);
            RenderUtils.drawItem(event.drawContext, stack, (int) x, (int) startY, 2, true, null, false);
        }
    }

    private void drawBg(double x, double y, double width, double height) {
        Renderer2D.COLOR.begin();
        Renderer2D.COLOR.quad(x - 1.0, y - 1.0, width + 2.0, height + 2.0, bgColor.get());
        Renderer2D.COLOR.render(); 
    }

    private static class EntityCluster {
        EntityType<?> type;
        List<Entity> entities = new ArrayList<>();
        Vec3d center;

        EntityCluster(Entity first) {
            this.type = first.getType();
            this.entities.add(first);
            this.center = new Vec3d(first.getX(), first.getY(), first.getZ());
        }

        void add(Entity e) {
            entities.add(e);
            double sumX = 0, sumY = 0, sumZ = 0;
            for (Entity ent : entities) {
                sumX += ent.getX();
                sumY += ent.getY();
                sumZ += ent.getZ();
            }
            this.center = new Vec3d(sumX / entities.size(), sumY / entities.size(), sumZ / entities.size());
        }
    }
}