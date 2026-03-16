package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class CreativeLavaMountain extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTimer = settings.createGroup("计时设置");
    private final SettingGroup sgRender = settings.createGroup("渲染设置");

    // === 基础设置 ===
    private final Setting<Integer> heightLimit = sgGeneral.add(new IntSetting.Builder()
            .name("停止高度 (Y)")
            .description("建造到此高度停止。")
            .defaultValue(319)
            .min(-60).max(320)
            .build()
    );

    private final Setting<Boolean> autoFly = sgGeneral.add(new BoolSetting.Builder()
            .name("自动悬停")
            .description("锁定在中心上方 2.5 格处 (最稳距离)。")
            .defaultValue(true)
            .build()
    );

    // === 计时设置 ===
    private final Setting<Boolean> autoCalc = sgTimer.add(new BoolSetting.Builder()
            .name("自动计算流淌时间")
            .description("根据高度差自动计算时间 (高度 * 30 tick)。")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> manualGroundY = sgTimer.add(new IntSetting.Builder()
            .name("地面 Y 坐标")
            .description("用于自动计算高度差。")
            .defaultValue(64)
            .visible(autoCalc::get)
            .build()
    );

    private final Setting<Integer> manualLavaTime = sgTimer.add(new IntSetting.Builder()
            .name("手动流淌时间 (秒)")
            .description("如果不自动计算，每层等待多少秒。")
            .defaultValue(120)
            .min(10)
            .visible(() -> !autoCalc.get())
            .build()
    );

    private final Setting<Integer> waterDelay = sgTimer.add(new IntSetting.Builder()
            .name("放水等待 (Tick)")
            .description("收回岩浆源后，等待多久让液面下降，再放水。")
            .defaultValue(40)
            .min(10)
            .build()
    );

    // === 渲染设置 ===
    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder().name("启用渲染").defaultValue(true).build());
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>().name("形状模式").defaultValue(ShapeMode.Lines).build());
    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder().name("填充颜色").defaultValue(new SettingColor(255, 0, 0, 50)).build());
    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder().name("线条颜色").defaultValue(new SettingColor(255, 0, 0, 255)).build());

    public CreativeLavaMountain() {
        super(AddonTemplate.CATEGORY, "创造模式自动铸山", "死磕验证版：不成功不下一步。");
    }

    private BlockPos centerPos;
    private int currentLayerY;
    
    // 计时与状态
    private int flowTimer = 0; // 倒计时
    private int actionCooldown = 0; // 动作冷却，防止发包太快
    private Stage stage = Stage.Info;

    private enum Stage {
        Info,
        BuildStructure, // 建造十字架 (验证模式)
        PlaceLava,      // 放岩浆 (验证模式)
        WaitingFlow,    // 纯等待倒计时
        PickupLava,     // 收岩浆 (验证模式)
        WaitDecay,      // 等待液面下降
        PlaceWater,     // 放水 (验证模式)
        WaitWaterFlow,  // 等水流一会
        PickupWater,    // 收水 (验证模式)
        NextLayer       // 上升
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) return;
        
        HitResult result = mc.player.raycast(100, 0, false);
        if (result.getType() == HitResult.Type.BLOCK) {
            BlockPos hit = ((BlockHitResult) result).getBlockPos();
            centerPos = hit;
            currentLayerY = hit.getY() + 1;
            
            stage = Stage.BuildStructure;
            actionCooldown = 0;
            flowTimer = 0;
            
            ChatUtils.sendMsg(Text.of("§a[自动铸山] §f中心: " + centerPos.toShortString() + " 起始层: " + currentLayerY));
        } else {
            error("请对准一个方块作为地基！");
            toggle();
        }
    }

    @EventHandler
    private void onPreTick(TickEvent.Pre event) {
        if (centerPos == null) return;

        // === 1. 自动悬停修正 (低空飞行) ===
        // 高度 +2.5 格。如果你在 Y=100，悬停在 Y=102.5。
        // 这个距离对于脚下的方块(距离2.5)和下一层的水(距离0.5)都是完美的交互距离。
        if (autoFly.get()) {
            if (!mc.player.getAbilities().flying) mc.player.getAbilities().flying = true;
            mc.player.setVelocity(0, 0, 0);
            double targetY = currentLayerY + 2.5; 
            mc.player.setPosition(centerPos.getX() + 0.5, targetY, centerPos.getZ() + 0.5);
            mc.player.setPitch(90); // 强制低头
        }

        // === 2. 动作冷却 ===
        if (actionCooldown > 0) {
            actionCooldown--;
            return; // 还在冷却中，不执行任何操作
        }

        // === 3. 计算流淌时间 ===
        int totalFlowTicks;
        if (autoCalc.get()) {
            int height = currentLayerY - manualGroundY.get();
            if (height < 1) height = 1;
            totalFlowTicks = height * 30 + 60; // 30tick/格 + 3秒缓冲
        } else {
            totalFlowTicks = manualLavaTime.get() * 20;
        }

        // === 4. 状态机 (死磕逻辑) ===
        switch (stage) {
            case BuildStructure:
                // 目标：确保 5 个方块都存在
                BlockPos center = new BlockPos(centerPos.getX(), currentLayerY, centerPos.getZ());
                BlockPos[] positions = {
                    center, center.north(), center.south(), center.east(), center.west()
                };
                
                boolean allBuilt = true;
                FindItemResult block = InvUtils.findInHotbar(item -> item.getItem() instanceof BlockItem);
                if (!block.found()) { error("没方块！"); toggle(); return; }

                for (BlockPos pos : positions) {
                    // 核心：如果这里是空的，就放，并且不允许进入下一步
                    if (mc.world.getBlockState(pos).isReplaceable()) {
                        allBuilt = false;
                        placeSmart(block, pos);
                        actionCooldown = 5; // 放一个等 5 tick，防止太快吞包
                        return; // 一次tick只放一个
                    }
                }

                if (allBuilt) {
                    ChatUtils.sendMsg(Text.of("§7结构确认完成，准备岩浆..."));
                    stage = Stage.PlaceLava;
                    actionCooldown = 5;
                }
                break;

            case PlaceLava:
                BlockPos lavaPos = new BlockPos(centerPos.getX(), currentLayerY + 1, centerPos.getZ());
                
                // 核心验证：必须亲眼看到岩浆方块
                if (mc.world.getBlockState(lavaPos).getBlock() == Blocks.LAVA) {
                    ChatUtils.sendMsg(Text.of("§c岩浆放置成功，开始倒计时: " + (totalFlowTicks/20) + "s"));
                    flowTimer = totalFlowTicks;
                    stage = Stage.WaitingFlow;
                    return;
                }

                FindItemResult lava = InvUtils.findInHotbar(Items.LAVA_BUCKET);
                if (!lava.found()) { error("没岩浆桶！"); toggle(); return; }

                placeSmart(lava, lavaPos);
                actionCooldown = 10; // 给服务器一点反应时间
                break;

            case WaitingFlow:
                if (flowTimer > 0) {
                    flowTimer--;
                    if (flowTimer % 20 == 0) {
                        mc.inGameHud.setOverlayMessage(Text.of("§c等待岩浆流淌: " + (flowTimer/20) + "s"), true);
                    }
                } else {
                    stage = Stage.PickupLava;
                    actionCooldown = 5;
                }
                break;

            case PickupLava:
                BlockPos lavaTarget = new BlockPos(centerPos.getX(), currentLayerY + 1, centerPos.getZ());
                
                // 核心验证：必须亲眼看到岩浆消失（变成空气或者非岩浆）
                if (mc.world.getBlockState(lavaTarget).getBlock() != Blocks.LAVA) {
                    ChatUtils.sendMsg(Text.of("§e岩浆源已回收，等待液面下降..."));
                    flowTimer = waterDelay.get(); // 复用计时器
                    stage = Stage.WaitDecay;
                    return;
                }

                FindItemResult bucket = InvUtils.findInHotbar(Items.BUCKET);
                if (!bucket.found()) { error("没空桶！"); toggle(); return; }

                interactSmart(bucket, lavaTarget);
                actionCooldown = 10;
                break;

            case WaitDecay:
                if (flowTimer > 0) {
                    flowTimer--;
                } else {
                    stage = Stage.PlaceWater;
                }
                break;

            case PlaceWater:
                BlockPos waterPos = new BlockPos(centerPos.getX(), currentLayerY + 1, centerPos.getZ());
                
                // 核心验证：必须亲眼看到水
                if (mc.world.getBlockState(waterPos).getBlock() == Blocks.WATER) {
                    flowTimer = 40; // 等2秒让水流一下
                    stage = Stage.WaitWaterFlow;
                    return;
                }

                FindItemResult water = InvUtils.findInHotbar(Items.WATER_BUCKET);
                if (!water.found()) { error("没水桶！"); toggle(); return; }

                placeSmart(water, waterPos);
                actionCooldown = 10;
                break;

            case WaitWaterFlow:
                if (flowTimer > 0) {
                    flowTimer--;
                } else {
                    stage = Stage.PickupWater;
                }
                break;

            case PickupWater:
                BlockPos waterTarget = new BlockPos(centerPos.getX(), currentLayerY + 1, centerPos.getZ());
                
                // 核心验证：必须亲眼看到水消失
                if (mc.world.getBlockState(waterTarget).getBlock() != Blocks.WATER) {
                    ChatUtils.sendMsg(Text.of("§a层 " + currentLayerY + " 完成！"));
                    stage = Stage.NextLayer;
                    actionCooldown = 10; // 给一点时间上升
                    return;
                }

                FindItemResult bucket2 = InvUtils.findInHotbar(Items.BUCKET);
                if (!bucket2.found()) { error("没空桶！"); toggle(); return; }

                interactSmart(bucket2, waterTarget);
                actionCooldown = 10;
                break;

            case NextLayer:
                currentLayerY++;
                if (currentLayerY > heightLimit.get()) {
                    ChatUtils.sendMsg(Text.of("§a建造全部完成！"));
                    toggle();
                    return;
                }
                stage = Stage.BuildStructure;
                break;
        }
    }

    // === 智能操作方法 ===

    // 放置方块/液体：自动寻找支点
    private void placeSmart(FindItemResult item, BlockPos target) {
        if (!item.found()) return;

        // 寻找支点逻辑
        BlockPos neighbor = target.down();
        Direction side = Direction.UP;

        // 如果下面是空的（比如建四周的时候），找旁边
        if (mc.world.getBlockState(neighbor).isReplaceable()) {
            if (!mc.world.getBlockState(target.north()).isReplaceable()) { neighbor = target.north(); side = Direction.SOUTH; }
            else if (!mc.world.getBlockState(target.south()).isReplaceable()) { neighbor = target.south(); side = Direction.NORTH; }
            else if (!mc.world.getBlockState(target.east()).isReplaceable()) { neighbor = target.east(); side = Direction.WEST; }
            else if (!mc.world.getBlockState(target.west()).isReplaceable()) { neighbor = target.west(); side = Direction.EAST; }
            else {
                // 如果四周都是空的（悬空），尝试直接对着空气交互（有些创造模式允许），或者只能等待
                // 但对于我们的结构，中心块下面肯定有上一层的岩浆源（变石头了），四周块肯定有中心块做支点
                return; 
            }
        }

        // 强制切物品
        mc.player.getInventory().selectedSlot = item.slot();
        
        // 精确点击向量
        Vec3d hitVec = new Vec3d(
            neighbor.getX() + 0.5 + (side.getOffsetX() * 0.5),
            neighbor.getY() + 0.5 + (side.getOffsetY() * 0.5),
            neighbor.getZ() + 0.5 + (side.getOffsetZ() * 0.5)
        );

        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(
            hitVec, side, neighbor, false
        ));
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    // 直接交互：用于收水/收岩浆 (对着流体块本身交互)
    private void interactSmart(FindItemResult item, BlockPos target) {
        mc.player.getInventory().selectedSlot = item.slot();
        
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(
            new Vec3d(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5),
            Direction.UP,
            target,
            false
        ));
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || centerPos == null) return;
        BlockPos pos = new BlockPos(centerPos.getX(), currentLayerY, centerPos.getZ());
        event.renderer.box(pos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }
}