package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ServerInfo;

import java.util.List;

public class AutoServer extends Module {

    // ================= 通用设置 =================
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

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

    private final Setting<Boolean> showFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("显示反馈")
        .description("显示指令发送提示和调试信息。")
        .defaultValue(true)
        .build()
    );

    // ================= 服务器 1 =================
    private final SettingGroup sgServer1 = settings.createGroup("服务器 1");
    
    private final Setting<Boolean> s1Enabled = sgServer1.add(new BoolSetting.Builder()
        .name("启用")
        .description("是否启用此服务器配置")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> s1Ip = sgServer1.add(new StringSetting.Builder()
        .name("服务器 IP")
        .description("触发此配置的服务器IP (如: mcyanglao.com)")
        .defaultValue("mcyanglao.com")
        .build()
    );

    private final Setting<List<String>> s1Cmds = sgServer1.add(new StringListSetting.Builder()
        .name("执行指令")
        .description("进入此服务器后按顺序执行的指令。")
        .defaultValue(List.of("login 123456", "server survival"))
        .build()
    );

    // ================= 服务器 2 =================
    private final SettingGroup sgServer2 = settings.createGroup("服务器 2");
    
    private final Setting<Boolean> s2Enabled = sgServer2.add(new BoolSetting.Builder()
        .name("启用")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> s2Ip = sgServer2.add(new StringSetting.Builder()
        .name("服务器 IP")
        .defaultValue("hypixel.net")
        .build()
    );

    private final Setting<List<String>> s2Cmds = sgServer2.add(new StringListSetting.Builder()
        .name("执行指令")
        .defaultValue(List.of("play bedwars"))
        .build()
    );

    // ================= 服务器 3 =================
    private final SettingGroup sgServer3 = settings.createGroup("服务器 3");
    
    private final Setting<Boolean> s3Enabled = sgServer3.add(new BoolSetting.Builder()
        .name("启用")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> s3Ip = sgServer3.add(new StringSetting.Builder()
        .name("服务器 IP")
        .defaultValue("example.com")
        .build()
    );

    private final Setting<List<String>> s3Cmds = sgServer3.add(new StringListSetting.Builder()
        .name("执行指令")
        .defaultValue(List.of("login password", "is"))
        .build()
    );

    // ================= 状态机 =================
    private int timer = 0;
    private int currentCmdIndex = 0;
    private boolean isSending = false;
    private List<String> currentCmdList = null;

    // 状态锁：记录当前连接的服务器指纹
    private String processedSessionID = "NONE";

    public AutoServer() {
        super(AddonTemplate.CATEGORY, "auto-server", "进服自动输入指令，支持多服务器不同指令配置。");
    }

    @Override
    public void onActivate() {
        processedSessionID = "NONE";
        isSending = false;
        currentCmdList = null;
    }

    @Override
    public void onDeactivate() {
        isSending = false;
        currentCmdList = null;
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
                currentCmdList = null;
                if (showFeedback.get()) info("检测到断开连接，记忆已重置。");
            }
            return;
        }

        // 情况 B: 新的服务器连接
        if (!currentSessionID.equals(processedSessionID)) {
            if (showFeedback.get()) {
                info("检测到新服务器连接: " + currentSessionID);
            }

            processedSessionID = currentSessionID;

            if (!currentSessionID.equals("SINGLEPLAYER")) {
                // 查找匹配的服务器配置指令
                List<String> matchedCommands = findMatchingCommands(currentSessionID);
                
                if (matchedCommands != null && !matchedCommands.isEmpty()) {
                    startSequence(matchedCommands);
                    if (showFeedback.get()) {
                        info("匹配到配置，将在 " + initialDelay.get() + " tick 后发送 " + matchedCommands.size() + " 条指令");
                    }
                } else {
                    isSending = false;
                    currentCmdList = null;
                    if (showFeedback.get()) info("未找到匹配的启用配置或指令列表为空。");
                }
            } else {
                isSending = false;
                currentCmdList = null;
            }
        }

        // --- 2. 发送逻辑 ---
        if (!isSending || currentCmdList == null) return;
        if (mc.player == null || mc.world == null) return;

        if (timer > 0) {
            timer--;
            return;
        }

        if (currentCmdIndex >= currentCmdList.size()) {
            if (showFeedback.get() && currentCmdIndex > 0) {
                info("指令序列执行完毕。");
            }
            isSending = false;
            currentCmdList = null;
            return;
        }

        String cmd = currentCmdList.get(currentCmdIndex);
        if (cmd != null && !cmd.trim().isEmpty()) {
            sendCommand(cmd);
            if (showFeedback.get()) {
                info("自动发送: " + cmd);
            }
        }

        currentCmdIndex++;

        if (currentCmdIndex < currentCmdList.size()) {
            timer = commandInterval.get();
        }
    }

    private void startSequence(List<String> cmdList) {
        isSending = true;
        currentCmdList = cmdList;
        currentCmdIndex = 0;
        timer = initialDelay.get();
    }

    /**
     * 检测哪个服务器的IP匹配，并返回对应的指令列表
     */
    private List<String> findMatchingCommands(String currentIp) {
        // 检查服务器 1
        if (s1Enabled.get() && isValidIp(s1Ip.get(), currentIp)) return s1Cmds.get();
        // 检查服务器 2
        if (s2Enabled.get() && isValidIp(s2Ip.get(), currentIp)) return s2Cmds.get();
        // 检查服务器 3
        if (s3Enabled.get() && isValidIp(s3Ip.get(), currentIp)) return s3Cmds.get();
        
        // 如果你需要更多的服务器，可以继续在这里 if ...

        return null;
    }

    private boolean isValidIp(String settingIp, String currentIp) {
        String trimIp = settingIp.trim().toLowerCase();
        return !trimIp.isEmpty() && currentIp.contains(trimIp);
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

    private void sendCommand(String cmd) {
        String cleanCmd = cmd.trim();
        if (cleanCmd.startsWith("/")) {
            cleanCmd = cleanCmd.substring(1);
        }
        if (mc.player != null && mc.player.networkHandler != null) {
            mc.player.networkHandler.sendChatCommand(cleanCmd);
        }
    }
}