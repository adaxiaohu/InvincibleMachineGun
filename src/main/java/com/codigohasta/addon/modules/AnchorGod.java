package com.codigohasta.addon.modules;

import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import org.joml.Vector3d;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AnchorGod extends Module {

    private class AnchorPlacement {
        private BlockPos pos;
        private Vec3d vec;
        private BlockHitResult hitResult;

        public AnchorPlacement() {}

        public void set(BlockPos anchorPos, PlayerEntity target) {
            this.pos = anchorPos;
            this.vec = Vec3d.ofCenter(anchorPos);
            this.hitResult = new BlockHitResult(this.vec, Direction.UP, anchorPos, true);
        }

        public double getSelfDMG(PlayerEntity player) {
            return getAnchorDamage(player);
        }

        public double getTargetDMG(PlayerEntity target) {
            return getAnchorDamage(target);
        }

        public BlockPos getPos() { return this.pos; }
        public BlockHitResult getHitResult() { return this.hitResult; }

        // 修复：使用 DamageUtils.anchorDamage(LivingEntity, Vec3d)
        // 报错信息提示该方法存在且接受 (LivingEntity, Vec3d)
        // this.vec 在 set() 方法中已被初始化为 Vec3d.ofCenter(anchorPos)
        private double getAnchorDamage(PlayerEntity player) {
            if (player == null) return 0;
            return DamageUtils.anchorDamage(player, this.vec);
        }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("索敌");
    private final SettingGroup sgTurbo = settings.createGroup("涡轮");
    private final SettingGroup sgAutoRefill = settings.createGroup("自动补充");
    private final SettingGroup sgRender = settings.createGroup("渲染");

    // General
    private final Setting<Integer> placeDelay = sgGeneral.add(new IntSetting.Builder().name("放置延迟").description("放置重生锚之间的刻数。").defaultValue(9).sliderRange(0, 20).build());
    private final Setting<Integer> breakDelay = sgGeneral.add(new IntSetting.Builder().name("引爆延迟").description("引爆重生锚之间的刻数。").defaultValue(9).sliderRange(0, 20).build());
    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder().name("旋转").description("在方块交互时进行旋转。").defaultValue(false).build());
    private final Setting<Boolean> antiSuicide = sgGeneral.add(new BoolSetting.Builder().name("防止自杀").description("防止引爆会杀死你的重生锚。").defaultValue(true).build());

    // Targeting
    private final Setting<Double> targetRange = sgTargeting.add(new DoubleSetting.Builder().name("索敌范围").defaultValue(7).sliderRange(1, 15).build());
    private final Setting<Integer> xRadius = sgTargeting.add(new IntSetting.Builder().name("X轴半径").defaultValue(5).sliderRange(1, 9).build());
    private final Setting<Integer> yRadius = sgTargeting.add(new IntSetting.Builder().name("Y轴半径").defaultValue(4).sliderRange(1, 5).build());
    private final Setting<Double> placeRange = sgTargeting.add(new DoubleSetting.Builder().name("放置范围").defaultValue(4.5).sliderRange(1, 10).build());
    private final Setting<Boolean> flatMode = sgTargeting.add(new BoolSetting.Builder().name("平地模式").description("只在地面上放置。").defaultValue(false).build());
    private final Setting<Double> minTargetDamage = sgTargeting.add(new DoubleSetting.Builder().name("最低目标伤害").description("对目标造成的最低伤害。").defaultValue(5.2).range(0, 36).sliderMax(36).build());
    private final Setting<Double> maxSelfDamage = sgTargeting.add(new DoubleSetting.Builder().name("最高自我伤害").description("对自己造成的最高伤害。").defaultValue(4).range(0, 36).sliderMax(36).build());
    private final Setting<Boolean> popOverride = sgTargeting.add(new BoolSetting.Builder().name("破盾优先").description("当你可以击破目标图腾且不会被秒杀时，无视自我伤害。").defaultValue(true).build());
    private final Setting<Double> popOverrideHP = sgTargeting.add(new DoubleSetting.Builder().name("破盾后血量").description("无视自我伤害后你需要的血量。").defaultValue(4.5).min(0).sliderMax(36).build());
    private final Setting<Boolean> prediction = sgTargeting.add(new BoolSetting.Builder().name("移动预测").description("预测玩家下一刻的位置用于计算。").defaultValue(false).build());
    private final Setting<Integer> predictionTicks = sgTargeting.add(new IntSetting.Builder().name("预测刻数").description("要预测多少刻。").defaultValue(3).sliderRange(1, 5).visible(prediction::get).build());

    // Turbo
    private final Setting<Boolean> turbo = sgTurbo.add(new BoolSetting.Builder().name("涡轮模式").description("当目标可以被连续击破图腾时，加快放置/引爆速度。").defaultValue(false).build());
    private final Setting<Boolean> turboHoleCheck = sgTurbo.add(new BoolSetting.Builder().name("需要目标在洞里").description("要求目标在洞里。").defaultValue(true).build());
    private final Setting<Boolean> turboHoleCheckSelf = sgTurbo.add(new BoolSetting.Builder().name("需要自己在洞里").description("要求你在洞里。").defaultValue(false).build());
    private final Setting<Double> turboHP = sgTurbo.add(new DoubleSetting.Builder().name("涡轮激活血量").description("涡轮模式激活的目标血量。").defaultValue(10.5).min(0).sliderMax(36).build());
    private final Setting<Integer> turboSpeed = sgTurbo.add(new IntSetting.Builder().name("涡轮速度").description("涡轮模式激活时的放置和引爆速度。").defaultValue(4).sliderRange(0, 5).build());

    // Auto Refill
    private final Setting<Boolean> autoRefill = sgAutoRefill.add(new BoolSetting.Builder().name("自动补充").description("自动将所需材料移动到快捷栏。").defaultValue(true).build());
    private final Setting<Integer> anchorSlot = sgAutoRefill.add(new IntSetting.Builder().name("重生锚槽位").description("要将重生锚移动到的槽位。").defaultValue(7).min(1).max(9).sliderMin(1).sliderMax(9).visible(autoRefill::get).build());
    private final Setting<Integer> glowstoneSlot = sgAutoRefill.add(new IntSetting.Builder().name("荧石槽位").description("要将荧石移动到的槽位。").defaultValue(7).min(1).max(9).sliderMin(1).sliderMax(9).visible(autoRefill::get).build());

    // Render
    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder().name("渲染").description("渲染将要放置重生锚的位置。").defaultValue(true).build());
    private final Setting<Boolean> renderBreak = sgRender.add(new BoolSetting.Builder().name("渲染引爆").description("渲染将要引爆的重生锚位置。").defaultValue(true).build());
    private final Setting<Integer> renderTime = sgRender.add(new IntSetting.Builder().name("渲染时间").description("放置位置的渲染持续时间。").defaultValue(3).min(1).sliderMax(10).visible(render::get).build());
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>().name("形状模式").description("如何渲染形状。").defaultValue(ShapeMode.Both).build());
    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder().name("侧面颜色").description("放置位置的侧面颜色。").defaultValue(new SettingColor(255, 0, 170, 35)).build());
    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder().name("线条颜色").description("放置位置的线条颜色。").defaultValue(new SettingColor(255, 0, 170)).build());
    private final Setting<Boolean> renderDamage = sgRender.add(new BoolSetting.Builder().name("渲染伤害").description("渲染重生锚将造成的伤害。").defaultValue(true).build());
    private final Setting<SettingColor> damageColor = sgRender.add(new ColorSetting.Builder().name("伤害颜色").description("伤害文本的颜色。").defaultValue(new SettingColor(15, 255, 211)).build());
    private final Setting<Double> damageScale = sgRender.add(new DoubleSetting.Builder().name("伤害缩放").description("伤害文本的大小。").defaultValue(1.4).min(0).max(5.0).sliderMax(5.0).build());

    private PlayerEntity target;
    private AnchorPlacement placePos, breakPos;
    private int placeTimer, breakTimer;

    private final List<RenderBlock> renderBlocks = new ArrayList<>();
    private double bestDamage;

    public AnchorGod() {
        super(com.codigohasta.addon.AddonTemplate.CATEGORY, "锚神", "自动计算并使用重生锚攻击最佳位置。");
    }

    @Override
    public void onActivate() {
        target = null;
        placePos = null;
        breakPos = null;
        placeTimer = 0;
        breakTimer = breakDelay.get();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!render.get()) return;
        renderBlocks.forEach(renderBlock -> renderBlock.render(event, shapeMode.get()));
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!render.get() || !renderDamage.get() || renderBlocks.isEmpty()) return;

        RenderBlock block = renderBlocks.get(renderBlocks.size() - 1);
        if (block.pos == null) return;

        Vec3d textVec3d = new Vec3d(block.pos.getX() + 0.5, block.pos.getY() + 0.5, block.pos.getZ() + 0.5);
        Vector3d textVecJoml = new Vector3d(textVec3d.x, textVec3d.y, textVec3d.z);

        if (NametagUtils.to2D(textVecJoml, damageScale.get())) {
            NametagUtils.begin(textVecJoml);
            TextRenderer.get().begin(1.0, false, true);
            String damageText = String.format("%.1f", bestDamage);
            final double w = TextRenderer.get().getWidth(damageText) / 2.0;
            TextRenderer.get().render(damageText, -w, 0.0, damageColor.get());
            TextRenderer.get().end();
            NametagUtils.end();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        renderBlocks.forEach(RenderBlock::tick);
        renderBlocks.removeIf(renderBlock -> renderBlock.ticks <= 0);

        target = TargetUtils.getPlayerTarget(targetRange.get(), SortPriority.LowestDistance);
        if (TargetUtils.isBadTarget(target, targetRange.get())) return;

        // Auto Refill
        if (autoRefill.get()) {
            if (!InvUtils.findInHotbar(Items.GLOWSTONE).found()) {
                FindItemResult g2 = InvUtils.find(Items.GLOWSTONE);
                if (g2.found()) InvUtils.move().from(g2.slot()).toHotbar(glowstoneSlot.get() - 1);
            }
            if (!InvUtils.findInHotbar(Items.RESPAWN_ANCHOR).found()) {
                FindItemResult a2 = InvUtils.find(Items.RESPAWN_ANCHOR);
                if (a2.found()) InvUtils.move().from(a2.slot()).toHotbar(anchorSlot.get() - 1);
            }
        }

        if (placeTimer <= 0) {
            placePos = getPlacePos();
            placeAnchor(placePos);
            placeTimer = getPlaceDelay();
        } else {
            placeTimer--;
        }

        if (breakTimer <= 0) {
            if (breakPos != null && meteordevelopment.meteorclient.utils.world.BlockUtils.canPlace(breakPos.getPos(), true)) {
                breakAnchor(breakPos);
            } else {
                breakAnchor(findBreak());
            }
            breakTimer = getBreakDelay();
        } else {
            breakTimer--;
        }
    }

    private int getPlaceDelay() {
        if (target == null) return placeDelay.get();
        if (turbo.get()) {
            if (turboHoleCheck.get() && !PlayerUtils.isInHole(false)) return placeDelay.get();
            if (turboHoleCheckSelf.get() && !PlayerUtils.isInHole(true)) return placeDelay.get();
            if (EntityUtils.getTotalHealth(target) > turboHP.get()) return placeDelay.get();
            return turboSpeed.get();
        }
        return placeDelay.get();
    }

    private int getBreakDelay() {
        if (target == null) return breakDelay.get();
        if (turbo.get()) {
            if (turboHoleCheck.get() && !PlayerUtils.isInHole(false)) return breakDelay.get();
            if (turboHoleCheckSelf.get() && !PlayerUtils.isInHole(true)) return breakDelay.get();
            if (EntityUtils.getTotalHealth(target) > turboHP.get()) return breakDelay.get();
            return turboSpeed.get();
        }
        return breakDelay.get();
    }

    private void placeAnchor(AnchorPlacement placement) {
        if (placement == null || placement.getPos() == null || target == null) return;
        breakPos = placement;
        meteordevelopment.meteorclient.utils.world.BlockUtils.place(placement.getPos(), InvUtils.findInHotbar(Items.RESPAWN_ANCHOR), rotate.get(), 50, true);
    }

    private void breakAnchor(AnchorPlacement placement) {
        if (placement == null || placement.getPos() == null || target == null) return;

        BlockPos pos = placement.getPos();
        if (mc.world.getBlockState(pos).getBlock() != Blocks.RESPAWN_ANCHOR) return;

        FindItemResult glowstone = InvUtils.findInHotbar(Items.GLOWSTONE);
        if (!glowstone.found()) return;

        int prevSlot = mc.player.getInventory().selectedSlot;
        InvUtils.swap(glowstone.slot(), false);

        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placement.getHitResult());
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placement.getHitResult());

        InvUtils.swap(prevSlot, false);

        if (renderBreak.get()) {
            renderBlocks.add(new RenderBlock().set(pos, renderTime.get(), sideColor.get(), lineColor.get()));
        }
    }

    private AnchorPlacement findBreak() {
        if (target == null) return null;
        AnchorPlacement finalPlacement = new AnchorPlacement();

        for (BlockPos p : getSphere(target, xRadius.get(), yRadius.get())) {
            if (mc.world.getBlockState(p).getBlock() != Blocks.RESPAWN_ANCHOR) continue;
            if (mc.player.getPos().distanceTo(Vec3d.ofCenter(p)) > placeRange.get()) continue;
            if (flatMode.get() && !mc.world.getBlockState(p.down()).isSideSolidFullSquare(mc.world, p.down(), Direction.UP)) continue;

            finalPlacement.set(p, target);
            double selfDMG = finalPlacement.getSelfDMG(mc.player);
            if (selfDMG > maxSelfDamage.get()) continue;
            if (antiSuicide.get() && PlayerUtils.getTotalHealth() - selfDMG <= 0) continue;

            return finalPlacement;
        }
        return null;
    }

    private AnchorPlacement getPlacePos() {
        if (target == null) return null;
        AnchorPlacement placement = new AnchorPlacement();
        AnchorPlacement finalPlacement = new AnchorPlacement();
        bestDamage = 0;

        for (BlockPos p : getSphere(target, xRadius.get(), yRadius.get())) {
            if (!meteordevelopment.meteorclient.utils.world.BlockUtils.canPlace(p, true)) continue;
            if (mc.player.getPos().distanceTo(Vec3d.ofCenter(p)) > placeRange.get()) continue;
            if (flatMode.get() && !mc.world.getBlockState(p.down()).isSideSolidFullSquare(mc.world, p.down(), Direction.UP)) continue;

            placement.set(p, target);
            double selfDMG = placement.getSelfDMG(mc.player);
            double targetDMG = placement.getTargetDMG(target);

            if (antiSuicide.get() && PlayerUtils.getTotalHealth() - selfDMG <= 0) continue;

            if (popOverride.get()) {
                if (EntityUtils.getTotalHealth(target) - targetDMG > 0 && PlayerUtils.getTotalHealth() - selfDMG < popOverrideHP.get()) continue;
            } else {
                if (selfDMG > maxSelfDamage.get()) continue;
            }

            if (targetDMG < minTargetDamage.get()) continue;

            if (targetDMG > bestDamage) {
                bestDamage = targetDMG;
                finalPlacement.set(p, target);
            }
        }

        if (finalPlacement.getPos() != null && render.get()) {
            renderBlocks.add(new RenderBlock().set(finalPlacement.getPos(), renderTime.get(), sideColor.get(), lineColor.get()));
        }
        return finalPlacement;
    }

    private List<BlockPos> getSphere(PlayerEntity target, int xRadius, int yRadius) {
        List<BlockPos> sphere = new ArrayList<>();
        BlockPos center = prediction.get() ? BlockPos.ofFloored(target.getPos().add(target.getVelocity().multiply(predictionTicks.get()))) : target.getBlockPos();

        for (int x = -xRadius; x <= xRadius; x++) {
            for (int y = -yRadius; y <= yRadius; y++) {
                for (int z = -xRadius; z <= xRadius; z++) {
                    BlockPos pos = center.add(x, y, z);
                    sphere.add(pos);
                }
            }
        }
        sphere.sort(Comparator.comparingDouble(p -> mc.player.getPos().distanceTo(Vec3d.ofCenter(p))));
        return sphere;
    }

    private static class RenderBlock {
        public BlockPos.Mutable pos = new BlockPos.Mutable();
        public int ticks;
        private int maxTicks;
        private SettingColor sideColor;
        private SettingColor lineColor;

        public RenderBlock set(BlockPos blockPos, int maxTicks, SettingColor sideColor, SettingColor lineColor) {
            this.pos.set(blockPos);
            this.ticks = maxTicks;
            this.maxTicks = maxTicks;
            this.sideColor = sideColor;
            this.lineColor = lineColor;
            return this;
        }

        public void tick() {
            ticks--;
        }

        public void render(Render3DEvent event, ShapeMode shapeMode) {
            double a = (double) ticks / maxTicks;
            event.renderer.box(pos, new Color(sideColor.r, sideColor.g, sideColor.b, (int) (sideColor.a * a)), new Color(lineColor.r, lineColor.g, lineColor.b, (int) (lineColor.a * a)), shapeMode, 0);
        }
    }
}