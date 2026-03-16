package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.smarttpaura.SmartTPAuraCore;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box; // [修复] 添加 Box 导入
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * SmartTPAura - 终极修复版
 * 解决了楼梯蹭角回弹、墙角卡死回弹、OnGround 状态错误
 */
public class SmartTPAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("目标选择");
    private final SettingGroup sgTeleport = settings.createGroup("瞬移核心");
    private final SettingGroup sgTiming = settings.createGroup("时序与延迟");
    private final SettingGroup sgDebug = settings.createGroup("调试设置");
    private final SettingGroup sgRender = settings.createGroup("可视化渲染");

    // --- General Settings ---
    private final Setting<Weapon> weapon = sgGeneral.add(new EnumSetting.Builder<Weapon>()
        .name("武器模式")
        .description("瞬移过去后优先使用的武器类型。")
        .defaultValue(Weapon.All)
        .build()
    );

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("自动切换武器")
        .description("在执行瞬移攻击前自动从热键栏寻找合适的武器。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swapBack = sgGeneral.add(new BoolSetting.Builder()
        .name("切回原位")
        .description("攻击完成后切换回瞬移前的物品槽位。")
        .visible(autoSwitch::get)
        .defaultValue(true)
        .build()
    );

    private final Setting<ShieldMode> shieldMode = sgGeneral.add(new EnumSetting.Builder<ShieldMode>()
        .name("破盾模式")
        .description("如果目标正在举盾，自动切到斧头攻击。")
        .defaultValue(ShieldMode.Break)
        .visible(() -> autoSwitch.get() && weapon.get() != Weapon.Axe)
        .build()
    );

    // --- Targeting Settings ---
    private final Setting<Set<EntityType<?>>> entities = sgTargeting.add(new EntityTypeListSetting.Builder()
        .name("实体白名单")
        .description("允许模块攻击的实体。")
        .defaultValue(EntityType.PLAYER)
        .onlyAttackable()
        .build()
    );

    private final Setting<SortPriority> priority = sgTargeting.add(new EnumSetting.Builder<SortPriority>()
        .name("优先级")
        .description("如何从多个实体中筛选出主攻击目标。")
        .defaultValue(SortPriority.LowestDistance)
        .build()
    );

    private final Setting<Boolean> ignorePassive = sgTargeting.add(new BoolSetting.Builder()
        .name("忽略被动生物")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreTamed = sgTargeting.add(new BoolSetting.Builder()
        .name("忽略已驯服生物")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreNamed = sgTargeting.add(new BoolSetting.Builder()
        .name("忽略命名生物")
        .defaultValue(false)
        .build()
    );

    // --- Teleport Settings ---
    private final Setting<Double> tpRange = sgTeleport.add(new DoubleSetting.Builder()
        .name("瞬移范围")
        .defaultValue(95.0)
        .min(5.0)
        .max(200.0)
        .build()
    );

    private final Setting<Double> maxStep = sgTeleport.add(new DoubleSetting.Builder()
        .name("单次步长")
        .description("每限数据包移动的最大距离。推荐 8.5 - 9.0。")
        .defaultValue(9.0)
        .min(1.0)
        .max(10.0)
        .build()
    );

    private final Setting<Boolean> padding = sgTeleport.add(new BoolSetting.Builder()
        .name("Paper 预热包")
        .description("瞬移前发送原地包增加服务器 Tick 计数。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> airPath = sgTeleport.add(new BoolSetting.Builder()
        .name("V-Clip 寻路")
        .description("允许垂直穿墙寻路。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> returnPos = sgTeleport.add(new BoolSetting.Builder()
        .name("回传")
        .description("攻击完成后瞬移回来。")
        .defaultValue(true)
        .build()
    );

    // --- Timing Settings ---
    private final Setting<Boolean> tpsSync = sgTiming.add(new BoolSetting.Builder()
        .name("TPS同步")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> hitDelayMult = sgTiming.add(new DoubleSetting.Builder()
        .name("攻击倍率")
        .defaultValue(1.0)
        .min(0.1)
        .max(2.0)
        .build()
    );

    private final Setting<Boolean> pauseOnLag = sgTiming.add(new BoolSetting.Builder()
        .name("卡顿暂停")
        .defaultValue(true)
        .build()
    );

    // --- Debug Settings ---
    private final Setting<Boolean> manualMode = sgDebug.add(new BoolSetting.Builder()
        .name("手动模式")
        .defaultValue(false)
        .build()
    );

    private final Setting<Keybind> attackKey = sgDebug.add(new KeybindSetting.Builder()
        .name("手动触发键")
        .defaultValue(Keybind.none())
        .visible(manualMode::get)
        .build()
    );

    // --- Render Settings ---
    private final Setting<Boolean> renderPath = sgRender.add(new BoolSetting.Builder()
        .name("显示路径")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> updateRenderOnAttack = sgRender.add(new BoolSetting.Builder()
        .name("仅攻击更新渲染")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> pathColor = sgRender.add(new ColorSetting.Builder()
        .name("正常颜色")
        .defaultValue(new SettingColor(0, 255, 255, 150))
        .build()
    );

    private final Setting<SettingColor> lagbackPathColor = sgRender.add(new ColorSetting.Builder()
        .name("回弹颜色")
        .defaultValue(new SettingColor(255, 0, 0, 200))
        .build()
    );

    // --- Internals ---
    private final List<Entity> targets = new ArrayList<>();
    private SmartTPAuraCore core;
    public boolean swapped;
    public static int previousSlot = -1;

    private List<Vec3d> renderSnapshot = new ArrayList<>();
    private double snapshotStep = 9.0;
    private boolean snapshotIsRed = false;

    public SmartTPAura() {
        super(AddonTemplate.CATEGORY, "SmartTPAura", "集成 LB/Jigsaw 灵魂的终极百米瞬移。");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;
        core = new SmartTPAuraCore(mc.world, mc.player);
        swapped = false;
        previousSlot = -1;
        renderSnapshot.clear();
        snapshotIsRed = false;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        // 监听 S08 回弹包
        if (event.packet instanceof PlayerPositionLookS2CPacket) {
            if (core != null) {
                core.desyncPos = null;
                // 标记路径为红色，指示回弹发生
                snapshotIsRed = true; 
            }
        }
    }

    private Vec3d getRealPlayerPos() {
        if (mc.player == null) return Vec3d.ZERO;
        return new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || core == null) return;
        if (TickRate.INSTANCE.getTimeSinceLastTick() >= 1.5f && pauseOnLag.get()) return;

        // 1. 目标筛选
        targets.clear();
        TargetUtils.getList(targets, this::entityCheck, priority.get(), 1);

        if (targets.isEmpty()) {
            core.desyncPos = null;
            return;
        }

        Entity target = targets.get(0);
        Vec3d localPos = getRealPlayerPos();
        handleAutoSwitch(target);

        // 2. 触发智能寻路
        core.setAirPath(airPath.get());
        
        // [核心修改] 传入 target 实体，让 Core 计算最佳攻击位 (不卡墙坐标)
        // 以前是 updatePathfinding(localPos, target.getPos())，会导致撞墙
        core.updatePathfinding(localPos, target);
        
        // 3. 非攻击状态下的渲染更新
        if (!updateRenderOnAttack.get()) {
            List<Vec3d> currentFound = core.getCurrentPath();
            if (currentFound != null && !currentFound.isEmpty()) {
                renderSnapshot = new ArrayList<>(currentFound);
                snapshotStep = maxStep.get();
                snapshotIsRed = false;
            }
        }

        // 4. 冷却判定
        float cooldownMult = (float) (0.5f * hitDelayMult.get());
        if (tpsSync.get()) cooldownMult /= (TickRate.INSTANCE.getTickRate() / 20.0f);
        
        boolean shouldAttack = false;
        if (manualMode.get()) {
            if (mc.currentScreen == null && attackKey.get().isPressed()) shouldAttack = true;
        } else {
            if (mc.player.getAttackCooldownProgress(cooldownMult) >= 1) shouldAttack = true;
        }

        // 5. 执行攻击
        if (shouldAttack) {
            double dynamicStep = maxStep.get();
            // 获取经过碰撞优化的路径
            List<Vec3d> path = core.getEfficientPath(dynamicStep);
            
            if (path != null && !path.isEmpty()) {
                // 更新渲染快照
                if (updateRenderOnAttack.get()) {
                    renderSnapshot = new ArrayList<>(path);
                    snapshotStep = dynamicStep;
                    snapshotIsRed = false;
                }

                // 强制对齐起点，防止微小误差累积
                path.set(0, localPos); 

                // 发包流程
                // A. 原地包
                sendPacketToNetwork(localPos); 

                // B. Paper 预热
                if (padding.get()) {
                    for (int i = 0; i < 2; i++) {
                        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(mc.player.isOnGround(), mc.player.horizontalCollision));
                    }
                }

                // C. 瞬移过程
                for (Vec3d p : path) {
                    sendPacketToNetwork(p);
                    core.desyncPos = p;
                }
                
                // D. 攻击
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.swingHand(Hand.MAIN_HAND);
                
                // E. 回传
                if (returnPos.get()) {
                    for (int i = path.size() - 2; i >= 0; i--) {
                        sendPacketToNetwork(path.get(i));
                    }
                    sendPacketToNetwork(localPos);
                }
            }
        }
    }

    private void sendPacketToNetwork(Vec3d p) {
        if (p == null || mc.getNetworkHandler() == null || mc.player == null) return;
        
        // [核心修复] 动态 OnGround 判定
        // 使用 Core 中的 CollisionHelper 判断该坐标脚下是否有方块
        // 这解决了在楼梯上发包时，Y坐标正确但 isOnGround 错误导致的拉回
        
        double floorY = core.getFloorHeightAt(p);
        
        // 判断1: 当前 Y 坐标是否紧贴物理地面 (误差 0.01)
        boolean physicallyOnGround = Math.abs(p.y - (Math.floor(p.y) + floorY)) < 0.01;
        
        // 判断2: 使用 Box 进行碰撞检查 (双重保险)
        // 检查脚下 0.05 米范围内是否有碰撞箱
        boolean collisionOnGround = mc.world.getBlockCollisions(null, 
            new Box(p.x - 0.3, p.y - 0.05, p.z - 0.3, p.x + 0.3, p.y, p.z + 0.3)).iterator().hasNext();

        // 只要满足任一条件，即视为 OnGround
        boolean finalOnGround = physicallyOnGround || collisionOnGround;

        // 发送 1.21.4 格式包
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
            p.x, p.y, p.z, finalOnGround, false // 瞬移时 horizontalCollision 设为 false 以减少服务器端计算
        ));
    }

    private void handleAutoSwitch(Entity target) {
        if (!autoSwitch.get()) return;
        Predicate<ItemStack> predicate = s -> s.getItem() instanceof MaceItem || s.getItem() instanceof AxeItem || s.isIn(ItemTags.SWORDS);
        FindItemResult r = InvUtils.find(predicate, 0, 8);
        
        if (target instanceof PlayerEntity player && player.isBlocking() && shieldMode.get() == ShieldMode.Break) {
            FindItemResult axe = InvUtils.find(itemStack -> itemStack.getItem() instanceof AxeItem, 0, 8);
            if (axe.found()) r = axe;
        }
        
        if (r.found()) {
            if (!swapped) {
                previousSlot = mc.player.getInventory().selectedSlot;
                swapped = true;
            }
            InvUtils.swap(r.slot(), false);
        }
    }

    private boolean entityCheck(Entity e) {
        if (e == mc.player || !e.isAlive() || getRealPlayerPos().distanceTo(e.getPos()) > tpRange.get()) return false;
        if (!entities.get().contains(e.getType())) return false;
        if (ignoreNamed.get() && e.hasCustomName()) return false;
        
        if (ignoreTamed.get() && e instanceof Tameable tame && tame.getOwner() != null && tame.getOwner().equals(mc.player)) return false;
        
        if (ignorePassive.get()) {
            if (e instanceof EndermanEntity ender && !ender.isAngry()) return false;
            if (e instanceof PiglinEntity pig && !pig.isAttacking()) return false;
            if (e instanceof ZombifiedPiglinEntity zpig && !zpig.isAttacking()) return false;
            if (e instanceof WolfEntity wolf && !wolf.isAttacking()) return false;
            if (e instanceof AnimalEntity) return false;
        }
        
        if (e instanceof PlayerEntity player) {
            if (player.isCreative() || player.isSpectator()) return false;
            if (!Friends.get().shouldAttack(player)) return false;
        }
        
        return e instanceof LivingEntity;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (core != null && renderPath.get() && !renderSnapshot.isEmpty()) {
            // 根据是否回弹变色
            Color drawColor = snapshotIsRed ? (Color) lagbackPathColor.get() : (Color) pathColor.get();
            // 渲染锁定的快照
            core.renderFixedSnapshot(event, renderSnapshot, drawColor, snapshotStep);
        }
    }

    @Override
    public void onDeactivate() {
        if (core != null) {
            core.desyncPos = null;
            core.cleanup();
        }
        if (swapBack.get() && swapped && previousSlot != -1) {
            InvUtils.swap(previousSlot, false);
            swapped = false;
        }
    }

    public enum Weapon { Sword, Axe, Mace, All }
    public enum ShieldMode { Break, Ignore, None }
}