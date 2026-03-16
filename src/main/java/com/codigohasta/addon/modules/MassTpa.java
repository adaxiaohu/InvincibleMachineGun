package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MassTpa extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgStop = settings.createGroup("停止条件");

    // --- 常规设置 ---
    private final Setting<Double> delay = sgGeneral.add(new DoubleSetting.Builder()
        .name("发送间隔")
        .description("向不同玩家发送TPA请求的间隔(秒)。")
        .defaultValue(5)
        .min(1)
        .sliderMax(20)
        .build()
    );

    private final Setting<String> command = sgGeneral.add(new StringSetting.Builder()
        .name("指令")
        .description("发送的指令，不需要输入玩家名。")
        .defaultValue("/tpa")
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("忽略好友")
        .description("不向Meteor好友列表里的玩家发送请求。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> randomOrder = sgGeneral.add(new BoolSetting.Builder()
        .name("随机顺序")
        .description("随机打乱玩家列表顺序，而不是按字母顺序。")
        .defaultValue(true)
        .build()
    );

    // --- 停止条件设置 ---
    private final Setting<Boolean> stopOnTeleport = sgStop.add(new BoolSetting.Builder()
        .name("位移停止")
        .description("检测到坐标发生大幅度变化(传送成功)时停止模块。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> stopOnChat = sgStop.add(new BoolSetting.Builder()
        .name("消息停止")
        .description("检测到聊天栏出现特定关键词时停止模块。")
        .defaultValue(true)
        .build()
    );

    // --- 自定义关键词设置 ---
    private final Setting<List<String>> successKeywords = sgStop.add(new StringListSetting.Builder()
        .name("成功关键词")
        .description("如果聊天消息包含列表中的任意一个词，视为对方接受了TPA。")
        .defaultValue("接受了") 
        .visible(stopOnChat::get)
        .build()
    );

    private final Setting<Boolean> debugChat = sgStop.add(new BoolSetting.Builder()
        .name("调试消息")
        .description("在本地显示服务器发送的所有原始消息，方便抓取关键词。")
        .defaultValue(false)
        .build()
    );

    // 运行变量
    private final List<String> targetPlayers = new ArrayList<>();
    private int playerIndex = 0;
    private int timer = 0;
    private Vec3d lastPos = null;

    public MassTpa() {
        super(AddonTemplate.CATEGORY, "MassTpa", "自动向全服玩家发送 /tpa 请求，有人接受即停止。");
    }

    @Override
    public void onActivate() {
        loadPlayers();
        timer = 0;
        lastPos = mc.player != null ? mc.player.getPos() : null;
    }

    @Override
    public void onDeactivate() {
        targetPlayers.clear();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        toggle();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        // 1. 检测位移
        if (stopOnTeleport.get() && lastPos != null) {
            double distance = lastPos.distanceTo(mc.player.getPos());
            if (distance > 8) {
                info("检测到位置突变 (位移 %.1f)，判定传送成功，停止模块。", distance);
                toggle();
                return;
            }
        }
        lastPos = mc.player.getPos();

        // 2. 计时器
        if (timer > 0) {
            timer--;
            return;
        }

        // 3. 发送请求
        if (targetPlayers.isEmpty()) {
            info("玩家列表已遍历完毕，重新加载...");
            loadPlayers();
            if (targetPlayers.isEmpty()) {
                warning("当前服务器没有其他可发送的玩家，停止模块。");
                toggle();
                return;
            }
        }

        if (playerIndex >= targetPlayers.size()) {
            playerIndex = 0;
        }

        String target = targetPlayers.get(playerIndex);
        
        if (!target.equals(mc.player.getName().getString())) {
            ChatUtils.sendPlayerMsg(command.get() + " " + target);
        }
        
        playerIndex++;
        timer = (int) (delay.get() * 20);
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (!stopOnChat.get()) return;

        if (event.packet instanceof GameMessageS2CPacket packet) {
            String message = packet.content().getString();
            
            if (debugChat.get()) {
                info("[Debug] 收到消息: " + message);
            }

            for (String keyword : successKeywords.get()) {
                if (message.toLowerCase().contains(keyword.toLowerCase())) {
                    info("检测到关键词 [" + keyword + "]，判定对方已接受，停止模块。");
                    toggle();
                    break;
                }
            }
        }
    }

    private void loadPlayers() {
        targetPlayers.clear();
        if (mc.getNetworkHandler() == null) return;

        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            String name = entry.getProfile().getName();
            if (name.equals(mc.player.getName().getString())) continue;
            
            // 修复点：直接传入 entry，而不是 entry.getProfile()
            if (ignoreFriends.get() && Friends.get().isFriend(entry)) continue;
            
            targetPlayers.add(name);
        }

        if (randomOrder.get()) {
            Collections.shuffle(targetPlayers);
        }
        
        info("已加载 " + targetPlayers.size() + " 名玩家进入 TPA 队列。");
        playerIndex = 0;
    }
}