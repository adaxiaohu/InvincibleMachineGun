package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * CustomFishingBot v4 — 全自动钓鱼模块（优化咬钩检测 & 游戏策略）
 * <p>
 * 主要改进：
 * 1. 粒子包检测咬钩（适配 Custom-Fishing 的粒子效果提示）
 * 2. 浮标Y轴追踪（位置突变检测）
 * 3. 简化游戏类型检测（不依赖特定颜色字符）
 * 4. 改进 ACCURATE_CLICK 游戏策略
 * <p>
 * 全自动循环：
 * IDLE → CASTING → WAITING(粒子/速度/状态三重检测)
 * → BITE → GAME(小游戏) → COOLDOWN → IDLE
 */
public class CustomFishingBot extends Module {

    private static final Minecraft mc = Minecraft.getInstance();

    // ================================================================
    //  字体字符常量（来自 Resource Pack for games）
    // ================================================================

    private static final char BAR_POINTER     = '\ub001';
    private static final char BAR_BAR1        = '\ub002';
    private static final char BAR_BAR2        = '\ub003';
    private static final char BAR_BAR3        = '\ub004';
    private static final char BAR_BAR4        = '\ub005';
    private static final char BAR_BAR5        = '\ub006';
    private static final char BAR_BAR6        = '\ub007';
    private static final char BAR_BAR7        = '\ub008';
    private static final char BAR_BAR8        = '\ub009';
    private static final char BAR_BAR9        = '\ub00a';
    private static final char BAR_RAINBOW     = '\ub00b';
    private static final char BAR_BAR10       = '\ub00c';
    private static final char BAR_FISH        = '\ub00d';
    private static final char BAR_STRUGGLE_0  = '\ub00e';
    private static final char BAR_STRUGGLE_1  = '\ub00f';
    private static final char BAR_STRUGGLE_2  = '\ub010';
    private static final char BAR_BAR11       = '\ub011';
    private static final char BAR_JUDGE_EASY   = '\ub012';
    private static final char BAR_JUDGE_NORMAL = '\ub013';
    private static final char BAR_JUDGE_HARD   = '\ub014';

    // 偏移字符映射表
    private static final Map<Character, Integer> OFFSET_MAP = buildOffsetMap();

    private static Map<Character, Integer> buildOffsetMap() {
        Map<Character, Integer> map = new HashMap<>();
        map.put('\uf801', 1);   map.put('\uf802', 2);   map.put('\uf803', 4);   map.put('\uf804', 8);
        map.put('\uf805', 16);  map.put('\uf806', 32);  map.put('\uf807', 64);  map.put('\uf808', 128);
        map.put('\uf811', -1);  map.put('\uf812', -2);  map.put('\uf813', -4);  map.put('\uf814', -8);
        map.put('\uf815', -16); map.put('\uf816', -32); map.put('\uf817', -64); map.put('\uf818', -128);
        return Map.copyOf(map);
    }

    /** 所有 bar 填充字符（不含指针和鱼图标） */
    private static final Set<Character> BAR_CHARS = Set.of(
        BAR_BAR1, BAR_BAR2, BAR_BAR3, BAR_BAR4, BAR_BAR5,
        BAR_BAR6, BAR_BAR7, BAR_BAR8, BAR_BAR9, BAR_BAR10,
        BAR_BAR11, BAR_RAINBOW
    );

    /** 判定区域字符（hold游戏中的目标区） */
    private static final Set<Character> JUDGE_CHARS = Set.of(
        BAR_JUDGE_EASY, BAR_JUDGE_NORMAL, BAR_JUDGE_HARD
    );

    /** 鱼图标字符 */
    private static final Set<Character> FISH_CHARS = Set.of(
        BAR_FISH, BAR_STRUGGLE_0, BAR_STRUGGLE_1, BAR_STRUGGLE_2
    );

    /** 所有自定义字体字符（用于通用游戏检测） */
    private static final Set<Character> ALL_CUSTOM_CHARS = new HashSet<>();

    static {
        ALL_CUSTOM_CHARS.add(BAR_POINTER);
        ALL_CUSTOM_CHARS.addAll(BAR_CHARS);
        ALL_CUSTOM_CHARS.addAll(JUDGE_CHARS);
        ALL_CUSTOM_CHARS.addAll(FISH_CHARS);
    }

    // ============ 设置项 ============

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> verboseLog = sgGeneral.add(new BoolSetting.Builder()
        .name("verbose-log").description("打印日志到聊天栏").defaultValue(true).build()
    );

    private final Setting<Integer> actionInterval = sgGeneral.add(new IntSetting.Builder()
        .name("action-interval").description("操作间隔(tick,1tick=50ms)").defaultValue(1).min(0).max(10).sliderRange(0, 10).build()
    );

    private final Setting<Integer> maxWaitTicks = sgGeneral.add(new IntSetting.Builder()
        .name("max-wait-ticks").description("最长等待咬钩时间(tick, 20tick=1秒)").defaultValue(600).min(100).max(3600).sliderRange(100, 1200).build()
    );

    private final Setting<Integer> cooldownTicks = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown-ticks").description("收杆后的等待时间(tick)").defaultValue(20).min(5).max(100).sliderRange(5, 60).build()
    );

    private final Setting<Integer> acClickDelay = sgGeneral.add(new IntSetting.Builder()
        .name("ac-click-delay").description("ACCURATE_CLICK 点击延迟(tick)，等待指针到达高概率区").defaultValue(20).min(5).max(80).sliderRange(5, 60).build()
    );

    // ============ 游戏类型 ============

    private enum GameType {
        NONE, HOLD, HOLD_V2, CLICK, CLICK_V2, TENSION, DANCE, ACCURATE_CLICK, ACCURATE_CLICK_V2, ACCURATE_CLICK_V3
    }

    // ============ 钓鱼状态机 ============

    private enum FishingState {
        IDLE,       // 空闲，准备抛竿
        CASTING,    // 抛竿中
        WAITING,    // 等待鱼咬钩
        BITE,       // 咬钩，收杆中
        GAME,       // 小游戏中
        COOLDOWN    // 等待冷却
    }

    private FishingState fishingState = FishingState.IDLE;
    private GameType currentGame = GameType.NONE;
    private String currentTitle = "";
    private String currentSubtitle = "";

    // 状态计时
    private int stateTimer = 0;
    private int castTimer = 0;
    private int waitTimer = 0;
    private int cooldownTimer = 0;

    // 浮标跟踪
    private int bobberEntityId = -1;
    private boolean bobberGoingDown = false;

    // ---- v4 新增：浮标Y轴跟踪 ----
    private double bobberLastY = Double.NaN;
    private int bobberDropTicks = 0;      // 连续下降tick数
    private static final double BOBBER_DROP_THRESHOLD = 0.05;  // 每tick下降阈值
    private static final int BOBBER_DROP_MIN_TICKS = 3;        // 连续下降最少tick数

    // ---- v4 新增：虚空钓鱼检测 ----
    private boolean bobberInVoid = false;           // 浮标是否在虚空中
    private boolean voidBiteDetectedBySound = false; // 虚空模式下通过音效检测到咬钩

    // ---- v4 新增：粒子/音效检测计数 ----
    private int particleNearBobberCount = 0;  // 最近tick内浮标附近的粒子数
    private int particleCheckTicks = 0;

    // 游戏操作状态
    private int tickCounter = 0;
    private boolean sneaking = false;
    private int pointerToJudgeGap = 0;
    private int prevGap = 0;
    private int sameDirectionTicks = 0;
    private int fishOffsetFromStart = 0;
    private int phaseTick = 0;
    private int acv2PointerIdx = -1;
    private int acv2TargetStart = -1;
    private int acv2TargetEnd = -1;

    // ---- v5：ACCURATE_CLICK 颜色目标追踪 ----
    // 从 Subtitle 偏移字符计算指针的像素偏移量
    private int acOffsetSum = 0;           // bar与pointer间的偏移字符总和 ≈ pointerOffset + progress
    private int acMinOffset = Integer.MAX_VALUE;  // 观察到的最大偏移
    private int acMaxOffset = Integer.MIN_VALUE;  // 观察到的最小偏移
    private int acTargetSection = -1;      // 目标区段索引（从 Title 颜色解析）
    private boolean acCalibrated = false;  // 是否完成一个完整扫描周期
    private int acPrevOffsetSum = 0;       // 上一tick的偏移值（用于判断方向变化）
    private boolean acOffsetInitialized = false;

    public CustomFishingBot() {
        super(AddonTemplate.CATEGORY, "custom-fishing-bot", "对一个服务器插件的钓鱼定制的模块，没做成，只是个实验");
    }

    @Override
    public void onActivate() {
        resetAll();
        if (verboseLog.get()) info("§bCustomFishingBot v5 已激活 — 全自动钓鱼模式");
    }

    @Override
    public void onDeactivate() {
        resetAll();
        releaseKeys();
    }

    private void resetAll() {
        fishingState = FishingState.IDLE;
        currentGame = GameType.NONE;
        currentTitle = "";
        currentSubtitle = "";
        stateTimer = 0;  castTimer = 0;  waitTimer = 0;  cooldownTimer = 0;
        bobberEntityId = -1;  bobberGoingDown = false;
        bobberLastY = Double.NaN;  bobberDropTicks = 0;
        bobberInVoid = false;  voidBiteDetectedBySound = false;
        particleNearBobberCount = 0;  particleCheckTicks = 0;
        tickCounter = 0;  sneaking = false;
        pointerToJudgeGap = 0;  prevGap = 0;  sameDirectionTicks = 0;
        fishOffsetFromStart = 0;  phaseTick = 0;
        acv2PointerIdx = -1;  acv2TargetStart = -1;  acv2TargetEnd = -1;
        acOffsetSum = 0;  acMinOffset = Integer.MAX_VALUE;
        acMaxOffset = Integer.MIN_VALUE;  acTargetSection = -1;
        acCalibrated = false;  acPrevOffsetSum = 0;  acOffsetInitialized = false;
    }

    // ================================================================
    //  数据包拦截 — 咬钩三重检测 + Title/Subtitle 捕获
    // ================================================================

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null) return;
        Packet<?> packet = event.packet;
        String cn = packet.getClass().getSimpleName();

        // ① Title 包 → 游戏检测
        // 注意：在 1.21+，TitleS2CPacket 同时包含 title 和 subtitle
        // 必须二者都提取，不能 return 提前退出！
        boolean hasTitle = false, hasSub = false;
        Component titleText = tryExtractTitle(packet, cn, true);
        if (titleText != null) {
            currentTitle = titleText.getString();
            hasTitle = true;
        }
        Component subText = tryExtractTitle(packet, cn, false);
        if (subText != null) {
            currentSubtitle = subText.getString();
            hasSub = true;
        }
        if (hasTitle || hasSub) {
            detectGame();
        }

        // ---- v4：粒子包检测咬钩（水模式）+ 虚空模式粒子检测 ----
        if (fishingState == FishingState.WAITING && cn.contains("Particle") && cn.contains("S2C")) {
            tryDetectBiteByParticle(packet);
        }

        // ---- v5：虚空模式音效检测咬钩（PlaySoundS2CPacket） ----
        // 虚空钓鱼咬钩时服务端播放 ITEM_TRIDENT_THUNDER 音效
        if (fishingState == FishingState.WAITING && bobberInVoid && cn.contains("PlaySound") && cn.contains("S2C")) {
            tryDetectBiteBySound(packet);
        }

        // ② 浮标速度更新 → 咬钩检测（保留作为备用）
        if (fishingState == FishingState.WAITING && bobberEntityId > 0
            && cn.contains("Velocity") && cn.contains("S2C")) {
            try {
                Method getId = packet.getClass().getMethod("getEntityId");
                int eid = (int) getId.invoke(packet);
                if (eid == bobberEntityId) {
                    Method getY = packet.getClass().getMethod("getVelocityY");
                    double vy = (double) getY.invoke(packet);
                    if (vy < -0.05) {
                        bobberGoingDown = true;
                        if (verboseLog.get()) info("§e! 鱼咬钩! (vy=" + String.format("%.3f", vy) + ")");
                    }
                }
            } catch (Exception ignored) {}
        }

        // ③ 实体状态包 → 咬钩（保留作为备用）
        if (fishingState == FishingState.WAITING && cn.contains("EntityStatus") && cn.contains("S2C")) {
            try {
                Method getId = packet.getClass().getMethod("getEntityId");
                Method getStatus = packet.getClass().getMethod("getStatus");
                int eid = (int) getId.invoke(packet);
                byte status = (byte) getStatus.invoke(packet);
                if (eid == bobberEntityId && (status == 0x10 || status == 0x17)) {
                    bobberGoingDown = true;
                    if (verboseLog.get()) info("§e! 鱼咬钩!");
                }
            } catch (Exception ignored) {}
        }
    }

    // ---- v4 新增：粒子包咬钩检测 ----
    // 水模式：任何粒子接近浮标都可能是咬钩信号
    // 虚空模式：需要更多粒子（END_ROD/DRAGON_BREATH 爆散）才判定
    private void tryDetectBiteByParticle(Packet<?> packet) {
        try {
            // 获取粒子位置
            Method getX = packet.getClass().getMethod("getX");
            Method getY = packet.getClass().getMethod("getY");
            Method getZ = packet.getClass().getMethod("getZ");
            double px = (double) getX.invoke(packet);
            double py = (double) getY.invoke(packet);
            double pz = (double) getZ.invoke(packet);

            // 检查浮标位置
            FishingHook bobber = mc.player.fishing;
            if (bobber == null) return;
            double bx = bobber.getX(), by = bobber.getY(), bz = bobber.getZ();

            double dist = Math.sqrt(
                Math.pow(px - bx, 2) +
                Math.pow(py - by, 2) +
                Math.pow(pz - bz, 2)
            );

            if (dist < 2.0) {
                particleNearBobberCount++;
                // 水模式：2个粒子足够触发
                // 虚空模式：需要更多粒子（虚空有常驻环绕粒子，要滤掉）
                int threshold = bobberInVoid ? 8 : 2;
                if (particleNearBobberCount >= threshold) {
                    bobberGoingDown = true;
                    if (verboseLog.get())
                        info("§e! 鱼咬钩! (粒子检测, 模式=" + (bobberInVoid ? "虚空" : "水") +
                            ", 距浮标=" + String.format("%.2f", dist) +
                            ", 计数=" + particleNearBobberCount + ")");
                }
            }
        } catch (Exception ignored) {}
    }

    // ---- v5 新增：虚空模式音效检测咬钩 ----
    // VoidFishingMechanic 在咬钩时执行：
    //   hook.getWorld().playSound(loc, Sound.ITEM_TRIDENT_THUNDER, 0.25F, 1.0F)
    // 捕获 PlaySoundS2CPacket → 检查音源是否在浮标附近
    private void tryDetectBiteBySound(Packet<?> packet) {
        try {
            // 取音源坐标（int，实际坐标 = value / 8.0）
            Method getX = packet.getClass().getMethod("getX");
            Method getY = packet.getClass().getMethod("getY");
            Method getZ = packet.getClass().getMethod("getZ");
            Object xRaw = getX.invoke(packet);
            Object yRaw = getY.invoke(packet);
            Object zRaw = getZ.invoke(packet);
            double sx = ((Number) xRaw).doubleValue() / 8.0;
            double sy = ((Number) yRaw).doubleValue() / 8.0;
            double sz = ((Number) zRaw).doubleValue() / 8.0;

            FishingHook bobber = mc.player.fishing;
            if (bobber == null) return;
            double bx = bobber.getX(), by = bobber.getY(), bz = bobber.getZ();
            double dist = Math.sqrt(
                Math.pow(sx - bx, 2) +
                Math.pow(sy - by, 2) +
                Math.pow(sz - bz, 2)
            );

            // 音源在浮标3格内 → 虚空钓鱼咬钩音效
            if (dist < 3.0) {
                voidBiteDetectedBySound = true;
                bobberGoingDown = true;
                if (verboseLog.get())
                    info("§e! 鱼咬钩! (虚空音效检测, 距浮标=" + String.format("%.2f", dist) + ")");
            }
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("SameParameterValue")
    private Component tryExtractTitle(Packet<?> packet, String cn, boolean isTitle) {
        try {
            String target = isTitle ? "SetTitle" : "SetSubtitle";
            if (cn.contains(target)) {
                Method m = packet.getClass().getMethod("getText");
                return (Component) m.invoke(packet);
            }
            if ("TitleS2CPacket".equals(cn)) {
                String mn = isTitle ? "getTitle" : "getSubtitle";
                try { Method m = packet.getClass().getMethod(mn); return (Component) m.invoke(packet); }
                catch (NoSuchMethodException ignored) {}
            }
            if (cn.contains("Title") && !cn.contains("Animation") && !cn.contains("Clear") && !cn.contains("Time")) {
                if ((isTitle && !cn.contains("Subtitle")) || (!isTitle && cn.contains("Subtitle"))) {
                    Method m = packet.getClass().getMethod("getText");
                    return (Component) m.invoke(packet);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ================================================================
    //  游戏类型检测（v4 简化版）
    // ================================================================

    private void detectGame() {
        if (currentTitle.isEmpty() && currentSubtitle.isEmpty()) return;
        GameType g = detectFromChars(currentTitle, currentSubtitle);
        if (g != GameType.NONE && g != currentGame) {
            currentGame = g;
            fishingState = FishingState.GAME;
            tickCounter = 0;  phaseTick = 0;
            pointerToJudgeGap = 0;  prevGap = 0;  sameDirectionTicks = 0;
            // 重置 ACCURATE_CLICK 校准状态
            acOffsetSum = 0;  acMinOffset = Integer.MAX_VALUE;
            acMaxOffset = Integer.MIN_VALUE;  acTargetSection = -1;
            acCalibrated = false;  acOffsetInitialized = false;
            // 尝试从 Title 解析目标颜色
            acTargetSection = parseColorTarget(currentTitle);
            releaseKeys();
            if (verboseLog.get()) {
                String extra = (acTargetSection >= 0) ? " 目标区段=" + acTargetSection : "";
                info("§b→ 小游戏: " + gameName(g) + extra);
            }
        }
    }

    private GameType detectFromChars(String title, String subtitle) {
        if (title.isEmpty() && subtitle.isEmpty()) return GameType.NONE;

        // ---- v4：通用检测 — 先检查是否有任何自定义字体字符 ----
        boolean hasCustomChars = false;
        boolean hasBar = false, hasJudge = false, hasPointer = false, hasFish = false;
        for (int i = 0; i < subtitle.length(); i++) {
            char c = subtitle.charAt(i);
            if (ALL_CUSTOM_CHARS.contains(c)) hasCustomChars = true;
            if (BAR_CHARS.contains(c)) hasBar = true;
            if (JUDGE_CHARS.contains(c)) hasJudge = true;
            if (FISH_CHARS.contains(c)) hasFish = true;
            if (c == BAR_POINTER) hasPointer = true;
        }

        // 没有自定义字符 → 不是小游戏
        if (!hasCustomChars) return GameType.NONE;

        // ---- 游戏类型推断 ----
        // TENSION: 有鱼图标 + 有Bar + 没有Judge
        if (hasFish && hasBar && !hasJudge) return GameType.TENSION;

        // ACCURATE_CLICK_V2: Title 中有 "█▲▼◆●■▶▷★✦►" 等指针字符
        if (title != null) {
            String[] ptrs = {"█", "▲", "▼", "◆", "●", "■", "▶", "▷", "★", "✦", "►", "⬛"};
            for (String s : ptrs) { if (title.contains(s)) return GameType.ACCURATE_CLICK_V2; }
        }

        // HOLD: 有 Judge 判定区域 + Pointer 指针 + Bar
        if (hasJudge && hasPointer && hasBar) return GameType.HOLD;

        // ACCURATE_CLICK_V3: 有 Judge + Pointer + Bar（但没有fish）
        // 但与HOLD不同的是，Title中没有 sneak/progress/key 等关键词
        if (hasBar && hasPointer && !hasFish) {
            String t = title.toLowerCase();
            if (t.contains("sneak") || t.contains("key.") || t.contains("潜行") || t.contains("按住")) return GameType.HOLD;
            // 检查是否只有"!"结尾的颜色名标题（RED! ORANGE! 等）
            return GameType.ACCURATE_CLICK_V3;
        }

        // ACCURATE_CLICK: 只有 Bar + Pointer
        if (hasBar && hasPointer) return GameType.ACCURATE_CLICK;

        // 文字类检测（支持中英文）
        String s = subtitle.toLowerCase();
        if (s.contains("dance") || s.contains("跳舞") || s.contains("舞")) return GameType.DANCE;
        if ((s.contains("click") || s.contains("点击") || s.contains("单击")) && (s.contains("times") || s.contains("次"))) return GameType.CLICK;
        if (s.contains("|") && (s.contains("time left") || s.contains("剩余") || s.contains("时间"))) return GameType.CLICK_V2;

        // 有自定义字符但无法精确匹配 → 可能是ACCURATE_CLICK变体
        if (hasBar || hasPointer) return GameType.ACCURATE_CLICK;

        return GameType.NONE;
    }

    /** 从 Title 文字解析目标颜色对应的区段索引（支持中英文） */
    private int parseColorTarget(String title) {
        if (title == null || title.isEmpty()) return -1;
        // 彩虹7色顺序 — 支持中英文
        if (title.contains("红") || title.toUpperCase().contains("RED"))     return 0;
        if (title.contains("橙") || title.toUpperCase().contains("ORANGE"))  return 1;
        if (title.contains("黄") || title.toUpperCase().contains("YELLOW"))  return 2;
        if (title.contains("绿") || title.toUpperCase().contains("GREEN"))   return 3;
        if (title.contains("青") || title.toUpperCase().contains("AQUA") || title.contains("CYAN")) return 4;
        if (title.contains("蓝") || title.toUpperCase().contains("BLUE"))    return 5;
        if (title.contains("紫") || title.toUpperCase().contains("PURPLE"))  return 6;
        return -1;
    }

    /** 从 Subtitle 计算指针偏移总和（bar与pointer间的偏移字符值之和） */
    private int calcAcOffsetSum(String sub) {
        if (sub == null || sub.isEmpty()) return 0;
        int bi = -1, pi = -1;
        // 找任意 bar 字符作为起点
        for (int i = 0; i < sub.length(); i++) {
            char c = sub.charAt(i);
            if (BAR_CHARS.contains(c) || JUDGE_CHARS.contains(c)) {
                if (bi < 0) bi = i;
            }
            if (c == BAR_POINTER) {
                pi = i;
                break;
            }
        }
        if (bi < 0 || pi < 0 || pi <= bi) return 0;
        int total = 0;
        for (int i = bi + 1; i < pi; i++) {
            Integer v = OFFSET_MAP.get(sub.charAt(i));
            if (v != null) total += v;
        }
        return total;
    }

    // ================================================================
    //  Tick 主驱动
    // ================================================================

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        // ---- v4：浮标Y轴跟踪（每tick检测） ----
        trackBobberY();

        // ---- v4：重置粒子计数（每20tick清理） ----
        particleCheckTicks++;
        if (particleCheckTicks >= 20) {
            particleNearBobberCount = Math.max(0, particleNearBobberCount - 1);
            particleCheckTicks = 0;
        }

        // 如果小游戏进行中 → 处理游戏
        if (currentGame != GameType.NONE) {
            tickCounter++;
            handleGameTick();
            return;
        }

        // 否则 → 全自动钓鱼状态机
        tickAutoFishing();
    }

    // ---- v4：浮标Y轴跟踪 + 虚空检测 ----
    private void trackBobberY() {
        if (mc.player == null || mc.player.fishing == null || mc.level == null) {
            bobberLastY = Double.NaN;
            bobberDropTicks = 0;
            bobberInVoid = false;
            return;
        }
        if (fishingState != FishingState.WAITING) return;

        double currentY = mc.player.fishing.getY();
        int bottomY = mc.level.getMinY();

        // 检测是否在虚空模式（浮标 Y <= 世界最低高度）
        boolean wasInVoid = bobberInVoid;
        bobberInVoid = currentY <= bottomY + 1;
        if (bobberInVoid && !wasInVoid && verboseLog.get()) {
            info("§5虚空钓鱼模式");
        }

        // 水模式：Y轴下降检测
        if (!bobberInVoid && !Double.isNaN(bobberLastY)) {
            double drop = bobberLastY - currentY;
            if (drop > BOBBER_DROP_THRESHOLD) {
                bobberDropTicks++;
                if (bobberDropTicks >= BOBBER_DROP_MIN_TICKS && !bobberGoingDown) {
                    bobberGoingDown = true;
                    if (verboseLog.get())
                        info("§e! 鱼咬钩! (浮标Y下降 " + String.format("%.2f", drop) + ", 连续=" + bobberDropTicks + "tick)");
                }
            } else {
                bobberDropTicks = 0;
            }
        }
        bobberLastY = currentY;
    }

    // ================================================================
    //  全自动钓鱼状态机
    // ================================================================

    private void tickAutoFishing() {
        switch (fishingState) {
            case IDLE -> {
                if (holdingFishingRod()) {
                    if (verboseLog.get()) info("§7抛竿...");
                    rightClick();
                    fishingState = FishingState.CASTING;
                    castTimer = 0;
                    bobberEntityId = -1;
                    bobberGoingDown = false;
                    bobberInVoid = false;
                    voidBiteDetectedBySound = false;
                    bobberLastY = Double.NaN;
                    bobberDropTicks = 0;
                    particleNearBobberCount = 0;
                }
            }

            case CASTING -> {
                castTimer++;
                FishingHook bobber = mc.player.fishing;
                if (bobber != null) {
                    bobberEntityId = bobber.getId();
                    fishingState = FishingState.WAITING;
                    waitTimer = 0;
                    bobberLastY = bobber.getY();
                    // 检测是否虚空钓鱼
                    if (mc.level != null && bobber.getY() <= mc.level.getMinY() + 1) {
                        bobberInVoid = true;
                        if (verboseLog.get()) info("§5虚空钓鱼模式 等待咬钩...");
                    } else {
                        bobberInVoid = false;
                        if (verboseLog.get()) info("§7等待咬钩...");
                    }
                } else if (castTimer > 40) {
                    fishingState = FishingState.IDLE;
                }
            }

            case WAITING -> {
                waitTimer++;
                if (mc.player.fishing == null) {
                    if (waitTimer > 20) fishingState = FishingState.COOLDOWN;
                    break;
                }
                bobberEntityId = mc.player.fishing.getId();

                // v4：三重咬钩检测聚合
                if (bobberGoingDown) {
                    if (verboseLog.get()) info("§6收杆!");
                    rightClick();
                    fishingState = FishingState.BITE;
                    stateTimer = 0;
                }

                // 超时保护
                if (waitTimer >= maxWaitTicks.get()) {
                    if (verboseLog.get()) info("§7等待超时，收杆重抛");
                    rightClick();
                    fishingState = FishingState.BITE;
                    stateTimer = 0;
                }
            }

            case BITE -> {
                stateTimer++;
                if (mc.player.fishing == null && currentGame == GameType.NONE) {
                    if (stateTimer > 5) {
                        fishingState = FishingState.COOLDOWN;
                        cooldownTimer = 0;
                    }
                }
                if (stateTimer > 60) {
                    fishingState = FishingState.IDLE;
                }
            }

            case GAME -> {
                stateTimer++;
                if (stateTimer > 600) {
                    currentGame = GameType.NONE;
                    fishingState = FishingState.COOLDOWN;
                    cooldownTimer = 0;
                }
            }

            case COOLDOWN -> {
                cooldownTimer++;
                if (cooldownTimer >= cooldownTicks.get()) {
                    fishingState = FishingState.IDLE;
                }
            }
        }
    }

    private boolean holdingFishingRod() {
        if (mc.player == null) return false;
        return mc.player.getMainHandItem().is(Items.FISHING_ROD)
            || mc.player.getOffhandItem().is(Items.FISHING_ROD);
    }

    // ================================================================
    //  小游戏自动操作（v4 优化版）
    // ================================================================

    private void handleGameTick() {
        if (tickCounter > 600) {
            if (verboseLog.get()) warning("游戏超时");
            currentGame = GameType.NONE;
            fishingState = FishingState.COOLDOWN;
            cooldownTimer = 0;
            return;
        }

        switch (currentGame) {
            case CLICK, CLICK_V2 -> {
                if (tickCounter % Math.max(1, actionInterval.get() + 1) == 0) rightClick();
                if (tickCounter > 200) gameEnded();
            }

            case DANCE -> {
                switch (tickCounter % 4) {
                    case 0 -> rightClick();
                    case 1 -> leftClick();
                    case 2 -> { if (mc.player != null) mc.player.jumpFromGround(); }
                    case 3 -> sneakTap();
                }
                if (tickCounter > 200) gameEnded();
            }

            case HOLD -> {
                parseHoldSubtitle(currentSubtitle);
                phaseTick++;
                if (pointerToJudgeGap > 5) {
                    startSneak();
                } else if (pointerToJudgeGap < -10) {
                    stopSneak();
                } else {
                    if (sameDirectionTicks > 5 && pointerToJudgeGap > 0) startSneak();
                    else if (sameDirectionTicks > 5 && pointerToJudgeGap < -5) stopSneak();
                    else startSneak();
                }
                if (tickCounter > 600) { releaseKeys(); gameEnded(); }
            }

            case HOLD_V2 -> {
                parseHoldSubtitle(currentSubtitle);
                phaseTick++;
                if (pointerToJudgeGap > 5) rightClick();
                if (tickCounter > 600) gameEnded();
            }

            case TENSION -> {
                phaseTick++;
                if (phaseTick % 14 < 6) startSneak();
                else stopSneak();
                if (tickCounter > 600) { releaseKeys(); gameEnded(); }
            }

            // ---- v5：优化 ACCURATE_CLICK 颜色目标追踪 ----
            case ACCURATE_CLICK -> {
                phaseTick++;

                // 计算当前偏移总和（不覆盖-1时的默认值，用于校准）
                int curOffset = calcAcOffsetSum(currentSubtitle);
                if (curOffset != 0) {
                    if (!acOffsetInitialized) {
                        acPrevOffsetSum = curOffset;
                        acMinOffset = curOffset;
                        acMaxOffset = curOffset;
                        acOffsetInitialized = true;
                        if (verboseLog.get()) info("§8AC 校准起始: offset=" + curOffset);
                    } else {
                        // 更新 min/max
                        if (curOffset < acMinOffset) acMinOffset = curOffset;
                        if (curOffset > acMaxOffset) acMaxOffset = curOffset;
                        acPrevOffsetSum = curOffset;
                    }
                }

                // 检测方向变化 → 完成一个完整扫描周期
                if (acOffsetInitialized && !acCalibrated) {
                    int totalRange = acMaxOffset - acMinOffset;
                    if (totalRange > 20) {
                        acCalibrated = true;
                        if (verboseLog.get())
                            info("§8AC 校准完成: range=" + totalRange + " 目标区段=" + acTargetSection);
                    }
                }

                // 如果解析到了目标颜色 → 精确瞄准
                if (acCalibrated && acTargetSection >= 0) {
                    int totalRange = acMaxOffset - acMinOffset;
                    if (totalRange > 0) {
                        // 当前偏移在总范围中的比例 → 估算区段
                        int relPos = curOffset - acMinOffset;
                        int estSection = (relPos * 7) / totalRange; // 假设7个区段
                        // 如果指针进入目标区段的中间范围，收杆
                        if (estSection == acTargetSection && phaseTick > 10) {
                            if (verboseLog.get())
                                info("§a✓ 命中目标颜色! section=" + estSection + " tick=" + phaseTick);
                            rightClick();
                            gameEnded();
                            break;
                        }
                    }
                }

                // 超时保底：一定 tick 后强制收杆
                if (phaseTick >= acClickDelay.get() + 30) {
                    if (verboseLog.get()) info("§eAC 超时收杆 (tick=" + phaseTick + ")");
                    rightClick();
                    gameEnded();
                }
            }

            case ACCURATE_CLICK_V2 -> {
                parseAcv2Bar(currentTitle);
                if (acv2PointerIdx >= 0 && acv2TargetStart >= 0 && acv2TargetEnd >= 0
                    && acv2PointerIdx >= acv2TargetStart && acv2PointerIdx <= acv2TargetEnd) {
                    rightClick();
                    if (verboseLog.get()) info("§a✓ ACv2 命中!");
                    gameEnded();
                } else if (tickCounter > 100) {
                    rightClick();
                    gameEnded();
                }
            }

            // ---- v4：优化 ACCURATE_CLICK_V3 策略 ----
            case ACCURATE_CLICK_V3 -> {
                parseHoldSubtitle(currentSubtitle);
                phaseTick++;
                // 等待指针进入判定区后点击
                if (pointerToJudgeGap < 0 && pointerToJudgeGap > -30) {
                    rightClick();
                    if (verboseLog.get()) info("§a✓ ACv3 命中! (gap=" + pointerToJudgeGap + ")");
                    gameEnded();
                } else if (phaseTick >= acClickDelay.get() + 10) {
                    // 超时保底点击
                    rightClick();
                    if (verboseLog.get()) info("§aACv3 超时点击 (phaseTick=" + phaseTick + ")");
                    gameEnded();
                }
            }
        }
    }

    /** 小游戏结束 → 进入冷却 */
    private void gameEnded() {
        currentGame = GameType.NONE;
        fishingState = FishingState.COOLDOWN;
        cooldownTimer = 0;
        tickCounter = 0;
        releaseKeys();
    }

    // ================================================================
    //  Subtitle 解析器
    // ================================================================

    private void parseHoldSubtitle(String sub) {
        if (sub == null || sub.isEmpty()) return;
        int ji = -1, pi = -1;
        for (int i = 0; i < sub.length(); i++) {
            char c = sub.charAt(i);
            if (JUDGE_CHARS.contains(c)) ji = i;
            if (c == BAR_POINTER) pi = i;
        }
        if (ji < 0 || pi < 0 || pi <= ji) return;
        int total = 0;
        for (int i = ji + 1; i < pi; i++) {
            Integer v = OFFSET_MAP.get(sub.charAt(i));
            if (v != null) total += v;
        }
        prevGap = pointerToJudgeGap;
        pointerToJudgeGap = total;
        sameDirectionTicks = (Math.signum(pointerToJudgeGap) == Math.signum(prevGap)) ? sameDirectionTicks + 1 : 0;
    }

    private void parseTensionSubtitle(String sub) {
        if (sub == null || sub.isEmpty()) return;
        int bi = -1, fi = -1;
        for (int i = 0; i < sub.length(); i++) {
            char c = sub.charAt(i);
            if (BAR_CHARS.contains(c)) bi = i;
            if (FISH_CHARS.contains(c)) fi = i;
        }
        if (bi < 0 || fi < 0 || fi <= bi) return;
        int total = 0;
        for (int i = bi + 1; i < fi; i++) {
            Integer v = OFFSET_MAP.get(sub.charAt(i));
            if (v != null) total += v;
        }
        fishOffsetFromStart = total;
    }

    private void parseAcv2Bar(String bar) {
        if (bar == null || bar.isEmpty()) return;
        String[] ptrs = {"█", "▲", "▼", "◆", "●", "■", "▶", "▷", "★", "✦", "►", "⬛"};
        String[] tgts = {"→", "◆", "■", "●", "★", "☆", "▬", "▮", "▓", "▰", "▱", "▭"};
        acv2PointerIdx = -1; acv2TargetStart = -1; acv2TargetEnd = -1;
        for (String s : ptrs) { int idx = bar.indexOf(s); if (idx >= 0) { acv2PointerIdx = idx; break; } }
        for (String s : tgts) {
            int start = bar.indexOf(s);
            if (start >= 0 && start != acv2PointerIdx) {
                acv2TargetStart = start; acv2TargetEnd = start;
                for (int i = start + 1; i < bar.length(); i++) {
                    if (String.valueOf(bar.charAt(i)).equals(s)) acv2TargetEnd = i;
                    else break;
                }
                break;
            }
        }
    }

    // ================================================================
    //  操作辅助
    // ================================================================

    private void rightClick() {
        if (mc.player != null && mc.gameMode != null)
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
    }

    private void leftClick() {
        if (mc.gameMode != null && mc.player != null)
            mc.gameMode.attack(mc.player, mc.player);
    }

    private void startSneak() {
        if (!sneaking && mc.options != null) { mc.options.keyShift.setDown(true); sneaking = true; }
    }

    private void stopSneak() {
        if (sneaking && mc.options != null) { mc.options.keyShift.setDown(false); sneaking = false; }
    }

    private void sneakTap() { if (mc.options != null) mc.options.keyShift.setDown(true); }

    private void releaseKeys() { stopSneak(); }

    private String gameName(GameType g) {
        return switch (g) {
            case HOLD -> "HOLD(按住潜行)";
            case HOLD_V2 -> "HOLD_V2(按住右键)";
            case CLICK -> "CLICK(连击)";
            case CLICK_V2 -> "CLICK_V2(衰减连击)";
            case TENSION -> "TENSION(张力)";
            case DANCE -> "DANCE(劲舞团)";
            case ACCURATE_CLICK -> "ACCURATE_CLICK(概率判定)";
            case ACCURATE_CLICK_V2 -> "ACCURATE_CLICK_V2(字符条精准)";
            case ACCURATE_CLICK_V3 -> "ACCURATE_CLICK_V3(图片精准)";
            default -> "NONE";
        };
    }
}
