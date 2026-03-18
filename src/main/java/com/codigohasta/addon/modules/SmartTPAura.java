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
import meteordevelopment.meteorclient.mixininterface.IPlayerMoveC2SPacket;
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
import net.minecraft.util.math.Box; 
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
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
        .defaultValue(10)
        .min(1.0)
        .max(100)
        .build()
    );

    private final Setting<Boolean> padding = sgTeleport.add(new BoolSetting.Builder()
        .name("Paper 预热包")
        .description("好像没用。瞬移前发送原地包增加服务器 Tick 计数。")
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

    private final Setting<Boolean> s08Return = sgTeleport.add(new BoolSetting.Builder()
        .name("防真传 ")
        .description("降低到目标脸上的几率。当发生真传送(S08把你拉回到了目标脸上)时，立刻回原点。")
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

     // S08 防真传
    private Vec3d originalPos = null;          // 记录攻击前原本站的位置
    private boolean pendingS08Return = false;  // S08 触发标记
    private int attackTickCounter = 0;         // 攻击计时器 (防止拉回到太久以前的位置)

    private List<Vec3d> renderSnapshot = new ArrayList<>();
    private double snapshotStep = 9.0;
    private boolean snapshotIsRed = false;

    public SmartTPAura() {
        super(AddonTemplate.CATEGORY, "SmartTPAura", "百米刀，参考了很多别的代码做出的。复杂场景效果不好，LB/Jigsaw/TS。");
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
        // 监听 S08 拉回包
        if (event.packet instanceof PlayerPositionLookS2CPacket) {
            if (core != null) {
                core.desyncPos = null;
                snapshotIsRed = true; 
                
                // [新增]: 如果开启了防真传，且记录了原位置，且距离上次攻击不超过 40 ticks (2秒内)
                if (s08Return.get() && originalPos != null && attackTickCounter < 40) {
                    pendingS08Return = true; // 触发下个 Tick 的紧急回传
                }
            }
        }
    }

    private Vec3d getRealPlayerPos() {
        if (mc.player == null) return Vec3d.ZERO;
        return new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
    }

    private Vec3d getOffset(Vec3d base) {
        double dx = 0.05; // 水平偏移0.05格
        double dy = 0.01; // 垂直偏移0.01格

        Vec3d[] shuffledOffsets = new Vec3d[]{
                base.add(dx, dy, 0),
                base.add(-dx, dy, 0),
                base.add(0, dy, dx),
                base.add(0, dy, -dx),
                base.add(dx, dy, dx),
                base.add(-dx, dy, -dx),
                base.add(-dx, dy, dx),
                base.add(dx, dy, -dx)
        };

        // 打乱偏移方向，防止反作弊检测出固定规律
        List<Vec3d> offsetList = new ArrayList<>(Arrays.asList(shuffledOffsets));
        Collections.shuffle(offsetList);

        for (Vec3d pos : offsetList) {
            // 检测这个偏移点会不会让你卡进墙里
            Box box = mc.player.getBoundingBox().offset(pos.x - base.x, pos.y - base.y, pos.z - base.z);
            if (!mc.world.getBlockCollisions(null, box).iterator().hasNext()) {
                return pos;
            }
        }

        // 如果四周都被方块堵死，就只稍微抬高一点点
        Vec3d noHorizontal = base.add(0, dy, 0);
        Box box = mc.player.getBoundingBox().offset(0, dy, 0);
        if (!mc.world.getBlockCollisions(null, box).iterator().hasNext()) {
            return noHorizontal;
        }

        return base; // 实在没地方偏就回原位
    }

     @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || core == null) return;
        
        // [新增]: 每一 Tick 累加攻击计时器 (用于判断 S08 是否为攻击导致的真传)
        attackTickCounter++;

      
        //[新增]: S08 紧急防真传处理 
    
        if (pendingS08Return) {
            pendingS08Return = false;
            
            // 此时客户端已经被 S08 强行拉到了目标面前 (比如山头上)，获取这个假拉回位置
            Vec3d currentLagPos = getRealPlayerPos();
            
            // 如果拉回位置距离我们的安全点大于 3 米，说明触发了恶性真传
            if (originalPos != null && currentLagPos.distanceTo(originalPos) > 3.0) {
                // 1. 紧急重新寻路：从被拉回的假位置，算一条路飞回安全的原坐标
                // 注意：这里使用 Vec3d 的重载方法，而不是 Entity
                core.updatePathfinding(currentLagPos, originalPos, maxStep.get());
                
                // 2. 获取压缩拉直后的逃生路径
                List<Vec3d> emergencyPath = core.getEfficientPath(maxStep.get());
                
                if (emergencyPath != null && !emergencyPath.isEmpty()) {
                    // 3. 瞬间发包飞回去
                    for (Vec3d p : emergencyPath) {
                        sendPacketToNetwork(p);
                    }
                    // 4. 精准对齐安全点终点
                    sendPacketToNetwork(originalPos);
                    
                    // 5. 强制把本地客户端视角也拉回去，防止客户端和服务器来回抽搐拉扯
                    mc.player.setPosition(originalPos);
                    
                    // 清理安全点标记
                    originalPos = null; 
                }
            }
            return; 
        }
        // ==========================================

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
        
        // 传入 target 实体，让 Core 计算最佳攻击位 (不卡墙坐标)
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
                
                // [新增]: 在确认要发包飞走之前，记录下当前的绝对安全坐标，并重置 S08 判定计时器
                originalPos = localPos;
                attackTickCounter = 0;

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
                    
                    // F. 防拉扯微调 (Anti-Rubberband Offset) 
                    Vec3d offsetPos = getOffset(localPos);
                    sendPacketToNetwork(offsetPos); // 发送最后一个带有极其微小偏移的假包
                    mc.player.setPosition(offsetPos); // 让本地客户端真实移动这 0.05 格，强制服务端与客户端在此刻重新对齐状态
                }
            }
        }
    }

    private void sendPacketToNetwork(Vec3d p) {
    if (p == null || mc.getNetworkHandler() == null || mc.player == null) return;
    
    double floorY = core.getFloorHeightAt(p);
    boolean physicallyOnGround = Math.abs(p.y - (Math.floor(p.y) + floorY)) < 0.01;
    boolean collisionOnGround = mc.world.getBlockCollisions(null, 
        new Box(p.x - 0.3, p.y - 0.05, p.z - 0.3, p.x + 0.3, p.y, p.z + 0.3)).iterator().hasNext();
    boolean finalOnGround = physicallyOnGround || collisionOnGround;

    // 构建数据包并打上 1337 标签
    PlayerMoveC2SPacket packet = new PlayerMoveC2SPacket.PositionAndOnGround(
        p.x, p.y, p.z, finalOnGround, false 
    );
    
    // 告诉 Meteor 客户端和其他模块，“这是一个伪造包，请不要用它来更新本地坐标预测！”
    ((IPlayerMoveC2SPacket) packet).meteor$setTag(1337); 
    
    mc.getNetworkHandler().sendPacket(packet);
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