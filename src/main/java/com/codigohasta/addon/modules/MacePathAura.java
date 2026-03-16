package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.smarttpaura.SmartTPAuraCore;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
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
import net.minecraft.item.MaceItem;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MacePathAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgExploit = settings.createGroup("秒杀机制 (降龙十八掌)");
    private final SettingGroup sgPath = settings.createGroup("A* 寻路设置");
    private final SettingGroup sgTargeting = settings.createGroup("目标设置");
    private final SettingGroup sgWhitelist = settings.createGroup("白名单/黑名单");
    private final SettingGroup sgRender = settings.createGroup("渲染设置");

    // --- General ---
    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder().name("自动切重锤").defaultValue(true).build());
    private final Setting<Boolean> swapBack = sgGeneral.add(new BoolSetting.Builder().name("切回原位").visible(autoSwitch::get).defaultValue(true).build());
    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder().name("自动瞄准").defaultValue(true).build());

    // --- Pathfinding ---
    private final Setting<Double> tpRange = sgPath.add(new DoubleSetting.Builder().name("寻路范围").defaultValue(95.0).min(10.0).max(200.0).build());
    private final Setting<Double> maxStep = sgPath.add(new DoubleSetting.Builder().name("瞬移步长").description("单次发包最大距离").defaultValue(8.5).min(1.0).max(10.0).build());
    private final Setting<Boolean> padding = sgPath.add(new BoolSetting.Builder().name("预热包").description("防反作弊距离检测").defaultValue(true).build());
    private final Setting<Boolean> airPath = sgPath.add(new BoolSetting.Builder().name("V-Clip穿墙").description("允许垂直穿墙寻路").defaultValue(true).build());

    // --- Exploit ---
    private final Setting<Boolean> preventDeath = sgExploit.add(new BoolSetting.Builder().name("防摔死保护").defaultValue(true).build());
    private final Setting<Boolean> maxPower = sgExploit.add(new BoolSetting.Builder().name("最大威力 (170格)").defaultValue(false).build());
    private final Setting<Integer> fallHeight = sgExploit.add(new IntSetting.Builder().name("伪造高度").defaultValue(22).sliderRange(1, 170).visible(() -> !maxPower.get()).build());
    private final Setting<Boolean> airCheck = sgExploit.add(new BoolSetting.Builder().name("空气检查").description("确保头顶有空间才执行爆发").defaultValue(true).build());
    private final Setting<Integer> attackDelay = sgExploit.add(new IntSetting.Builder().name("攻击延迟").defaultValue(15).min(0).build());

    // --- Targeting ---
    private final Setting<Set<EntityType<?>>> entities = sgTargeting.add(new EntityTypeListSetting.Builder().name("生物列表").defaultValue(EntityType.PLAYER).onlyAttackable().build());
    private final Setting<Boolean> players = sgTargeting.add(new BoolSetting.Builder().name("攻击玩家").defaultValue(true).build());
    private final Setting<Boolean> ignoreNamed = sgTargeting.add(new BoolSetting.Builder().name("忽略命名生物").defaultValue(true).build());
    private final Setting<Boolean> ignoreTamed = sgTargeting.add(new BoolSetting.Builder().name("忽略宠物").defaultValue(true).build());
    private final Setting<Boolean> ignorePassive = sgTargeting.add(new BoolSetting.Builder().name("忽略被动生物").defaultValue(true).build());

    // --- Whitelist ---
    public enum ListMode { Whitelist, Blacklist, Off }
    private final Setting<ListMode> listMode = sgWhitelist.add(new EnumSetting.Builder<ListMode>().name("模式").defaultValue(ListMode.Off).build());
    private final Setting<String> playerList = sgWhitelist.add(new StringSetting.Builder().name("玩家名单").defaultValue("").visible(() -> listMode.get() != ListMode.Off).build());

    // --- Render ---
    private final Setting<Boolean> renderTarget = sgRender.add(new BoolSetting.Builder().name("渲染目标").defaultValue(true).build());
    private final Setting<Boolean> renderPath = sgRender.add(new BoolSetting.Builder().name("渲染路径").defaultValue(true).build());
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>().name("边框模式").defaultValue(ShapeMode.Lines).visible(renderTarget::get).build());
    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder().name("填充颜色").defaultValue(new SettingColor(255, 0, 0, 40)).visible(renderTarget::get).build());
    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder().name("线条颜色").defaultValue(new SettingColor(255, 0, 0, 255)).visible(renderTarget::get).build());
    private final Setting<SettingColor> pathColor = sgRender.add(new ColorSetting.Builder().name("路径颜色").defaultValue(new SettingColor(0, 255, 255, 150)).visible(renderPath::get).build());

    private SmartTPAuraCore core;
    private final List<Entity> targets = new ArrayList<>();
    private Entity currentTarget;
    private int timer;
    private int originalSlot = -1;
    private boolean swapped;
    private List<Vec3d> currentPathDisplay = new ArrayList<>();

    public MacePathAura() {
        super(AddonTemplate.CATEGORY, "MacePathAura", "寻路版降龙十八掌：智能绕路+重锤秒杀");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;
        core = new SmartTPAuraCore(mc.world, mc.player);
        timer = 0;
        originalSlot = -1;
        swapped = false;
        currentPathDisplay.clear();
    }

    @Override
    public void onDeactivate() {
        if (core != null) core.cleanup();
        if (originalSlot != -1 && autoSwitch.get() && mc.player != null) {
            InvUtils.swap(originalSlot, false);
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerPositionLookS2CPacket) {
            if (core != null && mc.player != null) {
                core.desyncPos = null;
                // [修复1] 传入 mc.player 实体，而不是坐标
                core.updatePathfinding(mc.player.getPos(), mc.player);
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (timer > 0) { timer--; return; }

        targets.clear();
        TargetUtils.getList(targets, this::entityCheck, SortPriority.LowestDistance, 1);
        
        if (targets.isEmpty()) {
            if (core != null) core.desyncPos = null;
            return;
        }
        
        currentTarget = targets.get(0);
        Vec3d localPos = mc.player.getPos();

        boolean holdingMace = mc.player.getMainHandStack().getItem() instanceof MaceItem;
        if (autoSwitch.get() && !holdingMace) {
            if (!checkAndSwapWeapon()) return; 
        } else if (!holdingMace) {
            return;
        }

        // A* 寻路计算
        core.setAirPath(airPath.get());
        
        // [修复2] 传入 currentTarget 实体，而不是坐标
        core.updatePathfinding(localPos, currentTarget);
        
        double dynamicStep = localPos.distanceTo(currentTarget.getPos()) > 40 ? 9.2 : maxStep.get();
        
        // [修复3] 使用 getEfficientPath 替代 getChunkedPath
        List<Vec3d> path = core.getEfficientPath(dynamicStep);

        if (path != null && !path.isEmpty()) {
            if (path.get(path.size() - 1).distanceTo(currentTarget.getPos()) > 3.0) return;
            if (path.get(0).distanceTo(localPos) > 1.2) return;

            this.currentPathDisplay = path; 

            if (padding.get()) {
                int pCount = Math.min(2, (int) Math.floor(localPos.distanceTo(currentTarget.getPos()) / 10.0));
                for (int i = 0; i < pCount; i++) sendPos(localPos);
            }

            for (Vec3d p : path) {
                sendPos(p);
                core.desyncPos = p;
            }

            Vec3d targetPos = path.get(path.size() - 1);
            performMaceExploitRemote(targetPos);

            if (rotate.get()) {
                Rotations.rotate(Rotations.getYaw(currentTarget), Rotations.getPitch(currentTarget, Target.Body));
            }
            mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(currentTarget, mc.player.isSneaking()));
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

            for (int i = path.size() - 2; i >= 0; i--) {
                sendPos(path.get(i));
            }
            sendPos(mc.player.getPos()); 

            timer = attackDelay.get();
        }
    }

    private void performMaceExploitRemote(Vec3d origin) {
        int blocks;
        if (airCheck.get()) {
            blocks = getMaxHeightAbove(origin);
            if (blocks <= 0) return;
        } else {
            blocks = maxPower.get() ? 170 : fallHeight.get();
        }

        int packetsRequired = (int) Math.ceil(Math.abs(blocks / 10.0));
        if (packetsRequired > 20) packetsRequired = 1;

        if (blocks <= 22) {
            for (int i = 0; i < 4; i++) {
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(false, mc.player.horizontalCollision));
            }
            double heightY = origin.y + Math.min(22, blocks);
            doPlayerTeleportsRemote(origin, heightY);
        } else {
            for (int i = 0; i < packetsRequired - 1; i++) {
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(false, mc.player.horizontalCollision));
            }
            double heightY = origin.y + blocks;
            doPlayerTeleportsRemote(origin, heightY);
        }
    }

    private void doPlayerTeleportsRemote(Vec3d basePos, double heightY) {
        PlayerMoveC2SPacket movepacket = new PlayerMoveC2SPacket.PositionAndOnGround(
                basePos.x, heightY, basePos.z, false, mc.player.horizontalCollision);
        
        PlayerMoveC2SPacket homepacket = new PlayerMoveC2SPacket.PositionAndOnGround(
                basePos.x, basePos.y, basePos.z, false, mc.player.horizontalCollision);
        
        if (preventDeath.get()) {
            homepacket = new PlayerMoveC2SPacket.PositionAndOnGround(
                    basePos.x, basePos.y + 0.25, basePos.z, false, mc.player.horizontalCollision);
        }

        mc.getNetworkHandler().sendPacket(movepacket);
        mc.getNetworkHandler().sendPacket(homepacket);
    }

    private int getMaxHeightAbove(Vec3d pos) {
        BlockPos base = BlockPos.ofFloored(pos);
        int maxH = (int) pos.y + (maxPower.get() ? 170 : fallHeight.get());
        for (int i = maxH; i > pos.y; i--) {
            BlockPos up1 = new BlockPos(base.getX(), i, base.getZ());
            BlockPos up2 = up1.up();
            if (isSafeBlock(up1) && isSafeBlock(up2)) return (int) (i - pos.y);
        }
        return 0;
    }

    private boolean isSafeBlock(BlockPos pos) {
        return mc.world.getBlockState(pos).isReplaceable()
                && mc.world.getFluidState(pos).isEmpty()
                && !mc.world.getBlockState(pos).isOf(Blocks.POWDER_SNOW);
    }

    private void sendPos(Vec3d p) {
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(p.x, p.y, p.z, mc.player.isOnGround(), false));
    }

    private boolean checkAndSwapWeapon() {
        FindItemResult mace = InvUtils.find(itemStack -> itemStack.getItem() instanceof MaceItem, 0, 8);
        if (mace.found()) {
            if (originalSlot == -1) originalSlot = mc.player.getInventory().selectedSlot;
            InvUtils.swap(mace.slot(), false);
            return true;
        }
        return false;
    }

    private boolean entityCheck(Entity e) {
        if (e == mc.player || !e.isAlive() || mc.player.distanceTo(e) > tpRange.get()) return false;
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

        if (e instanceof PlayerEntity p) {
            if (!players.get()) return false;
            if (p.isCreative() || p.isSpectator()) return false;
            if (!Friends.get().shouldAttack(p)) return false;

            String name = p.getGameProfile().getName();
            List<String> list = Arrays.stream(playerList.get().split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
            if (listMode.get() == ListMode.Whitelist && !list.contains(name)) return false;
            if (listMode.get() == ListMode.Blacklist && list.contains(name)) return false;
        }
        return e instanceof LivingEntity;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (renderTarget.get() && currentTarget != null) {
            event.renderer.box(currentTarget.getBoundingBox(), sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
        if (renderPath.get() && core != null && !currentPathDisplay.isEmpty()) {
            // [修复4] 使用 renderFixedSnapshot 替代 renderPersistent
            core.renderFixedSnapshot(event, currentPathDisplay, pathColor.get(), maxStep.get());
        }
    }
}