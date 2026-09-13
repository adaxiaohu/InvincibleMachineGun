package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.mixin.InventoryAccessor;
import com.codigohasta.addon.modules.TpAura.AttackMode;
import com.codigohasta.addon.modules.TpAura.Mode;
import com.codigohasta.addon.utils.leaveshack.InventoryUtil;

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
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;


import java.util.*;
import java.util.stream.Collectors;

public class TpAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTiming = settings.createGroup("攻击机制");
    private final SettingGroup sgTP = settings.createGroup("打击");
    private final SettingGroup sgTargeting = settings.createGroup("目标");
    private final SettingGroup sgWhitelist = settings.createGroup("白名单");
    private final SettingGroup sgRender = settings.createGroup("渲染");

    // --- 1. Timing Settings ---两个模式都一样，没区别
    public enum AttackMode { Smart("满蓄力重击"), Fast("0蓄力连打");
        private final String title; AttackMode(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }
    private final Setting<AttackMode> attackMode = sgTiming.add(new EnumSetting.Builder<AttackMode>().name("攻击模式").defaultValue(AttackMode.Smart).build());
    private final Setting<Double> cooldownThreshold = sgTiming.add(new DoubleSetting.Builder().name("蓄力阈值").description("1.0为满伤害").defaultValue(1.0).min(0.1).sliderMax(1.0).visible(() -> attackMode.get() == AttackMode.Smart).build());
    private final Setting<Integer> attackDelay = sgTiming.add(new IntSetting.Builder().name("额外延迟(Tick)").defaultValue(0).min(0).build());

    // --- 2. General Settings ---
    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder().name("自动切武器").defaultValue(true).build());
    private final Setting<Boolean> requireMace = sgGeneral.add(new BoolSetting.Builder().name("仅手持重锤").defaultValue(false).build());
    private final Setting<Boolean> swingHand = sgGeneral.add(new BoolSetting.Builder().name("挥手").defaultValue(true).build());
    private final Setting<Boolean> silentSwap = sgGeneral.add(new BoolSetting.Builder()
        .name("静默切换")
        .description("使用数据包切换武器（无动画、无声音），其他玩家更难察觉。切换时会在客户端显示武器图标。")
        .defaultValue(true)
        .visible(() -> autoSwitch.get())
        .build()
    );

    // --- 3. TP Settings ---
    public enum Mode { Vanilla, Paper }
    private final Setting<Mode> mode = sgTP.add(new EnumSetting.Builder<Mode>().name("兼容模式").defaultValue(Mode.Paper).build());
    private final Setting<Double> maxRange = sgTP.add(new DoubleSetting.Builder().name("最大范围").defaultValue(49.0).min(1).sliderMax(99).build());
    private final Setting<Boolean> goUp = sgTP.add(new BoolSetting.Builder().name("V-Clip").defaultValue(true).visible(() -> mode.get() == Mode.Paper).build());
    private final Setting<Integer> paperPackets = sgTP.add(new IntSetting.Builder().name("垫包数量").defaultValue(8).min(1).sliderMax(20).build());
    private final Setting<Boolean> returnPos = sgTP.add(new BoolSetting.Builder().name("攻击后回传").defaultValue(true).build());

    private final Setting<Boolean> offsetFix = sgTP.add(new BoolSetting.Builder()
    .name("偏移同步")
    .description("发送微小偏移包防止拉回，但可能导致卡住")
    .defaultValue(true)
    .build()
);

    // --- 4. 其他设置补全 ---
    private final Setting<Set<EntityType<?>>> entities = sgTargeting.add(new EntityTypeListSetting.Builder().name("目标实体").defaultValue(Collections.singleton(EntityType.PLAYER)).build());
    
    // 条件开关设置
    private final Setting<Boolean> ignoreFriends = sgTargeting.add(new BoolSetting.Builder().name("忽略好友").defaultValue(false).description("开启后不将好友设为攻击目标").build());
    private final Setting<Boolean> ignoreNamed = sgTargeting.add(new BoolSetting.Builder().name("忽略命名").defaultValue(true).description("开启后不将命名实体设为攻击目标").build());
    private final Setting<Boolean> ignoreTamed = sgTargeting.add(new BoolSetting.Builder().name("忽略驯服").defaultValue(false).description("开启后不将驯服的生物设为攻击目标").build());
    
    public enum ListMode { Whitelist, Blacklist, Off }
    private final Setting<ListMode> listMode = sgWhitelist.add(new EnumSetting.Builder<ListMode>().name("名单模式").defaultValue(ListMode.Off).build());
    private final Setting<String> playerList = sgWhitelist.add(new StringSetting.Builder().name("玩家列表").defaultValue("").build());
    private final Setting<Boolean> renderPath = sgRender.add(new BoolSetting.Builder().name("显示路径").defaultValue(true).build());
    private final Setting<SettingColor> pathColor = sgRender.add(new ColorSetting.Builder().name("轨迹颜色").defaultValue(new SettingColor(255, 0, 0, 100)).build());
    private final Setting<SettingColor> targetColor = sgRender.add(new ColorSetting.Builder().name("目标颜色").defaultValue(new SettingColor(255, 0, 0, 200)).build());

    private final SettingGroup sgTotem = settings.createGroup("图腾绕过");
    private final Setting<Boolean> totemBypass = sgTotem.add(new BoolSetting.Builder().name("图腾绕过").description("连续多次攻击以突破图腾无敌帧，仅Paper模式有效").defaultValue(false).build());
    private final Setting<Integer> totemAttacks = sgTotem.add(new IntSetting.Builder().name("攻击次数").description("连续攻击次数(1-3)").defaultValue(2).min(1).max(3).sliderRange(1, 3).visible(() -> totemBypass.get()).build());
    private final Setting<Integer> totemHeightIncrease = sgTotem.add(new IntSetting.Builder().name("递增高度").description("每次额外攻击增加的下落高度").defaultValue(9).min(1).sliderRange(1, 100).visible(() -> totemBypass.get()).build());

    private final List<Entity> targets = new ArrayList<>();
    private final List<Vec3d> renderPathNodes = new ArrayList<>();
    private Entity currentTarget;
    private int originalSlot = -1;
    private int silentSwapSlot = -1;
    private int silentSwapPrevSlot = -1;
    private int delayTimer = 0;

    public TpAura() {
        super(AddonTemplate.CATEGORY, "如来神掌", "从天而降的掌法。抄袭了裤子条纹的tp。娱乐功能");
    }

    @Override
    public void onActivate() {
        originalSlot = -1;
        silentSwapSlot = -1;
        silentSwapPrevSlot = -1;
        delayTimer = 0;
        renderPathNodes.clear();
    }

    @Override
    public void onDeactivate() {
        if (silentSwapSlot != -1 && mc.player != null) {
            swapBackWeapon();
        }
        if (originalSlot != -1 && autoSwitch.get() && !silentSwap.get() && mc.player != null) {
            ((InventoryAccessor) mc.player.getInventory()).setSelectedSlot(originalSlot);
            originalSlot = -1;
        }
    }

    private int findWeaponInventorySlot() {
        for (int i = 0; i < 45; i++) {
            String name = mc.player.getInventory().getStack(i).getItem().toString().toLowerCase();
            if (name.contains("sword") || name.contains("mace") || name.contains("axe")) {
                return i < 9 ? i + 36 : i;
            }
        }
        return -1;
    }

    private boolean checkAndSwapWeapon() {
        String itemMain = mc.player.getMainHandStack().getItem().toString().toLowerCase();
        boolean isWeapon = itemMain.contains("sword") || itemMain.contains("mace") || itemMain.contains("axe");
        if (isWeapon && !(requireMace.get() && !itemMain.contains("mace"))) return true;

        if (silentSwap.get()) {
            int slot = findWeaponInventorySlot();
            if (slot != -1) {
                silentSwapSlot = slot;
                silentSwapPrevSlot = ((InventoryAccessor) mc.player.getInventory()).getSelectedSlot();
                if (slot >= 36) {
                    InventoryUtil.switchToSlot(slot - 36);
                } else {
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, 0, SlotActionType.SWAP, mc.player);
                    InventoryUtil.switchToSlot(0);
                }
                return true;
            }
        } else {
            FindItemResult weapon = InvUtils.find(s -> {
                String name = s.getItem().toString().toLowerCase();
                return name.contains("sword") || name.contains("mace") || name.contains("axe");
            }, 0, 8);
            if (weapon.found()) {
                if (originalSlot == -1) originalSlot = ((InventoryAccessor) mc.player.getInventory()).getSelectedSlot();
                InvUtils.swap(weapon.slot(), false);
                return true;
            }
        }
        return false;
    }

    private void swapBackWeapon() {
        if (silentSwapSlot == -1) return;
        if (silentSwapSlot >= 36) {
            InventoryUtil.switchToSlot(silentSwapPrevSlot);
        } else {
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, silentSwapSlot, 0, SlotActionType.SWAP, mc.player);
            InventoryUtil.switchToSlot(silentSwapPrevSlot);
            mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
        }
        silentSwapSlot = -1;
        silentSwapPrevSlot = -1;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // 1. 武器切换
        if (autoSwitch.get()) {
            if (!checkAndSwapWeapon()) return;
        }

        // 2. 蓄力检查
        if (attackMode.get() == AttackMode.Smart) {
            if (mc.player.getAttackCooldownProgress(0.5f) < cooldownThreshold.get()) {
                return;
            }
        }

        // 3. 额外延迟处理
        if (delayTimer > 0) {
            delayTimer--;
            swapBackWeapon();
            return;
        }

        // 4. 索敌逻辑
        targets.clear();
        TargetUtils.getList(targets, this::entityCheck, SortPriority.LowestDistance, 1);
        if (targets.isEmpty()) {
            currentTarget = null;
            swapBackWeapon();
            return;
        }
        currentTarget = targets.get(0);

        // 5. 执行瞬移轰炸
        executeTrouserAttack(currentTarget);
        swapBackWeapon();

        // 6. 重置延迟计时器
        delayTimer = attackDelay.get();
    }

    private void executeTrouserAttack(Entity target) {
        Vec3d startPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        Vec3d targetPos = new Vec3d(target.getX(), target.getY(), target.getZ());
        double reach = maxRange.get();

        Vec3d finalPos = !invalid(targetPos) ? targetPos : findNearestPos(targetPos);
        if (finalPos == null) return;

        Vec3d highStart = startPos.add(0, reach, 0);
        Vec3d highTarget = finalPos.add(0, reach, 0);

        renderPathNodes.clear();
        renderPathNodes.add(startPos);
        if (mode.get() == Mode.Paper && goUp.get()) {
            renderPathNodes.add(highStart);
            renderPathNodes.add(highTarget);
        }
        renderPathNodes.add(finalPos);

        // A. 垫包预热
        int spam = mode.get() == Mode.Paper ? paperPackets.get() : 4;
        for (int i = 0; i < spam; i++) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(false, mc.player.horizontalCollision));
        }

        boolean totemMode = totemBypass.get() && mode.get() == Mode.Paper;

        // B. 攻击阶段
        if (totemMode) {
            // 图腾绕过：多次递增高度攻击以突破无敌帧
            int attackCount = totemAttacks.get();
            int currentHeight = (int) reach;

            for (int i = 0; i < attackCount; i++) {
                int blocks = (i == 0) ? (int) reach : currentHeight;

                if (mc.world != null) {
                    int worldTop = mc.world.getTopYInclusive() - 1;
                    if (finalPos.y + blocks > worldTop) {
                        blocks = (int) (worldTop - finalPos.y);
                        if (blocks < 1) break;
                    }
                }

                Vec3d progressiveAbove = finalPos.add(0, blocks, 0);
                if (goUp.get()) sendMove(progressiveAbove);
                sendMove(finalPos);

                if (swingHand.get()) mc.player.swingHand(Hand.MAIN_HAND);
                mc.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));

                currentHeight += totemHeightIncrease.get();
            }
        } else {
            // 原版单次攻击
            if (mode.get() == Mode.Paper && goUp.get()) {
                sendMove(highStart);
                sendMove(highTarget);
            }
            sendMove(finalPos);

            if (swingHand.get()) mc.player.swingHand(Hand.MAIN_HAND);
            mc.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));
        }

        // C. 瞬间回传
        if (returnPos.get()) {
            if (mode.get() == Mode.Paper && goUp.get() && !totemMode) {
                sendMove(highTarget);
                sendMove(highStart);
            }
            sendMove(startPos);

            if (offsetFix.get()) {
                Vec3d offset = getOffset(startPos);
                sendMove(offset);
                mc.player.setPosition(offset.x, offset.y, offset.z);
            } else {
                mc.player.setPosition(startPos.x, startPos.y, startPos.z);
            }
        } else {
            if (offsetFix.get()) {
                Vec3d offset = getOffset(finalPos);
                sendMove(offset);
                mc.player.setPosition(offset.x, offset.y, offset.z);
            } else {
                mc.player.setPosition(finalPos.x, finalPos.y, finalPos.z);
            }
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
        List<Vec3d> offsets = Arrays.asList(base.add(dx, dy, 0), base.add(-dx, dy, 0), base.add(0, dy, dx), base.add(0, dy, -dx));
        Collections.shuffle(offsets);
        for (Vec3d pos : offsets) { if (!invalid(pos)) return pos; }
        return base.add(0, dy, 0);
    }

    private boolean invalid(Vec3d pos) {
        if (mc.world == null) return true;
        BlockPos bp = BlockPos.ofFloored(pos.x, pos.y, pos.z);
        if (mc.world.getChunk(bp.getX() >> 4, bp.getZ() >> 4) == null) return true;
        Box box = mc.player.getBoundingBox().offset(pos.subtract(new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ())));
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
        
        // 条件开关过滤
        if (ignoreFriends.get() && entity instanceof PlayerEntity p && Friends.get().isFriend(p)) {
            return false; // 忽略好友
        }
        if (ignoreNamed.get() && entity.hasCustomName()) {
            return false; // 忽略命名实体
        }
        if (ignoreTamed.get()) {
            // 检查实体是否被驯服（适用于狼、猫等可驯服生物）
            if (entity instanceof TameableEntity tameable && tameable.isTamed()) {
                return false; // 忽略驯服的生物
            }
        }
        
        // 玩家特殊处理
        if (entity instanceof PlayerEntity p) {
            if (p.isCreative() || p.isSpectator()) return false;
            if (!Friends.get().shouldAttack(p)) return false;
            String name = p.getName().getString();
            List<String> list = Arrays.stream(playerList.get().split(",")).map(String::trim).collect(Collectors.toList());
            if (listMode.get() == ListMode.Whitelist && !list.contains(name)) return false;
            if (listMode.get() == ListMode.Blacklist && list.contains(name)) return false;
        }
        
        return true;
    }

    @Override
    public String getInfoString() {
        return currentTarget != null ? EntityUtils.getName(currentTarget) : null;
    }
}