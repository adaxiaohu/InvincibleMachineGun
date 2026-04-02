package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.mixin.InventoryAccessor;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class XTpaura extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTpOptions = settings.createGroup("TP Options");
    private final SettingGroup sgMace = settings.createGroup("Mace Exploit");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final List<Vec3d> renderPath = new ArrayList<>();

    // ================= [ General Settings ] =================
    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("目标实体")
        .description("选择要攻击的实体类型。")
        .defaultValue(EntityType.PLAYER, EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, EntityType.SPIDER)
        .build()
    );

    private final Setting<Integer> attackDelay = sgGeneral.add(new IntSetting.Builder()
        .name("攻击延迟")
        .description("攻击延迟(毫秒)，当不使用武器冷却时生效。")
        .defaultValue(800)
        .min(1)
        .sliderRange(1, 2000)
        .build()
    );

    private final Setting<Integer> attackTimes = sgGeneral.add(new IntSetting.Builder()
        .name("攻击次数")
        .description("单次瞬移的攻击次数（发包次数）。")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 200)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("目标范围")
        .description("最大锁敌距离。")
        .defaultValue(50.0)
        .min(1.0)
        .sliderRange(1.0, 100.0)
        .build()
    );

    private final Setting<Boolean> bvr = sgGeneral.add(new BoolSetting.Builder()
        .name("严格可见性/范围目标筛选旁路")
        .description("严格可见性/范围目标筛选旁路。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> critical = sgGeneral.add(new BoolSetting.Builder()
        .name("暴击伤害")
        .description("通过发包伪造微小下落实现刀刀暴击。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> findVecToAttack = sgGeneral.add(new BoolSetting.Builder()
        .name("自动寻找攻击点")
        .description("自动寻找目标附近安全的无碰撞点，防止卡墙。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> prev = sgGeneral.add(new DoubleSetting.Builder()
        .name("目标移动位置预测")
        .description("目标移动位置预测(Tick)，用于补偿延迟。")
        .defaultValue(0.0)
        .min(0.0)
        .sliderRange(0.0, 5.0)
        .build()
    );

    private final Setting<Boolean> swingHand = sgGeneral.add(new BoolSetting.Builder()
        .name("挥手动画")
        .description("攻击时在客户端进行挥手动画。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> useCooldown = sgGeneral.add(new BoolSetting.Builder()
        .name("武器冷却")
        .description("依据武器自身的冷却时间进行攻击。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> useCooldownBaseTime = sgGeneral.add(new DoubleSetting.Builder()
        .name("武器冷却阈值")
        .description("武器冷却阈值。")
        .defaultValue(0.75)
        .min(0.1)
        .sliderRange(0.1, 1.0)
        .visible(useCooldown::get)
        .build()
    );

    private final Setting<SortPriority> sortPriority = sgGeneral.add(new EnumSetting.Builder<SortPriority>()
        .name("目标优先级")
        .description("选择目标的优先级排序方式。")
        .defaultValue(SortPriority.LowestDistance)
        .build()
    );

    // ================= [ TP Options ] =================
    public enum VClipMode { NONE, NORMAL, UP, DOWN }

    private final Setting<VClipMode> searchVclipMode = sgTpOptions.add(new EnumSetting.Builder<VClipMode>()
        .name("VClip寻路模式")
        .description("穿墙寻路的模式。")
        .defaultValue(VClipMode.UP)
        .build()
    );

    private final Setting<Double> moveDistance = sgTpOptions.add(new DoubleSetting.Builder()
        .name("移动距离")
        .description("每次发包切片的最大距离(防拉回关键)。")
        .defaultValue(8.0)
        .min(1.0)
        .sliderRange(1.0, 10.0)
        .build()
    );

    private final Setting<Double> searchFindStep = sgTpOptions.add(new DoubleSetting.Builder()
        .name("VClip寻路精度步长")
        .description("VClip寻路精度步长。")
        .defaultValue(1.0)
        .min(0.1)
        .sliderRange(0.1, 2.0)
        .build()
    );

    private final Setting<Boolean> back = sgTpOptions.add(new BoolSetting.Builder()
        .name("是否瞬移回原位")
        .description("打完人后是否瞬移回原位（实现幽灵杀）。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> allowIntoVoid = sgTpOptions.add(new BoolSetting.Builder()
        .name("是否允许进入虚空")
        .description("是否允许寻路进入虚空。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> limitPacket = sgTpOptions.add(new IntSetting.Builder()
        .name("最大位移包数量")
        .description("单Tick允许发送的最大位移包数量（防Kick）。")
        .defaultValue(20)
        .min(5)
        .sliderRange(5, 50)
        .build()
    );

    private final Setting<Boolean> printWhenTooManyPacket = sgTpOptions.add(new BoolSetting.Builder()
        .name("是否超出包限制时提示")
        .description("当超出包限制时在聊天框提示。")
        .defaultValue(true)
        .build()
    );

    // ================= [ Mace Exploit ] =================
    private final Setting<Boolean> useMace = sgMace.add(new BoolSetting.Builder()
        .name("是否使用Mace秒杀漏洞")
        .description("使用 1.21 Mace 秒杀漏洞（利用距离累计高空下落伤害）。")
        .defaultValue(false)
        .build()
    );

    // =================[ Render ] =================
    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("是否渲染目标")
        .description("渲染当前目标。")
        .defaultValue(true)
        .build()
    );



    // 内部状态
    private Entity target;
    private long lastAttackTime = 0;

    public XTpaura() {
        super(AddonTemplate.CATEGORY, "百米刀", " “我允许你先走99米。”。,娱乐功能，抄袭了gcore，裤子条纹得来的");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        updateTarget();
        doAura();
    }

   @EventHandler
private void onRender3D(Render3DEvent event) {
    if (render.get() && !renderPath.isEmpty()) {
        for (int i = 0; i < renderPath.size() - 1; i++) {
            event.renderer.line(renderPath.get(i).getX(), renderPath.get(i).getY(), renderPath.get(i).getZ(), 
                               renderPath.get(i + 1).getX(), renderPath.get(i + 1).getY(), renderPath.get(i + 1).getZ(), Color.RED);
        }
    }
}

    @Override
    public String getInfoString() {
        return target != null ? EntityUtils.getName(target) : null;
    }

    private void updateTarget() {
        List<Entity> potentialTargets = new ArrayList<>();
        TargetUtils.getList(potentialTargets, this::entityCheck, sortPriority.get(), 1);
        
        if (!potentialTargets.isEmpty()) {
            target = potentialTargets.get(0);
        } else {
            target = null;
        }
    }

    private boolean entityCheck(Entity entity) {
        if (!(entity instanceof LivingEntity) || !entity.isAlive() || entity == mc.player) return false;
        if (!entities.get().contains(entity.getType())) return false;
        if (mc.player.distanceTo(entity) > range.get()) return false;
        if (entity instanceof PlayerEntity p) {
            if (p.isCreative() || p.isSpectator()) return false;
        }
        return true;
    }

    private boolean isReadyToAttack() {
        if (useCooldown.get()) {
            return mc.player.getAttackCooldownProgress(0.0f) >= useCooldownBaseTime.get();
        } else {
            return System.currentTimeMillis() - lastAttackTime >= attackDelay.get();
        }
    }

   private void doAura() {

    if (!isReadyToAttack() || target == null || target.isRemoved() || !target.isAlive()) return;


    Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
    Vec3d targetBasePos = new Vec3d(target.getX(), target.getY(), target.getZ());


    if (playerPos.distanceTo(targetBasePos) > range.get()) return;

  
    Vec3d targetVec = new Vec3d(
        targetBasePos.getX() + target.getVelocity().x * prev.get(),
        targetBasePos.getY() + target.getVelocity().y * prev.get(),
        targetBasePos.getZ() + target.getVelocity().z * prev.get()
    );


    Vec3d attackPos = null;

    Vec3d[] attackTries = {
        new Vec3d(targetVec.getX(), targetVec.getY() + target.getStandingEyeHeight() + 0.5, targetVec.getZ()),
        new Vec3d(targetVec.getX() + 0.2, targetVec.getY(), targetVec.getZ() + 0.2),
        targetVec
    };
    for (Vec3d p : attackTries) {
        if (isSpaceEmpty(p)) {
            attackPos = p;
            break;
        }
    }
    if (attackPos == null) return;


    Vec3d vClipStart = null; 
    Vec3d vClipEnd = null;   
    boolean foundPath = false;

  
    double horizontalDist = new Vec3d(playerPos.x, 0, playerPos.z).distanceTo(new Vec3d(attackPos.x, 0, attackPos.z));
    

    double maxHeight = Math.max(playerPos.y, attackPos.y);
    double startSearchHeight = maxHeight + 3.0; 

    for (double yLevel = startSearchHeight; yLevel < startSearchHeight + 50.0; yLevel += 2.0) {
        Vec3d testUp = new Vec3d(playerPos.x, yLevel, playerPos.z);
        Vec3d testTargetUp = new Vec3d(attackPos.x, yLevel, attackPos.z);
        

        if (horizontalDist < 0.8) {
            if (isSpaceEmpty(testUp)) {
                vClipStart = testUp;
                vClipEnd = testUp;
                foundPath = true;
                break;
            }
        } 
    
        else if (isSpaceEmpty(testUp) && isSpaceEmpty(testTargetUp) && hasClearPath(testUp, testTargetUp)) {
            vClipStart = testUp;
            vClipEnd = testTargetUp;
            foundPath = true;
            break;
        }
    }

    if (!foundPath) return;

 

    
    renderPath.clear();
    renderPath.add(playerPos);
    renderPath.add(vClipStart);
    renderPath.add(vClipEnd);
    renderPath.add(attackPos);


    int maxPackets = (int) (
        Math.ceil(playerPos.distanceTo(vClipStart) / moveDistance.get()) +
        Math.ceil(vClipStart.distanceTo(vClipEnd) / moveDistance.get()) +
        Math.ceil(vClipEnd.distanceTo(attackPos) / moveDistance.get())
    ) + 5;

    if (maxPackets > limitPacket.get()) {
        if (printWhenTooManyPacket.get()) ChatUtils.warning("百米刀: 路径过长 (" + maxPackets + " 包)，已拦截。");
        return;
    }

   
    lastAttackTime = System.currentTimeMillis();

 
    for (int i = 0; i < 3; i++) sendC04(playerPos.getX(), playerPos.getY(), playerPos.getZ(), false);

  
    sendC04(vClipStart.getX(), vClipStart.getY(), vClipStart.getZ(), false);
    if (vClipStart.distanceTo(vClipEnd) > 0.1) {
    sendC04(vClipEnd.getX(), vClipEnd.getY(), vClipEnd.getZ(), false);
    }
    sendC04(attackPos.getX(), attackPos.getY(), attackPos.getZ(), false);

  
    if (critical.get()) {
        sendC04(attackPos.getX(), attackPos.getY() + 0.01, attackPos.getZ(), false);
        sendC04(attackPos.getX(), attackPos.getY(), attackPos.getZ(), false);
    }

    
    int oldSlot = ((InventoryAccessor) mc.player.getInventory()).getSelectedSlot();
    int maceSlot = -1;
    if (useMace.get()) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem().toString().contains("mace")) {
                maceSlot = i;
                break;
            }
        }
    }

    if (maceSlot != -1) {
        ((InventoryAccessor) mc.player.getInventory()).setSelectedSlot(maceSlot);
        mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));
        ((InventoryAccessor) mc.player.getInventory()).setSelectedSlot(oldSlot);
    } else {
        for (int i = 0; i < attackTimes.get(); i++) {
            mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));
        }
    }

    if (swingHand.get()) {
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    
     if (back.get()) {
       
        sendC04(vClipEnd.getX(), vClipEnd.getY(), vClipEnd.getZ(), false);
        sendC04(vClipStart.getX(), vClipStart.getY(), vClipStart.getZ(), false);
        
        
        double tinyOffset = 0.01;
        double finalX = playerPos.getX();
        double finalY = playerPos.getY() + tinyOffset; 
        double finalZ = playerPos.getZ();

      
        sendC04(finalX, finalY, finalZ, false); 
        
    
        mc.player.setPosition(finalX, finalY, finalZ);
        
    } else {
    
        mc.player.setPosition(attackPos.getX(), attackPos.getY(), attackPos.getZ());
    }

    // F. 状态清理
    mc.player.fallDistance = 0.0f; 
    mc.player.resetTicksSinceLastAttack();
}


   private boolean hasClearPath(Vec3d start, Vec3d end) {
    double dist = start.distanceTo(end);
    int steps = (int) (dist * 2.5); // 每格检测2.5次，防止漏掉薄方块
    for (int i = 0; i <= steps; i++) {
        Vec3d check = start.lerp(end, (double) i / steps);
        if (!isSpaceEmpty(check)) return false;
    }
    return true;
}

  

    private void sendC04(double x, double y, double z, boolean onGround) {
        // 1.21.x Record Data Packet 发包规范
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, onGround, mc.player.horizontalCollision));
    }

    private boolean isSpaceEmpty(Vec3d pos) {
    // 模拟一个 0.6宽 x 1.8高的碰撞箱
    Box box = new Box(pos.getX() - 0.3, pos.getY(), pos.getZ() - 0.3, pos.getX() + 0.3, pos.getY() + 1.8, pos.getZ() + 0.3);
    return mc.world.isSpaceEmpty(box);
}

  
    private Vec3d findVecToAttack(Vec3d targetVec, double targetHeight) {
        double startY = targetVec.getY();
        double endY = targetVec.getY() + targetHeight + 1.0;

       
        if (isSpaceEmpty(new Vec3d(targetVec.getX(), endY, targetVec.getZ()))) {
            return new Vec3d(targetVec.getX(), endY, targetVec.getZ());
        }


        double[][] offsets = {
            {1.0, 0.0}, {-1.0, 0.0}, {0.0, 1.0}, {0.0, -1.0},
            {0.7, 0.7}, {-0.7, -0.7}, {0.7, -0.7}, {-0.7, 0.7}
        };

     
        for (double y = startY; y <= endY; y += 1.0) {
            for (double[] offset : offsets) {
                double checkX = targetVec.getX() + offset[0];
                double checkZ = targetVec.getZ() + offset[1];
                if (isSpaceEmpty(new Vec3d(checkX, y, checkZ))) {
                    return new Vec3d(checkX, y, checkZ);
                }
            }
        }
        
        return null; 
    }

    
      private Vec3d findVClipVecToMove(Vec3d start, Vec3d end, double step, boolean allowVoid) {
        VClipMode mode = searchVclipMode.get();
        if (mode == VClipMode.NONE) {
            return start; 
        }

        double clipY = start.y;
        boolean foundSafePath = false;
        double maxSearchDistance = 25.0; 

        if (mode == VClipMode.UP || mode == VClipMode.NORMAL) {
            for (double i = 0.0; i < maxSearchDistance; i += step) {
                clipY = start.getY() + i;
                Vec3d testStart = new Vec3d(start.getX(), clipY, start.getZ());
                Vec3d testEnd = new Vec3d(end.getX(), clipY, end.getZ());
                
               
                if (hasClearPath(testStart, testEnd)) {
                    foundSafePath = true;
                    break;
                }
            }
        } else if (mode == VClipMode.DOWN) {
            for (double i = 0.0; i < maxSearchDistance; i += step) {
                clipY = start.getY() - i;
                if (!allowVoid && clipY < mc.world.getBottomY()) break; 
                
                Vec3d testStart = new Vec3d(start.getX(), clipY, start.getZ());
                Vec3d testEnd = new Vec3d(end.getX(), clipY, end.getZ());

                if (hasClearPath(testStart, testEnd)) {
                    foundSafePath = true;
                    break;
                }
            }
        }

        if (foundSafePath) {
            return new Vec3d(start.getX(), clipY, start.getZ());
        }

        return start;
    }
}