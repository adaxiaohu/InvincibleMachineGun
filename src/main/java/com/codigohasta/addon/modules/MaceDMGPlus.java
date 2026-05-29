package com.codigohasta.addon.modules;

import net.minecraft.util.math.Vec3d;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.mixin.InventoryAccessor;
import com.codigohasta.addon.utils.leaveshack.InventoryUtil;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IPlayerInteractEntityC2SPacket;
import meteordevelopment.meteorclient.mixininterface.IPlayerMoveC2SPacket;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
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
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MaceDMGPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgExploit = settings.createGroup("秒杀机制 (降龙十八掌)");
    private final SettingGroup sgTargeting = settings.createGroup("目标设置");
    private final SettingGroup sgWhitelist = settings.createGroup("白名单/黑名单");
    private final SettingGroup sgRender = settings.createGroup("渲染设置");

    // --- General (通用设置) ---
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("检测范围")
        .description("自动攻击敌人的最大距离。")
        .defaultValue(4.5)
        .min(0)
        .sliderMax(7)
        .build()
    );

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("自动切刀")
        .description("攻击时自动切换到重锤 (Mace)。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> silentSwap = sgGeneral.add(new BoolSetting.Builder()
        .name("静默切换")
        .description("使用数据包切换武器（无动画、无声音），其他玩家更难察觉。")
        .defaultValue(true)
        .visible(() -> autoSwitch.get())
        .build()
    );

    private final Setting<Integer> attackDelay = sgGeneral.add(new IntSetting.Builder()
        .name("攻击延迟")
        .description("自动攻击的间隔 (Tick)。建议15左右，太快可能被踢。")
        .defaultValue(15)
        .min(0)
        .sliderRange(0, 40)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("自动瞄准")
        .description("攻击时强制将视角转向目标。")
        .defaultValue(true)
        .build()
    );

    // --- Exploit (核心算法) ---
    private final Setting<Boolean> preventDeath = sgExploit.add(new BoolSetting.Builder()
        .name("防摔死保护")
        .description("尝试防止因伪造高度而导致摔死 (推荐开启)。")
        .defaultValue(true)
        .build()
    );
    
    private final Setting<Boolean> maxPower = sgExploit.add(new BoolSetting.Builder()
        .name("最大威力-如来神掌 (仅Paper/Spigot)")
        .description("模拟从170格高空掉落。在原版服不需要开启。")
        .defaultValue(false)
        .build()
    );
    
    private final Setting<Integer> fallHeight = sgExploit.add(new IntSetting.Builder()
        .name("伪造高度")
        .description("伪造的掉落高度。默认22在地下室也能用且不易被踢。")
        .defaultValue(22)
        .sliderRange(1, 170)
        .min(1)
        .max(170)
        .visible(() -> !maxPower.get())
        .build()
    );

    // --- 新增：空气检查开关 ---
    private final Setting<Boolean> airCheck = sgExploit.add(new BoolSetting.Builder()
        .name("空气检查 (防回弹)")
        .description("开启：检查头顶是否有空气（稳健，防反作弊）。关闭：无视地形强制发包（暴力，可在2格高使用）。")
        .defaultValue(true)
        .build()
    );

    // --- Targeting (目标选择) ---
    private final Setting<Set<EntityType<?>>> entities = sgTargeting.add(new EntityTypeListSetting.Builder()
        .name("生物列表")
        .description("选择你要攻击的具体生物种类。")
        .onlyAttackable()
        .defaultValue(EntityType.PLAYER, EntityType.ZOMBIE, EntityType.SKELETON)
        .build()
    );

    private final Setting<Boolean> players = sgTargeting.add(new BoolSetting.Builder()
        .name("攻击玩家")
        .description("是否攻击玩家。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> throughWalls = sgTargeting.add(new BoolSetting.Builder()
        .name("穿墙攻击")
        .description("无视墙壁阻挡直接攻击。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreNamed = sgTargeting.add(new BoolSetting.Builder()
        .name("忽略命名生物")
        .description("不攻击拥有名字标签的生物。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreTamed = sgTargeting.add(new BoolSetting.Builder()
        .name("忽略宠物")
        .description("不攻击已被驯服的生物。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgTargeting.add(new BoolSetting.Builder()
        .name("忽略好友")
        .description("开启后不将好友设为攻击目标")
        .defaultValue(false)
        .build()
    );

    // --- Whitelist/Blacklist (名单设置) ---
    public enum ListMode {
        Whitelist, // 白名单
        Blacklist, // 黑名单
        Off        // 关闭
    }

    private final Setting<ListMode> listMode = sgWhitelist.add(new EnumSetting.Builder<ListMode>()
        .name("模式")
        .description("名单过滤模式。白名单=只打名单里的人,不在名单不打；黑名单=不打名单里的人，不在名单的打。")
        .defaultValue(ListMode.Off)
        .build()
    );

    private final Setting<String> playerList = sgWhitelist.add(new StringSetting.Builder()
        .name("玩家名单")
        .description("玩家名字列表，使用英文逗号(,)分隔。")
        .defaultValue("Player1,Player2")
        .visible(() -> listMode.get() != ListMode.Off)
        .build()
    );

    // --- Render (渲染) ---
    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("渲染目标")
        .description("绘制目标边框。")
        .defaultValue(true)
        .build()
    );
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("边框模式")
        .defaultValue(ShapeMode.Lines)
        .visible(render::get)
        .build()
    );
    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("填充颜色")
        .defaultValue(new SettingColor(255, 0, 0, 40))
        .visible(render::get)
        .build()
    );
    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("线条颜色")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .visible(render::get)
        .build()
    );

    private final SettingGroup sgTotem = settings.createGroup("图腾绕过");
    private final Setting<Boolean> totemBypass = sgTotem.add(new BoolSetting.Builder().name("图腾绕过").description("连续多次攻击以突破图腾无敌帧").defaultValue(false).build());
    private final Setting<Integer> totemAttacks = sgTotem.add(new IntSetting.Builder().name("攻击次数").description("连续攻击次数(1-3)").defaultValue(2).min(1).max(3).sliderRange(1, 3).visible(() -> totemBypass.get()).build());
    private final Setting<Integer> totemHeightIncrease = sgTotem.add(new IntSetting.Builder().name("递增伪造高度").description("每次额外攻击增加的伪造掉落高度(格)").defaultValue(10).min(1).sliderRange(1, 50).visible(() -> totemBypass.get()).build());

    private int timer;
    private int originalSlot = -1;
    private int silentSwapSlot = -1;
    private int silentSwapPrevSlot = -1;
    private final List<Entity> targets = new ArrayList<>();
    private Entity currentTarget;
    private Vec3d previouspos;
    private boolean isSendingTotem = false;

    public MaceDMGPlus() {
        super(AddonTemplate.CATEGORY, "降龙十八掌", " 致密5重锤最高可以打出800伤害 秒天秒地没敌了，只能在无反使用。 基本原理抄了裤子条纹Mackill，和Alien的一些逻辑使这个模块可以在二格方块的情况下生效。图腾绕过现在没有用，这整个模块的发包有问题，知道怎么改的大神可以提交github，多谢。 ");
    }

    @Override
    public void onActivate() {
        timer = 0;
        originalSlot = -1;
        silentSwapSlot = -1;
        silentSwapPrevSlot = -1;
        targets.clear();
        currentTarget = null;
    }

    @Override
    public void onDeactivate() {
        if (silentSwapSlot != -1 && mc.player != null) {
            swapBackWeapon();
        }
        if (originalSlot != -1 && autoSwitch.get() && !silentSwap.get() && mc.player != null) {
            InvUtils.swap(originalSlot, false);
            originalSlot = -1;
        }
    }

    // --- 自动光环逻辑 ---
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (timer > 0) {
            timer--;
            return;
        }

        boolean holdingMace = mc.player.getMainHandStack().getItem().toString().contains("mace");
        if (autoSwitch.get()) {
            if (!holdingMace && !checkAndSwapWeapon()) return;
        } else if (!holdingMace) {
            return;
        }

        targets.clear();
        TargetUtils.getList(targets, this::entityCheck, SortPriority.ClosestAngle, 1);

        if (targets.isEmpty()) {
            currentTarget = null;
            swapBackWeapon();
            return;
        }
        currentTarget = targets.get(0);

        if (rotate.get()) {
            Rotations.rotate(Rotations.getYaw(currentTarget), Rotations.getPitch(currentTarget));
        }

        if (totemBypass.get()) {
            isSendingTotem = true;
            try {
                performTotemBypass(currentTarget);
            } finally {
                isSendingTotem = false;
            }
        } else {
            performMaceExploit(currentTarget);
            mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(currentTarget, mc.player.isSneaking()));
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        }
        swapBackWeapon();

        timer = attackDelay.get();
    }

    // --- 手动点击触发逻辑 ---
    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (mc.player == null) return;
        if (mc.player.getMainHandStack().getItem() != Items.MACE) return;
        
        if (!(event.packet instanceof PlayerInteractEntityC2SPacket)) return;
        
        // 绕过 1.21.4 private 枚举检查
        if (!String.valueOf(((IPlayerInteractEntityC2SPacket) event.packet).meteor$getType()).equals("ATTACK")) return;

        Entity target = ((IPlayerInteractEntityC2SPacket) event.packet).meteor$getEntity();
        if (target == null || !entityCheck(target)) return;

        if (isSendingTotem) return;

        if (totemBypass.get()) {
            event.cancel();
            isSendingTotem = true;
            try {
                performTotemBypass(target);
            } finally {
                isSendingTotem = false;
            }
        } else {
            performMaceExploit(target);
        }
    }

    // --- 图腾绕过统一逻辑 (含空气检查) ---
    private void performTotemBypass(Entity target) {
        int baseHeight = maxPower.get() ? 170 : fallHeight.get();
        int count = totemAttacks.get();

        for (int i = 0; i < count; i++) {
            int height = baseHeight + (i > 0 ? totemHeightIncrease.get() * i : 0);

            // 空气检查：如果开启，就按实际可用空间裁剪
            if (airCheck.get()) {
                int maxAir = getMaxHeightAbovePlayer();
                if (maxAir <= 0) break; // 没空间了，停止
                height = Math.min(height, maxAir);
            }

            sendExploitPackets(height);
            mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        }
    }

    // --- 核心秒杀算法 (MaceKill) ---
    private void performMaceExploit(Entity target) {
        int blocks;

        // --- 逻辑分流：空气检查 vs 暴力模式 ---
        if (airCheck.get()) {
            // [Safe Mode] 检查头顶空气，防止反作弊回弹
            blocks = getMaxHeightAbovePlayer();
            BlockPos isopenair1 = mc.player.getBlockPos().add(0, blocks, 0);
            BlockPos isopenair2 = mc.player.getBlockPos().add(0, blocks + 1, 0);
            if (!isSafeBlock(isopenair1) || !isSafeBlock(isopenair2)) return; // 没空气就取消
        } else {
            // [Rage Mode] 暴力模式，直接使用设定值，无视地形
            blocks = maxPower.get() ? 170 : fallHeight.get();
        }

        // 如果高度为0（安全模式下没找到空间），则不执行
        if (blocks <= 0) return;

        sendExploitPackets(blocks);
    }

    private void sendExploitPackets(int blocks) {
        previouspos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        int packetsRequired = (int) Math.ceil(Math.abs(blocks / 10.0));
        if (packetsRequired > 20) packetsRequired = 1;

        // 发包逻辑
        if (blocks <= 22) {
            if (mc.player.hasVehicle()) {
                for (int i = 0; i < 4; i++) {
                    mc.player.networkHandler.sendPacket(VehicleMoveC2SPacket.fromVehicle(mc.player.getVehicle()));
                }
                double maxHeight = Math.min(mc.player.getVehicle().getY() + 22, mc.player.getVehicle().getY() + blocks);
                doVehicleTeleports(maxHeight, blocks);
            } else {
                // 关键点：发送4个OnGroundOnly(false)包，欺骗服务器状态而不改变位置，从而在3格空间生效
                for (int i = 0; i < 4; i++) {
                    mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(false, mc.player.horizontalCollision));
                }
                double heightY = Math.min(mc.player.getY() + 22, mc.player.getY() + blocks);
                doPlayerTeleports(heightY);
            }
        } else {
            if (mc.player.hasVehicle()) {
                for (int packetNumber = 0; packetNumber < (packetsRequired - 1); packetNumber++) {
                    mc.player.networkHandler.sendPacket(VehicleMoveC2SPacket.fromVehicle(mc.player.getVehicle()));
                }
                double maxHeight = mc.player.getVehicle().getY() + blocks;
                doVehicleTeleports(maxHeight, blocks);
            } else {
                for (int i = 0; i < packetsRequired - 1; i++) {
                    mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(false, mc.player.horizontalCollision));
                }
                double heightY = mc.player.getY() + blocks;
                doPlayerTeleports(heightY);
            }
        }
    }

    private void doPlayerTeleports(double height) {
        PlayerMoveC2SPacket movepacket = new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(), height, mc.player.getZ(), false, mc.player.horizontalCollision);
                
        PlayerMoveC2SPacket homepacket = new PlayerMoveC2SPacket.PositionAndOnGround(
                previouspos.getX(), previouspos.getY(), previouspos.getZ(),
                false, mc.player.horizontalCollision);
                
        if (preventDeath.get()) {
            homepacket = new PlayerMoveC2SPacket.PositionAndOnGround(
                    previouspos.getX(), previouspos.getY() + 0.25, previouspos.getZ(),
                    false, mc.player.horizontalCollision);
        }
        
        ((IPlayerMoveC2SPacket) homepacket).meteor$setTag(1337);
        ((IPlayerMoveC2SPacket) movepacket).meteor$setTag(1337);
        
        mc.player.networkHandler.sendPacket(movepacket);
        mc.player.networkHandler.sendPacket(homepacket);
        
        if (preventDeath.get()) {
            mc.player.setVelocity(mc.player.getVelocity().x, 0.1, mc.player.getVelocity().z);
            mc.player.fallDistance = 0;
        }
    }

    private void doVehicleTeleports(double height, int blocks) {
        if (mc.player.getVehicle() == null) return;
        mc.player.getVehicle().setPosition(mc.player.getVehicle().getX(), height + blocks, mc.player.getVehicle().getZ());
        mc.player.networkHandler.sendPacket(VehicleMoveC2SPacket.fromVehicle(mc.player.getVehicle()));
        mc.player.getVehicle().setPosition(previouspos);
        mc.player.networkHandler.sendPacket(VehicleMoveC2SPacket.fromVehicle(mc.player.getVehicle()));
    }

    private boolean checkAndSwapWeapon() {
        if (mc.player.getMainHandStack().getItem().toString().contains("mace")) return true;

        if (silentSwap.get()) {
            int slot = InventoryUtil.findItemInventorySlot(Items.MACE);
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
            FindItemResult mace = InvUtils.find(itemStack -> itemStack.getItem().toString().contains("mace"), 0, 8);
            if (mace.found()) {
                if (originalSlot == -1) originalSlot = ((InventoryAccessor) mc.player.getInventory()).getSelectedSlot();
                InvUtils.swap(mace.slot(), false);
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

    private int getMaxHeightAbovePlayer() {
        BlockPos playerPos = mc.player.getBlockPos();
        int maxHeight = playerPos.getY() + (maxPower.get() ? 170 : fallHeight.get());
        for (int i = maxHeight; i > playerPos.getY(); i--) {
            BlockPos up1 = new BlockPos(playerPos.getX(), i, playerPos.getZ());
            BlockPos up2 = up1.up(1);
            if (isSafeBlock(up1) && isSafeBlock(up2)) return i - playerPos.getY();
        }
        return 0;
    }

    private boolean isSafeBlock(BlockPos pos) {
        return mc.world.getBlockState(pos).isReplaceable()
                && mc.world.getFluidState(pos).isEmpty()
                && !mc.world.getBlockState(pos).isOf(Blocks.POWDER_SNOW);
    }

    // --- 实体过滤检查 (含黑白名单) ---
    private boolean entityCheck(Entity entity) {
        if (!(entity instanceof LivingEntity) || !entity.isAlive()) return false;
        if (entity == mc.player) return false;
        if (mc.player.distanceTo(entity) > range.get()) return false;
        if (!throughWalls.get() && !mc.player.canSee(entity)) return false;

        // 玩家检查 + 名单逻辑
        if (entity instanceof PlayerEntity p) {
            if (!players.get()) return false;
            if (p.isCreative()) return false;
            
            // 忽略好友检查
            if (ignoreFriends.get() && Friends.get().isFriend(p)) return false;
            
            if (!Friends.get().shouldAttack(p)) return false;

            // 名单检查逻辑
            String name = p.getName().getString();
            // 解析名字列表
            List<String> list = Arrays.stream(playerList.get().split(","))
                                      .map(String::trim)
                                      .filter(s -> !s.isEmpty())
                                      .collect(Collectors.toList());
            
            // 白名单：不在列表里就不打
            if (listMode.get() == ListMode.Whitelist && !list.contains(name)) return false;
            // 黑名单：在列表里就不打
            if (listMode.get() == ListMode.Blacklist && list.contains(name)) return false;
        }
        
        // 忽略命名生物
        if (ignoreNamed.get() && entity.hasCustomName()) return false;
        
        // 忽略宠物
        if (ignoreTamed.get() && entity instanceof TameableEntity t && t.isTamed()) return false;

        // 检查生物类型是否在允许列表中
        return entities.get().contains(entity.getType());
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || currentTarget == null) return;
        event.renderer.box(currentTarget.getBoundingBox(), sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }
}