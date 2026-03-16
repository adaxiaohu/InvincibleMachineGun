package com.codigohasta.addon.modules;

// 1. 导入你的主类
import com.codigohasta.addon.AddonTemplate;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
// 注意：移除了 Categories 的导入
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import java.util.ArrayList;
import java.util.List;

public class SilentAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgTiming = settings.createGroup("Timing");

    // General
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("攻击范围")
        .defaultValue(4.5)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Boolean> throughWalls = sgGeneral.add(new BoolSetting.Builder()
        .name("through-walls")
        .description("穿墙攻击")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> wallsRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("walls-range")
        .description("穿墙攻击范围")
        .defaultValue(3.5)
        .min(0)
        .sliderMax(6)
        .visible(throughWalls::get)
        .build()
    );

    private final Setting<Boolean> swingHand = sgGeneral.add(new BoolSetting.Builder()
        .name("swing-hand")
        .description("是否显示手臂摆动动画")
        .defaultValue(true)
        .build()
    );

    // Targeting
    private final Setting<Boolean> players = sgTargeting.add(new BoolSetting.Builder()
        .name("players")
        .description("攻击玩家")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> mobs = sgTargeting.add(new BoolSetting.Builder()
        .name("mobs")
        .description("攻击敌对生物")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> animals = sgTargeting.add(new BoolSetting.Builder()
        .name("animals")
        .description("攻击动物")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> maxTargets = sgTargeting.add(new IntSetting.Builder()
        .name("max-targets")
        .description("最大目标数量")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 5)
        .build()
    );

    // Timing
    private final Setting<Integer> attackDelay = sgTiming.add(new IntSetting.Builder()
        .name("attack-delay")
        .description("攻击延迟（刻）")
        .defaultValue(10)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    private int attackTimer;
    private final List<Entity> targets = new ArrayList<>();

    public SilentAura() {
        // 2. 修改这里：使用自定义分类
        super(AddonTemplate.CATEGORY, "静默攻击", "直接发送攻击包实现静默攻击，不转动视角。只是一个测试用的");
    }

    @Override
    public void onActivate() {
        attackTimer = 0;
        targets.clear();
    }

    @Override
    public void onDeactivate() {
        targets.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || !mc.player.isAlive()) return;

        // 更新攻击计时器
        if (attackTimer > 0) {
            attackTimer--;
            return;
        }

        // 获取目标
        targets.clear();
        TargetUtils.getList(targets, this::entityCheck, SortPriority.ClosestAngle, maxTargets.get());

        if (targets.isEmpty()) return;

        // 攻击所有目标
        for (Entity target : targets) {
            sendSilentAttack(target);
        }

        // 重置计时器
        attackTimer = attackDelay.get();
    }

    private void sendSilentAttack(Entity target) {
        // 发送实体攻击包
        // 注意：attack() 方法在不同版本 Meteor/MC 可能参数不同，如果有报错请告知
        PlayerInteractEntityC2SPacket attackPacket = PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking());
        mc.getNetworkHandler().sendPacket(attackPacket);

        // 发送手臂摆动包（可选）
        if (swingHand.get()) {
            HandSwingC2SPacket swingPacket = new HandSwingC2SPacket(Hand.MAIN_HAND);
            mc.getNetworkHandler().sendPacket(swingPacket);
        }
    }

    private boolean entityCheck(Entity entity) {
        if (!(entity instanceof LivingEntity) || !entity.isAlive()) return false;
        if (entity.equals(mc.player)) return false;

        double distance = mc.player.distanceTo(entity);
        double effectiveRange = throughWalls.get() ? wallsRange.get() : range.get();

        // 距离检查
        if (distance > effectiveRange) return false;

        // 视线检查（如果不穿墙）
        if (!throughWalls.get() && !mc.player.canSee(entity)) return false;

        // 类型检查
        if (entity instanceof PlayerEntity) {
            if (!players.get()) return false;
            PlayerEntity player = (PlayerEntity) entity;
            if (player.isCreative()) return false;
        }
        else if (isHostile(entity)) {
            if (!mobs.get()) return false;
        }
        else if (isAnimal(entity)) {
            if (!animals.get()) return false;
        }
        else {
            return false;
        }

        return true;
    }

    private boolean isHostile(Entity entity) {
        String entityName = EntityUtils.getName(entity);
        if (entityName == null) return false;
        return entityName.contains("Zombie") || 
               entityName.contains("Skeleton") || 
               entityName.contains("Creeper") ||
               entityName.contains("Spider") ||
               entityName.contains("Enderman") ||
               entityName.contains("Witch") ||
               entityName.contains("Piglin") ||
               entityName.contains("Warden");
    }

    private boolean isAnimal(Entity entity) {
        String entityName = EntityUtils.getName(entity);
        if (entityName == null) return false;
        return entityName.contains("Cow") || 
               entityName.contains("Pig") || 
               entityName.contains("Sheep") ||
               entityName.contains("Chicken") ||
               entityName.contains("Wolf") ||
               entityName.contains("Cat") ||
               entityName.contains("Horse");
    }

    @Override
    public String getInfoString() {
        if (!targets.isEmpty()) {
            return EntityUtils.getName(targets.get(0));
        }
        return null;
    }
}