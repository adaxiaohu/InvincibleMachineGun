package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ServerInfo;

import java.util.List;

public class AutoServer extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<String>> commands = sgGeneral.add(new StringListSetting.Builder()
        .name("指令列表")
        .description("按顺序执行的指令列表 (无需加斜杠 /)。")
        .defaultValue(List.of("login 123456", "server survival"))
        .build()
    );

    private final Setting<Integer> initialDelay = sgGeneral.add(new IntSetting.Builder()
        .name("初始延迟 (Tick)")
        .description("进入服务器后，执行第一条指令前等待的时间。")
        .defaultValue(40)
        .min(0)
        .sliderMax(200)
        .build()
    );

    private final Setting<Integer> commandInterval = sgGeneral.add(new IntSetting.Builder()
        .name("指令间隔 (Tick)")
        .description("每两条指令之间等待的时间 (20 Tick = 1秒)。")
        .defaultValue(20)
        .min(0)
        .sliderMax(100)
        .build()
    );

    private final Setting<List<String>> servers = sgGeneral.add(new StringListSetting.Builder()
        .name("服务器白名单")
        .description("仅在这些IP的服务器启用。")
        .defaultValue(List.of("mcyanglao.com"))
        .build()
    );

    private final Setting<Boolean> showFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("显示反馈")
        .description("显示指令发送提示和 IP 调试信息。")
        .defaultValue(true)
        .build()
    );

    // 状态机
    private int timer = 0;
    private int currentCmdIndex = 0;
    private boolean isSending = false;
    
    // 状态锁：记录当前连接的服务器指纹
    private String processedSessionID = "NONE";

    public AutoServer() {
        super(AddonTemplate.CATEGORY, "auto-server", "进服自动指令 ");
    }

    @Override
    public void onActivate() {
        // 手动开启时重置状态，方便测试
        processedSessionID = "NONE";
        isSending = false;
    }

    @Override
    public void onDeactivate() {
        isSending = false;
        // 关闭模块时不重置指纹，防止误触开关导致重发
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // --- 1. 指纹检测 ---
        String currentSessionID = getSessionID();

        // 情况 A: 彻底断开连接 (回到主菜单)
        if (currentSessionID.equals("NONE")) {
            if (!processedSessionID.equals("NONE")) {
                processedSessionID = "NONE";
                isSending = false;
                if (showFeedback.get()) info("检测到断开连接，记忆已重置。");
            }
            return;
        }

        // 情况 B: 新的服务器连接
        if (!currentSessionID.equals(processedSessionID)) {
            
            // 调试信息：把这一行显示的地址复制到白名单里即可
            if (showFeedback.get()) {
                info("检测到新服务器连接，识别地址: " + currentSessionID);
            }

            // 更新锁
            processedSessionID = currentSessionID;
            
            // 排除单人游戏
            if (!currentSessionID.equals("SINGLEPLAYER")) {
                if (checkWhitelist(currentSessionID)) {
                    startSequence();
                } else {
                    if (showFeedback.get()) info("该服务器不在白名单内，已忽略。");
                }
            } else {
                isSending = false;
            }
        }

        // --- 2. 发送逻辑 ---

        if (!isSending) return;
        // 确保玩家实体已加载
        if (mc.player == null || mc.world == null) return;

        if (timer > 0) {
            timer--;
            return;
        }

        List<String> cmdList = commands.get();
        
        // 检查是否全部发完
        if (currentCmdIndex >= cmdList.size()) {
            if (showFeedback.get() && currentCmdIndex > 0) {
                info("指令序列执行完毕。");
            }
            isSending = false;
            return;
        }

        // 发送指令
        String cmd = cmdList.get(currentCmdIndex);
        if (cmd != null && !cmd.trim().isEmpty()) {
            sendCommand(cmd);
            if (showFeedback.get()) {
                info("自动发送: " + cmd);
            }
        }

        currentCmdIndex++;
        
        // 设置间隔
        if (currentCmdIndex < cmdList.size()) {
            timer = commandInterval.get();
        }
    }

    private void startSequence() {
        isSending = true;
        currentCmdIndex = 0;
        timer = initialDelay.get();
        if (showFeedback.get()) {
            info("白名单匹配成功，将在 " + timer + " tick 后发送指令...");
        }
    }

    /**
     * 获取当前连接的唯一标识 (IP地址)
     */
    private String getSessionID() {
        if (mc.isInSingleplayer()) return "SINGLEPLAYER";
        
        ServerInfo info = mc.getCurrentServerEntry();
        if (info == null) return "NONE"; // 代表未连接或在主菜单
        
        // 返回小写的 IP 地址
        return info.address.toLowerCase();
    }

    private boolean checkWhitelist(String ip) {
        List<String> allowed = servers.get();
        if (allowed.isEmpty()) return true;

        for (String s : allowed) {
            // 模糊匹配：只要当前IP包含白名单里的字符串就算通过
            if (ip.contains(s.toLowerCase())) return true;
        }
        return false;
    }

    private void sendCommand(String cmd) {
        String cleanCmd = cmd.trim();
        if (cleanCmd.startsWith("/")) {
            cleanCmd = cleanCmd.substring(1);
        }
        if (mc.player.networkHandler != null) {
            mc.player.networkHandler.sendChatCommand(cleanCmd);
        }
    }
}