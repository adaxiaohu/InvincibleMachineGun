package com.codigohasta.addon.modules;


import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.mixininterface.IPlayerMoveC2SPacket;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.MaceItem;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.stream.Collectors;

public class TpAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTiming = settings.createGroup(" (攻击延迟)");
    private final SettingGroup sgTP = settings.createGroup("打击");
    private final SettingGroup sgTargeting = settings.createGroup("目标");
    private final SettingGroup sgWhitelist = settings.createGroup("白名单");
    private final SettingGroup sgRender = settings.createGroup("渲染");

    // --- General ---
    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder().name("自动切重锤").description("自动切到重锤").defaultValue(true).build());
    private final Setting<Boolean> requireMace = sgGeneral.add(new BoolSetting.Builder().name("仅手持重锤").description("仅手持重锤触发").defaultValue(false).build());
    private final Setting<Boolean> swingHand = sgGeneral.add(new BoolSetting.Builder().name("挥手").defaultValue(true).build());

    // ---  Timing  -----
    private final Setting<Boolean> tpsSync = sgTiming.add(new BoolSetting.Builder().name("TPS同步").defaultValue(true).build());
    private final Setting<Double> hitDelayMult = sgTiming.add(new DoubleSetting.Builder().name("冷却倍率").description("·").defaultValue(1.0).min(0.1).sliderMax(1.5).build());
    private final Setting<Integer> attackDelay = sgTiming.add(new IntSetting.Builder().name("延迟").defaultValue(0).min(0).build());

    // --- Teleport Options ---
    public enum Mode { Vanilla, Paper }
    private final Setting<Mode> mode = sgTP.add(new EnumSetting.Builder<Mode>().name("模式").defaultValue(Mode.Paper).build());
    private final Setting<Double> maxRange = sgTP.add(new DoubleSetting.Builder().name("最大范围").defaultValue(49.0).min(1).sliderMax(99).build());
    private final Setting<Boolean> goUp = sgTP.add(new BoolSetting.Builder().name("V-Clip").defaultValue(true).visible(() -> mode.get() == Mode.Paper).build());
    private final Setting<Integer> paperPackets = sgTP.add(new IntSetting.Builder().name("垫包数量").defaultValue(8).min(1).sliderMax(20).build());
    private final Setting<Boolean> returnPos = sgTP.add(new BoolSetting.Builder().name("攻击后回传").defaultValue(true).build());

    // --- Targeting ---
    private final Setting<Set<EntityType<?>>> entities = sgTargeting.add(new EntityTypeListSetting.Builder().name("目标").defaultValue(EntityType.PLAYER).build());
    public enum SortMode { Closest, Angle, LowestHealth }
    private final Setting<SortMode> sortMode = sgTargeting.add(new EnumSetting.Builder<SortMode>().name("排序模式").defaultValue(SortMode.Angle).build());
    private final Setting<Boolean> ignoreNamed = sgTargeting.add(new BoolSetting.Builder().name("忽略命名实体").defaultValue(true).build());

    // --- Whitelist ---
    public enum ListMode { Whitelist, Blacklist, Off }
    private final Setting<ListMode> listMode = sgWhitelist.add(new EnumSetting.Builder<ListMode>().name("白名单模式").defaultValue(ListMode.Off).build());
    private final Setting<String> playerList = sgWhitelist.add(new StringSetting.Builder().name("玩家列表").defaultValue("").build());

    // --- Render ---
    private final Setting<Boolean> renderPath = sgRender.add(new BoolSetting.Builder().name("显示路径").defaultValue(true).build());
    private final Setting<SettingColor> pathColor = sgRender.add(new ColorSetting.Builder().name("轨迹颜色").defaultValue(new SettingColor(255, 0, 0, 100)).build());
    private final Setting<SettingColor> targetColor = sgRender.add(new ColorSetting.Builder().name("目标颜色").defaultValue(new SettingColor(255, 0, 0, 200)).build());

    // --- 内部状态 ---
    private final List<Entity> targets = new ArrayList<>();
    private final List<Vec3d> renderPathNodes = new ArrayList<>();
    private Entity currentTarget;
    private int originalSlot = -1;
    private int delayTimer = 0;

    public TpAura() {
        super(AddonTemplate.CATEGORY, "如来神掌", "如来神掌。重锤的百米，用刀是没伤害的。抄袭了裤子条纹的tp。娱乐功能");
    }

    @Override
    public void onActivate() {
        originalSlot = -1;
        delayTimer = 0;
        renderPathNodes.clear();
    }

    @Override
    public void onDeactivate() {
        if (originalSlot != -1 && autoSwitch.get()) {
            InvUtils.swap(originalSlot, false);
            originalSlot = -1;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // 1. 检查攻击延迟
        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        // 2. 核心：智能冷却检测 (解决1.7伤害)
        float cooldownMult = (float) (0.5f * hitDelayMult.get());
        if (tpsSync.get()) cooldownMult /= (TickRate.INSTANCE.getTickRate() / 20.0f);
        
        if (mc.player.getAttackCooldownProgress(cooldownMult) < 0.99) return;

        // 3. 武器检查
        if (requireMace.get() && !(mc.player.getMainHandStack().getItem() instanceof MaceItem)) {
            if (autoSwitch.get()) {
                FindItemResult mace = InvUtils.find(s -> s.getItem() instanceof MaceItem, 0, 8);
                if (mace.found()) {
                    if (originalSlot == -1) originalSlot = mc.player.getInventory().selectedSlot;
                    InvUtils.swap(mace.slot(), false);
                } else return;
            } else return;
        }

        // 4. 索敌
        SortPriority priority = switch (sortMode.get()) {
            case Closest -> SortPriority.LowestDistance;
            case LowestHealth -> SortPriority.LowestHealth;
            case Angle -> SortPriority.ClosestAngle;
        };

        targets.clear();
        TargetUtils.getList(targets, this::entityCheck, priority, 1);

        if (targets.isEmpty()) {
            currentTarget = null;
            return;
        }

        currentTarget = targets.get(0);

        // 5. 执行轰炸逻辑
        executeTrouserAttack(currentTarget);
        delayTimer = attackDelay.get();
    }

    private void executeTrouserAttack(Entity target) {
        Vec3d startPos = mc.player.getPos();
        Vec3d targetPos = target.getPos();
        double reach = maxRange.get();

        Vec3d finalPos = !invalid(targetPos) ? targetPos : findNearestPos(targetPos);
        if (finalPos == null) return;

        Vec3d highStart = startPos.add(0, reach, 0);
        Vec3d highTarget = finalPos.add(0, reach, 0);

        // 渲染轨迹记录
        renderPathNodes.clear();
        renderPathNodes.add(startPos);
        if (mode.get() == Mode.Paper && goUp.get()) {
            renderPathNodes.add(highStart);
            renderPathNodes.add(highTarget);
        }
        renderPathNodes.add(finalPos);

        // A. 垫包
        int spam = mode.get() == Mode.Paper ? paperPackets.get() : 4;
        for (int i = 0; i < spam; i++) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(false, mc.player.horizontalCollision));
        }

        // B. 移动序列
        if (mode.get() == Mode.Paper && goUp.get()) {
            sendMove(highStart);
            sendMove(highTarget);
        }
        sendMove(finalPos);

        // C. 攻击
        if (swingHand.get()) mc.player.swingHand(Hand.MAIN_HAND);
        mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));

        // D. 回传
        if (returnPos.get()) {
            if (mode.get() == Mode.Paper && goUp.get()) {
                sendMove(highTarget);
                sendMove(highStart);
            }
            sendMove(startPos);
            
            Vec3d offset = getOffset(startPos);
            sendMove(offset);
            mc.player.setPosition(offset.x, offset.y, offset.z);
        } else {
            Vec3d offset = getOffset(finalPos);
            sendMove(offset);
            mc.player.setPosition(offset.x, offset.y, offset.z);
        }
    }

    private void sendMove(Vec3d pos) {
        PlayerMoveC2SPacket packet = new PlayerMoveC2SPacket.PositionAndOnGround(pos.x, pos.y, pos.z, false, false);
        ((IPlayerMoveC2SPacket) packet).meteor$setTag(1337);
        mc.player.networkHandler.sendPacket(packet);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (currentTarget != null) {
            event.renderer.box(currentTarget.getBoundingBox(), targetColor.get(), targetColor.get(), ShapeMode.Lines, 0);
        }

        if (renderPath.get() && !renderPathNodes.isEmpty()) {
            for (int i = 0; i < renderPathNodes.size() - 1; i++) {
                Vec3d n1 = renderPathNodes.get(i);
                Vec3d n2 = renderPathNodes.get(i+1);
                event.renderer.line(n1.x, n1.y + 1, n1.z, n2.x, n2.y + 1, n2.z, pathColor.get());
                event.renderer.box(new Box(n1.x - 0.2, n1.y, n1.z - 0.2, n1.x + 0.2, n1.y + 2, n1.z + 0.2), pathColor.get(), pathColor.get(), ShapeMode.Lines, 0);
            }
        }
    }

    private Vec3d getOffset(Vec3d base) {
        double dx = 0.05, dy = 0.01;
        List<Vec3d> offsets = Arrays.asList(
            base.add(dx, dy, 0), base.add(-dx, dy, 0), base.add(0, dy, dx), base.add(0, dy, -dx)
        );
        Collections.shuffle(offsets);
        for (Vec3d pos : offsets) { if (!invalid(pos)) return pos; }
        return base.add(0, dy, 0);
    }

    private boolean invalid(Vec3d pos) {
        if (mc.world == null) return true;
        BlockPos bp = BlockPos.ofFloored(pos);
        if (mc.world.getChunk(bp.getX() >> 4, bp.getZ() >> 4) == null) return true;
        Box box = mc.player.getBoundingBox().offset(pos.subtract(mc.player.getPos()));
        for (BlockPos bPos : BlockPos.iterate(BlockPos.ofFloored(box.minX, box.minY, box.minZ), BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ))) {
            BlockState state = mc.world.getBlockState(bPos);
            if (!state.getCollisionShape(mc.world, bPos).isEmpty() || state.isOf(Blocks.LAVA)) return true;
        }
        return false;
    }

    private Vec3d findNearestPos(Vec3d desired) {
        for (int dy = 0; dy <= 2; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Vec3d test = desired.add(dx, dy, dz);
                    if (!invalid(test)) return test;
                }
            }
        }
        return null;
    }

    private boolean entityCheck(Entity entity) {
        if (!(entity instanceof LivingEntity) || !entity.isAlive() || entity == mc.player) return false;
        if (!entities.get().contains(entity.getType())) return false;
        if (mc.player.distanceTo(entity) > maxRange.get()) return false;
        if (entity instanceof PlayerEntity p) {
            if (p.isCreative() || p.isSpectator()) return false;
            if (!Friends.get().shouldAttack(p)) return false;
            String name = p.getGameProfile().getName();
            List<String> list = Arrays.stream(playerList.get().split(",")).map(String::trim).collect(Collectors.toList());
            if (listMode.get() == ListMode.Whitelist && !list.contains(name)) return false;
            if (listMode.get() == ListMode.Blacklist && list.contains(name)) return false;
        }
        return !ignoreNamed.get() || !entity.hasCustomName();
    }

    @Override
    public String getInfoString() {
        return currentTarget != null ? EntityUtils.getName(currentTarget) : null;
    }
}