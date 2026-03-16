package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Text;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CommandFlooder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // 指令内容
    private final Setting<String> command = sgGeneral.add(new StringSetting.Builder()
        .name("指令内容")
        .description("要发送的指令 (无需加 /)。")
        .defaultValue("help")
        .build()
    );

    // 数量
    private final Setting<Integer> amount = sgGeneral.add(new IntSetting.Builder()
        .name("瞬间数量")
        .description("一次激活发送多少个包。")
        .defaultValue(100)
        .min(1)
        .sliderMax(1000)
        .build()
    );

    // 模式：推荐使用多线程以防止客户端卡死
    private final Setting<Boolean> useThread = sgGeneral.add(new BoolSetting.Builder()
        .name("使用独立线程")
        .description("开启后，发包操作将在后台运行，不会导致游戏画面卡顿 (推荐)。")
        .defaultValue(true)
        .build()
    );

    // 自动关闭
    private final Setting<Boolean> autoDisable = sgGeneral.add(new BoolSetting.Builder()
        .name("自动关闭")
        .description("发送完毕后自动关闭模块。")
        .defaultValue(true)
        .build()
    );

    public CommandFlooder() {
        super(AddonTemplate.CATEGORY, "command-flooder", "利用多线程在一瞬间发送海量指令。");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.getNetworkHandler() == null) {
            toggle();
            return;
        }

        String rawCmd = command.get();
        // 去除开头的斜杠，保证格式正确
        final String cmd = rawCmd.startsWith("/") ? rawCmd.substring(1) : rawCmd;
        final int count = amount.get();
        final ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();

        if (useThread.get()) {
            // --- 多线程模式 (极速 & 不卡顿) ---
            // 创建一个临时的线程来执行轰炸
            new Thread(() -> {
                try {
                    for (int i = 0; i < count; i++) {
                        // 在 1.21.4 中，sendCommand 会进行签名计算，这很耗时
                        // 放在线程里跑可以避免主线程掉帧
                        networkHandler.sendCommand(cmd);
                    }
                    // 发送完毕的提示（可选）
                    // ChatUtils.info("已发送 " + count + " 条指令。");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        } else {
            // --- 主线程模式 (传统模式) ---
            // 如果数量巨大，这里会卡死画面几秒钟
            for (int i = 0; i < count; i++) {
                networkHandler.sendCommand(cmd);
            }
        }

        if (autoDisable.get()) {
            toggle();
        }
    }
}