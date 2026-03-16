package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class AutoTPAccept extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDebug = settings.createGroup("调试与高级");

    public enum Action {
        Accept("接受"),
        Deny("拒绝");

        private final String title;
        Action(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }

    private final Setting<Action> action = sgGeneral.add(new EnumSetting.Builder<Action>()
        .name("模式")
        .description("自动接受还是自动拒绝。")
        .defaultValue(Action.Accept)
        .build()
    );

    private final Setting<Boolean> onlyFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("仅限好友")
        .description("只处理 Meteor 好友列表里的玩家请求。")
        .defaultValue(true) 
        .build()
    );

    // 建议把关键词改短一点，比如 "请求传送"
    private final Setting<List<String>> keywords = sgGeneral.add(new StringListSetting.Builder()
        .name("检测关键词")
        .description("当聊天栏出现包含这些词的消息时，触发扫描。")
        .defaultValue("请求传送", "tpa", "teleport") 
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("延迟(Tick)")
        .description("收到请求后延迟多少 tick 再处理。")
        .defaultValue(20)
        .min(0)
        .sliderMax(60)
        .build()
    );

    // --- 调试设置 ---
    private final Setting<Boolean> debug = sgDebug.add(new BoolSetting.Builder()
        .name("开启调试")
        .description("在聊天栏显示详细日志，用来修bug。")
        .defaultValue(false)
        .build()
    );

    public AutoTPAccept() {
        super(AddonTemplate.CATEGORY, "AutoTPAccept", "自动点击聊天栏中的 [接受] 或 [拒绝] 按钮。");
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        // 1.21.4 系统提示消息 (如 TPA) 都是 GameMessageS2CPacket
        if (event.packet instanceof GameMessageS2CPacket packet) {
            Text textComponent = packet.content();
            String rawMessage = textComponent.getString(); // 获取去除颜色的纯文本

            // 调试步骤 1：看看模块有没有收到这条消息
            if (debug.get() && rawMessage.length() > 5) {
                // 只打印长一点的消息，避免刷屏
                info("[Debug-收到] " + rawMessage);
            }

            // 1. 关键词初筛
            boolean hasKeyword = false;
            for (String k : keywords.get()) {
                if (rawMessage.contains(k)) {
                    hasKeyword = true;
                    break;
                }
            }

            if (!hasKeyword) return;

            if (debug.get()) info("[Debug] 关键词匹配成功！开始寻找指令...");

            // 2. 挖掘所有指令
            List<String> foundCommands = new ArrayList<>();
            collectCommands(textComponent, foundCommands);

            if (foundCommands.isEmpty()) {
                if (debug.get()) warning("[Debug] 消息里没找到任何可点击的指令！可能是JSON结构太深。");
                return;
            }

            if (debug.get()) info("[Debug] 找到的所有指令: " + foundCommands);

            // 3. 筛选正确的指令 (接受/拒绝)
            String targetCommand = null;
            for (String cmd : foundCommands) {
                // 转换为小写方便比较
                String lower = cmd.toLowerCase();
                
                boolean isAccept = lower.contains("accept") || lower.contains("yes") || lower.contains("confirm");
                boolean isDeny = lower.contains("deny") || lower.contains("no") || lower.contains("cancel") || lower.contains("reject");

                if (action.get() == Action.Accept && isAccept) {
                    targetCommand = cmd;
                    break;
                }
                if (action.get() == Action.Deny && isDeny) {
                    targetCommand = cmd;
                    break;
                }
            }

            if (targetCommand != null) {
                // 4. 好友检查
                if (onlyFriends.get()) {
                    boolean isFriend = false;
                    for (var friend : Friends.get()) {
                        // 检查原始消息 OR 指令中是否包含好友名
                        if (rawMessage.contains(friend.getName()) || targetCommand.contains(friend.getName())) {
                            isFriend = true;
                            break;
                        }
                    }
                    if (!isFriend) {
                        if (debug.get()) warning("[Debug] 拦截：发送者不是好友。");
                        return;
                    }
                }

                // 5. 执行
                String finalCmd = targetCommand;
                if (debug.get()) info("[Debug] 准备执行: " + finalCmd);

                if (delay.get() > 0) {
                    new Thread(() -> {
                        try {
                            Thread.sleep(delay.get() * 50L);
                            ChatUtils.sendPlayerMsg(finalCmd);
                        } catch (InterruptedException ignored) {}
                    }).start();
                } else {
                    ChatUtils.sendPlayerMsg(finalCmd);
                }
                
                info((action.get() == Action.Accept ? "已接受" : "已拒绝") + " TPA请求。");
            } else {
                if (debug.get()) warning("[Debug] 找到了点击事件，但没有符合模式(" + action.get() + ")的指令。");
            }
        }
    }

    /**
     * 暴力递归：把所有子组件、子组件的子组件全部翻一遍
     */
    private void collectCommands(Text text, List<String> results) {
        // 1. 检查自己
        Style style = text.getStyle();
        if (style != null && style.getClickEvent() != null) {
            ClickEvent click = style.getClickEvent();
            if (click.getAction() == ClickEvent.Action.RUN_COMMAND || click.getAction() == ClickEvent.Action.SUGGEST_COMMAND) {
                results.add(click.getValue());
            }
        }

        // 2. 检查所有子节点 (Siblings)
        for (Text sibling : text.getSiblings()) {
            collectCommands(sibling, results);
        }
    }
}