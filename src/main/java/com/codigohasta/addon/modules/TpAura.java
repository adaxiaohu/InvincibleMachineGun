package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.mixin.InventoryAccessor;
import com.codigohasta.addon.modules.TpAura.AttackMode;
import com.codigohasta.addon.modules.TpAura.Mode;
import com.codigohasta.addon.utils.gcore.GcoreAimUtil;
import com.codigohasta.addon.utils.gcore.GcoreBlockUtil;
import com.codigohasta.addon.utils.gcore.GcoreCombatUtil;
import com.codigohasta.addon.utils.gcore.GcoreMoveSequenceUtil;
import com.codigohasta.addon.utils.gcore.GcoreServerEquivMove;
import com.codigohasta.addon.utils.gcore.GcoreSpaceSearchUtil;
import com.codigohasta.addon.utils.gcore.GcoreTpOption;
import com.codigohasta.addon.utils.gcore.GcoreTpUtil;
import com.codigohasta.addon.utils.gcore.MoveSequenceGate;
import com.codigohasta.addon.utils.img.ImgPositionPredict;
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
import meteordevelopment.meteorclient.events.packets.PacketEvent;
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
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
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
    private final Setting<Double> cooldownThreshold = sgTiming.add(new DoubleSetting.Builder().name("蓄力阈值").description("1").defaultValue(1.0).min(0.1).sliderMax(1.0).visible(() -> attackMode.get() == AttackMode.Smart).build());
    private final Setting<Integer> attackDelay = sgTiming.add(new IntSetting.Builder().name("额外延迟(Tick)").defaultValue(0).min(0).build());

    private final Setting<Integer> burstMinGapMs = sgTiming.add(new IntSetting.Builder()
        .name("连发最小间隔(ms)")
        .description("两次「如来神掌」出手之间的最小间隔。")
        .defaultValue(60)
        .min(0)
        .sliderRange(0, 200)
        .max(1000)
        .build());

    // --- 2. General Settings ---
    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder().name("自动切武器").defaultValue(true).build());
    private final Setting<Boolean> requireMace = sgGeneral.add(new BoolSetting.Builder().name("仅手持重锤").defaultValue(false).build());
    private final Setting<Boolean> swingHand = sgGeneral.add(new BoolSetting.Builder().name("挥手").defaultValue(true).build());
    private final Setting<Boolean> silentSwap = sgGeneral.add(new BoolSetting.Builder()
        .name("静默切换")
        .description("偷偷切换武器")
        .defaultValue(true)
        .visible(() -> autoSwitch.get())
        .build()
    );

    // --- 3. TP Settings ---
    public enum Mode { Vanilla, Paper }
    private final Setting<Mode> mode = sgTP.add(new EnumSetting.Builder<Mode>().name("兼容模式").defaultValue(Mode.Paper).build());
    private final Setting<Double> maxRange = sgTP.add(new DoubleSetting.Builder().name("最大范围").description("索敌与瞬移的最大距离。\n（旧版本这个值同时被当作模拟高度用，现已拆开，见下方「模拟高度」。）").defaultValue(99.0).min(1).sliderMax(999999).build());
    private final Setting<Boolean> goUp = sgTP.add(new BoolSetting.Builder().name("V-Clip").defaultValue(true).visible(() -> mode.get() == Mode.Paper).build());

    private final Setting<Boolean> separateLiftColumn = sgTP.add(new BoolSetting.Builder()
        .name("分离抬升柱")
        .description("把「抬升」放到附近一根头顶有空间的柱子上，没用")
        .defaultValue(false)
        .visible(() -> goUp.get() && mode.get() == Mode.Paper)
        .build());

    private final Setting<Integer> liftColumnRadius = sgTP.add(new IntSetting.Builder()
        .name("抬升柱搜索半径")
        .description("在自身周围多少格内找「头顶有空间」的柱没有用的。")
        .defaultValue(3)
        .min(1)
        .sliderRange(1, 8)
        .visible(() -> separateLiftColumn.get() && goUp.get() && mode.get() == Mode.Paper)
        .build());

    private final Setting<Boolean> driftDiag = sgTP.add(new BoolSetting.Builder()
        .name("漂移诊断")
        .description("打印客户端 y 的逐轮变化，用于排查「回弹到天上」。开发的时候用的")
        .defaultValue(false)
        .build());

    private double lastDiagClientY = Double.NaN;

    private final Setting<Boolean> maxPower = sgTP.add(new BoolSetting.Builder()
        .name("最大威力-如来神掌 (仅Paper/Spigot)")
        .description("模拟从 170 格高空掉落，用于在原版/Spigot 系服务端打出最大重锤伤害。\n"
            + "开启后使用固定 170 格，忽略「模拟高度」。")
        .defaultValue(false)
        .build()
    );
    private final Setting<Integer> fallHeight = sgTP.add(new IntSetting.Builder()
        .name("模拟高度")
        .description("如来神掌降落时候的模拟掉落高度基准")
        .defaultValue(30)
        .min(1)
        .sliderRange(1, 170)
        .max(170)
        .visible(() -> !maxPower.get())
        .build()
    );
    private final Setting<Boolean> dynamicHeight = sgTP.add(new BoolSetting.Builder()
        .name("动态找空间")
        .description(" 从「模拟高度」起点向下搜索，\n"
            + "找到第一个不卡住的高度。\n"
            + "关闭就直接使用「模拟高度」的固定值。")
        .defaultValue(true)
        .build());

    public enum OverBudgetMode {
        LowerHeight("降低高度继续", "把伪造高度降到本 tick 装得下的最大值"),
        DeferNextTick("延后到下一 tick", "这个没有用");

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
    private final Setting<Integer> paperPackets = sgTP.add(new IntSetting.Builder()
        .name("保底垫包数")
        .description("垫包数的**下限**。token 模型会算出本 tick 实际需要的垫包\n"
            + "只有在确认服务器放宽了限制、且确认垫包坐标正确时才需要调大。")
        .defaultValue(0)
        .min(0)
        .sliderMax(20)
        .max(999999999)
        .build());

    private final Setting<Boolean> usePredict = sgTP.add(new BoolSetting.Builder()
        .name("目标预判")
        .description("按服务端位置包跟踪目标，并按速度外推，乱飞的目标无处可逃哈哈。。\n"
            + "客户端位置是插值出来的，会滞后 3 tick。")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> predictAuto = sgTP.add(new BoolSetting.Builder()
        .name("按延迟自动预判")
        .description("按自己的往返延迟自动算预判刻数：ticks = round(延迟/2/50)。\n"
            + "攻击包要飞半个 RTT 才到服务端，所以要把目标外推到那时。\n"
            + "例：延迟 100ms → 预判 1 tick；延迟 200ms → 2 tick。")
        .defaultValue(true)
        .visible(usePredict::get)
        .build());

    private final Setting<Integer> predictTicks = sgTP.add(new IntSetting.Builder()
        .name("预判刻数")
        .description("往前预测多少 tick。\n"
            + "0 = 只用「服务端位置」消掉插值滞后；\n"
            + "1~3 = 额外按速度外推，追高速目标。")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 10)
        .visible(() -> usePredict.get() && !predictAuto.get())
        .build());

    private final Setting<Integer> predictMaxTicks = sgTP.add(new IntSetting.Builder()
        .name("自动预判上限")
        .description("自动模式下允许的最大预判刻数。")
        .defaultValue(3)
        .min(0)
        .sliderRange(0, 10)
        .visible(() -> usePredict.get() && predictAuto.get())
        .build());

    private final Setting<ImgPositionPredict.Mode> predictMode = sgTP.add(new EnumSetting.Builder<ImgPositionPredict.Mode>()
        .name("预判算法")
        .description("可能都差不多")
        .defaultValue(ImgPositionPredict.Mode.LINEAR)
        .visible(usePredict::get)
        .build());

    // ───────────── 包数预算（ k 预算模型） ─────────────
    //
    // 2026-10-11 重写
    //
    // 服务端「本 tick 第几个移动包」k（= i）：
    //     ServerGamePacketListenerImpl.java:1490-1495
    //         i = receivedMovePacketCount - knownMovePacketCount;
    //         if (i > Math.max(allowedPlayerTicks, 5)) { i = 1; }   // ★ k>20 → 塌回
    //     :1527  阈值 = max(100, (10 * i * speed)²)                // 本服 = 100·k²
    // ⇒ 第 k 个包最多请求 10k 格；k > 20 时阈值塌回 100，任何大位移都被拒。
    //
    //
    //     i = receivedMovePacketCount - knownMovePacketCount;   // 本 tick 已收包数
    //     if (i > max(allowedPlayerTicks, 5)) i = 1;            // ★ 超限 → i 归 1
    //     if (!hasRot && !(d7 > 0)) allowedPlayerTicks = 20;    // 无位移包 → 重置额度
    //     else                       allowedPlayerTicks--;
    //     阈值 = max(100, (movedTooQuicklyMultiplier * i * speed)²)   // 本服 = 100·i²
    //
    // ⇒ 单包允许的位移 **随 i 增大而增大**（阈值是 (10·i)²，兜底 100）。
    private final Setting<Double> moveDistance = sgTP.add(new DoubleSetting.Builder()
        .name("单包位移")
        .description("★ 每个移动包允许走的位移参考值（服务端阈值 = 10 × 本包下标）。\n"
            + "需要多少下标 = ceil(高度 / 本值)。")
        .defaultValue(9.9)
        .range(1.0, Double.MAX_VALUE)
        .sliderRange(5.0, 10.0)
        .build()
    );
    private final Setting<Integer> limitPacket = sgTP.add(new IntSetting.Builder()
        .name("移动包上限")
        .description("本 tick 允许发出的移动包数量上限（服务端 allowedPlayerTicks 即 20）。\n"
            + "这是「k 预算」的封顶值：垫包 + 位移包 + 攻击包的总下标不能超过它，\n"
            + "否则服务端会把 i 归 1、阈值塌回 100，大位移包会被整包丢弃回弹。")
        .defaultValue(20)
        .range(5, Integer.MAX_VALUE)
        .sliderRange(5, 20)
        .build()
    );

    private final Setting<Double> serverStepMult = sgTP.add(new DoubleSetting.Builder()
        .name("服务端单包位移倍率")
        .description("对应服务端 spigot.yml 的 moved-too-quickly-multiplier（默认 10.0）。\n"
            + "服务端判据：单包位移 ≤ 本值 × 本包下标。\n"
            + "只有服务器**特意放宽/关闭**了该检查时才需要调大；\n"
            + "  调大后「单包位移」也要一起调大才能生效。\n"
            + "默认 10.0 = Spigot 默认值，行为与原版一致。")
        .defaultValue(GcoreMoveSequenceUtil.SERVER_STEP_MULT)
        .min(0.1)
        .sliderRange(1.0, 100.0)
        .max(Double.MAX_VALUE)
        .build()
    );
    private final Setting<Boolean> countOthersPackets = sgTP.add(new BoolSetting.Builder()
        .name("计入他人已发包")
        .description("把「本 tick 其他来源（防回拉 GAntiLag / 原版）已发出的移动包」\n"
            + "从垫包预算里扣掉。\n"
            + "服务端的「第几个包」是全 tick 所有来源累计的，不是只数自己 —— \n"
            + "不扣掉它们，位移包会被顶到第 21 个之后，服务端把 i 归 1、阈值塌回 100，\n"
            + "位移包被整包丢弃。")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> suppressVanillaMove = sgTP.add(new BoolSetting.Builder()
        .name("抑制原版移动包")
        .description("发包时压掉原版 ClientPlayerEntity.sendMovementPackets。")
        .defaultValue(true)
        .build()
    );
    private final Setting<Integer> budgetBias = sgTP.add(new IntSetting.Builder()
        .name("预算余量")
        .description("【已失效，保留仅为兼容旧存档】垫包公式已改为纯几何（不减「别人已发」），\n"
            + "因此本值不再影响上跳包的真实下标。")
        .defaultValue(1)
        .range(0, 3)
        .sliderRange(0, 3)
        .visible(() -> true)
        .build()
    );
    private final Setting<Boolean> printWhenTooManyPacket = sgTP.add(new BoolSetting.Builder()
        .name("超限时提示")
        .description("包数超限时在聊天栏提示。")
        .defaultValue(true)
        .build()
    );
    private final Setting<OverBudgetMode> overBudgetMode = sgTP.add(new EnumSetting.Builder<OverBudgetMode>()
        .name("超限时行为")
        .description("本 tick 装不下整段位移时怎么办。")
        .defaultValue(OverBudgetMode.DeferNextTick)
        .build()
    );
    private final Setting<Boolean> debugLog = sgTP.add(new BoolSetting.Builder()
        .name("调试日志")
        .description("把每次攻击的关键中间量打到聊天栏/日志，用于定位问题。\n"
            + "包含：是否找站位、最终落点、动态高度、token 数、攻击是否发出。开发用的")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> preventFallDamage = sgTP.add(new BoolSetting.Builder()
        .name("防摔死 (清掉落债务)")
        .description("位移结束后补发一个抬高极小量(9E-8)的包，让服务端 resetFallDistance")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> findVecToAttack = sgTP.add(new BoolSetting.Builder()
        .name("自动找攻击站位")
        .description("目标脚下站不住时，在附近找一个「站得住又打得到」的位置。暂时没用")
        .defaultValue(true)
        .build());
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
    private final Setting<Boolean> totemCompat = sgTotem.add(new BoolSetting.Builder().name("图腾绕过").description("连续多次攻击打穿图腾无敌帧，仅Paper模式有效").defaultValue(false).build());
    private final Setting<Boolean> totemDynamic = sgTotem.add(new BoolSetting.Builder()
        .name("图腾动态判断")
        .description("只在目标【手上持有图腾】或【刚刚消耗过图腾】时才执行连击。。")
        .defaultValue(false)
        .visible(totemCompat::get)
        .build());
    private final Setting<Integer> totemAttacks = sgTotem.add(new IntSetting.Builder().name("攻击次数").description("连续攻击次数(1-3)，3打穿所有图腾但射程只有20m，超过射程会降级哦！").defaultValue(3).min(1).max(3).sliderRange(1, 3).visible(() -> totemCompat.get()).build());
    private final Setting<Integer> totemHeightIncrease = sgTotem.add(new IntSetting.Builder().name("递增高度").description("每次额外攻击增加的下落高度").defaultValue(9).min(1).sliderRange(1, 100).visible(() -> totemCompat.get()).build());

    private final SettingGroup sgInvuln = settings.createGroup("无敌帧");
    private final Setting<Integer> teleportThrottle = sgInvuln.add(new IntSetting.Builder()
        .name("被传送后暂停")
        .description("收到服务端拉回(传送)包后，暂停发包的 tick 数。没用不开")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 40)
        .build());
    private final Setting<Boolean> smartInvuln = sgInvuln.add(new BoolSetting.Builder()
        .name("智能无敌帧")
        .description("自动判断打不打你")
        .defaultValue(false)
        .build());
    private final Setting<Integer> invulnMargin = sgInvuln.add(new IntSetting.Builder()
        .name("等待余量")
        .description("自动等待时额外多等的 tick 数。没用不开")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 10)
        .visible(smartInvuln::get)
        .build());
    private final Setting<Boolean> ignoreBlocking = sgInvuln.add(new BoolSetting.Builder()
        .name("忽略举盾目标")
        .description("目标正在举盾格挡时不攻击。")
        .defaultValue(false)
        .build());

    private static final String MODULE_NAME = "TpAura";

    private final List<Entity> targets = new ArrayList<>();
    private final List<Vec3d> renderPathNodes = new ArrayList<>();
    private Entity currentTarget;
    private int originalSlot = -1;
    private int silentSwapSlot = -1;
    private int silentSwapPrevSlot = -1;
    private int delayTimer = 0;
    private int teleportPause = 0;
    private int waitTicks = 0;
    private long lastBurstNanos = 0L;
    private GcoreMoveSequenceUtil.Context moveCtx;
    private final List<Vec3d> pendingForward = new ArrayList<>();
    private final List<Vec3d> pendingBack = new ArrayList<>();
    private boolean pendingNeedAttack = false;
    private Entity pendingTarget;
    private Vec3d pendingStartPos;
    private Vec3d pendingResumeFrom;
    private int pendingFailCount = 0;
    private static final int PENDING_MAX_FAIL = 3;

    public TpAura() {
        super(AddonTemplate.CATEGORY, "如来神掌", "有没有听过一招，从天而降的掌法...娱乐功能");
    }

    @Override
    public void onActivate() {
        originalSlot = -1;
        silentSwapSlot = -1;
        silentSwapPrevSlot = -1;
        delayTimer = 0;
        teleportPause = 0;
        waitTicks = 0;
        lastBurstNanos = 0L;
        clearPendingMoves();
        renderPathNodes.clear();
        GcoreCombatUtil.clear();
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
        waitTicks = 0;
        clearPendingMoves();
        MoveSequenceGate.disarm();
        GcoreCombatUtil.clear();
    }
    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (event.packet instanceof EntityStatusS2CPacket packet) {
            GcoreCombatUtil.onEntityStatus(packet.getEntity(mc.world), packet.getStatus());
            return;
        }
        if (event.packet instanceof PlayerPositionLookS2CPacket packet) {

            Vec3d to = packet.change().position();
            double dy = to.y - mc.player.getY();
            if (dy > 5.0) {
                warning("被服务端向上拉回 %.1f 格（到 y=%.1f，我方 y=%.1f）。"
                    + "这下回弹到天上了吧！。", dy, to.y, mc.player.getY());
            }
            if (teleportThrottle.get() > 0) {
                teleportPause = teleportThrottle.get();
            }
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

    private void clearPendingMoves() {
        pendingForward.clear();
        pendingBack.clear();
        pendingNeedAttack = false;
        pendingTarget = null;
        pendingStartPos = null;
        pendingResumeFrom = null;
        pendingFailCount = 0;
    }

    private boolean continuePendingMoves() {
        if (pendingForward.isEmpty() && pendingBack.isEmpty() && !pendingNeedAttack) {
            return false;
        }
        if (mc.player == null || mc.getNetworkHandler() == null) {
            clearPendingMoves();
            return false;
        }

        Vec3d from = (pendingResumeFrom != null) ? pendingResumeFrom : pendingStartPos;
        if (from == null) {
            clearPendingMoves();
            return false;
        }

        moveCtx = newContext(from);

        boolean suppressed = suppressVanillaMove.get();
        if (suppressed) {
            MoveSequenceGate.arm();
        }
        int sentThisTick = 0;
        try {
            // ── 阶段 1：把「去程剩余」发完 ──
            if (!pendingForward.isEmpty()) {
                int sent = 0;
                for (Vec3d mv : pendingForward) {
                    if (!GcoreMoveSequenceUtil.sendOne(moveCtx, mv)) {
                        break;
                    }
                    sent++;
                }
                if (sent > 0) {
                    pendingResumeFrom = pendingForward.get(sent - 1);
                    pendingForward.subList(0, sent).clear();
                    sentThisTick += sent;
                }
                if (!pendingForward.isEmpty()) {
                    // 本 tick 又没发完 ⇒ 留到下一 tick（ 只留剩下的）
                    pendingFailCount = (sent == 0) ? pendingFailCount + 1 : 0;
                    if (pendingFailCount >= PENDING_MAX_FAIL) {
                        if (debugLog.get()) {
                            info("[TpAura] 续传连续 %d 次发不出包，放弃本轮",
                                pendingFailCount);
                        }
                        if (returnPos.get() && pendingStartPos != null) {
                            mc.player.setPosition(pendingStartPos.x, pendingStartPos.y,
                                pendingStartPos.z);
                        }
                        clearPendingMoves();
                    }
                    if (returnPos.get() && pendingStartPos != null) {
                        mc.player.setPosition(pendingStartPos.x, pendingStartPos.y,
                            pendingStartPos.z);
                    }
                    applyFallProtection(pendingStartPos != null ? pendingStartPos : from);
                    return true;
                }
                // 去程发完了 ⇒ 本 tick 接着攻击（若这轮攻击还没发过）
                pendingNeedAttack = true;
            }

            // ── 阶段 2：在攻击位出招 ──
            if (pendingNeedAttack && pendingTarget != null) {
                if (swingHand.get()) {
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
                mc.player.networkHandler.sendPacket(
                    PlayerInteractEntityC2SPacket.attack(pendingTarget, mc.player.isSneaking()));
                pendingNeedAttack = false;
            }

            // ── 阶段 3：把「回程剩余」发完 ──
            if (!pendingBack.isEmpty()) {
                int sent = 0;
                for (Vec3d mv : pendingBack) {
                    if (!GcoreMoveSequenceUtil.sendOne(moveCtx, mv)) {
                        break;
                    }
                    sent++;
                }
                if (sent > 0) {
                    pendingResumeFrom = pendingBack.get(sent - 1);
                    pendingBack.subList(0, sent).clear();
                    sentThisTick += sent;
                }
                if (!pendingBack.isEmpty()) {
                    pendingFailCount = (sent == 0) ? pendingFailCount + 1 : 0;
                    if (pendingFailCount >= PENDING_MAX_FAIL) {
                        if (debugLog.get()) {
                            info("[TpAura] 续传连续 %d 次发不出包，放弃本轮",
                                pendingFailCount);
                        }
                        clearPendingMoves();
                    }
                    applyFallProtection(pendingStartPos != null ? pendingStartPos : from);
                    return true;
                }
            }

            // ── 全部完成 ──
            if (debugLog.get()) {
                info("[TpAura] 续传完成（本 tick 发 %d 包）", sentThisTick);
            }
            Vec3d home = pendingStartPos;
            clearPendingMoves();
            if (returnPos.get() && home != null) {
                mc.player.setPosition(home.x, home.y, home.z);
            }
            applyFallProtection(home != null ? home : from);
            return true;
        } finally {
            if (suppressed) {
                MoveSequenceGate.disarm();
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        //  图腾记忆倒计时（记录「谁刚消耗过图腾」）
        GcoreCombatUtil.tick();
        if (mc.player.isUsingItem()) {
            return;
        }
        if (teleportPause > 0) {
            teleportPause--;
            return;
        }
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        if (continuePendingMoves()) {
            return;
        }
        if (burstMinGapMs.get() > 0 && lastBurstNanos != 0L) {
            long elapsedMs = (System.nanoTime() - lastBurstNanos) / 1_000_000L;
            if (elapsedMs < burstMinGapMs.get()) {
                return;
            }
        }

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

        Entity target = targets.get(0);

        //  举盾目标：
        if (ignoreBlocking.get() && GcoreCombatUtil.isBlocking(target)) {
            currentTarget = target;
            return;
        }

        //  智能无敌帧：判断现在攻击有没有用
        if (smartInvuln.get() && GcoreCombatUtil.invulnerableTicks(target) > 0) {
            boolean compatting = totemCompat.get() && mode.get() == Mode.Paper
                && (!totemDynamic.get()
                    || GcoreCombatUtil.shouldCompatTotem(
                           target instanceof LivingEntity living ? living : null));
            if (!compatting) {
                int wait = GcoreCombatUtil.suggestWaitTicks(target, invulnMargin.get());
                if (wait > 0) {
                    if (debugLog.get()) {
                        info("[TpAura] 智能无敌帧: 目标 regen=%d → 等待 %d tick 再打",
                            GcoreCombatUtil.invulnerableTicks(target), wait);
                    }
                    currentTarget = target;
                    waitTicks = wait;
                    return;
                }
            }
        }

        currentTarget = target;

        // 5. 执行瞬移轰炸
        executeIMGAttack(currentTarget);
        swapBackWeapon();

        // 6. 重置延迟计时器
        delayTimer = attackDelay.get();
    }

    private void executeIMGAttack(Entity target) {
        Vec3d startPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        if (driftDiag.get()) {
            info("[TpAura] 漂移诊断: clientY=%.2f 上一轮末clientY=%.2f 目标Y=%.2f",
                startPos.y, lastDiagClientY, target.getY());
            lastDiagClientY = startPos.y;
        }
        Vec3d targetPos = targetPosFor(target);
        double reach = maxPower.get() ? 170 : fallHeight.get();
        Vec3d finalPos = !invalid(targetPos) ? targetPos : findNearestPos(targetPos);
        if (finalPos == null) return;
        boolean needFind = false;
        String findReason = "";
        if (findVecToAttack.get()) {
            if (!GcoreBlockUtil.checkNoPosCollie(finalPos)) {
                needFind = true;
                findReason = "原位站不住";
            } else if (!GcoreAimUtil.checkCanSee(
                           finalPos.add(0, 1.62, 0), target.getEyePos())) {
                needFind = true;
                findReason = "原位看不到目标";
            } else if (mc.world != null && !mc.world.getFluidState(BlockPos.ofFloored(finalPos)).isEmpty()) {
                needFind = true;
                findReason = "目标在液体中";
            } else if (mc.world != null && !mc.world.getFluidState(BlockPos.ofFloored(targetPos)).isEmpty()) {
                needFind = true;
                findReason = "目标在液体中";
            }
        }

        try (var ignored = GcoreServerEquivMove.enter(MODULE_NAME)) {
            if (needFind) {
                Vec3d found = GcoreTpUtil.findVecToAttack(
                    finalPos, target instanceof LivingEntity living ? living.getStandingEyeHeight() : 1.62);
                if (found == null) {
                    if (debugLog.get()) {
                        info("[TpAura] 找站位失败(%s)，放弃本次。目标=%s",
                            findReason, String.format("%.1f,%.1f,%.1f", targetPos.x, targetPos.y, targetPos.z));
                    }
                    return;   
                }
                if (debugLog.get()) {
                    info("[TpAura] 找站位命中(%s): %.1f,%.1f,%.1f → %.1f,%.1f,%.1f",
                        findReason, finalPos.x, finalPos.y, finalPos.z, found.x, found.y, found.z);
                }
                finalPos = found;
            }
        }

        if (mode.get() == Mode.Paper) {
            double maxMace = maxPower.get() ? 170 : fallHeight.get();
            double maceHeight;
            if (dynamicHeight.get()) {
                double deltaY = target.getY() - finalPos.y;
                double gap = GcoreSpaceSearchUtil.searchFirstNoCollisionSpaceYHeight(
                    finalPos.add(0, maxMace, 0), 0, Math.max(0, maxMace - 2 - deltaY), false);
                maceHeight = maxMace + gap;
            } else {
                maceHeight = maxMace;
            }

            if (maceHeight <= 2.0) {
                if (printWhenTooManyPacket.get()) {
                    warning("头顶空间不足（可用 %.1f 格），无法执行重锤模拟。", maceHeight);
                }
                return;
            }
            reach = maceHeight;
        }

        Vec3d highStart = startPos.add(0, reach, 0);
        Vec3d highTarget = finalPos.add(0, reach, 0);
        renderPathNodes.clear();
        renderPathNodes.add(startPos);
        if (mode.get() == Mode.Paper && goUp.get()) {
            renderPathNodes.add(highStart);
            renderPathNodes.add(highTarget);
        }
        renderPathNodes.add(finalPos);
        if (mode.get() != Mode.Paper) {
            for (int i = 0; i < 4; i++) {
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    startPos.x, startPos.y, startPos.z, false, mc.player.horizontalCollision));
            }
        }

        // 图腾动态判断：只在目标手上有图腾 / 刚用过图腾时才连击
        boolean totemMode = totemCompat.get() && mode.get() == Mode.Paper
            && (!totemDynamic.get()
                || GcoreCombatUtil.shouldCompatTotem(
                       target instanceof LivingEntity living ? living : null));

        if (mode.get() == Mode.Paper && !totemMode) {
            // 起点
            Vec3d currentStartPos = startPos;
            moveCtx = newContext(currentStartPos);
            List<Vec3d> toMoves = new ArrayList<>();
            List<Vec3d> backMoves = new ArrayList<>();
            List<Vec3d> allMoves = new ArrayList<>();
            double usedReach = reach;
            int tokens;
            Vec3d apexGo = null;
            if (goUp.get()) {
                apexGo = findApexNear(currentStartPos, finalPos, usedReach);
                if (apexGo == null) {
                    //  拱门搜索失败
                    if (debugLog.get()) {
                        info("[TpAura] 拱门搜索失败（找不到三段都能走通的拱顶）→ 本次放弃"
                                + " 起点=[%.1f,%.1f,%.1f] 目标=[%.1f,%.1f,%.1f]",
                            currentStartPos.x, currentStartPos.y, currentStartPos.z,
                            finalPos.x, finalPos.y, finalPos.z);
                    }
                    applyFallProtection(currentStartPos);
                    return;
                }
            }
            // 回程同理：单独搜一个拱顶高度（起终点互换）
            Vec3d apexBack = null;
            if (goUp.get()) {
                apexBack = findApexNear(finalPos, currentStartPos, usedReach);
            }

            while (true) {
                toMoves.clear();
                backMoves.clear();
                allMoves.clear();
                if (goUp.get()) {
                    // 2026-10-15 三段共用【同一个拱顶高度】⇒ 每段纯轴向
                    //   ① (startX, apexY, startZ)   纯竖直
                    //   ② (finalX, apexY, finalZ)   纯水平  ← y 与 ① 相同
                    //   ③ (finalX, finalY, finalZ)  纯竖直
                    double apexY = apexGo.y;
                    toMoves.add(new Vec3d(currentStartPos.x, apexY, currentStartPos.z));   // ①
                    toMoves.add(new Vec3d(finalPos.x, apexY, finalPos.z));                 // ②
                }
                // ③ 下降到攻击位（x/z 已在 ② 到位 ⇒ 这一包纯竖直）
                toMoves.add(finalPos);
                // 回程同样拆成纯轴向三段（起终点互换，拱顶用 apexBack）
                if (goUp.get() && apexBack != null) {
                    backMoves.add(new Vec3d(finalPos.x, apexBack.y, finalPos.z));          // ①'
                    backMoves.add(new Vec3d(currentStartPos.x, apexBack.y, currentStartPos.z)); // ②'
                }
                backMoves.add(currentStartPos);   // ③'
                allMoves.addAll(toMoves);
                allMoves.addAll(backMoves);

                // planTokens 内部已按「别人已发 + 垫包 + 本段动作 ≤ 预算」判定；
                // 返回 -1 时**一个包都没发**，所以这里可以安全地降高度重试。
                tokens = GcoreMoveSequenceUtil.planTokens(moveCtx, allMoves);
                if (tokens >= 0) {
                    break;
                }

                if (overBudgetMode.get() == OverBudgetMode.LowerHeight) {
                    double maxReach = GcoreMoveSequenceUtil.maxReachForBudget(
                        moveCtx.limit(), moveCtx.alreadyInTick(), allMoves.size(),
                        moveCtx.step());
                    // 保证严格下降，避免死循环
                    double lowered = Math.min(usedReach - 1.0, maxReach);
                    if (lowered > 2.0) {
                        usedReach = lowered;
                        //  降高度后拱顶重搜（更低的拱顶可能就够用了）
                        Vec3d re = GcoreTpUtil.findVClipVecToMove(
                            currentStartPos, finalPos,
                            Math.max(1.0, liftColumnRadius.get()),
                            false, GcoreTpOption.TpVClipFindType.ALL,
                            GcoreBlockUtil.getBufferMap());
                        if (re == null) {
                            break;      // 降无可降 ⇒ 走下面的放弃分支
                        }
                        apexGo = re;
                        Vec3d rb = GcoreTpUtil.findVClipVecToMove(
                            finalPos, currentStartPos,
                            Math.max(1.0, liftColumnRadius.get()),
                            false, GcoreTpOption.TpVClipFindType.ALL,
                            GcoreBlockUtil.getBufferMap());
                        apexBack = rb;
                        // 重新采样「别人已发数」并重置本序列计数（此时尚未发出任何包）
                        moveCtx.resetTick(currentStartPos);
                        continue;
                    }
                }

                if (debugLog.get()) {
                    info("[TpAura] 本 tick 装不下（别人已发=%d 预算=%d 段数=%d），按「%s」处理，本次不出手",
                        moveCtx.alreadyInTick(), moveCtx.limit(), allMoves.size(),
                        overBudgetMode.get());
                } else if (printWhenTooManyPacket.get()) {
                    warning("本 tick 包数预算不足，本次不出手。");
                }
                return;
            }

            if (debugLog.get()) {
                info("[TpAura] 发包: 模拟高度=%.1f 落点=%.1f,%.1f,%.1f 别人已发=%d 预算=%d 垫包=%d 段=去程%d/回程%d",
                    usedReach, finalPos.x, finalPos.y, finalPos.z,
                    moveCtx.alreadyInTick(), moveCtx.limit(), tokens,
                    toMoves.size(), backMoves.size());
            }

            // ★★★ 2026-10-14 修渲染与实际发包的 toMoves/backMoves 逐点一致
            renderPathNodes.clear();
            renderPathNodes.add(currentStartPos);          // 起点
            renderPathNodes.addAll(toMoves);               // 去程（①/②/③，含 riseCol）
            renderPathNodes.addAll(backMoves);             // 回程

            boolean suppressed = suppressVanillaMove.get();
            if (suppressed) {
                MoveSequenceGate.arm();
            }

            lastBurstNanos = System.nanoTime();

            // ① 走【去程】：上跳 + 落回
            int sentTo = 0;
            for (Vec3d mv : toMoves) {
                if (!GcoreMoveSequenceUtil.sendOne(moveCtx, mv)) {
                    break;
                }
                sentTo++;
            }
            if (sentTo < toMoves.size()) {
                if (debugLog.get()) {
                    info("[TpAura] ★去程未走完: 已发 %d/%d 包（预算 %d，别人已发 %d）"
                            + " → 留到下一 tick 续传",
                        sentTo, toMoves.size(), moveCtx.limit(), moveCtx.alreadyInTick());
                }
                clearPendingMoves();
                for (int i = sentTo; i < toMoves.size(); i++) {
                    pendingForward.add(toMoves.get(i));
                }
                // 回程此时一个都还没发（攻击都还没执行 全部留待续传
                pendingBack.addAll(backMoves);
                pendingNeedAttack = true;
                pendingTarget = target;
                pendingStartPos = currentStartPos;
                // 续传起点 = 最后一个真正发出的包的位置
                pendingResumeFrom = (sentTo > 0) ? toMoves.get(sentTo - 1) : currentStartPos;
                pendingFailCount = (sentTo == 0) ? 1 : 0;
                applyFallProtection(currentStartPos);
                if (suppressed) {
                    MoveSequenceGate.disarm();
                }
                return;
            }

            // ②  在攻击位出招（此时已落回目标处，fallDistance 已由上升+下降累积）
            boolean attacked = false;
            if (swingHand.get()) mc.player.swingHand(Hand.MAIN_HAND);
            mc.player.networkHandler.sendPacket(
                PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));
            attacked = true;

            // ③ 走【回程】
            int sentBack = 0;
            for (Vec3d mv : backMoves) {
                if (!GcoreMoveSequenceUtil.sendOne(moveCtx, mv)) {
                    break;
                }
                sentBack++;
            }
            if (sentBack < backMoves.size()) {
                // 2026-10-15：改成留到下一 tick 续传
                //   攻击已经发出，只差回程 ⇒ 下一 tick 只需补发剩下几包。
                if (debugLog.get()) {
                    info("[TpAura] ★回程未走完: 已发 %d/%d 包 → 留到下一 tick 续传",
                        sentBack, backMoves.size());
                }
                pendingForward.clear();
                pendingBack.clear();
                for (int i = sentBack; i < backMoves.size(); i++) {
                    pendingBack.add(backMoves.get(i));
                }
                pendingNeedAttack = false;          // 攻击已发过
                pendingTarget = target;
                pendingStartPos = currentStartPos;
                pendingResumeFrom = (sentBack > 0) ? backMoves.get(sentBack - 1) : finalPos;
                pendingFailCount = (sentBack == 0) ? 1 : 0;
                //  同上：回程没走完也要先清债务，避免被结算。
                applyFallProtection(currentStartPos);
                if (suppressed) {
                    MoveSequenceGate.disarm();
                }
                return;
            }

            if (debugLog.get()) {
                info("[TpAura] 攻击已发出=%s；回程完成", attacked);
            }

            // 收尾回到开始时的位置，不是 finalPos
            if (returnPos.get()) {
                mc.player.setPosition(currentStartPos.x, currentStartPos.y, currentStartPos.z);
            }
            // 摔落伤害保护（用 gcore 的 clearFallDebt）
            applyFallProtection(currentStartPos);
            if (suppressed) {
                MoveSequenceGate.disarm();
            }
            return;
        }

        // ───── 图腾连击═══════════════════════════════════════════════
        //
        // 2026-10-11 重写。
        if (totemMode) {
            Vec3d currentStartPos = startPos;
            moveCtx = newContext(currentStartPos);
            // 装不下就减少击数（而不是硬发会被服务端拒的包）
            // 2026-10-11：改为把「收尾包」也交给 planLadder 一起模拟。
            //   收尾包（回程 + 清摔落债务）同样会递减服务端的 allowedPlayerTicks，
            //   忽略它们会低估塌陷风险 —— 而塌陷一旦落在落回包上，
            //   服务端就停在高处并把我们拉上去回弹到天上。
            Vec3d[] tail = new Vec3d[]{
                currentStartPos,                                       // 回程
                currentStartPos.add(0, GcoreMoveSequenceUtil.DELTA_Y, 0) // 清债
            };
            GcoreMoveSequenceUtil.Ladder ladder = null;
            int hits = totemAttacks.get();
            int archPerHit = 3;

            // 2026-10-11：图腾路径也改用拱门。
            for (int n = hits; n >= 1 && ladder == null; n--) {
                ladder = GcoreMoveSequenceUtil.planLadder(
                    limitPacket.get(), moveCtx.alreadyInTick(), moveDistance.get(),
                    n, reach, totemHeightIncrease.get(), tail,
                    currentStartPos, finalPos, 3, serverStepMult.get());
                if (ladder != null) {
                    hits = n;
                    archPerHit = 3;
                }
            }
            if (ladder == null) {
                // 拱门排不出→ 退回对角跳
                for (int n = hits; n >= 1 && ladder == null; n--) {
                    ladder = GcoreMoveSequenceUtil.planLadder(
                        limitPacket.get(), moveCtx.alreadyInTick(), moveDistance.get(),
                        n, reach, totemHeightIncrease.get(), tail,
                        currentStartPos, finalPos, 2, serverStepMult.get());
                    if (ladder != null) {
                        hits = n;
                        archPerHit = 2;
                    }
                }
            }
            if (ladder == null) {
                // 两种形态都排不出 → 本 tick 不出手
                if (printWhenTooManyPacket.get()) {
                    warning("包数预算/高度排不出图腾连击，本次跳过。");
                }
                return;
            }
            if (hits < totemAttacks.get() && debugLog.get()) {
                info("[TpAura] 图腾连击：%d 击排不出严格递增阶梯 → 降级为 %d 击",
                    totemAttacks.get(), hits);
            }
            if (debugLog.get()) {
                info("[TpAura] 图腾路径形态=%s（%d 包/击）",
                    archPerHit == 3 ? "拱门" : "对角跳", archPerHit);
            }

            boolean suppressed = suppressVanillaMove.get();
            if (suppressed) {
                MoveSequenceGate.arm();
            }
            lastBurstNanos = System.nanoTime();
            try {
                GcoreMoveSequenceUtil.sendFillers(moveCtx, ladder.fillers);

                // 2026-10-14 修渲染renderPathNodes
                //   现在按实际发包的包序逐点生成：每击都是
                //     拱门 : 自身 → 自身+h → 目标上空+h → 目标
                //     对角 : 目标上空+h → 目标
                //   多击时呈锯齿。
                renderPathNodes.clear();
                renderPathNodes.add(currentStartPos);
                for (int i = 0; i < hits; i++) {
                    double h = ladder.heights[i];
                    if (archPerHit == 3) {
                        renderPathNodes.add(currentStartPos.add(0, h, 0));   // ① 自身拔高
                        renderPathNodes.add(finalPos.add(0, h, 0));          // ② 目标上空
                    } else {
                        renderPathNodes.add(finalPos.add(0, h, 0));          // 对角上跳
                    }
                    renderPathNodes.add(finalPos);                           // ③ 落回攻击位
                }
                renderPathNodes.add(currentStartPos);                        // 收尾回程

                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < hits; i++) {
                    if (archPerHit == 3) {
                        if (!GcoreMoveSequenceUtil.sendOne(moveCtx,
                                currentStartPos.add(0, ladder.heights[i], 0))) {
                            break;
                        }
                        if (!GcoreMoveSequenceUtil.sendOne(moveCtx,
                                finalPos.add(0, ladder.heights[i], 0))) {
                            break;
                        }
                    } else {
                        if (!GcoreMoveSequenceUtil.sendOne(moveCtx,
                                finalPos.add(0, ladder.heights[i], 0))) {
                            break;
                        }
                    }
                    // 落回攻击位
                    if (!GcoreMoveSequenceUtil.sendOne(moveCtx, finalPos)) {
                        break;
                    }
                    // 落回之后才攻击
                    if (swingHand.get()) {
                        mc.player.swingHand(Hand.MAIN_HAND);
                    }
                    mc.player.networkHandler.sendPacket(
                        PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));

                    if (debugLog.get()) {
                        sb.append(String.format(Locale.ROOT, "%.1f ", ladder.heights[i]));
                    }
                }
                if (debugLog.get()) {
                    // 诊断2026-10-11把「预算/塌陷」相关的中间量打出来
                    info("[TpAura] 图腾连击: 垫包=%d 击数=%d 高度=%s 别人已发=%d 请求击数=%d",
                        ladder.fillers, hits, sb.toString().trim(),
                        moveCtx.alreadyInTick(), totemAttacks.get());
                }

                // 收尾回到起点
                if (returnPos.get()) {
                    mc.player.setPosition(currentStartPos.x, currentStartPos.y, currentStartPos.z);
                }
                applyFallProtection(currentStartPos);
            } finally {
                if (suppressed) {
                    MoveSequenceGate.disarm();
                }
            }
            return;
        }

        // ───── Vanilla 模式：保持原有固定包序 ─────
        {
            if (goUp.get()) {
                sendMove(highStart);
                sendMove(highTarget);
            }
            sendMove(finalPos);

            if (swingHand.get()) mc.player.swingHand(Hand.MAIN_HAND);
            mc.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));
        }

        // C. Vanilla 模式的瞬间回传
        if (returnPos.get()) {
            if (goUp.get()) {
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
        } else if (offsetFix.get()) {
            Vec3d offset = getOffset(finalPos);
            sendMove(offset);
            mc.player.setPosition(offset.x, offset.y, offset.z);
        } else {
            mc.player.setPosition(finalPos.x, finalPos.y, finalPos.z);
        }
    }

    
    private GcoreMoveSequenceUtil.Context newContext(Vec3d from) {
        GcoreMoveSequenceUtil.Context ctx =
            new GcoreMoveSequenceUtil.Context(from, limitPacket.get());
        ctx.setCountOthers(countOthersPackets.get());
        ctx.setMinFillers(paperPackets.get());
        ctx.setStep(moveDistance.get());
        ctx.setServerStepMult(serverStepMult.get());
        return ctx;
    }

    private Vec3d findApexNear(Vec3d from, Vec3d to, double wantReach) {
        Map<BlockPos, BlockState> buf = GcoreBlockUtil.getBufferMap();
        double step = Math.max(1.0, liftColumnRadius.get());

        Vec3d direct = GcoreTpUtil.findVClipVecToMove(
            from, to, step, false, GcoreTpOption.TpVClipFindType.ALL, buf);
        if (direct != null) {
            if (Math.abs(direct.y - (from.y + wantReach)) <= 8.0) {
                return direct;
            }
        }
        for (double extra = 0.0; extra <= 60.0; extra += step) {
            Vec3d probe = GcoreTpUtil.findVClipVecToMove(
                from.add(0, wantReach + extra, 0), to,
                step, false, GcoreTpOption.TpVClipFindType.ALL, buf);
            if (probe != null) {
                return probe;
            }
            if (extra == 0.0) {
                Vec3d lower = GcoreTpUtil.findVClipVecToMove(
                    from.add(0, Math.max(1.0, wantReach - step), 0), to,
                    step, false, GcoreTpOption.TpVClipFindType.ALL, buf);
                if (lower != null) {
                    return lower;
                }
            }
        }
        return direct;   
    }

    private void sendMove(Vec3d pos) {
        PlayerMoveC2SPacket packet = new PlayerMoveC2SPacket.PositionAndOnGround(pos.x, pos.y, pos.z, false, false);
        ((IPlayerMoveC2SPacket) packet).meteor$setTag(1337);
        mc.player.networkHandler.sendPacket(packet);
    }

    private void applyFallProtection(Vec3d at) {
        if (mc.player == null) {
            return;
        }
        if (!preventFallDamage.get()) {
            return;
        }
        Vec3d last = (moveCtx != null) ? moveCtx.lastSent() : null;
        double safeY = (last != null) ? Math.max(at.y, last.y) : at.y;

        GcoreMoveSequenceUtil.clearFallDebt(at.x, safeY, at.z);
        if (debugLog.get()) {
            info("[TpAura] 清除摔落债: 在 %.1f,%.1f,%.1f 抬高 %.0e (客户端y=%.1f 我方末位y=%s)",
                at.x, safeY, at.z, GcoreMoveSequenceUtil.DELTA_Y,
                mc.player.getY(), last == null ? "n/a" : String.format(Locale.ROOT, "%.1f", last.y));
        }
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

    private Vec3d targetPosFor(Entity entity) {
        if (!usePredict.get()) {
            return new Vec3d(entity.getX(), entity.getY(), entity.getZ());
        }
        return ImgPositionPredict.INSTANCE.predict(entity, predictTicksNow(), predictMode.get());
    }

    
    private int predictTicksNow() {
        if (predictAuto.get()) {
            return ImgPositionPredict.ticksFromPing(0, predictMaxTicks.get());
        }
        return predictTicks.get();
    }

    private boolean entityCheck(Entity entity) {
        if (!(entity instanceof LivingEntity) || !entity.isAlive() || entity == mc.player) return false;
        if (!entities.get().contains(entity.getType())) return false;
        // 2026-10-13：距离判定改用预判位置这里也改了啊与落点口径一致，
        if (mc.player.getEntityPos().distanceTo(targetPosFor(entity)) > maxRange.get()) return false;
        
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