package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPlus;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.dialog.input.NumberRangeInputControl;
import net.minecraft.dialog.type.Dialog;
import net.minecraft.dialog.type.DialogInput;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.packet.c2s.common.CustomClickActionC2SPacket;
import net.minecraft.network.packet.s2c.common.ShowDialogS2CPacket;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AutoServer
 *
 * 目标：
 * 1. 动态服务器 Profile，可在 Meteor 模块界面无限添加/删除。
 * 2. 每个 Profile 独立开关 AuthMeUI human_slider 自动验证。
 * 3. 以底层连接对象作为“物理会话”标识；Velocity 子服切换不会重复执行指令。
 * 4. 每个 Profile 可选按当前 Minecraft 用户名匹配，同一服务器可为不同账号保存不同密码。
 * 5. 兼容旧版固定 3 服务器配置与 AS2 动态配置，并自动迁移。
 *
 * AuthMeUI r1.2 登录 Dialog 约定：
 * - 密码 input key: password
 * - 滑块 input key: human_slider
 * - 登录 action: authmeui:action/login
 *
 * Dialog 自动验证直接使用 1.21.11 Yarn 的强类型 Dialog/Packet/NBT API。
 * 不使用运行时类名或反射，避免 Fabric/Loom 重映射后类名变化导致自动验证失效。
 */
public class AutoServer extends Module {
    private static final String PROFILE_VERSION = "AS3";
    private static final String LEGACY_PROFILE_VERSION = "AS2";
    private static final String HUMAN_SLIDER_KEY = "human_slider";
    private static final String PASSWORD_KEY = "password";
    private static final String LOGIN_ACTION_NAMESPACE = "authmeui";
    private static final String LOGIN_ACTION_PATH = "action/login";

    private static final int DISCONNECT_GRACE_TICKS = 40;

    private static final Pattern TARGET_RANGE_PATTERN = Pattern.compile(
        "(-?\\d+(?:\\.\\d+)?)\\s*(?:~|～|—|–|-)\\s*(-?\\d+(?:\\.\\d+)?)"
    );

    private static final Pattern LOGIN_COMMAND_PATTERN = Pattern.compile(
        "(?i)^/?(?:login|l|log)\\s+(.+?)\\s*$"
    );

    // ================= 通用设置 =================
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> initialDelay = sgGeneral.add(new IntSetting.Builder()
        .name("初始延迟 (Tick)")
        .description("建立一次新的物理服务器连接后，执行第一条指令前等待的时间。Velocity 子服切换不会重新计时。")
        .defaultValue(40)
        .min(0)
        .sliderMax(200)
        .build()
    );

    private final Setting<Integer> commandInterval = sgGeneral.add(new IntSetting.Builder()
        .name("指令间隔 (Tick)")
        .description("每两条自动指令之间等待的时间，20 Tick 约等于 1 秒。")
        .defaultValue(20)
        .min(0)
        .sliderMax(100)
        .build()
    );

    private final Setting<Boolean> showFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("显示反馈")
        .description("显示服务器匹配、自动验证和指令状态。密码内容不会写入反馈。")
        .defaultValue(true)
        .build()
    );

    /**
     * 真正持久化动态 Profile 的 Setting。
     * 界面由 getWidget() 自定义，因此这里隐藏原始编码列表。
     */
    private final Setting<List<String>> profileStorage = sgGeneral.add(new StringListSetting.Builder()
        .name("动态服务器配置存储")
        .description("AutoServer 内部数据，请使用下方服务器管理界面编辑。")
        .defaultValue(List.of())
        .visible(() -> false)
        .build()
    );

    private final Setting<Boolean> profileStorageInitialized = sgGeneral.add(new BoolSetting.Builder()
        .name("动态服务器配置已初始化")
        .description("内部迁移标记。")
        .defaultValue(false)
        .visible(() -> false)
        .build()
    );

    // ================= 旧版 3 服务器配置（仅迁移，不显示） =================
    // 保留相同 group/name，使旧 Meteor 配置可以被读取，然后迁移到 profileStorage。
    private final SettingGroup sgLegacy1 = settings.createGroup("服务器 1");
    private final Setting<Boolean> legacy1Enabled = sgLegacy1.add(new BoolSetting.Builder()
        .name("启用").defaultValue(true).visible(() -> false).build());
    private final Setting<String> legacy1Ip = sgLegacy1.add(new StringSetting.Builder()
        .name("服务器 IP").defaultValue("mcyanglao.com").visible(() -> false).build());
    private final Setting<List<String>> legacy1Cmds = sgLegacy1.add(new StringListSetting.Builder()
        .name("执行指令").defaultValue(List.of("login 123456", "server survival")).visible(() -> false).build());

    private final SettingGroup sgLegacy2 = settings.createGroup("服务器 2");
    private final Setting<Boolean> legacy2Enabled = sgLegacy2.add(new BoolSetting.Builder()
        .name("启用").defaultValue(false).visible(() -> false).build());
    private final Setting<String> legacy2Ip = sgLegacy2.add(new StringSetting.Builder()
        .name("服务器 IP").defaultValue("hypixel.net").visible(() -> false).build());
    private final Setting<List<String>> legacy2Cmds = sgLegacy2.add(new StringListSetting.Builder()
        .name("执行指令").defaultValue(List.of("play bedwars")).visible(() -> false).build());

    private final SettingGroup sgLegacy3 = settings.createGroup("服务器 3");
    private final Setting<Boolean> legacy3Enabled = sgLegacy3.add(new BoolSetting.Builder()
        .name("启用").defaultValue(false).visible(() -> false).build());
    private final Setting<String> legacy3Ip = sgLegacy3.add(new StringSetting.Builder()
        .name("服务器 IP").defaultValue("example.com").visible(() -> false).build());
    private final Setting<List<String>> legacy3Cmds = sgLegacy3.add(new StringListSetting.Builder()
        .name("执行指令").defaultValue(List.of("login password", "is")).visible(() -> false).build());

    // ================= 物理连接 / 指令状态机 =================
    private Object activeConnection;
    private ServerProfile activeProfile;
    private String activeAddress = "";
    private int disconnectedTicks;
    private boolean commandsScheduledForSession;

    private int timer;
    private int currentCmdIndex;
    private boolean isSending;
    private List<String> currentCmdList;

    public AutoServer() {
        super(
            AddonTemplate.CATEGORY,
            "auto-server",
            "动态服务器自动指令 + AuthMeUI 滑块自动验证；Velocity 子服切换不会重复执行。"
        );
    }

    @Override
    public void onActivate() {
        resetSession(false);
        loadProfiles(); // 同时触发一次旧配置迁移
    }

    @Override
    public void onDeactivate() {
        resetSequence();
        activeConnection = null;
        activeProfile = null;
        activeAddress = "";
        disconnectedTicks = 0;
        commandsScheduledForSession = false;
    }

    // ========================================================================
    // 动态 Profile GUI
    // ========================================================================

    @Override
    public WWidget getWidget(GuiTheme theme) {
        List<ServerProfile> profiles = loadProfiles();
        WVerticalList root = theme.verticalList();
        rebuildProfileWidget(theme, root, profiles);
        return root;
    }

    private void rebuildProfileWidget(GuiTheme theme, WVerticalList root, List<ServerProfile> profiles) {
        root.clear();

        root.add(theme.horizontalSeparator("动态服务器配置")).expandX();
        root.add(theme.label("每个服务器可独立按用户名匹配并开启自动验证码。自动指令可逐条无限添加，按界面顺序执行。"));
        String currentUsername = getCurrentUsername();
        root.add(theme.label("当前客户端用户名: " + (currentUsername.isBlank() ? "<无法读取>" : currentUsername)));

        for (int i = 0; i < profiles.size(); i++) {
            final int index = i;
            final ServerProfile profile = profiles.get(i);

            root.add(theme.horizontalSeparator("服务器 " + (i + 1))).expandX();

            // 第一行：启用 / 名称 / 删除
            WHorizontalList header = root.add(theme.horizontalList()).expandX().widget();

            WCheckbox enabled = header.add(theme.checkbox(profile.enabled)).widget();
            header.add(theme.label("启用"));
            enabled.action = () -> {
                profile.enabled = enabled.checked;
                saveProfiles(profiles);
            };

            WTextBox name = header.add(theme.textBox(profile.name, "服务器名称")).expandX().widget();
            name.action = () -> {
                profile.name = name.get().trim();
                saveProfiles(profiles);
            };

            WMinus remove = header.add(theme.minus()).widget();
            remove.action = () -> {
                if (index >= 0 && index < profiles.size()) {
                    profiles.remove(index);
                    saveProfiles(profiles);
                    rebuildProfileWidget(theme, root, profiles);
                }
            };

            // 第二行：地址匹配
            WHorizontalList addressRow = root.add(theme.horizontalList()).expandX().widget();
            addressRow.add(theme.label("地址匹配"));
            WTextBox address = addressRow.add(theme.textBox(profile.address, "example.com 或 ip:port")).expandX().widget();
            address.action = () -> {
                profile.address = normalizeAddress(address.get());
                saveProfiles(profiles);
            };

            // 第三行：可选按当前 Minecraft 用户名匹配
            WHorizontalList usernameRow = root.add(theme.horizontalList()).expandX().widget();
            WCheckbox verifyUsername = usernameRow.add(theme.checkbox(profile.verifyUsername)).widget();
            usernameRow.add(theme.label("验证当前用户名"));
            verifyUsername.action = () -> {
                profile.verifyUsername = verifyUsername.checked;
                saveProfiles(profiles);
            };

            usernameRow.add(theme.label("当前用户名"));
            WTextBox username = usernameRow.add(theme.textBox(profile.username, "例如 Steve")).expandX().widget();
            username.action = () -> {
                profile.username = username.get().trim();
                saveProfiles(profiles);
            };

            // 第四行：验证码开关 + 密码
            WHorizontalList verificationRow = root.add(theme.horizontalList()).expandX().widget();
            WCheckbox autoVerify = verificationRow.add(theme.checkbox(profile.autoHumanVerification)).widget();
            verificationRow.add(theme.label("AuthMeUI 自动验证"));
            autoVerify.action = () -> {
                profile.autoHumanVerification = autoVerify.checked;
                saveProfiles(profiles);
            };

            verificationRow.add(theme.label("密码"));
            WTextBox password = verificationRow.add(theme.textBox(profile.password, "AuthMe 密码")).expandX().widget();
            password.action = () -> {
                profile.password = password.get();
                saveProfiles(profiles);
            };

            // 第五部分：自动指令动态列表（每条一行，可无限添加/删除）
            root.add(theme.label("自动指令（按从上到下顺序执行）"));

            if (profile.commands.isEmpty()) {
                root.add(theme.label("暂无自动指令。"));
            } else {
                for (int commandRowIndex = 0; commandRowIndex < profile.commands.size(); commandRowIndex++) {
                    final int commandIndex = commandRowIndex;
                    WHorizontalList commandRow = root.add(theme.horizontalList()).expandX().widget();

                    commandRow.add(theme.label("#" + (commandIndex + 1)));
                    WTextBox commandBox = commandRow.add(
                        theme.textBox(profile.commands.get(commandIndex), "server survival")
                    ).expandX().widget();

                    commandBox.action = () -> {
                        if (commandIndex >= 0 && commandIndex < profile.commands.size()) {
                            profile.commands.set(commandIndex, commandBox.get());
                            saveProfiles(profiles);
                        }
                    };

                    WMinus removeCommand = commandRow.add(theme.minus()).widget();
                    removeCommand.action = () -> {
                        if (commandIndex >= 0 && commandIndex < profile.commands.size()) {
                            profile.commands.remove(commandIndex);
                            saveProfiles(profiles);
                            rebuildProfileWidget(theme, root, profiles);
                        }
                    };
                }
            }

            WHorizontalList addCommandRow = root.add(theme.horizontalList()).expandX().widget();
            WPlus addCommand = addCommandRow.add(theme.plus()).widget();
            addCommandRow.add(theme.label("添加自动指令"));
            addCommand.action = () -> {
                profile.commands.add("");
                rebuildProfileWidget(theme, root, profiles);
            };
        }

        WHorizontalList addRow = root.add(theme.horizontalList()).expandX().widget();
        WPlus add = addRow.add(theme.plus()).widget();
        addRow.add(theme.label("添加服务器"));
        add.action = () -> {
            profiles.add(new ServerProfile(
                "服务器 " + (profiles.size() + 1),
                false,
                "",
                false,
                "",
                false,
                "",
                new ArrayList<>()
            ));
            saveProfiles(profiles);
            rebuildProfileWidget(theme, root, profiles);
        };
    }

    // ========================================================================
    // Tick：只以底层连接对象变化作为“新物理会话”
    // ========================================================================

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        Object liveConnection = getLiveConnection();

        if (liveConnection != null) {
            disconnectedTicks = 0;

            if (liveConnection != activeConnection) {
                beginSession(liveConnection);
            } else if (activeProfile == null) {
                // 配置阶段可能先拿到连接、稍后 ServerInfo 才可用。
                tryAttachProfileToCurrentSession();
            }
        } else if (activeConnection != null) {
            // Velocity 切服过程中某些客户端状态可能短暂为空，不立即当成断线。
            if (mc.getCurrentServerEntry() == null && !mc.isInSingleplayer()) {
                disconnectedTicks++;
                if (disconnectedTicks >= DISCONNECT_GRACE_TICKS) {
                    resetSession(true);
                }
            } else {
                disconnectedTicks = 0;
            }
        }

        runCommandSequence();
    }

    private Object getLiveConnection() {
        try {
            if (mc.getNetworkHandler() != null) {
                return mc.getNetworkHandler().getConnection();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void beginSession(Object connection) {
        activeConnection = connection;
        disconnectedTicks = 0;
        activeAddress = getCurrentServerAddress();
        activeProfile = findMatchingProfile(activeAddress);
        commandsScheduledForSession = false;
        resetSequence();

        if (showFeedback.get()) {
            info("检测到新的物理连接会话" + (activeAddress.isBlank() ? "。" : ": " + activeAddress));
        }

        scheduleCommandsForActiveProfile();
    }

    private void tryAttachProfileToCurrentSession() {
        String address = getCurrentServerAddress();
        if (address.isBlank()) return;

        ServerProfile matched = findMatchingProfile(address);
        if (matched == null) return;

        activeAddress = address;
        activeProfile = matched;

        if (showFeedback.get()) {
            info("匹配服务器配置: " + displayName(matched));
        }

        scheduleCommandsForActiveProfile();
    }

    private void scheduleCommandsForActiveProfile() {
        if (commandsScheduledForSession || activeProfile == null) return;
        commandsScheduledForSession = true;

        List<String> commands = commandsForSession(activeProfile);
        if (commands.isEmpty()) {
            if (showFeedback.get()) info("当前服务器没有需要执行的自动指令。");
            return;
        }

        startSequence(commands);
        if (showFeedback.get()) {
            info("将在 " + initialDelay.get() + " tick 后执行 " + commands.size() + " 条指令（本物理连接仅执行一次）。");
        }
    }

    private void resetSession(boolean feedback) {
        activeConnection = null;
        activeProfile = null;
        activeAddress = "";
        disconnectedTicks = 0;
        commandsScheduledForSession = false;
        resetSequence();

        if (feedback && showFeedback.get()) {
            info("确认已断开物理连接，会话记忆已重置。");
        }
    }

    // ========================================================================
    // 自动验证码：接收原版 ShowDialog 包后解析 human_slider 并直接提交 action
    // ========================================================================

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        // 必须使用强类型 instanceof。运行环境里 named 类名会被 Loom 重映射，
        // 运行时类名字符串判断在生产 JAR 中不可靠。
        if (!(event.packet instanceof ShowDialogS2CPacket packet)) return;

        // 配置阶段 Dialog 可能早于 ClientPlayNetworkHandler，因此优先使用事件自带 connection。
        if (event.connection != null && event.connection != activeConnection) {
            beginSession(event.connection);
        }

        ServerProfile profile = findMatchingProfile(getCurrentServerAddress());
        if (profile == null) profile = activeProfile;
        if (profile == null || !profile.enabled || !profile.autoHumanVerification) return;

        if (showFeedback.get()) {
            info("检测到原版 Dialog，正在检查 AuthMeUI 人机验证。");
        }

        String password = resolvePassword(profile);
        if (password.isBlank()) {
            if (showFeedback.get()) warning("检测到 Dialog，但当前服务器 Profile 没有可用密码。已保留原 Dialog。");
            return;
        }

        try {
            DialogChallenge challenge = extractDialogChallenge(packet);
            if (challenge == null) {
                if (showFeedback.get()) {
                    info("Dialog 已收到，但未找到 password + human_slider 或无法解析目标区间，保留手动界面。");
                }
                return;
            }

            float answer = chooseSliderValue(
                challenge.targetMin,
                challenge.targetMax,
                challenge.sliderMin,
                challenge.sliderMax,
                challenge.step
            );

            NbtCompound payload = new NbtCompound();
            payload.putString(PASSWORD_KEY, password);
            payload.putFloat(HUMAN_SLIDER_KEY, answer);

            CustomClickActionC2SPacket submitPacket = new CustomClickActionC2SPacket(
                Identifier.of(LOGIN_ACTION_NAMESPACE + ":" + LOGIN_ACTION_PATH),
                Optional.<NbtElement>of(payload)
            );

            // 直接走收到 Dialog 的同一底层连接。这样配置阶段和游戏阶段都可提交。
            // 强类型方法调用会由 Loom 正确重映射。
            event.connection.send(submitPacket);

            // 已经等价提交了原 Dialog，取消原包，避免 GUI 一闪而过。
            event.setCancelled(true);

            if (showFeedback.get()) {
                info("AuthMeUI 自动验证已提交: 目标 "
                    + formatNumber(challenge.targetMin) + "~" + formatNumber(challenge.targetMax)
                    + "，提交 " + formatNumber(answer));
            }
        } catch (Throwable t) {
            // 自动验证失败不能破坏正常登录：任何异常都让原 Dialog 正常显示。
            if (showFeedback.get()) {
                warning("AuthMeUI 自动验证失败，已回退到手动 Dialog: " + t);
            }
        }
    }

    private DialogChallenge extractDialogChallenge(ShowDialogS2CPacket packet) {
        Dialog dialog = packet.dialog().value();
        if (dialog == null) return null;

        boolean hasPasswordInput = false;
        NumberRangeInputControl sliderControl = null;

        for (DialogInput input : dialog.common().inputs()) {
            if (input == null) continue;

            String key = input.key();
            if (PASSWORD_KEY.equals(key)) {
                hasPasswordInput = true;
            } else if (HUMAN_SLIDER_KEY.equals(key)
                && input.control() instanceof NumberRangeInputControl numberRange) {
                sliderControl = numberRange;
            }
        }

        // 双重约束：必须同时有 password 和 human_slider，避免误处理其他服的普通 Dialog。
        if (!hasPasswordInput || sliderControl == null) return null;

        String label = sliderControl.label().getString();
        float[] target = parseTargetRange(label);
        if (target == null) {
            if (showFeedback.get()) warning("找到 human_slider，但无法从标签解析目标区间: " + label);
            return null;
        }

        NumberRangeInputControl.RangeInfo rangeInfo = sliderControl.rangeInfo();
        Float step = rangeInfo.step().orElse(null);

        return new DialogChallenge(
            target[0],
            target[1],
            rangeInfo.start(),
            rangeInfo.end(),
            step
        );
    }

    static float[] parseTargetRange(String label) {
        if (label == null || label.isBlank()) return null;

        Matcher matcher = TARGET_RANGE_PATTERN.matcher(label);
        if (!matcher.find()) return null;

        try {
            float a = Float.parseFloat(matcher.group(1));
            float b = Float.parseFloat(matcher.group(2));
            return a <= b ? new float[]{a, b} : new float[]{b, a};
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static float chooseSliderValue(float targetMin, float targetMax, float sliderMin, float sliderMax, Float step) {
        float low = Math.max(Math.min(targetMin, targetMax), Math.min(sliderMin, sliderMax));
        float high = Math.min(Math.max(targetMin, targetMax), Math.max(sliderMin, sliderMax));
        if (low > high) return clamp((targetMin + targetMax) / 2.0f, sliderMin, sliderMax);

        float value = (low + high) / 2.0f;

        if (step != null && Float.isFinite(step) && step > 0.000001f) {
            float base = Math.min(sliderMin, sliderMax);
            float aligned = base + Math.round((value - base) / step) * step;

            if (aligned < low) {
                aligned = base + (float) Math.ceil((low - base) / step) * step;
            }
            if (aligned > high) {
                aligned = base + (float) Math.floor((high - base) / step) * step;
            }

            if (aligned >= low - 0.0001f && aligned <= high + 0.0001f) value = aligned;
        }

        return clamp(value, sliderMin, sliderMax);
    }

    // ========================================================================
    // 指令状态机
    // ========================================================================

    private void startSequence(List<String> cmdList) {
        isSending = true;
        currentCmdList = new ArrayList<>(cmdList);
        currentCmdIndex = 0;
        timer = initialDelay.get();
    }

    private void runCommandSequence() {
        if (!isSending || currentCmdList == null) return;
        if (mc.player == null || mc.world == null) return;

        if (timer > 0) {
            timer--;
            return;
        }

        if (currentCmdIndex >= currentCmdList.size()) {
            if (showFeedback.get() && currentCmdIndex > 0) {
                info("本物理连接的自动指令序列执行完毕。");
            }
            resetSequence();
            return;
        }

        String cmd = currentCmdList.get(currentCmdIndex);
        if (cmd != null && !cmd.trim().isEmpty()) {
            sendCommand(cmd);
            if (showFeedback.get()) {
                info("自动发送: " + redactCommand(cmd));
            }
        }

        currentCmdIndex++;
        if (currentCmdIndex < currentCmdList.size()) {
            timer = commandInterval.get();
        }
    }

    private void resetSequence() {
        timer = 0;
        currentCmdIndex = 0;
        isSending = false;
        currentCmdList = null;
    }

    private void sendCommand(String cmd) {
        String cleanCmd = cmd.trim();
        if (cleanCmd.startsWith("/")) cleanCmd = cleanCmd.substring(1);

        if (mc.player != null && mc.player.networkHandler != null) {
            mc.player.networkHandler.sendChatCommand(cleanCmd);
        }
    }

    private List<String> commandsForSession(ServerProfile profile) {
        List<String> result = new ArrayList<>();
        String resolvedPassword = resolvePassword(profile);
        boolean automaticLoginWillHandlePassword = profile.autoHumanVerification && !resolvedPassword.isBlank();

        for (String command : profile.commands) {
            if (command == null || command.isBlank()) continue;

            // 开启 Dialog 自动验证后，不再重复发送旧配置中的 /login 密码。
            if (automaticLoginWillHandlePassword && isLoginCommand(command)) continue;
            result.add(command.trim());
        }

        return result;
    }

    private String resolvePassword(ServerProfile profile) {
        if (profile.password != null && !profile.password.isBlank()) return profile.password;

        // 兼容旧配置：从 "login xxx" / "/login xxx" 中提取，但不会写日志。
        for (String command : profile.commands) {
            if (command == null) continue;
            Matcher matcher = LOGIN_COMMAND_PATTERN.matcher(command.trim());
            if (matcher.matches()) return matcher.group(1);
        }

        return "";
    }

    private boolean isLoginCommand(String command) {
        return command != null && LOGIN_COMMAND_PATTERN.matcher(command.trim()).matches();
    }

    private String redactCommand(String command) {
        if (isLoginCommand(command)) return "login ********";
        return command;
    }

    // ========================================================================
    // Profile 匹配 / 持久化 / 旧配置迁移
    // ========================================================================

    private ServerProfile findMatchingProfile(String currentAddress) {
        if (currentAddress == null || currentAddress.isBlank()) return null;

        String normalizedAddress = normalizeAddress(currentAddress);
        String currentUsername = getCurrentUsername();
        ServerProfile genericAddressMatch = null;

        // 优先选择“地址 + 用户名”精确 Profile。这样同一个服务器可以为不同账号保存不同密码，
        // 即使列表里同时存在一个不校验用户名的通用 Profile，也不会抢在专用账号 Profile 前面。
        for (ServerProfile profile : loadProfiles()) {
            if (!profile.enabled || !addressMatches(profile.address, normalizedAddress)) continue;

            if (profile.verifyUsername) {
                if (usernameMatches(profile.username, currentUsername)) return profile;
            } else if (genericAddressMatch == null) {
                genericAddressMatch = profile;
            }
        }

        return genericAddressMatch;
    }

    private static boolean addressMatches(String configuredAddress, String currentAddress) {
        String configured = normalizeAddress(configuredAddress);
        String current = normalizeAddress(currentAddress);
        return !configured.isBlank() && !current.isBlank() && current.contains(configured);
    }

    private static boolean usernameMatches(String configuredUsername, String currentUsername) {
        if (configuredUsername == null || configuredUsername.isBlank()) return false;
        if (currentUsername == null || currentUsername.isBlank()) return false;
        return configuredUsername.trim().equalsIgnoreCase(currentUsername.trim());
    }

    private String getCurrentUsername() {
        try {
            if (mc.getSession() == null || mc.getSession().getUsername() == null) return "";
            return mc.getSession().getUsername().trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String getCurrentServerAddress() {
        if (mc.isInSingleplayer()) return "";

        try {
            ServerInfo info = mc.getCurrentServerEntry();
            if (info == null || info.address == null) return "";
            return normalizeAddress(info.address);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private List<ServerProfile> loadProfiles() {
        List<String> raw = profileStorage.get();
        List<ServerProfile> profiles = new ArrayList<>();

        if (raw != null) {
            for (String line : raw) {
                ServerProfile profile = decodeProfile(line);
                if (profile != null) profiles.add(profile);
            }
        }

        if (!profiles.isEmpty() || profileStorageInitialized.get()) return profiles;

        // 第一次升级：把原固定 3 组转换成动态 Profile。
        profiles.add(legacyProfile("服务器 1", legacy1Enabled.get(), legacy1Ip.get(), legacy1Cmds.get()));
        profiles.add(legacyProfile("服务器 2", legacy2Enabled.get(), legacy2Ip.get(), legacy2Cmds.get()));
        profiles.add(legacyProfile("服务器 3", legacy3Enabled.get(), legacy3Ip.get(), legacy3Cmds.get()));

        saveProfiles(profiles);
        return profiles;
    }

    private ServerProfile legacyProfile(String name, boolean enabled, String address, List<String> commands) {
        List<String> safeCommands = commands == null ? new ArrayList<>() : new ArrayList<>(commands);
        String password = "";

        for (String command : safeCommands) {
            if (command == null) continue;
            Matcher matcher = LOGIN_COMMAND_PATTERN.matcher(command.trim());
            if (matcher.matches()) {
                password = matcher.group(1);
                break;
            }
        }

        return new ServerProfile(name, enabled, normalizeAddress(address), false, "", false, password, safeCommands);
    }

    private void saveProfiles(List<ServerProfile> profiles) {
        List<String> encoded = new ArrayList<>();
        for (ServerProfile profile : profiles) encoded.add(encodeProfile(profile));
        profileStorage.set(encoded);
        profileStorageInitialized.set(true);
    }

    static String encodeProfile(ServerProfile profile) {
        String commandBlob = String.join("\n", profile.commands == null ? List.of() : profile.commands);
        return String.join("|",
            PROFILE_VERSION,
            b64(profile.name),
            profile.enabled ? "1" : "0",
            b64(normalizeAddress(profile.address)),
            profile.verifyUsername ? "1" : "0",
            b64(profile.username),
            profile.autoHumanVerification ? "1" : "0",
            b64(profile.password),
            b64(commandBlob)
        );
    }

    static ServerProfile decodeProfile(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;

        try {
            String[] parts = encoded.split("\\|", -1);

            if (parts.length == 9 && PROFILE_VERSION.equals(parts[0])) {
                String commandBlob = unb64(parts[8]);
                List<String> commands = decodeCommands(commandBlob);

                return new ServerProfile(
                    unb64(parts[1]),
                    "1".equals(parts[2]),
                    normalizeAddress(unb64(parts[3])),
                    "1".equals(parts[4]),
                    unb64(parts[5]),
                    "1".equals(parts[6]),
                    unb64(parts[7]),
                    commands
                );
            }

            // AS2 -> AS3 兼容：旧动态 Profile 没有用户名字段，按“任意账号”处理。
            if (parts.length == 7 && LEGACY_PROFILE_VERSION.equals(parts[0])) {
                String commandBlob = unb64(parts[6]);
                List<String> commands = decodeCommands(commandBlob);

                return new ServerProfile(
                    unb64(parts[1]),
                    "1".equals(parts[2]),
                    normalizeAddress(unb64(parts[3])),
                    false,
                    "",
                    "1".equals(parts[4]),
                    unb64(parts[5]),
                    commands
                );
            }

            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static List<String> decodeCommands(String commandBlob) {
        List<String> commands = new ArrayList<>();
        if (commandBlob == null || commandBlob.isBlank()) return commands;

        for (String command : commandBlob.split("\\n", -1)) {
            if (!command.isBlank()) commands.add(command);
        }
        return commands;
    }

    private static String b64(String value) {
        if (value == null) value = "";
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String unb64(String value) {
        if (value == null || value.isEmpty()) return "";
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static String normalizeAddress(String address) {
        return address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
    }

    private static String displayName(ServerProfile profile) {
        if (profile == null) return "未知";
        if (profile.name != null && !profile.name.isBlank()) return profile.name;
        return profile.address == null || profile.address.isBlank() ? "未命名服务器" : profile.address;
    }

    // ========================================================================
    // 数值辅助
    // ========================================================================

    private static float clamp(float value, float a, float b) {
        float min = Math.min(a, b);
        float max = Math.max(a, b);
        return Math.max(min, Math.min(max, value));
    }

    private static String formatNumber(float value) {
        if (Math.abs(value - Math.round(value)) < 0.0001f) return Integer.toString(Math.round(value));
        return Float.toString(value);
    }

    // ========================================================================
    // 数据结构（全部保持在单个 Java 文件内）
    // ========================================================================

    static final class ServerProfile {
        String name;
        boolean enabled;
        String address;
        boolean verifyUsername;
        String username;
        boolean autoHumanVerification;
        String password;
        List<String> commands;

        ServerProfile(
            String name,
            boolean enabled,
            String address,
            boolean verifyUsername,
            String username,
            boolean autoHumanVerification,
            String password,
            List<String> commands
        ) {
            this.name = name == null ? "" : name;
            this.enabled = enabled;
            this.address = normalizeAddress(address);
            this.verifyUsername = verifyUsername;
            this.username = username == null ? "" : username.trim();
            this.autoHumanVerification = autoHumanVerification;
            this.password = password == null ? "" : password;
            this.commands = commands == null ? new ArrayList<>() : new ArrayList<>(commands);
        }
    }

    private record DialogChallenge(
        float targetMin,
        float targetMax,
        float sliderMin,
        float sliderMax,
        Float step
    ) {
    }
}
