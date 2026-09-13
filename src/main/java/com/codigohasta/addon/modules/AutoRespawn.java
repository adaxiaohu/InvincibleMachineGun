package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;

import java.util.Arrays;
import java.util.List;

public class AutoRespawn extends Module {

    // 定义模式枚举
    public enum Mode {
        None,           // 不发送任何消息
        Send_Messages   // 发送消息或指令
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // 保留原有的本地提示设置
    private final Setting<Boolean> showMessage = sgGeneral.add(new BoolSetting.Builder()
        .name("show-message")
        .description("在复活时发送本地反馈。这个消息只有你能看到。")
        .defaultValue(true)
        .build()
    );

    // 新增：模式选择
    private final Setting<Mode> actionMode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("action-mode")
        .description("复活后执行的操作模式。")
        .defaultValue(Mode.Send_Messages)
        .build()
    );

    // 新增：延迟设置
    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("发送消息/指令前的延迟（Tick，20 Tick = 1秒）。")
        .defaultValue(20) // 默认1秒
        .min(0)
        .sliderRange(0, 100)
        .visible(() -> actionMode.get() == Mode.Send_Messages) // 仅在选择了发送模式时显示
        .build()
    );

    // 新增：多条消息/指令填写框
    private final Setting<List<String>> messages = sgGeneral.add(new StringListSetting.Builder()
        .name("messages")
        .description("要发送的消息或指令（如果是指令请以 / 开头）。支持多条。")
        .defaultValue(Arrays.asList("I will be back.", "/back"))
        .visible(() -> actionMode.get() == Mode.Send_Messages) // 仅在选择了发送模式时显示
        .build()
    );

    // 用于状态控制的变量
    private boolean wasDead = false;
    private boolean waitingToSend = false;
    private int delayTimer = 0;

    public AutoRespawn() {
        super(AddonTemplate.CATEGORY, "c自动复活", "当你死亡时自动复活并可选择执行自定义指令/消息。");
    }

    @Override
    public void onActivate() {
        // 模块开启时重置状态
        wasDead = false;
        waitingToSend = false;
        delayTimer = 0;
    }

    @Override
    public void onDeactivate() {
        // 模块关闭时取消等待发送
        waitingToSend = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        // 1. 检查死亡状态（边缘触发，仅在刚死的那一刻执行一次）
        if (mc.player.isDead()) {
            if (!wasDead) {
                // 向服务器发送重生请求
                mc.player.networkHandler.sendPacket(new ClientStatusC2SPacket(ClientStatusC2SPacket.Mode.PERFORM_RESPAWN));

                if (showMessage.get()) {
                    info("已自动复活。");
                }

                // 如果设置了发送消息，则开始倒计时
                if (actionMode.get() == Mode.Send_Messages && !messages.get().isEmpty()) {
                    waitingToSend = true;
                    delayTimer = delay.get();
                }
            }
            wasDead = true;
        } else {
            // 如果玩家活着，重置死亡标志
            wasDead = false;
        }

        // 2. 处理延迟与发送逻辑
        if (waitingToSend) {
            if (delayTimer > 0) {
                delayTimer--; // 倒计时
            } else {
                // 倒计时结束，遍历并发送所有配置好的消息/指令
                for (String msg : messages.get()) {
                    if (msg != null && !msg.trim().isEmpty()) {
                        // 使用 Meteor 官方的 ChatUtils，它会自动兼容最新版本MC的消息和指令（/开头）的包差异
                        ChatUtils.sendPlayerMsg(msg);
                    }
                }
                // 发送完毕，关闭等待状态
                waitingToSend = false;
            }
        }
    }
}