package com.codigohasta.addon.modules;

import net.minecraft.world.phys.Vec3;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AutoChorus extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("渲染设置");

    // --- 设置 (已汉化) ---
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("攻击范围")
        .description("最大攻击距离。")
        .defaultValue(50)
        .min(10)
        .max(100)
        .build()
    );

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("自动切枪")
        .description("自动切换到背包里的远程武器。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("自动瞄准")
        .description("自动旋转视角瞄准。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("射击延迟")
        .description("射击后的冷却时间 (Ticks)。")
        .defaultValue(2)
        .min(0)
        .build()
    );

    // --- 渲染设置 (已汉化) ---
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>().name("渲染模式").defaultValue(ShapeMode.Lines).build());
    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder().name("填充颜色").defaultValue(new SettingColor(255, 0, 255, 40)).build());
    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder().name("线条颜色").defaultValue(new SettingColor(255, 0, 255, 200)).build());

    // --- 内部变量 ---
    private BlockPos currentTarget;
    private int timer;

    public AutoChorus() {
        super(AddonTemplate.CATEGORY, "自动打紫菘花", "自动用弩，弓，投掷物打击紫颂花，这样可以采集很多紫菘花 ");
    }

    @Override
    public void onActivate() {
        currentTarget = null;
        timer = 0;
    }

    @Override
    public void onDeactivate() {
        mc.options.keyUse.setDown(false);
        if (mc.player != null) mc.gameMode.releaseUsingItem(mc.player);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        // 1. 查找目标
        currentTarget = findTarget();
        
        if (currentTarget == null) {
            // 如果没目标且正在拉弓，松开
            if (mc.player.isUsingItem() && mc.player.getActiveItem().getItem() instanceof BowItem) {
                mc.options.keyUse.setDown(false);
                mc.gameMode.releaseUsingItem(mc.player);
            }
            return;
        }

        // 2. 武器选择
        FindItemResult weaponResult = findWeapon();
        if (!weaponResult.found() && !isValidWeapon(mc.player.getMainHandItem().getItem())) return;

        if (autoSwitch.get() && !isValidWeapon(mc.player.getMainHandItem().getItem())) {
            InvUtils.swap(weaponResult.slot(), true);
        }

        Item handItem = mc.player.getMainHandItem().getItem();
        if (!isValidWeapon(handItem)) return;

        // 3. 计算弹道
        Vec3 targetPos = new Vec3(currentTarget.getX() + 0.5, currentTarget.getY() + 0.5, currentTarget.getZ() + 0.5);
        
        // **重点优化**：传入目标距离，让弹道计算知道我们打算用多大的力气射箭
        double distToTarget = Math.sqrt(mc.player.distanceToSqr(targetPos));
        float[] rotations = solveBallistic(targetPos, handItem, distToTarget);
        
        if (rotations == null) return;

        if (rotate.get()) {
            // 弓箭必须持续锁定瞄准 (100优先级)，其他武器射击瞬间瞄准即可 (50)
            int priority = (handItem instanceof BowItem) ? 100 : 50;
            Rotations.rotate(rotations[0], rotations[1], priority, () -> shoot(handItem, distToTarget));
        } else {
            shoot(handItem, distToTarget);
        }
    }

    private void shoot(Item item, double distance) {
        if (timer > 0) {
            timer--;
            return;
        }

        // --- 弓箭逻辑 (智能蓄力优化版) ---
        if (item instanceof BowItem) {
            if (!mc.player.isUsingItem()) {
                mc.options.keyUse.setDown(true);
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                return;
            }

            int useTicks = mc.player.getTicksUsingItem();
            
            // **智能蓄力计算**
            // 获取应该蓄力多少 tick
            int targetChargeTicks = getOptimalBowCharge(distance);

            // 只有当蓄力时间达到要求时才发射
            if (useTicks >= targetChargeTicks) {
                mc.gameMode.releaseUsingItem(mc.player); // 强制发送松手包
                mc.options.keyUse.setDown(false);
                timer = delay.get();
            }
        } 
        
        // --- 弩逻辑 (无需改动) ---
        else if (item instanceof CrossbowItem) {
            boolean isCharged = CrossbowItem.isCharged(mc.player.getMainHandItem());
            if (isCharged) {
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                mc.player.swing(InteractionHand.MAIN_HAND);
                timer = delay.get();
            } else {
                mc.options.keyUse.setDown(true);
                if (!mc.player.isUsingItem()) {
                    mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                }
            }
        } 
        
        // --- 投掷物逻辑 (无需改动) ---
        else {
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            mc.player.swing(InteractionHand.MAIN_HAND);
            timer = delay.get();
        }
    }

    // --- 核心：计算最佳蓄力 Tick ---
    private int getOptimalBowCharge(double distance) {
        // 如果距离小于 10 格，只需要 12 tick (稍大于半弦) 即可快速射击
        if (distance < 10) return 12;
        // 否则拉满 (20 tick)，保证精准度和距离
        return 20;
    }

    // --- 核心：根据蓄力计算模拟速度 ---
    private float getBowVelocity(int chargeTicks) {
        float f = (float)chargeTicks / 20.0F;
        f = (f * f + f * 2.0F) / 3.0F;
        if (f > 1.0F) f = 1.0F;
        return f * 3.0F;
    }

    // --- 核心：弹道解算 (引入距离参数来修正弓箭速度) ---
    private float[] solveBallistic(Vec3 target, Item weapon, double distance) {
        Vec3 playerPos = mc.player.getEyePosition();
        double dx = target.x - playerPos.x;
        double dy = target.y - playerPos.y;
        double dz = target.z - playerPos.z;
        double distH = Math.sqrt(dx * dx + dz * dz);

        double v = 1.0; 
        double g = 0.05;

        if (weapon instanceof BowItem) {
            // **同步逻辑**：
            // 我们根据距离算出我们会蓄力多久，然后用那个蓄力时间算出箭头飞出去的实际速度
            // 这样瞄准的角度才是对的
            int intendedTicks = getOptimalBowCharge(distance);
            v = getBowVelocity(intendedTicks);
            
            g = 0.05; 
            dy += 0.25; // 弓箭微调
        } 
        else if (weapon instanceof CrossbowItem) {
            v = 3.15; g = 0.05; dy += 0.25;
        } 
        else if (weapon == Items.SNOWBALL || weapon == Items.EGG) {
            v = 1.5; g = 0.03;
        } 
        else if (weapon == Items.TRIDENT) {
            v = 2.5; g = 0.05;
        }
        else if (weapon == Items.WIND_CHARGE) {
            v = 1.6; g = 0.01;
        }

        double v2 = v * v;
        double v4 = v2 * v2;
        double x2 = distH * distH;
        double root = v4 - g * (g * x2 + 2 * dy * v2);

        if (root < 0) return null; 

        double angleRad = Math.atan2((v2 - Math.sqrt(root)), (g * distH));
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
        float pitch = (float) -Math.toDegrees(angleRad);

        return new float[]{yaw, pitch};
    }

    // --- 目标查找 (保持不变) ---
    private BlockPos findTarget() {
        BlockPos pPos = mc.player.blockPosition();
        int r = (int) Math.ceil(range.get());
        List<BlockPos> candidates = new ArrayList<>();

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = -10; y <= r; y++) {
                    BlockPos pos = pPos.offset(x, y, z);
                    if (pos.distSqr(pPos) > r * r) continue;
                    if (mc.level.getBlockState(pos).getBlock() == Blocks.CHORUS_FLOWER) {
                        if (canSee(pos)) candidates.add(pos);
                    }
                }
            }
        }
        if (candidates.isEmpty()) return null;
        candidates.sort(Comparator.comparingDouble(p -> mc.player.distanceToSqr(p.getCenter())));
        return candidates.get(0);
    }

    private boolean canSee(BlockPos pos) {
        Vec3 start = mc.player.getEyePosition();
        Vec3 end = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        ClipContext context = new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player);
        return mc.level.clip(context).getType() == HitResult.Type.MISS || mc.level.clip(context).getBlockPos().equals(pos);
    }

    private FindItemResult findWeapon() { return InvUtils.find(item -> isValidWeapon(item.getItem())); }

    private boolean isValidWeapon(Item item) {
        return item == Items.SNOWBALL || item == Items.EGG || item == Items.WIND_CHARGE || item == Items.TRIDENT || item instanceof BowItem || item instanceof CrossbowItem;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (currentTarget != null) {
            event.renderer.box(currentTarget, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
    }
}