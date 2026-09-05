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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.TamableAnimal;
import meteordevelopment.meteorclient.systems.friends.Friends;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;

public class XTpaura extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTpOptions = settings.createGroup("TP Options");
    private final SettingGroup sgMace = settings.createGroup("Mace Exploit");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final List<Vec3> renderPath = new ArrayList<>();

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

    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("忽略好友")
        .description("忽略好友列表中的玩家。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ignoreNamed = sgGeneral.add(new BoolSetting.Builder()
        .name("忽略命名实体")
        .description("忽略带有自定义名称的实体。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ignoreTamed = sgGeneral.add(new BoolSetting.Builder()
        .name("忽略驯服实体")
        .description("忽略被驯服的生物。")
        .defaultValue(false)
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
        if (mc.player == null || mc.level == null) return;

        updateTarget();
        doAura();
    }

   @EventHandler
private void onRender3D(Render3DEvent event) {
    if (render.get() && !renderPath.isEmpty()) {
        for (int i = 0; i < renderPath.size() - 1; i++) {
            event.renderer.line(renderPath.get(i).x(), renderPath.get(i).y(), renderPath.get(i).z(), 
                               renderPath.get(i + 1).x(), renderPath.get(i + 1).y(), renderPath.get(i + 1).z(), Color.RED);
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
        if (entity instanceof Player p) {
            if (p.isCreative() || p.isSpectator()) return false;
            if (ignoreFriends.get() && Friends.get().isFriend(p)) return false;
        }
        if (ignoreNamed.get() && entity.hasCustomName()) return false;
        if (ignoreTamed.get() && entity instanceof TamableAnimal && ((TamableAnimal) entity).isTame()) return false;
        return true;
    }

    private boolean isReadyToAttack() {
        if (useCooldown.get()) {
            return mc.player.getAttackStrengthScale(0.0f) >= useCooldownBaseTime.get();
        } else {
            return System.currentTimeMillis() - lastAttackTime >= attackDelay.get();
        }
    }

   private void doAura() {

    if (!isReadyToAttack() || target == null || target.isRemoved() || !target.isAlive()) return;


    Vec3 playerPos = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());
    Vec3 targetBasePos = new Vec3(target.getX(), target.getY(), target.getZ());


    if (playerPos.distanceTo(targetBasePos) > range.get()) return;

  
    Vec3 targetVec = new Vec3(
        targetBasePos.x() + target.getDeltaMovement().x * prev.get(),
        targetBasePos.y() + target.getDeltaMovement().y * prev.get(),
        targetBasePos.z() + target.getDeltaMovement().z * prev.get()
    );


    Vec3 attackPos = null;

    Vec3[] attackTries = {
        new Vec3(targetVec.x(), targetVec.y() + target.getEyeHeight() + 0.5, targetVec.z()),
        new Vec3(targetVec.x() + 0.2, targetVec.y(), targetVec.z() + 0.2),
        targetVec
    };
    for (Vec3 p : attackTries) {
        if (isSpaceEmpty(p)) {
            attackPos = p;
            break;
        }
    }
    if (attackPos == null) return;


    Vec3 vClipStart = null; 
    Vec3 vClipEnd = null;   
    boolean foundPath = false;

  
    double horizontalDist = new Vec3(playerPos.x, 0, playerPos.z).distanceTo(new Vec3(attackPos.x, 0, attackPos.z));
    

    double maxHeight = Math.max(playerPos.y, attackPos.y);
    double startSearchHeight = maxHeight + 3.0; 

    for (double yLevel = startSearchHeight; yLevel < startSearchHeight + 50.0; yLevel += 2.0) {
        Vec3 testUp = new Vec3(playerPos.x, yLevel, playerPos.z);
        Vec3 testTargetUp = new Vec3(attackPos.x, yLevel, attackPos.z);
        

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

 
    for (int i = 0; i < 3; i++) sendC04(playerPos.x(), playerPos.y(), playerPos.z(), false);

  
    sendC04(vClipStart.x(), vClipStart.y(), vClipStart.z(), false);
    if (vClipStart.distanceTo(vClipEnd) > 0.1) {
    sendC04(vClipEnd.x(), vClipEnd.y(), vClipEnd.z(), false);
    }
    sendC04(attackPos.x(), attackPos.y(), attackPos.z(), false);

  
    if (critical.get()) {
        sendC04(attackPos.x(), attackPos.y() + 0.01, attackPos.z(), false);
        sendC04(attackPos.x(), attackPos.y(), attackPos.z(), false);
    }

    
    int oldSlot = ((InventoryAccessor) mc.player.getInventory()).getSelectedSlot();
    int maceSlot = -1;
    if (useMace.get()) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).getItem().toString().contains("mace")) {
                maceSlot = i;
                break;
            }
        }
    }

    if (maceSlot != -1) {
        ((InventoryAccessor) mc.player.getInventory()).setSelectedSlot(maceSlot);
        mc.getConnection().send(new ServerboundAttackPacket(target.getId()));
        ((InventoryAccessor) mc.player.getInventory()).setSelectedSlot(oldSlot);
    } else {
        for (int i = 0; i < attackTimes.get(); i++) {
            mc.getConnection().send(new ServerboundAttackPacket(target.getId()));
        }
    }

    if (swingHand.get()) {
        mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    
     if (back.get()) {
       
        sendC04(vClipEnd.x(), vClipEnd.y(), vClipEnd.z(), false);
        sendC04(vClipStart.x(), vClipStart.y(), vClipStart.z(), false);
        
        
        double tinyOffset = 0.01;
        double finalX = playerPos.x();
        double finalY = playerPos.y() + tinyOffset; 
        double finalZ = playerPos.z();

      
        sendC04(finalX, finalY, finalZ, false); 
        
    
        mc.player.setPos(finalX, finalY, finalZ);
        
    } else {
    
        mc.player.setPos(attackPos.x(), attackPos.y(), attackPos.z());
    }

    // F. 状态清理
    mc.player.fallDistance = 0.0f; 
    mc.player.resetOnlyAttackStrengthTicker();
}


   private boolean hasClearPath(Vec3 start, Vec3 end) {
    double dist = start.distanceTo(end);
    int steps = (int) (dist * 2.5); // 每格检测2.5次，防止漏掉薄方块
    for (int i = 0; i <= steps; i++) {
        Vec3 check = start.lerp(end, (double) i / steps);
        if (!isSpaceEmpty(check)) return false;
    }
    return true;
}

  

    private void sendC04(double x, double y, double z, boolean onGround) {
        // 1.21.x Record Data Packet 发包规范
        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(x, y, z, onGround, mc.player.horizontalCollision));
    }

    private boolean isSpaceEmpty(Vec3 pos) {
    // 模拟一个 0.6宽 x 1.8高的碰撞箱
    AABB box = new AABB(pos.x() - 0.3, pos.y(), pos.z() - 0.3, pos.x() + 0.3, pos.y() + 1.8, pos.z() + 0.3);
    return mc.level.noCollision(box);
}

  
    private Vec3 findVecToAttack(Vec3 targetVec, double targetHeight) {
        double startY = targetVec.y();
        double endY = targetVec.y() + targetHeight + 1.0;

       
        if (isSpaceEmpty(new Vec3(targetVec.x(), endY, targetVec.z()))) {
            return new Vec3(targetVec.x(), endY, targetVec.z());
        }


        double[][] offsets = {
            {1.0, 0.0}, {-1.0, 0.0}, {0.0, 1.0}, {0.0, -1.0},
            {0.7, 0.7}, {-0.7, -0.7}, {0.7, -0.7}, {-0.7, 0.7}
        };

     
        for (double y = startY; y <= endY; y += 1.0) {
            for (double[] offset : offsets) {
                double checkX = targetVec.x() + offset[0];
                double checkZ = targetVec.z() + offset[1];
                if (isSpaceEmpty(new Vec3(checkX, y, checkZ))) {
                    return new Vec3(checkX, y, checkZ);
                }
            }
        }
        
        return null; 
    }

    
      private Vec3 findVClipVecToMove(Vec3 start, Vec3 end, double step, boolean allowVoid) {
        VClipMode mode = searchVclipMode.get();
        if (mode == VClipMode.NONE) {
            return start; 
        }

        double clipY = start.y;
        boolean foundSafePath = false;
        double maxSearchDistance = 25.0; 

        if (mode == VClipMode.UP || mode == VClipMode.NORMAL) {
            for (double i = 0.0; i < maxSearchDistance; i += step) {
                clipY = start.y() + i;
                Vec3 testStart = new Vec3(start.x(), clipY, start.z());
                Vec3 testEnd = new Vec3(end.x(), clipY, end.z());
                
               
                if (hasClearPath(testStart, testEnd)) {
                    foundSafePath = true;
                    break;
                }
            }
        } else if (mode == VClipMode.DOWN) {
            for (double i = 0.0; i < maxSearchDistance; i += step) {
                clipY = start.y() - i;
                if (!allowVoid && clipY < mc.level.getMinY()) break; 
                
                Vec3 testStart = new Vec3(start.x(), clipY, start.z());
                Vec3 testEnd = new Vec3(end.x(), clipY, end.z());

                if (hasClearPath(testStart, testEnd)) {
                    foundSafePath = true;
                    break;
                }
            }
        }

        if (foundSafePath) {
            return new Vec3(start.x(), clipY, start.z());
        }

        return start;
    }
}