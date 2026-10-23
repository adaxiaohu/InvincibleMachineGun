package com.codigohasta.addon.modules;

import net.minecraft.util.math.Vec3d;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.mixin.InventoryAccessor;
import com.codigohasta.addon.utils.gcore.GcoreCombatUtil;
import com.codigohasta.addon.utils.gcore.GcoreMoveBudget;
import com.codigohasta.addon.utils.gcore.GcoreMoveSequenceUtil;
import com.codigohasta.addon.utils.gcore.GcoreSpaceSearchUtil;
import com.codigohasta.addon.utils.gcore.MoveSequenceGate;
import com.codigohasta.addon.utils.img.ImgPositionPredict;
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
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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

    private final Setting<Boolean> usePredict = sgGeneral.add(new BoolSetting.Builder()
        .name("目标预判")
        .description("按服务端位置包跟踪目标，并按速度外推。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> predictAuto = sgGeneral.add(new BoolSetting.Builder()
        .name("按延迟自动预判")
        .description("按自己的往返延迟自动算预判刻数：ticks = round(延迟/2/50)。\n"
            + "攻击包要飞半个 RTT 才到服务端，所以要把目标外推到那时。\n"
            + "例：延迟 100ms → 预判 1 tick；延迟 200ms → 2 tick。")
        .defaultValue(true)
        .visible(usePredict::get)
        .build()
    );

    private final Setting<Integer> predictTicks = sgGeneral.add(new IntSetting.Builder()
        .name("预判刻数")
        .description("往前预测多少 tick。\n"
            + "0 = 只用「服务端位置」消掉插值滞后；\n"
            + "1~3 = 额外按速度外推，追高速目标更准（但可能打过头）。")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 10)
        .visible(() -> usePredict.get() && !predictAuto.get())
        .build()
    );

    private final Setting<Integer> predictMaxTicks = sgGeneral.add(new IntSetting.Builder()
        .name("自动预判上限")
        .description("自动模式下允许的最大预判刻数（防止高延迟时外推过头）。")
        .defaultValue(3)
        .min(0)
        .sliderRange(0, 10)
        .visible(() -> usePredict.get() && predictAuto.get())
        .build()
    );

    private final Setting<ImgPositionPredict.Mode> predictMode = sgGeneral.add(new EnumSetting.Builder<ImgPositionPredict.Mode>()
        .name("预判算法")
        .description("1")
        .defaultValue(ImgPositionPredict.Mode.LINEAR)
        .visible(usePredict::get)
        .build()
    );

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("自动切刀")
        .description("攻击时自动切换到重锤。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> silentSwap = sgGeneral.add(new BoolSetting.Builder()
        .name("静默切换")
        .description("使用数据包切换武器")
        .defaultValue(true)
        .visible(() -> autoSwitch.get())
        .build()
    );

    private final Setting<Integer> attackDelay = sgGeneral.add(new IntSetting.Builder()
        .name("攻击延迟")
        .description("自动攻击的间隔 重锤伤害来自坠落高度、与武器冷却无关，"
            + "设 0 也能打出伤害。\n")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 40)
        .build()
    );

    private final Setting<Boolean> debugLog = sgExploit.add(new BoolSetting.Builder()
        .name("调试日志")
        .description("把每次攻击的关键中间量打到聊天栏/日志，用于定位问题。\n"
            + "模拟高度、是否真的发包、空气检查结果、攻击是否发出。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> dynamicHeight = sgExploit.add(new BoolSetting.Builder()
        .name("动态找空间")
        .description("（没用）")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("转身")
        .description("攻击时将视角转向目标没用。")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> preventDeath = sgExploit.add(new BoolSetting.Builder()
        .name("防摔死保护")
        .description("把模拟高度造成的 fallDistance 清零，防止自己跌死 。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> teleportThrottle = sgExploit.add(new IntSetting.Builder()
        .name("被传送后暂停")
        .description("收到服务端拉回(传送)包后，暂停发包的 tick 数。 没有用")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 40)
        .build()
    );
    
    private final Setting<Boolean> maxPower = sgExploit.add(new BoolSetting.Builder()
        .name("最大威力-如来神掌 (仅Paper/Spigot)")
        .description("模拟从170格高空掉落。在原版服不需要开启。")
        .defaultValue(false)
        .build()
    );
    
    private final Setting<Integer> fallHeight = sgExploit.add(new IntSetting.Builder()
        .name("模拟高度")
        .description("模拟的天降高度。。")
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
        .description("开启：检查头顶是否有空气（稳健）。关闭：无视地形强制发包（暴力，可在2格高使用）。")
        .defaultValue(true)
        .build()
    );

    public enum OverBudgetMode {
        LowerHeight("降低高度继续", "把伪造高度降到本 tick 装得下的最大值没用"),
        DeferNextTick("延后到下一 tick", "没用");

        private final String title;
        private final String description;

        OverBudgetMode(String title, String description) {
            this.title = title;
            this.description = description;
        }

        @Override
        public String toString() {
            return title;
        }

        public String description() {
            return description;
        }
    }

    private final Setting<Boolean> countOthersPackets = sgExploit.add(new BoolSetting.Builder()
        .name("计入他人已发包")
        .description("没用")
        .defaultValue(true)
        .build()
    );
    private final Setting<Integer> packetLimit = sgExploit.add(new IntSetting.Builder()
        .name("移动包上限")
        .description("本tick允许发出的移动包数量上限（服务端 allowedPlayerTicks 即 20）。\n"
            + "垫包+位移包的总下标不能超过它，否则服务端把 i 归 1、阈值塌回 100。如果你服务器没限制就随便拉")
        .defaultValue(20)
        .range(5, 99999999)
        .sliderRange(5, 20)
        .build()
    );

    
    private final Setting<Double> serverStepMult = sgExploit.add(new DoubleSetting.Builder()
        .name("服务端单包位移倍率")
        .description("对应服务端 spigot.yml 的 moved-too-quickly-multiplier（默认 10.0）。\n"
            + "服务端判据：单包位移 ≤ 本值 × 本包下标。\n"
            + "只有服务器特意放宽或者关了该检查时才需要调大。\n"
            + "默认 10.0 = Spigot 默认值。")
        .defaultValue(GcoreMoveSequenceUtil.SERVER_STEP_MULT)
        .min(0.1)
        .sliderRange(1.0, 100.0)
        .max(Double.MAX_VALUE)
        .build()
    );
    private final Setting<OverBudgetMode> overBudgetMode = sgExploit.add(new EnumSetting.Builder<OverBudgetMode>()
        .name("超限时行为")
        .description("本 tick 装不下整套位移时怎么办。")
        .defaultValue(OverBudgetMode.DeferNextTick)
        .build()
    );
    private final Setting<Boolean> suppressVanillaMove = sgExploit.add(new BoolSetting.Builder()
        .name("抑制原版移动包")
        .description("发包时压掉原版 ClientPlayerEntity.sendMovementPackets。\n"
            + "服务端「第几个包」是全 tick 所有来源累计的，而原版每 tick 也会发 1~2 个移动包，\n"
            + "且它正好夹在两次序列之间 —— 会把我们的下标整体顶偏，\n"
            + "上跳包可能落到 k < 需要值 而被服务端整包拒绝。\n"
            + "压掉它之后「本 tick 的包全是我们发的」，下标变成确定值，垫包数才算得准。\n"
            + "只在发包的那一瞬生效一次，不会影响正常移动。没有用")
        .defaultValue(true)
        .build()
    );
    private final Setting<Integer> budgetBias = sgExploit.add(new IntSetting.Builder()
        .name("预算余量")
        .description("【没有用】垫包公式已改为纯几何（不减「别人已发」），\n"
            + "因此本值不再影响上跳包的真实下标。")
        .defaultValue(1)
        .range(0, 3)
        .sliderRange(0, 3)
        .visible(() -> true)
        .build()
    );
    private final Setting<Boolean> printWhenTooManyPacket = sgExploit.add(new BoolSetting.Builder()
        .name("超限时提示")
        .description("包数超限、本次不出手时在聊天栏提示。")
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
    private final Setting<Boolean> totemCompat = sgTotem.add(new BoolSetting.Builder().name("图腾绕过").description("连续发动攻击，每击伤害递增，打穿目标的受击无敌帧。").defaultValue(false).build());
    private final Setting<Boolean> totemDynamic = sgTotem.add(new BoolSetting.Builder()
        .name("图腾动态判断")
        .description("只在目标【手上持有图腾】或【刚刚消耗过图腾】时才执行连击。\n"
            + "关闭时：对任何目标都连击。")
        .defaultValue(true)
        .visible(totemCompat::get)
        .build());
    private final Setting<Integer> totemAttacks = sgTotem.add(new IntSetting.Builder().name("攻击次数").description("连续攻击次数(1-3)。图腾只能挡第一下，建议 3。").defaultValue(3).min(1).max(3).sliderRange(1, 3).visible(() -> totemCompat.get()).build());
    private final Setting<Integer> totemHeightIncrease = sgTotem.add(new IntSetting.Builder().name("递增伪造高度").description("每次额外攻击增加的伪造掉落高度(格)。\n每一击必须比上一击伤害更高，否则会被无敌帧整击丢弃，故此处必须 > 0。").defaultValue(10).min(1).sliderRange(1, 50).visible(() -> totemCompat.get()).build());

    private final SettingGroup sgInvuln = settings.createGroup("无敌帧");
    private final Setting<Boolean> smartInvuln = sgInvuln.add(new BoolSetting.Builder()
        .name("智能无敌帧")
        .description("自动判断该『等』还是该『绕』。\n"
            + "客户端可直接读目标的无敌帧剩余量 (timeUntilRegen)。\n"
            + "受伤后为 20，每 tick 递减；<= 10 时服务端必定给全额伤害。\n"
            + "没啥用")
        .defaultValue(false)
        .build());
    private final Setting<Integer> invulnMargin = sgInvuln.add(new IntSetting.Builder()
        .name("等待余量")
        .description("自动等待时额外多等的 tick 数。没啥用")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 10)
        .visible(smartInvuln::get)
        .build());
    private final Setting<Integer> manualWait = sgInvuln.add(new IntSetting.Builder()
        .name("固定等待")
        .description("关闭『智能无敌帧』时使用的固定等待 tick 数。\n"
            + "如果某个服务端/版本行为不同步时仍可用。")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 40)
        .visible(() -> !smartInvuln.get())
        .build());
    private final Setting<Boolean> ignoreBlocking = sgInvuln.add(new BoolSetting.Builder()
        .name("忽略举盾目标")
        .description("目标正在举盾格挡时不攻击）。\n"
            + "默认关闭。")
        .defaultValue(false)
        .build());

    private int timer;
    private int originalSlot = -1;
    private int silentSwapSlot = -1;
    private int silentSwapPrevSlot = -1;
    private final List<Entity> targets = new ArrayList<>();
    private Entity currentTarget;
    private Vec3d previouspos;
    private boolean isSendingTotem = false;
    private int teleportPause = 0;
    private int waitTicks = 0;
    private boolean isSelfSending = false;
    private boolean pendingFallClear = false;
    private static final double TOTEM_STEP = 9.9;

    private void sendOwnMove(net.minecraft.network.packet.Packet<?> packet) {
        mc.getNetworkHandler().sendPacket(packet);
    }

    public MaceDMGPlus() {            //基本原理参考了裤子条纹Mackill，和Alien的一些逻辑，Gcore。
        super(AddonTemplate.CATEGORY, "降龙十八掌", " 致密5重锤最高可以打出800伤害 秒天秒地没敌了，只能在原版使用。重做了至高境界。 ");
    }

    @Override
    public void onActivate() {
        timer = 0;
        originalSlot = -1;
        silentSwapSlot = -1;
        silentSwapPrevSlot = -1;
        targets.clear();
        currentTarget = null;
        teleportPause = 0;
        waitTicks = 0;
        isSelfSending = false;
        GcoreCombatUtil.clear();
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
        waitTicks = 0;
        pendingFallClear = false;
        GcoreCombatUtil.clear();
    }

    // --- 自动光环逻辑 ---
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        GcoreCombatUtil.tick();
        if (mc.player.isUsingItem()) {
            return;
        }

        if (teleportPause > 0) {
            teleportPause--;
            return;
        }

        // ★ 智能无敌帧：上一轮判定「该等」，这里倒数。
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        if (timer > 0) {
            timer--;
            return;
        }
        targets.clear();
        TargetUtils.getList(targets, this::entityCheck, SortPriority.ClosestAngle, 1);

        if (targets.isEmpty()) {
            currentTarget = null;
            swapBackWeapon();
            return;
        }

        Entity target = targets.get(0);
        if (ignoreBlocking.get() && GcoreCombatUtil.isBlocking(target)) {
            currentTarget = target;
            return;
        }

        if (smartInvuln.get() && GcoreCombatUtil.invulnerableTicks(target) > 0) {
            boolean compatting = totemCompat.get()
                && (!totemDynamic.get() || GcoreCombatUtil.shouldCompatTotem((LivingEntity) target));
            if (!compatting) {
                int wait = GcoreCombatUtil.suggestWaitTicks(target, invulnMargin.get());
                if (wait > 0) {
                    currentTarget = target;
                    waitTicks = wait;
                    return;
                }
            }
        }

        boolean holdingMace = isHoldingMace();
        if (autoSwitch.get()) {
            if (!holdingMace && !checkAndSwapWeapon()) return;
        } else if (!holdingMace) {
            return;
        }

        currentTarget = target;

        if (rotate.get()) {
            Rotations.rotate(Rotations.getYaw(currentTarget), Rotations.getPitch(currentTarget));
        }
        boolean useTotemCompat = totemCompat.get()
            && (!totemDynamic.get()
                || GcoreCombatUtil.shouldCompatTotem(
                       currentTarget instanceof LivingEntity living ? living : null));

        if (useTotemCompat) {
            isSendingTotem = true;
            isSelfSending = true;
            try {
                performTotemCompat(currentTarget);
            } finally {
                isSendingTotem = false;
                isSelfSending = false;
            }
        } else {
            isSelfSending = true;
            try {
                if (performMaceExploit(currentTarget)) {
                    mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(currentTarget, mc.player.isSneaking()));
                    mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
                    clearFallDebtAfterAttack();
                }
            } finally {
                isSelfSending = false;
            }
        }
        swapBackWeapon();

        timer = attackDelay.get();
    }

    
    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (event.packet instanceof EntityStatusS2CPacket packet) {
            GcoreCombatUtil.onEntityStatus(packet.getEntity(mc.world), packet.getStatus());
            return;
        }

        if (!(event.packet instanceof PlayerPositionLookS2CPacket)) return;
        if (teleportThrottle.get() > 0) {
            teleportPause = teleportThrottle.get();
        }
    }

    
    @EventHandler
    private void onPacketSent(PacketEvent.Sent event) {
        if (!pendingFallClear) {
            return;
        }
        if (!(event.packet instanceof PlayerInteractEntityC2SPacket)) {
            return;
        }
        pendingFallClear = false;
        clearFallDebtAfterAttack();
    }

    // --- 手动点击触发逻辑 ---
    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (mc.player == null) return;
        // 自己发出的包不再处理（避免重复发包）
        if (isSelfSending) return;
        if (mc.player.getMainHandStack().getItem() != Items.MACE) return;
        if (!(event.packet instanceof PlayerInteractEntityC2SPacket)) return;
        if (!String.valueOf(((IPlayerInteractEntityC2SPacket) event.packet).meteor$getType()).equals("ATTACK")) return;
        Entity target = ((IPlayerInteractEntityC2SPacket) event.packet).meteor$getEntity();
        if (target == null || !entityCheck(target)) return;
        if (isSendingTotem) return;
        boolean useTotemCompat = totemCompat.get()
            && (!totemDynamic.get()
                || GcoreCombatUtil.shouldCompatTotem(
                       target instanceof LivingEntity living ? living : null));

        if (useTotemCompat) {
            event.cancel();
            isSendingTotem = true;
            isSelfSending = true;
            try {
                performTotemCompat(target);
            } finally {
                isSendingTotem = false;
                isSelfSending = false;
            }
        } else {
            isSelfSending = true;
            try {
                if (performMaceExploit(target)) {
                    pendingFallClear = true;
                }
            } finally {
                isSelfSending = false;
            }
        }
    }

    // --- 图腾绕过统一逻辑 (含空气检查) ---
    
    private void performTotemCompat(Entity target) {
        int baseHeight = maxPower.get() ? 170 : fallHeight.get();
        if (airCheck.get()) {
            int maxAir = getMaxHeightAbovePlayer();
            if (maxAir <= 0) {
                if (debugLog.get()) {
                    info("[降龙] 图腾连击：头顶无空间 → 跳过");
                }
                return;
            }
            baseHeight = Math.min(baseHeight, maxAir);
        }

        // ── 起点（上一包位置）与落点（当前位置）──
        Vec3d startPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        Vec3d landPos = startPos;

        // ── 高度从预算反推，不能直接用设置值 ──
        //   「最大威力 170 再加递增高度 肯定超限」——正确。
        //   同一 tick 内 N 击，第一击下标最小、上限最低（N=3 时仅 ~128.7），
        //   所以「170+10+10」数学上不可能。
        //   服务端证：垫包 48 个 → i>20 → i 归 1 → 阈值塌回 100 → 被拒。
        //
        // 2026-10-11 追加：planLadder 现在会模拟服务端真实的
        //   allowedPlayerTicks 递减（每发一个「有位移」包 -1）。
        //   修了上跳被接受、落回被拒 → 回弹到天上。
        //
        // 降龙十八掌不需要拱门路径tp：
        //   它的每一个包都用 `mc.player.getX()/getZ()` ⇒ 水平位移恒为 0
        //   （ 2081 个 tick 只有 1 个 x/z 列）。
        //   水平位移为 0 的包不可能 MOVED_WRONGLY。
        //   所以这里用 archPerHit = 2（① 上跳 ② 落回）
        Vec3d[] tail = new Vec3d[0];
        GcoreMoveSequenceUtil.Ladder ladder = null;
        int hits = totemAttacks.get();
        for (int n = hits; n >= 1; n--) {
            ladder = GcoreMoveSequenceUtil.planLadder(
                packetLimit.get(), 0, TOTEM_STEP, n, baseHeight,
                totemHeightIncrease.get(), tail, startPos, landPos, 2,
                serverStepMult.get());
            if (ladder != null) {
                hits = n;
                break;
            }
        }
        if (ladder == null) {
            if (debugLog.get()) {
                info("[降龙] 图腾阶梯排不出（高度=%.0f 预算=%d）→ 本次跳过",
                    baseHeight, packetLimit.get());
            }
            if (printWhenTooManyPacket.get()) {
                warning("本 tick 包数预算不足，图腾连击跳过。");
            }
            return;
        }
        if (hits < totemAttacks.get() && debugLog.get()) {
            info("[降龙] 图腾连击：%d 击排不出严格递增阶梯 → 降为 %d 击",
                totemAttacks.get(), hits);
        }

        // ── 只补一次垫包 ──
        boolean suppressed = suppressVanillaMove.get();
        if (suppressed) {
            MoveSequenceGate.arm();
        }
        try {
            for (int i = 0; i < ladder.fillers; i++) {
                //  2026-10-14：垫包必须带坐标（= 本 tick 起点 startPos），
                //    否则服务端按「玩家当前坐标」算 d7 ⇒ d7 > 0 ⇒ 不重置
                //    allowedPlayerTicks ⇒ 衰减到塌陷 ⇒ 阈值塌回 100 ⇒ 回弹。
                sendOwnMove(new PlayerMoveC2SPacket.PositionAndOnGround(
                    startPos.x, startPos.y, startPos.z,
                    false, mc.player.horizontalCollision));
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < hits; i++) {
                // 目标已打死不必再打死
                if (!(target instanceof LivingEntity living) || !living.isAlive()) {
                    break;
                }
                // ① 上跳
                sendOwnMove(new PlayerMoveC2SPacket.PositionAndOnGround(
                    mc.player.getX(), mc.player.getY() + ladder.heights[i], mc.player.getZ(),
                    false, mc.player.horizontalCollision));
                // ② 落回
                sendOwnMove(new PlayerMoveC2SPacket.PositionAndOnGround(
                    startPos.x, startPos.y, startPos.z,
                    false, mc.player.horizontalCollision));
                // ③ 攻击
                mc.getNetworkHandler().sendPacket(
                    PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));
                mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

                if (debugLog.get()) {
                    sb.append(String.format(Locale.ROOT, "%.1f ", ladder.heights[i]));
                }
            }
            if (debugLog.get()) {
                info("[降龙] 图腾阶梯: 垫包=%d 击数=%d 高度=%s",
                    ladder.fillers, hits, sb.toString().trim());
            }

            //  2026-10-11 修小概率结算摔落伤害：
            //   图腾连击的最后是「落回 startPos」（向下的包）⇒ 服务端
            //   `flag = d4 > 0` 为 false ⇒ **不会** resetFallDistance，
            //   于是 fallDistance 留了下来。补一个「抬高 9E-8」的包把债务清掉。
            //   机制详见 doPlayerTeleports 的注释。
            if (preventDeath.get()) {
                GcoreMoveSequenceUtil.clearFallDebt(startPos);
                mc.player.fallDistance = 0;
            }
        } finally {
            if (suppressed) {
                MoveSequenceGate.disarm();
            }
        }
    }

    // --- 核心秒杀算法  ---
    
    private boolean performMaceExploit(Entity target) {
        int blocks;
        // --- 逻辑分流：空气检查 vs 没敌模式 ---
        if (airCheck.get()) {
            blocks = getMaxHeightAbovePlayer();
            BlockPos isopenair1 = mc.player.getBlockPos().add(0, blocks, 0);
            BlockPos isopenair2 = mc.player.getBlockPos().add(0, blocks + 1, 0);
            if (!isSafeBlock(isopenair1) || !isSafeBlock(isopenair2)) {
                if (debugLog.get()) {
                    info("[降龙] 空气检查失败: blocks=%d p1=%s p2=%s → 不发包、不攻击",
                        blocks, isSafeBlock(isopenair1), isSafeBlock(isopenair2));
                }
                return false; // 没空气就取消
            }
        } else {
            // 没敌模式，直接使用设定值，无视地形
            blocks = maxPower.get() ? 170 : fallHeight.get();
        }

        if (!airCheck.get() && dynamicHeight.get() && mc.world != null) {
            double maxMace = blocks;
            double deltaY = target.getY() - mc.player.getY();
            double gap = GcoreSpaceSearchUtil.searchFirstNoCollisionSpaceYHeight(
                new Vec3d(mc.player.getX(), mc.player.getY() + maxMace, mc.player.getZ()),
                0, Math.max(0, maxMace - 2 - deltaY), false);
            double maceHeight = maxMace + gap;
            if (debugLog.get()) {
                info("[降龙] 动态找空间: maxMace=%.0f deltaY=%.1f gap=%.2f → 高度=%.2f",
                    maxMace, deltaY, gap, maceHeight);
            }
            if (maceHeight > 0 && maceHeight < blocks) {
                blocks = (int) maceHeight;
            }
        }

        // 如果高度为0（安全模式下没找到空间），则不执行
        if (blocks <= 0) {
            if (debugLog.get()) {
                info("[降龙] 高度为 0 → 不发包、不攻击");
            }
            return false;
        }

        sendExploitPackets(blocks);
        if (debugLog.get()) {
            info("[降龙] 发包完成: blocks=%d (上跳 %.1f + 落回) → 即将攻击",
                blocks, mc.player.getY() + blocks);
        }
        return true;
    }

    
    private void sendExploitPackets(int blocks) {
        previouspos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        //  2026-10-11 修复：垫包数必须扣掉「本 tick 别人已发的移动包数」。
        int others = countOthersPackets.get() ? GcoreMoveBudget.sentThisTick() : 0;
        int budget = packetLimit.get();

        // 需要的下标（服务端阈值 = 倍率·i，故 need_i = ceil(高度 / 倍率)）
        //
        //  2026-10-11：把固定的 10.0 换成可配置的「服务端单包位移倍率」。
        //    服务端真实判据是 `位移 ≤ movedTooQuicklyMultiplier × i`，
        //    Spigot 默认 multiplier = 10.0 ⇒ 原式 ceil(高度/10) 正确。
        //    但服务器若把该倍率调大，本式就可以跟着放宽，否则会误判「预算不足」
        double mult = serverStepMult.get();
        int indexNeeded = (int) Math.ceil(Math.abs(blocks) / mult);
        // ★ 纯几何：垫包数 = 需要下标 - 自己的动作数（上跳 + 落回），与 others 无关
        int packetsRequired = Math.max(0, indexNeeded);
        int assumedOthers = countOthersPackets.get() ? others : 0;

        // ★ 预算校验：别人 + 自己（垫包 + 上跳 + 落回）不能超过本 tick 额度
        //   自己总包数 = (packetsRequired - 1) + 2 = packetsRequired + 1
        int total = assumedOthers + packetsRequired + 1;
        if (total > budget) {
            if (debugLog.get()) {
                info("[降龙] 预算不足：别人已发=%d 垫包=%d 合计=%d > 上限=%d",
                    others, Math.max(0, packetsRequired - 1), total, budget);
            }
            if (overBudgetMode.get() == OverBudgetMode.LowerHeight) {
                // 把高度降到本 tick 装得下的最大值
                int usableFillers = budget - assumedOthers - 2;   // -2：上跳 + 落回
                if (usableFillers < 0) {
                    if (printWhenTooManyPacket.get()) {
                        warning("本 tick 包数预算不足（别人已发 %d），本次不出手。", others);
                    }
                    return;
                }
                int maxIndex = assumedOthers + usableFillers + 1;
                int lowered = (int) Math.floor(maxIndex * mult);
                if (lowered < 1) {
                    if (printWhenTooManyPacket.get()) {
                        warning("本 tick 包数预算不足，本次不出手。");
                    }
                    return;
                }
                if (debugLog.get()) {
                    info("[降龙] 预算不足 → 高度 %d 降到 %d", blocks, lowered);
                }
                blocks = lowered;
                packetsRequired = usableFillers + 1;
            } else {
                // DeferNextTick：本 tick 不发（调用方下一 tick 会再试）
                if (debugLog.get()) {
                    info("[降龙] 预算不足 → 按「%s」跳过本次（下一 tick 重试）", overBudgetMode.get());
                }
                if (printWhenTooManyPacket.get()) {
                    warning("本 tick 包数预算不足，本次攻击跳过。");
                }
                return;
            }
        }

        if (packetsRequired > budget) packetsRequired = 1;
        boolean suppressed = suppressVanillaMove.get();
        if (suppressed) {
            MoveSequenceGate.arm();
        }
        try {
        // 发包逻辑
        if (blocks <= 22) {
            if (mc.player.hasVehicle()) {
                for (int i = 0; i < 4; i++) {
                    mc.player.networkHandler.sendPacket(VehicleMoveC2SPacket.fromVehicle(mc.player.getVehicle()));
                }
                double maxHeight = Math.min(mc.player.getVehicle().getY() + 22, mc.player.getVehicle().getY() + blocks);
                doVehicleTeleports(maxHeight, blocks);
            } else {
                // 关键点：发送4个**带坐标**的包（坐标 = 当前位置），恍惚服务器状态
                // 而不改变位置，从而在3格空间生效。
                //  2026-10-14：必须带坐标 —— 无坐标包会让服务端用「玩家当前坐标」
                //   算 d7，一旦偏离 firstGood 就 d7>0 ⇒ 不重置 allowedPlayerTicks。
                //   见 GcoreMoveSequenceUtil#sendDummy。
                for (int i = 0; i < 4; i++) {
                    mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        previouspos.x, previouspos.y, previouspos.z,
                        false, mc.player.horizontalCollision));
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
                    //  同上：垫包必须带坐标（= 当前位置），否则 d7>0 不重置额度。
                    mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        previouspos.x, previouspos.y, previouspos.z,
                        false, mc.player.horizontalCollision));
                }
                double heightY = mc.player.getY() + blocks;
                doPlayerTeleports(heightY);
            }
        }
        } finally {
            // 总是复位，不然卡住自己动不了了
            if (suppressed) {
                MoveSequenceGate.disarm();
            }
        }
    }

    
    private void doPlayerTeleports(double height) {
        PlayerMoveC2SPacket movepacket = new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(), height, mc.player.getZ(), false, mc.player.horizontalCollision);

        PlayerMoveC2SPacket homepacket = new PlayerMoveC2SPacket.PositionAndOnGround(
                previouspos.getX(), previouspos.getY(), previouspos.getZ(),
                false, mc.player.horizontalCollision);

        ((IPlayerMoveC2SPacket) homepacket).meteor$setTag(1337);
        ((IPlayerMoveC2SPacket) movepacket).meteor$setTag(1337);
        mc.player.networkHandler.sendPacket(movepacket);
        mc.player.networkHandler.sendPacket(homepacket);
        if (preventDeath.get()) {
            mc.player.fallDistance = 0;
        }

    }

    //以前+0.25回位，搞得人回到墙里了，就回弹飞天了。
    private void clearFallDebtAfterAttack() {
        if (!preventDeath.get()) {
            return;
        }
        double homeY = previouspos.getY();
        GcoreMoveSequenceUtil.clearFallDebt(previouspos.getX(), homeY, previouspos.getZ());
        // 客户端那份也顺手清掉（保持与原来一致，且避免本地预测差异）
        mc.player.fallDistance = 0;
    }

    private void doVehicleTeleports(double height, int blocks) {
        if (mc.player.getVehicle() == null) return;
        mc.player.getVehicle().setPosition(mc.player.getVehicle().getX(), height + blocks, mc.player.getVehicle().getZ());
        mc.player.networkHandler.sendPacket(VehicleMoveC2SPacket.fromVehicle(mc.player.getVehicle()));
        mc.player.getVehicle().setPosition(previouspos);
        mc.player.networkHandler.sendPacket(VehicleMoveC2SPacket.fromVehicle(mc.player.getVehicle()));
    }

    
    private boolean isHoldingMace() {
        return mc.player != null && mc.player.getMainHandStack().getItem() == Items.MACE;
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

    
    private Vec3d targetPosFor(Entity entity) {
        if (!usePredict.get()) {
            return entity.getEntityPos();
        }
        return ImgPositionPredict.INSTANCE.predict(entity, predictTicksNow(), predictMode.get());
    }

    
    private int predictTicksNow() {
        if (predictAuto.get()) {
            return ImgPositionPredict.ticksFromPing(0, predictMaxTicks.get());
        }
        return predictTicks.get();
    }

    // --- 实体过滤检查 (含黑白名单) ---
    private boolean entityCheck(Entity entity) {
        if (!(entity instanceof LivingEntity) || !entity.isAlive()) return false;
        if (entity == mc.player) return false;
        //  2026-10-13：距离判定改用「预判位置」。
        //  客户端实体位置是插值出来的（滞后 3 tick），高速目标会让
        //   `distanceTo` 偏大 ⇒ 明明在射程内却判定为「没目标」⇒ 什么都不做。
        //   见 ImgPositionPredict 的类注释。
        if (mc.player.getEntityPos().distanceTo(targetPosFor(entity)) > range.get()) return false;
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
