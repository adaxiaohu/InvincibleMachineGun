package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.alien.AlienTimer;
import com.codigohasta.addon.utils.notify.ArmorAlert;
import com.codigohasta.addon.utils.notify.ChatMode;
import com.codigohasta.addon.utils.notify.ImgNotifications;
import com.codigohasta.addon.utils.notify.ImgNotificationType;
import com.codigohasta.addon.utils.notify.NotifySounds;
import com.codigohasta.addon.utils.sakura.render.SakuraTextRenderer;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;


public class Notifier extends Module {

    private final SettingGroup sgTotem = settings.createGroup("图腾 / 护甲");
    private final SettingGroup sgPlayer = settings.createGroup("玩家");
    private final SettingGroup sgWorld = settings.createGroup("世界 / 网络");
    private final SettingGroup sgMisc = settings.createGroup("其它");

    // ── 图腾 / 护甲 ──
    
    private final Setting<Boolean> totemPops = sgTotem.add(new BoolSetting.Builder()
        .name("图腾弹出")
        .description("记录并提示玩家弹出不死图腾（totem-pops）。")
        .defaultValue(true).build());
    
    private final Setting<Boolean> lowArmor = sgTotem.add(new BoolSetting.Builder()
        .name("低耐久护甲")
        .description("护甲即将损坏时提示（low-armor）。")
        .defaultValue(true).build());

    
    private final Setting<Boolean> lowArmorSound = sgTotem.add(new BoolSetting.Builder()
        .name("低甲音效")
        .description("护甲即将损坏时播放警报音（armor-alert）。")
        .defaultValue(true).visible(lowArmor::get).build());

    
    private final Setting<Boolean> totemSoundEnabled = sgTotem.add(new BoolSetting.Builder()
        .name("图腾音效")
        .description("弹图腾时播放音效：自己用 totem-pop，别人用 he_poptotem。")
        .defaultValue(true).visible(totemPops::get).build());

    
    private final Setting<Boolean> totemDeathMsg = sgTotem.add(new BoolSetting.Builder()
        .name("死亡提示")
        .description("玩家死亡时提示他弹了几个图腾后死亡。")
        .defaultValue(true).build());

    
    private final Setting<Boolean> deathSound = sgTotem.add(new BoolSetting.Builder()
        .name("死亡音效")
        .description("玩家死亡提示时播放音效。")
        .defaultValue(true).visible(totemDeathMsg::get).build());

    
    private final Setting<Boolean> chineseText = sgTotem.add(new BoolSetting.Builder()
        .name("中文文案")
        .description("开 = 中文；关 = 英文）。")
        .defaultValue(true).build());
    
    private final Setting<Integer> lowArmorThreshold = sgTotem.add(new IntSetting.Builder()
        .name("低甲阈值")
        .description("耐久低于该百分比时提示。")
        .defaultValue(10).min(1).max(50).sliderRange(1, 50)
        .visible(lowArmor::get).build());
    
    private final Setting<Double> alertIconSize = sgTotem.add(new DoubleSetting.Builder()
        .name("横幅图标尺寸")
        .description("屏幕顶端低甲警报的盾牌图标大小。")
        .defaultValue(26.90).min(8.0).max(48.0).sliderRange(8.0, 48.0)
        .visible(lowArmor::get).build());
    
    private final Setting<Double> alertBlinkPeriodMs = sgTotem.add(new DoubleSetting.Builder()
        .name("闪烁周期")
        .description("横幅闪烁一个来回的毫秒数；越大越慢。")
        .defaultValue(2313.24).min(300.0).max(4000.0).sliderRange(300.0, 4000.0)
        .visible(lowArmor::get).build());
    
    private final Setting<SettingColor> alertColorA =
        sgTotem.add(new ColorSetting.Builder()
            .name("横幅颜色 A")
            .description("闪烁的起始颜色。")
            .defaultValue(new SettingColor(255, 255, 255, 255))
            .visible(lowArmor::get).build());
    
    private final Setting<SettingColor> alertColorB =
        sgTotem.add(new ColorSetting.Builder()
            .name("横幅颜色 B")
            .description("闪烁的结束颜色。")
            .defaultValue(new SettingColor(255, 12, 171, 255))
            .visible(lowArmor::get).build());
    
    private final Setting<ChatMode> pastEffects = sgTotem.add(new EnumSetting.Builder<ChatMode>()
        .name("药水到期")
        .description("药水效果消失时提示。")
        .defaultValue(ChatMode.NOTIFICATION).build());

    // ── 玩家 ──
    
    private final Setting<Boolean> rangeEnter = sgPlayer.add(new BoolSetting.Builder()
        .name("进入视野")
        .description("玩家进入可视范围时提示。")
        .defaultValue(false).build());
    
    private final Setting<Boolean> rangeLeave = sgPlayer.add(new BoolSetting.Builder()
        .name("离开视野")
        .description("玩家离开可视范围时提示。")
        .defaultValue(false).build());

    
    private final Setting<Boolean> rangeSound = sgPlayer.add(new BoolSetting.Builder()
        .name("视野音效")
        .description("玩家进入/离开视野时播放音效（range-in / range-out）。")
        .defaultValue(true).visible(() -> rangeEnter.get() || rangeLeave.get()).build());
    
    private final Setting<Integer> rangeRadius = sgPlayer.add(new IntSetting.Builder()
        .name("视野半径")
        .description("判定「进入/离开视野」的半径（格）。")
        .defaultValue(64).min(8).max(256).sliderRange(8, 256)
        .visible(() -> rangeEnter.get() || rangeLeave.get()).build());
    
    private final Setting<Boolean> mention = sgPlayer.add(new BoolSetting.Builder()
        .name("被提及")
        .description("聊天里有人提到你的名字时提示。")
        .defaultValue(false).build());

    
    private final Setting<Boolean> mentionSound = sgPlayer.add(new BoolSetting.Builder()
        .name("被提及音效")
        .description("被提及时播放音效（chat-mention）。")
        .defaultValue(true).visible(mention::get).build());
    
    private final Setting<Boolean> self = sgPlayer.add(new BoolSetting.Builder()
        .name("包含自己")
        .description("上面这些玩家类提示是否也对自己生效。")
        .defaultValue(true).build());
    
    private final Setting<ChatMode> useTracker = sgPlayer.add(new EnumSetting.Builder<ChatMode>()
        .name("使用追踪")
        .description("追踪玩家使用物品（ use-tracker）。")
        .defaultValue(ChatMode.NOTIFICATION).build());

    // ── 世界 / 网络 ──
    
    private final Setting<Boolean> lag = sgWorld.add(new BoolSetting.Builder()
        .name("延迟检测")
        .description("服务器长时间没有响应时提示。")
        .defaultValue(true).build());

    
    private final Setting<Boolean> lagSound = sgWorld.add(new BoolSetting.Builder()
        .name("延迟音效")
        .description("服务器无响应时播放音效（lag）。")
        .defaultValue(true).visible(lag::get).build());
    
    private final Setting<Integer> lagThresholdMs = sgWorld.add(new IntSetting.Builder()
        .name("延迟阈值")
        .description("超过该毫秒数没有收到任何包就视为服务器卡了。")
        .defaultValue(2000).min(500).max(10000).sliderRange(500, 10000)
        .visible(lag::get).build());
    
    private final Setting<Boolean> rubberBand = sgWorld.add(new BoolSetting.Builder()
        .name("回弹检测")
        .description("服务端把你传送回原位（LagBack）时提示。")
        .defaultValue(true).build());

    
    private final Setting<Boolean> rubberBandSound = sgWorld.add(new BoolSetting.Builder()
        .name("回弹音效")
        .description("检测到回弹时播放音效（lag-back）。")
        .defaultValue(true).visible(rubberBand::get).build());
    
    private final Setting<ChatMode> itemCollect = sgWorld.add(new EnumSetting.Builder<ChatMode>()
        .name("物品收集")
        .description("拾取物品时提示（ item-collect）。")
        .defaultValue(ChatMode.NOTIFICATION).build());
    
    private final Setting<ChatMode> splashBuff = sgWorld.add(new EnumSetting.Builder<ChatMode>()
        .name("投掷药水")
        .description("受到投掷药水影响时提示（ splash-buff）。")
        .defaultValue(ChatMode.NOTIFICATION).build());
    
    private final Setting<Boolean> stash = sgWorld.add(new BoolSetting.Builder()
        .name("物资点检测")
        .description("附近箱子/潜影盒数量达到阈值时提示（ stash）。")
        .defaultValue(false).build());

    
    private final Setting<Boolean> stashSound = sgWorld.add(new BoolSetting.Builder()
        .name("物资点音效")
        .description("发现物资点时播放音效（found-stash）。")
        .defaultValue(false).visible(stash::get).build());
    
    private final Setting<Integer> minChests = sgWorld.add(new IntSetting.Builder()
        .name("最少箱子")
        .description("触发物资点提示所需的最少箱子数。")
        .defaultValue(1).min(1).max(64).sliderRange(1, 64)
        .visible(stash::get).build());
    
    private final Setting<Integer> minShulkers = sgWorld.add(new IntSetting.Builder()
        .name("最少潜影盒")
        .description("触发物资点提示所需的最少潜影盒数。")
        .defaultValue(0).min(0).max(64).sliderRange(0, 64)
        .visible(stash::get).build());
    
    private final Setting<Boolean> ftCompat = sgWorld.add(new BoolSetting.Builder()
        .name("图腾绕过提示")
        .description(" 暂时没效果，不知道怎么写")
        .defaultValue(false).build());

    // ── 其它 ──
    
    private final Setting<Boolean> old = sgMisc.add(new BoolSetting.Builder()
        .name("1.21 之前")
        .description("用旧版（&lt;1.21）的物品名写法（ pre-1.21）。")
        .defaultValue(false).build());
    
    private final Setting<Boolean> onlyDon = sgMisc.add(new BoolSetting.Builder()
        .name("仅白名单玩家")
        .description("只对白名单里的玩家发提示。")
        .defaultValue(false).build());
    
    private final Setting<String> donWhitelist = sgMisc.add(new StringSetting.Builder()
        .name("白名单")
        .description("逗号分隔的玩家名。only-don 开启时只对这些玩家发提示。")
        .defaultValue("").visible(onlyDon::get).build());

    // ═══════════ 运行时状态 ═══════════

    
    private final AlienTimer activityTimer = new AlienTimer();
    
    private double lastX, lastY, lastZ;
    private boolean hadPos = false;
    
    private final Set<String> inRange = new HashSet<>();
    
    private final Set<String> lastEffects = new HashSet<>();

    
    private final java.util.Map<String, String> lastEffectNames = new java.util.HashMap<>();
    
    private boolean lagNotified = false;
    
    private String stashMilestone = null;

    
    private final java.util.Map<String, Integer> totemCounts = new java.util.HashMap<>();

    
    private final java.util.Set<String> deadPlayers = new java.util.HashSet<>();

    
    private net.minecraft.world.World lastWorld;

    
    private static final class PendingUse {
        final ItemStack stack;
        final long startedAt;
        PendingUse(ItemStack s, long t) { this.stack = s; this.startedAt = t; }
    }

    
    private final java.util.Map<String, PendingUse> pendingUse = new java.util.concurrent.ConcurrentHashMap<>();

    
    private final Setting<Integer> eatConfirmMs = sgPlayer.add(new IntSetting.Builder()
        .name("进食确认时长")
        .description("别人要持续使用多久才算「吃掉了」。MC 每一口约 1600ms。")
        .defaultValue(1500).min(200).max(4000).sliderRange(200, 4000).build());

    public Notifier() {
        super(AddonTemplate.IMG_HUD_CATEGORY, "notifier",
            "通知器，在你屏幕上画个面板，遇到特定事件就会弹窗告诉你。");
    }

    @Override
    public void onActivate() {
        activityTimer.reset();
        hadPos = false;
        inRange.clear();
        lastEffects.clear();
        lagNotified = false;
        ArmorAlert.INSTANCE.reset();
        // 死亡去重集合要清空：换世界/重进后旧名字不该残留
        deadPlayers.clear();
        totemCounts.clear();
    }

    @Override
    public void onDeactivate() {
        ArmorAlert.INSTANCE.reset();
    }

    // ═══════════ 低甲横幅绘制（独立于通知队列）═══════════

    
    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!ArmorAlert.INSTANCE.isActive()) {
            ArmorAlert.INSTANCE.clearText();
            return;
        }
        if (ArmorAlert.INSTANCE.line1.isEmpty() && ArmorAlert.INSTANCE.line2.isEmpty()) return;

        float scale = 1.0f;
        float fontPx = 9f;
        String l1 = ArmorAlert.INSTANCE.line1;
        String l2 = ArmorAlert.INSTANCE.line2;

        float w1 = ArmorAlert.textWidth(l1, fontPx);
        float w2 = ArmorAlert.textWidth(l2, fontPx);
        float textW = Math.max(w1, w2);
        float screenW = event.screenWidth;
        float x = screenW / 2f - textW / 2f - 17f * scale;
        float phase = ArmorAlert.INSTANCE.blinkPhase(alertBlinkPeriodMs.get());
        var ca = alertColorA.get();
        var cb = alertColorB.get();
        int r = Math.round(ca.r + (cb.r - ca.r) * phase);
        int g = Math.round(ca.g + (cb.g - ca.g) * phase);
        int b = Math.round(ca.b + (cb.b - ca.b) * phase);
        int argb = (255 << 24) | (r << 16) | (g << 8) | b;
        float iconPx = alertIconSize.get().floatValue();

        SakuraTextRenderer.begin();
        try {
            ArmorAlert.drawText(l1, x, 8f * scale, fontPx, argb);
            ArmorAlert.drawText(l2, x, 20f * scale, fontPx, argb);
        } finally {
            SakuraTextRenderer.end();
        }

        int blinkAlpha = Math.round(255 * (0.35f + 0.65f * phase));
        com.codigohasta.addon.utils.sakura.render.SakuraSvgIcons.drawViaMesh(
            "armor", x + 5f * scale + textW, 4f * scale, iconPx * scale,
            new meteordevelopment.meteorclient.utils.render.color.Color(r, g, b, blinkAlpha));

    }

    
    private void checkPlayerDeaths() {
        if (!totemDeathMsg.get()) return;
        if (mc.player == null || mc.world == null) return;

        if (mc.world != lastWorld) {
            lastWorld = mc.world;
            totemCounts.clear();
            deadPlayers.clear();
        }

        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == null) continue;
            String pname = p.getName().getString();
            if (p.isDead() || p.getHealth() <= 0.0f) {
                if (deadPlayers.add(pname)) {
                    reportDeath(p, pname);
                }
            } else {
                deadPlayers.remove(pname);
            }
        }
    }

    
    private void reportDeath(PlayerEntity p, String pname) {
        Integer popped = totemCounts.remove(pname);
        if (popped == null || popped <= 0) return;

        var deathSnd = !(totemSoundEnabled.get() && deathSound.get()) ? null
            : (p == mc.player ? NotifySounds.TOTEM_POP : NotifySounds.HE_POPTOTEM);

        notifyWithIcon(
            chineseText.get()
                ? String.format(Locale.ROOT, "%s 在弹了 %d 个图腾后死了", pname, popped)
                : String.format(Locale.ROOT, "%s died after popping %d totem(s)!", pname, popped),
            "死亡", ImgNotificationType.ERROR, null,
            deathSnd,
            new ItemStack(net.minecraft.item.Items.TOTEM_OF_UNDYING));
    }

    // ═══════════ 包检测 ═══════════

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        activityTimer.reset();
        if (lagNotified) lagNotified = false;

        if (event.packet instanceof PlayerPositionLookS2CPacket) {
            if (rubberBand.get() && hadPos && mc.player != null) {
                double dist = mc.player.squaredDistanceTo(lastX, lastY, lastZ);
                if (dist > 9.0) {   
                    notify(chineseText.get() ? "检测到回弹！" : "LagBack detected!", "回弹检测",
                    ImgNotificationType.WARNING, null,
                    rubberBandSound.get() ? NotifySounds.LAG_BACK : null);
                }
            }
            if (mc.player != null) {
                lastX = mc.player.getX();
                lastY = mc.player.getY();
                lastZ = mc.player.getZ();
                hadPos = true;
            }
        }

    
        if (totemPops.get() && event.packet instanceof EntityStatusS2CPacket packet) {
            if (packet.getStatus() == EntityStatuses.USE_TOTEM_OF_UNDYING
                && packet.getEntity(mc.world) instanceof PlayerEntity player) {
                if (allowByWhitelist(player)) {
                    String pname = player.getName().getString();
                    int total = totemCounts.merge(pname, 1, Integer::sum);
                
                    boolean isSelf = player == mc.player;
                    var totemSound = !totemSoundEnabled.get() ? null
                        : (isSelf ? NotifySounds.TOTEM_POP : NotifySounds.HE_POPTOTEM);

                    String text = chineseText.get()
                        ? String.format(Locale.ROOT, "%s 弹了 %d 个图腾！", pname, total)
                        : String.format(Locale.ROOT, "%s was popped %d totem(s)!", pname, total);
                    notifyWithIcon(text,
                        "图腾弹出", ImgNotificationType.WARNING, null,
                        totemSound,
                        new ItemStack(net.minecraft.item.Items.TOTEM_OF_UNDYING));
                }
            }
        }

        
        if (useTracker.get().wantsNotification()
            && event.packet instanceof net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket tracker
            && mc.world != null && mc.player != null) {
            Entity ent = mc.world.getEntityById(tracker.id());
            if (ent instanceof PlayerEntity other && other != mc.player && allowPlayer(other)) {
                String who = other.getName().getString();
                for (var entry : tracker.trackedValues()) {
                    if (!"8".equals(String.valueOf(entry.id()))) continue;
                    String val = String.valueOf(entry.value());
                    if ("1".equals(val) || "3".equals(val)) {
                        var hand = "3".equals(val)
                            ? net.minecraft.util.Hand.OFF_HAND
                            : net.minecraft.util.Hand.MAIN_HAND;
                        ItemStack used = other.getStackInHand(hand);
                        if (used.isEmpty()) continue;

                        boolean consumable =
                            used.getComponents().contains(
                                net.minecraft.component.DataComponentTypes.FOOD)
                            || used.getComponents().contains(
                                net.minecraft.component.DataComponentTypes.POTION_CONTENTS);
                        if (!consumable) continue;
                        pendingUse.put(who, new PendingUse(used.copy(), System.currentTimeMillis()));
                    } else {
                        PendingUse pending = pendingUse.remove(who);
                        if (pending == null) continue;
                        long elapsed = System.currentTimeMillis() - pending.startedAt;
                        if (elapsed < eatConfirmMs.get()) continue;   

                        notifyWithIcon(
                            chineseText.get()
                                ? String.format(Locale.ROOT, "%s 吃掉了 %s",
                                    who, pending.stack.getName().getString())
                                : String.format(Locale.ROOT, "%s used %s",
                                    who, pending.stack.getName().getString()),
                            "使用物品", ImgNotificationType.INFO, null, null, pending.stack);
                    }
                }
            }
        }

        // 被提及
        if (mention.get() && event.packet instanceof GameMessageS2CPacket pkt && mc.player != null) {
            String msg = pkt.content().getString();
            String myName = mc.player.getName().getString();
            if (msg.toLowerCase(Locale.ROOT).contains(myName.toLowerCase(Locale.ROOT))) {
                notify("you were mentioned in the chat!", "被提及", ImgNotificationType.INFO, null,
                    mentionSound.get() ? NotifySounds.CHAT_MENTION : null);
            }
        }

        if (event.packet instanceof ItemPickupAnimationS2CPacket pick) {
            ChatMode mode = itemCollect.get();
            ItemEntity itemEntity = null;
            Entity collector;
            if (mc.world != null) {
                Entity raw = mc.world.getEntityById(pick.getEntityId());
                if (raw instanceof ItemEntity ie) itemEntity = ie;
                collector = mc.world.getEntityById(pick.getCollectorEntityId());
            } else {
                collector = null;
            }
            if (itemEntity != null && collector != null && mc.player != null) {
                ItemStack picked = itemEntity.getStack().copy();
                String itemName = picked.getName().getString();
                int count = pick.getStackAmount() > 0 ? pick.getStackAmount() : picked.getCount();

                if (mode.wantsNotification()) {
                    if (collector.getId() == mc.player.getId()) {
                        notifyWithIcon(
                            chineseText.get()
                                ? String.format(Locale.ROOT, "捡到了物品：%s x%d", itemName, count)
                                : String.format(Locale.ROOT, "collected item: %s x%d", itemName, count),
                            "物品收集", ImgNotificationType.SUCCESS, null, null, picked);
                    } else {
                        notifyWithIcon(
                            chineseText.get()
                                ? String.format(Locale.ROOT, "%s 捡到了物品：%s x%d",
                                    collector.getName().getString(), itemName, count)
                                : String.format(Locale.ROOT, "%s collected item: %s x%d",
                                    collector.getName().getString(), itemName, count),
                            "物品收集", ImgNotificationType.SUCCESS, null, null, picked);
                    }
                }
            }
        }
    }

    // ═══════════ Tick 检测 ═══════════

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        checkPlayerDeaths();
        if (lag.get() && !lagNotified && activityTimer.passedMs(lagThresholdMs.get())) {
            lagNotified = true;
            notify(chineseText.get() ? "服务器卡住了！" : "The server is fucked up!", "服务器无响应",
                ImgNotificationType.ERROR,
                chineseText.get() ? "距上次响应 " + activityTimer.getMs() + "ms"
                    : "time from last response: " + activityTimer.getMs() + "ms",
                lagSound.get() ? NotifySounds.LAG : null);
        }

        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        if (hadPos) {
            double moved = Math.sqrt(
                Math.pow(px - lastX, 2) + Math.pow(py - lastY, 2) + Math.pow(pz - lastZ, 2));
            // 单 tick 移动超过 10 格 = 被拉回/传送
            if (rubberBand.get() && moved > 10.0) {
                notify(chineseText.get()
                        ? String.format(Locale.ROOT, "单 tick 移动了 %.1f 格", moved)
                        : String.format(Locale.ROOT, "moved %.1f blocks in one tick", moved),
                    "回弹检测", ImgNotificationType.WARNING, null,
                rubberBandSound.get() ? NotifySounds.LAG_BACK : null);
            }
        }
        lastX = px; lastY = py; lastZ = pz;
        hadPos = true;
        if (lowArmor.get()) {
            int worst = 100;
            for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                int pct = ArmorAlert.durabilityPercent(mc.player.getEquippedStack(slot));
                if (pct < worst) worst = pct;
            }
            boolean danger = worst <= lowArmorThreshold.get();
            String l1 = danger
                ? (chineseText.get() ? "小心！" : "Be careful!") : "";
            String l2 = danger
                ? (chineseText.get() ? "你的护甲快损坏了" : "Your armor is almost broken") : "";
            boolean justEntered = ArmorAlert.INSTANCE.update(danger, l1, l2);
            if (justEntered && soundsAllowed() && lowArmorSound.get()) {
                NotifySounds.play(NotifySounds.ARMOR_ALERT, soundVolume());
            }
        } else {
            ArmorAlert.INSTANCE.update(false, null, null);
        }

        {
            Set<String> now = new HashSet<>();
            java.util.Map<String, String> idToName = new java.util.HashMap<>();
            for (var eff : mc.player.getStatusEffects()) {
                String id = eff.getEffectType().getKey()
                    .map(k -> k.getValue().getPath()).orElse(null);
                if (id == null) continue;
                now.add(id);
                idToName.put(id, eff.getEffectType().value().getName().getString());
            }

            if (pastEffects.get().wantsNotification()) {
                for (String prev : lastEffects) {
                    if (!now.contains(prev)) {
                
                        String nm = lastEffectNames.getOrDefault(prev, prev);
                        notifyWithSprite(chineseText.get()
                                ? "你的 " + nm + " 效果结束了"
                                : "You've run out of " + nm, "药水到期",
                            ImgNotificationType.INFO, "mob_effect/" + prev);
                    }
                }
            }

            if (splashBuff.get().wantsNotification()) {
                for (String cur : now) {
                    if (!lastEffects.contains(cur)) {
                        String nm = idToName.getOrDefault(cur, cur);
                        notifyWithSprite(chineseText.get()
                                ? "得到效果：" + nm
                                : "got effect: " + nm, "投掷药水",
                            ImgNotificationType.SUCCESS, "mob_effect/" + cur);
                    }
                }
            }

            lastEffects.clear();
            lastEffects.addAll(now);
            lastEffectNames.clear();
            lastEffectNames.putAll(idToName);
        }

        // 视野进出
        if (rangeEnter.get() || rangeLeave.get()) {
            double r2 = (double) rangeRadius.get() * rangeRadius.get();
            Set<String> now = new HashSet<>();
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p == mc.player) continue;
                if (!allowPlayer(p)) continue;
                if (mc.player.squaredDistanceTo(p) <= r2) now.add(p.getName().getString());
            }
            for (String n : now) {
                if (!inRange.contains(n) && rangeEnter.get()) {
                    notify(chineseText.get() ? n + " 进入了你的视野！" : n + " has entered your visual range!", "进入视野",
                        ImgNotificationType.INFO, null,
                rangeSound.get() ? NotifySounds.RANGE_IN : null);
                }
            }
            for (String n : inRange) {
                if (!now.contains(n) && rangeLeave.get()) {
                    notify(chineseText.get() ? n + " 离开了你的视野！" : n + " has left your visual range!", "离开视野",
                        ImgNotificationType.INFO, null,
                rangeSound.get() ? NotifySounds.RANGE_OUT : null);
                }
            }
            inRange.clear();
            inRange.addAll(now);
        }

        if (stash.get() && mc.world.getTime() % 20 == 0) {
            checkStash();
        }
    }

    
    private void checkStash() {
        int chests = 0;
        int shulkers = 0;
        int pcx = mc.player.getBlockX() >> 4;
        int pcz = mc.player.getBlockZ() >> 4;
        int cr = 2;   // ±2 区块 ≈ 32 格
        var cm = mc.world.getChunkManager();
        for (int cx = pcx - cr; cx <= pcx + cr; cx++) {
            for (int cz = pcz - cr; cz <= pcz + cr; cz++) {
                if (!cm.isChunkLoaded(cx, cz)) continue;
                var chunk = cm.getWorldChunk(cx, cz, false);
                if (chunk == null) continue;
                for (var be : chunk.getBlockEntities().values()) {
                    if (be instanceof net.minecraft.block.entity.ChestBlockEntity) chests++;
                    else if (be instanceof net.minecraft.block.entity.ShulkerBoxBlockEntity) shulkers++;
                }
            }
        }

        boolean reached = chests >= minChests.get() && shulkers >= minShulkers.get();
        if (!reached) {
            stashMilestone = null;  
            return;
        }

        String now = chests + "/" + shulkers;
        if (now.equals(stashMilestone)) return;
        stashMilestone = now;

        notifyWithIcon(chineseText.get()
                ? String.format(Locale.ROOT, "物资点：X: %d Z: %d 箱子: %d 潜影盒: %d",
                    mc.player.getBlockX(), mc.player.getBlockZ(), chests, shulkers)
                : String.format(Locale.ROOT, "Stash: X: %d Z: %d Chests: %d Shulkers: %d",
                    mc.player.getBlockX(), mc.player.getBlockZ(), chests, shulkers),
            "物资点", ImgNotificationType.SUCCESS, null,
            stashSound.get() ? NotifySounds.FOUND_STASH : null, null);
    }

    // ═══════════ 辅助 ═══════════

    
    private boolean allowPlayer(PlayerEntity p) {
        if (p == mc.player && !self.get()) return false;
        return allowByWhitelist(p);
    }

    
    private boolean allowByWhitelist(PlayerEntity p) {
        if (!onlyDon.get()) return true;
        String list = donWhitelist.get();
        if (list == null || list.isBlank()) return false;
        String name = p.getName().getString();
        for (String s : list.split(",")) {
            if (s.trim().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    
    private void notify(String title, String content, ImgNotificationType type) {
        notify(title, content, type, null, null);
    }

    private void notify(String title, String content, ImgNotificationType type, String extra) {
        notify(title, content, type, extra, null);
    }

    
    private void notify(String title, String content, ImgNotificationType type,
                        String extra, net.minecraft.util.Identifier soundId) {
        notifyWithIcon(title, content, type, extra, soundId, null);
    }

    
    private void notifyWithIcon(String title, String content, ImgNotificationType type,
                                String extra, net.minecraft.util.Identifier soundId,
                                ItemStack icon) {
        String body = extra == null || extra.isEmpty() ? content : content + "  " + extra;
        ImgNotifications mgr = ImgNotifications.INSTANCE;
        mgr.post(title, body, type, icon, 3);
        // 音效：受通知模块的「音效」开关控制
        if (soundId != null && soundsAllowed()) {
            NotifySounds.play(soundId, soundVolume());
        }
    }

    
    private void notifyWithSprite(String title, String content, ImgNotificationType type,
                                  String spritePath) {
        ImgNotifications mgr = ImgNotifications.INSTANCE;
        mgr.post(title, content, type, null, spritePath, 3);
    }

    
    private boolean soundsAllowed() {        NotificationsWidget cfg =
            meteordevelopment.meteorclient.systems.modules.Modules.get()
                .get(NotificationsWidget.class);
        return cfg == null || cfg.soundEnabled();
    }

    
    private float soundVolume() {
        NotificationsWidget cfg =
            meteordevelopment.meteorclient.systems.modules.Modules.get()
                .get(NotificationsWidget.class);
        return cfg == null ? 1.0f : cfg.soundVolume();
    }
}
