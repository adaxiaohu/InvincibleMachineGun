package com.codigohasta.addon.modules;

import net.minecraft.util.math.Vec3d;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.nbt.NbtCompound;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MusicPlayer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public enum Mode {
        Sequential,
        Request,
        Random
    }

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("运行模式")
        .description("选择点歌机的工作方式。")
        .defaultValue(Mode.Sequential)
        .build()
    );

    private final Setting<Double> delay = sgGeneral.add(new DoubleSetting.Builder()
        .name("发送间隔 (秒)")
        .description("每隔多少秒发送下一条指令。")
        .defaultValue(2.0)
        .min(0.1)
        .sliderMax(10.0)
        .visible(() -> mode.get() == Mode.Sequential || mode.get() == Mode.Random)
        .build()
    );

    // --- 新增：进度记忆与控制 ---
    // 这个设置项既是"记忆"，也是"控制条"。Meteor会自动保存它的值，所以重启游戏后进度还在。
    private final Setting<Integer> currentLine = sgGeneral.add(new IntSetting.Builder()
        .name("当前行号 (进度)")
        .description("当前播放到第几行 (可手动修改)。")
        .defaultValue(1)
        .min(1)
        .noSlider() // 建议不使用滑块，直接输入数字更精确
        .visible(() -> mode.get() == Mode.Sequential)
        .build()
    );

    private final Setting<String> triggerPrefix = sgGeneral.add(new StringSetting.Builder()
        .name("点歌触发前缀")
        .description("聊天栏识别的前缀 (例如: #点歌 1)。")
        .defaultValue("#")
        .visible(() -> mode.get() == Mode.Request)
        .build()
    );

    private final Setting<Boolean> listenToOthers = sgGeneral.add(new BoolSetting.Builder()
        .name("允许他人点歌")
        .description("是否响应其他玩家发送的点歌指令。")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Request)
        .build()
    );

    // --- 文件选择器相关变量 ---
    private File file = null;
    private final PointerBuffer filters;
    // -----------------------

    private final List<String> allLines = new ArrayList<>();
    // 移除了 sequentialQueue，因为现在直接用 index (currentLine) 访问 allLines
    private final Random random = new Random();
    private int timer;

    public MusicPlayer() {
        super(AddonTemplate.CATEGORY, "music-player", "用/music自动点歌。你可以自己添加歌单txt文件。就可以自动点歌了，国内一些服务器有/music插件");

        filters = BufferUtils.createPointerBuffer(1);
        ByteBuffer txtFilter = MemoryUtil.memASCII("*.txt");
        filters.put(txtFilter);
        filters.rewind();
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        // 使用垂直列表，让按钮分行显示，更整洁
        WVerticalList root = theme.verticalList();

        // 第一行：文件选择
        WHorizontalList fileList = root.add(theme.horizontalList()).widget();
        WButton selectFile = fileList.add(theme.button("选择歌单文件")).widget();
        WLabel fileNameLabel = fileList.add(theme.label((file != null && file.exists()) ? file.getName() : "当前未选择文件")).widget();

        // 第二行：重置进度按钮 (仅在顺序模式下有用，但常驻显示也无妨)
        WHorizontalList controlList = root.add(theme.horizontalList()).widget();
        WButton resetProgress = controlList.add(theme.button("重置播放进度 (归1)")).widget();

        // --- 逻辑：选择文件 ---
        selectFile.action = () -> {
            String initialPath = (file != null) ? file.getAbsolutePath() : new File(MeteorClient.FOLDER, "songs.txt").getAbsolutePath();
            
            String path = TinyFileDialogs.tinyfd_openFileDialog(
                "选择歌单文件 (.txt)",
                initialPath,
                filters,
                "Text Files",
                false
            );

            if (path != null) {
                File newFile = new File(path);
                if (newFile.exists()) {
                    file = newFile;
                    fileNameLabel.set(file.getName());
                    loadFile();
                    
                    // 切换文件时，通常用户希望从头开始，但为了保险，我们可以提示用户手动重置
                    // 或者在这里强制重置：
                    currentLine.set(1); 
                    info("已切换文件，进度已重置为第 1 首。");
                }
            }
        };

        // --- 逻辑：重置进度 ---
        resetProgress.action = () -> {
            currentLine.set(1);
            info("播放进度已重置为 1。");
        };

        return root;
    }

    @Override
    public void onActivate() {
        if (file == null || !file.exists()) {
            error("未选择文件！请打开设置选择文件。");
            toggle();
            return;
        }

        loadFile();
        
        // 验证当前进度是否超出文件范围
        if (currentLine.get() > allLines.size()) {
            warning("之前的进度 (" + currentLine.get() + ") 超出了当前文件行数，已重置为 1。");
            currentLine.set(1);
        }

        // 启动延迟
        timer = (int) (delay.get() * 20);
        
        if (mode.get() == Mode.Sequential) {
            info("开始播放，从第 " + currentLine.get() + " 行开始。");
        }
    }

    private void loadFile() {
        allLines.clear();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    allLines.add(line.trim());
                }
            }
        } catch (IOException e) {
            error("读取文件失败: " + e.getMessage());
            if (isActive()) toggle();
        }

        if (allLines.isEmpty()) {
            error("文件是空的！");
            if (isActive()) toggle();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mode.get() == Mode.Request) return;

        if (timer <= 0) {
            if (mode.get() == Mode.Sequential) {
                // 获取当前应该播放的行号 (1-based)
                int lineNum = currentLine.get();
                // 转换为 List 索引 (0-based)
                int index = lineNum - 1;

                // 检查是否播放完毕
                if (index >= allLines.size()) {
                    info("列表播放完毕 (" + allLines.size() + " 首)，自动关闭。");
                    toggle();
                    return;
                }

                // 发送当前行
                sendCommand(allLines.get(index));

                // === 关键点：播放完后，进度+1，并保存到设置中 ===
                // 这样下次开启或重启游戏时，Setting 里的值就是下一首
                currentLine.set(lineNum + 1); 

            } else if (mode.get() == Mode.Random) {
                if (allLines.isEmpty()) return;
                int randomIndex = random.nextInt(allLines.size());
                sendCommand(allLines.get(randomIndex));
            }
            
            // 重置计时器
            timer = (int) (delay.get() * 20);
        } else {
            timer--;
        }
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (mode.get() != Mode.Request) return;

        String message = event.getMessage().getString();
        String prefix = triggerPrefix.get();

        if (message.contains(prefix)) {
            try {
                int indexInMsg = message.indexOf(prefix);
                String temp = message.substring(indexInMsg + prefix.length()).trim();
                String[] parts = temp.split("\\s+");
                
                if (parts.length == 0) return;
                
                String numberStr = parts[0].replaceAll("[^0-9]", "");
                if (numberStr.isEmpty()) return;

                int lineNumber = Integer.parseInt(numberStr);
                
                boolean isMe = false;
                if (mc.player != null && message.contains(mc.player.getName().getString())) isMe = true;
                
                if (!listenToOthers.get() && !isMe) return;

                executeRequest(lineNumber);
            } catch (Exception ignored) {
            }
        }
    }

    private void executeRequest(int lineNum) {
        int index = lineNum - 1;
        if (index >= 0 && index < allLines.size()) {
            info("点歌响应：第 " + lineNum + " 行。");
            sendCommand(allLines.get(index));
        }
    }

    private void sendCommand(String cmd) {
        if (mc.player == null) return;
        if (cmd.startsWith("/")) {
            mc.getNetworkHandler().sendChatCommand(cmd.substring(1));
        } else {
            mc.getNetworkHandler().sendChatMessage(cmd);
        }
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = super.toTag();
        if (file != null && file.exists()) {
            tag.putString("file", file.getAbsolutePath());
        }
        return tag;
    }

    @Override
    public Module fromTag(NbtCompound tag) {
        if (tag.contains("file")) {
            file = new File(tag.getString("file").orElse(""));
        }
        return super.fromTag(tag);
    }
}