package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.meteor.MouseButtonEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_MIDDLE;

public class CrossbowAura extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargets = settings.createGroup("目标筛选");
    private final SettingGroup sgWhitelist = settings.createGroup("名单设置");
    private final SettingGroup sgCrossbow = settings.createGroup("弩设置");
    private final SettingGroup sgPrediction = settings.createGroup("预判设置");
    private final SettingGroup sgRender = settings.createGroup("渲染");

    public enum PriorityMode {
        Closest("距离最近"),
        LowestHealth("血量最低"),
        LockOnly("仅攻击锁定");

        private final String title;
        PriorityMode(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder().name("射程").defaultValue(60).min(5).max(150).build());
    
    public enum AimMode { Silent, Lock }
    private final Setting<AimMode> aimMode = sgGeneral.add(new EnumSetting.Builder<AimMode>().name("瞄准方式").defaultValue(AimMode.Silent).build());
    
    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder().name("自动切枪").defaultValue(true).build());
    private final Setting<Boolean> autoFire = sgGeneral.add(new BoolSetting.Builder().name("自动开火").defaultValue(true).build());
    
    private final Setting<PriorityMode> priority = sgGeneral.add(new EnumSetting.Builder<PriorityMode>()
        .name("排序方式")
        .description("距离/血量模式下自动攻击。选择'仅攻击锁定'时，必须用中键选定目标。")
        .defaultValue(PriorityMode.Closest)
        .build()
    );
    
    private final Setting<Boolean> middleClickLock = sgGeneral.add(new BoolSetting.Builder()
        .name("中键锁敌")
        .description("允许使用中键设置锁定目标。")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> resetOnMiss = sgGeneral.add(new BoolSetting.Builder()
        .name("空击解锁")
        .description("对着空气按中键时取消锁定。")
        .defaultValue(true)
        .visible(middleClickLock::get)
        .build()
    );

    // --- 目标设置 ---
    private final Setting<Set<EntityType<?>>> entities = sgTargets.add(new EntityTypeListSetting.Builder().name("目标实体").defaultValue(EntityType.PLAYER, EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER).build());
    private final Setting<Boolean> ignorePets = sgTargets.add(new BoolSetting.Builder().name("忽略宠物").defaultValue(true).build());
    private final Setting<Boolean> ignoreNamed = sgTargets.add(new BoolSetting.Builder().name("忽略命名生物").defaultValue(true).build());
    
    private final Setting<Boolean> targetSurvival = sgTargets.add(new BoolSetting.Builder().name("攻击生存玩家").defaultValue(true).build());
    private final Setting<Boolean> targetCreative = sgTargets.add(new BoolSetting.Builder().name("攻击创造玩家").defaultValue(false).build());
    private final Setting<Boolean> targetAdventure = sgTargets.add(new BoolSetting.Builder().name("攻击冒险玩家").defaultValue(true).build());

    // --- 名单设置 ---
    public enum ListMode { Whitelist, Blacklist, Off }
    private final Setting<ListMode> listMode = sgWhitelist.add(new EnumSetting.Builder<ListMode>()
        .name("名单模式")
        .description("白名单/黑名单模式。")
        .defaultValue(ListMode.Off)
        .build()
    );

    private final Setting<String> playerList = sgWhitelist.add(new StringSetting.Builder()
        .name("玩家列表")
        .description("玩家ID列表，用英文逗号(,)分隔。")
        .defaultValue("")
        .visible(() -> listMode.get() != ListMode.Off)
        .build()
    );

    // --- 弩设置 ---
    public enum CrossbowMode { Native, Control, Packet }
    private final Setting<CrossbowMode> cbMode = sgCrossbow.add(new EnumSetting.Builder<CrossbowMode>().name("装填模式").defaultValue(CrossbowMode.Native).build());
    private final Setting<Integer> delay = sgCrossbow.add(new IntSetting.Builder().name("射击延迟").defaultValue(0).min(0).max(10).build());
    private final Setting<Integer> tolerance = sgCrossbow.add(new IntSetting.Builder().name("装填容错").defaultValue(6).min(0).max(50).visible(() -> cbMode.get() != CrossbowMode.Native).build());

    // --- 预判设置 (增强版) ---
    private final Setting<Boolean> predictMovement = sgPrediction.add(new BoolSetting.Builder()
        .name("移动预判")
        .description("根据目标速度和距离计算提前量。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> calculateAcceleration = sgPrediction.add(new BoolSetting.Builder()
        .name("计算加速度 (二阶)")
        .description("预测目标的加减速趋势。这是对付急停/变向/起步的关键！")
        .defaultValue(true)
        .visible(predictMovement::get)
        .build()
    );

    private final Setting<Boolean> pingCompensation = sgPrediction.add(new BoolSetting.Builder()
        .name("延迟补偿 (Ping)")
        .description("将网络延迟纳入计算。")
        .defaultValue(true)
        .visible(predictMovement::get)
        .build()
    );

    private final Setting<Double> predictionScale = sgPrediction.add(new DoubleSetting.Builder()
        .name("预判倍率")
        .description("微调提前量。如果箭落在目标身后，请调高此值。")
        .defaultValue(1.0)
        .min(0.0)
        .max(2.0)
        .sliderMax(1.5)
        .visible(predictMovement::get)
        .build()
    );

    // --- 渲染设置 ---
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>().name("渲染模式").defaultValue(ShapeMode.Lines).build());
    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder().name("普通目标颜色").defaultValue(new SettingColor(255, 0, 0, 40)).build());
    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder().name("普通线条颜色").defaultValue(new SettingColor(255, 0, 0, 200)).build());
    private final Setting<SettingColor> lockSideColor = sgRender.add(new ColorSetting.Builder().name("锁定目标颜色").defaultValue(new SettingColor(0, 255, 0, 80)).build());
    private final Setting<SettingColor> lockLineColor = sgRender.add(new ColorSetting.Builder().name("锁定线条颜色").defaultValue(new SettingColor(0, 255, 0, 255)).build());
    private final Setting<Boolean> renderPredict = sgRender.add(new BoolSetting.Builder().name("显示预判点").description("渲染预判的击中位置(青色框)。").defaultValue(true).build());

    private Entity currentTarget;
    private Entity lockedTarget;
    private Vec3d lastPredictedPos = null;
    private int timer;

    // 存储上一次的速度，用于计算加速度
    private final Map<Integer, Vec3d> prevVelocities = new HashMap<>();

    public CrossbowAura() {
        super(AddonTemplate.CATEGORY, "万弩射江潮", "诗云：百年霸越，钱王万弩射江潮。机关枪打死你。");
    }

    @Override public void onActivate() { 
        timer = 0; 
        currentTarget = null; 
        lastPredictedPos = null;
        prevVelocities.clear();
        if (priority.get() != PriorityMode.LockOnly) lockedTarget = null;
    }
    
    @Override public void onDeactivate() {
        mc.options.useKey.setPressed(false);
        if (mc.player != null) mc.interactionManager.stopUsingItem(mc.player);
        lockedTarget = null;
        lastPredictedPos = null;
        prevVelocities.clear();
    }

    @EventHandler
    private void onMouseButton(MouseButtonEvent event) {
        if (!middleClickLock.get() || mc.currentScreen != null) return;

        if (event.action == KeyAction.Press && event.button == GLFW_MOUSE_BUTTON_MIDDLE) {
            Entity target = getEntityInCrosshair(range.get());

            if (target != null && isValid(target)) {
                lockedTarget = target;
                ChatUtils.info("已锁定目标: " + target.getName().getString());
            } else if (resetOnMiss.get()) {
                if (lockedTarget != null) {
                    lockedTarget = null;
                    ChatUtils.info("已取消锁定");
                }
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        currentTarget = findTarget();
        lastPredictedPos = null; 
        
        // 自动切枪逻辑
        ItemStack mainStack = mc.player.getMainHandStack();
        ItemStack offStack = mc.player.getOffHandStack();
        boolean mainValid = isValidWeapon(mainStack.getItem());
        boolean offValid = isValidWeapon(offStack.getItem());

        if (currentTarget != null && autoSwitch.get() && !mainValid && !offValid) {
            FindItemResult weapon = findWeapon();
            if (weapon.found()) {
                InvUtils.swap(weapon.slot(), true);
                mainStack = mc.player.getMainHandStack();
                mainValid = isValidWeapon(mainStack.getItem());
            }
        }
        
        Hand activeHand;
        ItemStack activeStack;
        
        if (mainValid) {
            activeHand = Hand.MAIN_HAND;
            activeStack = mainStack;
        } else if (offValid) {
            activeHand = Hand.OFF_HAND;
            activeStack = offStack;
        } else {
            currentTarget = null;
            prevVelocities.clear(); // 没有目标时清除缓存
            return; 
        }

        boolean shouldShoot = autoFire.get() || mc.options.useKey.isPressed();

        if (currentTarget != null) {
            double dist = mc.player.distanceTo(currentTarget);
            float[] rots = solveBallistic(currentTarget, activeStack.getItem(), dist);
            
            // 记录当前目标速度，供下一tick计算加速度
            Vec3d currentVel = new Vec3d(currentTarget.getX() - currentTarget.prevX, currentTarget.getY() - currentTarget.prevY, currentTarget.getZ() - currentTarget.prevZ);
            prevVelocities.put(currentTarget.getId(), currentVel);

            if (rots != null) {
                if (aimMode.get() == AimMode.Lock) {
                    mc.player.setYaw(rots[0]);
                    mc.player.setPitch(rots[1]);
                }

                if (shouldShoot || aimMode.get() == AimMode.Lock) {
                    Rotations.rotate(rots[0], rots[1], 100, () -> {
                        if (shouldShoot) handleShooting(activeStack, activeHand, dist);
                    });
                }
            }
        } else {
            prevVelocities.clear();
            if (autoFire.get() && mc.options.useKey.isPressed()) {
                mc.options.useKey.setPressed(false);
                mc.interactionManager.stopUsingItem(mc.player);
            }
        }
    }

    private Entity findTarget() {
        if (priority.get() == PriorityMode.LockOnly) {
            if (lockedTarget != null && isValid(lockedTarget)) {
                return lockedTarget;
            }
            return null;
        }

        List<Entity> candidates = new ArrayList<>();
        for (Entity entity : mc.world.getEntities()) {
            if (isValid(entity)) {
                candidates.add(entity);
            }
        }

        if (candidates.isEmpty()) return null;
        candidates.sort(this::compareTargets);
        return candidates.get(0);
    }

    private boolean isValid(Entity entity) {
        if (entity == null) return false;
        if (!(entity instanceof LivingEntity) || entity == mc.player || !entity.isAlive()) return false;
        if (mc.player.distanceTo(entity) > range.get()) return false;
        
        if (!entities.get().contains(entity.getType())) return false;

        if (entity instanceof PlayerEntity player) {
            if (player.isCreative() && !targetCreative.get()) return false;
            if (!player.isCreative() && !player.isSpectator() && !targetSurvival.get() && !targetAdventure.get()) return false; 
            GameMode gm = getGameMode(player);
            if (gm == GameMode.SURVIVAL && !targetSurvival.get()) return false;
            if (gm == GameMode.ADVENTURE && !targetAdventure.get()) return false;
            if (gm == GameMode.SPECTATOR) return false;

            if (!Friends.get().shouldAttack(player)) return false;

            if (listMode.get() != ListMode.Off) {
                String name = player.getGameProfile().getName();
                List<String> validNames = Arrays.stream(playerList.get().split(","))
                                             .map(String::trim)
                                             .collect(Collectors.toList());
                switch (listMode.get()) {
                    case Whitelist -> { if (!validNames.contains(name)) return false; }
                    case Blacklist -> { if (validNames.contains(name)) return false; }
                }
            }
        } else {
            if (ignoreNamed.get() && entity.hasCustomName()) return false;
            if (ignorePets.get() && isPet(entity)) return false;
        }

        if (!mc.player.canSee(entity)) return false;
        return true;
    }
    
    private int compareTargets(Entity a, Entity b) {
        return switch (priority.get()) {
            case LowestHealth -> Float.compare(((LivingEntity)a).getHealth(), ((LivingEntity)b).getHealth());
            default -> Double.compare(mc.player.distanceTo(a), mc.player.distanceTo(b));
        };
    }

    private void handleShooting(ItemStack stack, Hand hand, double distance) {
        if (timer > 0) {
            timer--;
            if (cbMode.get() == CrossbowMode.Native || autoFire.get()) mc.options.useKey.setPressed(false);
            return;
        }

        Item item = stack.getItem();
        if (item instanceof CrossbowItem) handleCrossbow(stack, hand);
        else if (item instanceof BowItem) handleBow(hand, distance);
        else if (item == Items.TRIDENT) handleTrident(hand); // 新增三叉戟专门处理
        else {
            mc.interactionManager.interactItem(mc.player, hand);
            mc.player.swingHand(hand);
            timer = delay.get();
        }
    }

    private void handleCrossbow(ItemStack stack, Hand hand) {
        switch (cbMode.get()) {
            case Native -> {
                if (CrossbowItem.isCharged(stack)) {
                    mc.interactionManager.interactItem(mc.player, hand);
                    mc.player.swingHand(hand);
                    timer = delay.get();
                } else {
                    mc.options.useKey.setPressed(true);
                    if (!mc.player.isUsingItem()) mc.interactionManager.interactItem(mc.player, hand);
                }
            }
            case Control -> {
                if (CrossbowItem.isCharged(stack)) {
                    mc.interactionManager.interactItem(mc.player, hand);
                    mc.player.swingHand(hand);
                    timer = delay.get(); return;
                }
                mc.options.useKey.setPressed(true);
                if (!mc.player.isUsingItem()) { mc.interactionManager.interactItem(mc.player, hand); return; }
                int time = getPullTime(stack) + tolerance.get();
                if (mc.player.getItemUseTime() >= time) mc.interactionManager.stopUsingItem(mc.player);
            }
            case Packet -> {
                if (CrossbowItem.isCharged(stack)) {
                    mc.interactionManager.interactItem(mc.player, hand);
                    mc.player.swingHand(hand);
                    timer = delay.get();
                }
                if (!mc.player.isUsingItem()) {
                    mc.interactionManager.interactItem(mc.player, hand);
                    mc.options.useKey.setPressed(true); return;
                }
                mc.options.useKey.setPressed(true);
                int time = getPullTime(stack) + tolerance.get();
                if (mc.player.getItemUseTime() >= time) mc.interactionManager.stopUsingItem(mc.player);
            }
        }
    }

    private void handleBow(Hand hand, double distance) {
        if (!mc.player.isUsingItem()) {
            mc.options.useKey.setPressed(true);
            mc.interactionManager.interactItem(mc.player, hand); 
            return;
        }
        int useTicks = mc.player.getItemUseTime();
        int targetCharge = (distance < 10) ? 12 : 20;
        if (useTicks >= targetCharge) {
            mc.interactionManager.stopUsingItem(mc.player); 
            mc.options.useKey.setPressed(false);
            timer = delay.get();
        }
    }

    // --- 新增：三叉戟专门处理逻辑 (模拟蓄力) ---
    private void handleTrident(Hand hand) {
        // 如果当前没有在使用物品，开始蓄力
        if (!mc.player.isUsingItem()) {
            mc.options.useKey.setPressed(true);
            mc.interactionManager.interactItem(mc.player, hand);
            return;
        }

        // 三叉戟需要至少 10 tick 才能丢出，我们使用 12 tick 以确保稳定性
        int useTicks = mc.player.getItemUseTime();
        if (useTicks >= 12) {
            mc.interactionManager.stopUsingItem(mc.player); // 释放按键，发射
            mc.options.useKey.setPressed(false);
            timer = delay.get();
        }
    }

    // --- 终极迭代预判算法 (含加速度) ---
    private float[] solveBallistic(Entity target, Item weapon, double dist) {
        double v = 1.0; 
        double g = 0.05;
        
        if (weapon instanceof CrossbowItem) { v = 3.15; g = 0.05; }
        else if (weapon instanceof BowItem) { v = 3.0; g = 0.05; } 
        else if (weapon == Items.TRIDENT) { v = 2.5; g = 0.05; }
        else if (weapon == Items.SNOWBALL || weapon == Items.EGG) { v = 1.5; g = 0.03; }

        Vec3d playerPos = mc.player.getEyePos();
        Vec3d targetPos = target.getPos().add(0, target.getHeight() * 0.5, 0);
        
        // 计算当前速度 (Velocity)
        Vec3d targetVel = new Vec3d(target.getX() - target.prevX, target.getY() - target.prevY, target.getZ() - target.prevZ);
        
        // 计算加速度 (Acceleration)
        Vec3d targetAccel = Vec3d.ZERO;
        if (calculateAcceleration.get() && prevVelocities.containsKey(target.getId())) {
            Vec3d lastVel = prevVelocities.get(target.getId());
            // 加速度 = (当前速度 - 上一刻速度)
            targetAccel = targetVel.subtract(lastVel);
            
            // 限制加速度影响，防止瞬移造成的预判飞出天际
            if (targetAccel.lengthSquared() > 1.0) targetAccel = Vec3d.ZERO; 
        }

        if (predictMovement.get()) {
            double d = playerPos.distanceTo(targetPos);
            double t = d / v; 
            
            double pingTicks = 0;
            if (pingCompensation.get() && mc.getNetworkHandler() != null && mc.player != null) {
                int latency = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid()).getLatency();
                pingTicks = latency / 50.0; 
            }

            double scale = predictionScale.get();

            // 迭代 5 次逼近真实碰撞点
            for (int i = 0; i < 5; i++) {
                double totalTime = t + pingTicks;
                double timeSec = totalTime; // 时间因子
                
                // 公式：P_final = P_0 + (V * t) + (0.5 * A * t^2)
                Vec3d velTerm = targetVel.multiply(timeSec);
                Vec3d accelTerm = targetAccel.multiply(0.5 * timeSec * timeSec);
                
                Vec3d prediction = velTerm;
                if (calculateAcceleration.get()) {
                    prediction = prediction.add(accelTerm);
                }
                
                Vec3d futurePos = targetPos.add(prediction.multiply(scale));
                double newDist = playerPos.distanceTo(futurePos);
                t = newDist / v;
            }

            double finalTime = t + pingTicks;
            Vec3d velTerm = targetVel.multiply(finalTime);
            Vec3d accelTerm = calculateAcceleration.get() ? targetAccel.multiply(0.5 * finalTime * finalTime) : Vec3d.ZERO;
            
            Vec3d prediction = velTerm.add(accelTerm).multiply(scale);
            targetPos = targetPos.add(prediction);
        }
        
        lastPredictedPos = targetPos;

        double dx = targetPos.x - playerPos.x;
        double dy = targetPos.y - playerPos.y;
        double dz = targetPos.z - playerPos.z;
        double distH = Math.sqrt(dx * dx + dz * dz);

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

    private int getPullTime(ItemStack stack) {
        try {
            var registry = mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
            var quickChargeEntry = registry.getOrThrow(Enchantments.QUICK_CHARGE);
            int level = EnchantmentHelper.getLevel(quickChargeEntry, stack);
            return Math.max(0, 25 - 5 * level);
        } catch (Exception e) { return 25; }
    }

    private FindItemResult findWeapon() { return InvUtils.find(item -> isValidWeapon(item.getItem())); }
    private boolean isValidWeapon(Item item) { return item == Items.SNOWBALL || item == Items.EGG || item == Items.TRIDENT || item instanceof BowItem || item instanceof CrossbowItem; }
    private boolean isPet(Entity e) {
        if (e instanceof TameableEntity tameable && tameable.isTamed()) return true;
        if (e instanceof AbstractHorseEntity horse && horse.isTame()) return true;
        return false;
    }
    private GameMode getGameMode(PlayerEntity player) {
        if (mc.getNetworkHandler() == null) return GameMode.SURVIVAL;
        var entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
        return entry == null ? GameMode.SURVIVAL : entry.getGameMode();
    }

    private Entity getEntityInCrosshair(double reachDistance) {
        Vec3d cameraPos = mc.player.getCameraPosVec(1.0F);
        Vec3d rotationVec = mc.player.getRotationVec(1.0F);
        Vec3d endPos = cameraPos.add(rotationVec.multiply(reachDistance));
        Box box = mc.player.getBoundingBox().stretch(rotationVec.multiply(reachDistance)).expand(1.0D, 1.0D, 1.0D);

        EntityHitResult result = ProjectileUtil.raycast(
            mc.player,
            cameraPos,
            endPos,
            box,
            (entity) -> !entity.isSpectator() && entity.canHit(),
            reachDistance * reachDistance
        );

        return result != null ? result.getEntity() : null;
    }

    @EventHandler private void onRender3D(Render3DEvent event) {
        if (currentTarget != null) {
            boolean isLocked = (priority.get() == PriorityMode.LockOnly && currentTarget == lockedTarget);
            SettingColor sColor = isLocked ? lockSideColor.get() : sideColor.get();
            SettingColor lColor = isLocked ? lockLineColor.get() : lineColor.get();
            event.renderer.box(currentTarget.getBoundingBox(), sColor, lColor, shapeMode.get(), 0);
            
            if (renderPredict.get() && lastPredictedPos != null) {
                double size = 0.3;
                Box pBox = new Box(
                    lastPredictedPos.x - size, lastPredictedPos.y - size, lastPredictedPos.z - size,
                    lastPredictedPos.x + size, lastPredictedPos.y + size, lastPredictedPos.z + size
                );
                event.renderer.box(pBox, new SettingColor(0, 255, 255, 80), new SettingColor(0, 255, 255, 200), ShapeMode.Both, 0);
            }
        }
    }
}