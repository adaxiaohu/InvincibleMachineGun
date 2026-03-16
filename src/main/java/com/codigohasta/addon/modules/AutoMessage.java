package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;

import java.util.ArrayList;
import java.util.List;

public class AutoMessage extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // 1. 指令列表设置 (已修改默认内容)
    private final Setting<List<String>> messages = sgGeneral.add(new StringListSetting.Builder()
        .name("messages")
        .name("指令列表")
        .description("指令列表。若开启'只运行选中'，请在要运行的指令前加前缀。\n语法: [+前缀] [内容] #[延迟秒数]\n例如: '+ /spawn #5' (代表发送后等待5秒)")
        .defaultValue(List.of(
            "+ 测试消息你好 #3",
            "1 #60",
            "+ 3 #5",
            "测试 #30"
        ))
        .build()
    );

    // 2. 默认延迟 (已修改单位为秒)
    private final Setting<Integer> defaultDelay = sgGeneral.add(new IntSetting.Builder()
        .name("default-delay")
        .name("默认延迟 (秒)")
        .description("未指定延迟时使用的默认等待时间 (单位: 秒)。")
        .defaultValue(3) // 默认3秒
        .min(1)
        .sliderMax(300)
        .build()
    );

    // 3. 循环模式
    private final Setting<Boolean> loop = sgGeneral.add(new BoolSetting.Builder()
        .name("loop")
        .name("循环模式")
        .description("列表执行完毕后是否重新开始。")
        .defaultValue(true)
        .build()
    );

    // 4. 随机模式
    private final Setting<Boolean> random = sgGeneral.add(new BoolSetting.Builder()
        .name("random")
        .name("随机顺序")
        .description("从有效列表中随机选择指令发送。")
        .defaultValue(false)
        .build()
    );

    // 5. 只运行选中
    private final Setting<Boolean> onlySelected = sgGeneral.add(new BoolSetting.Builder()
        .name("only-selected")
        .name("只运行选中项")
        .description("开启后，只有以'选中前缀'开头的指令会被发送。")
        .defaultValue(false)
        .build()
    );

    // 6. 选中前缀
    private final Setting<String> selectPrefix = sgGeneral.add(new StringSetting.Builder()
        .name("select-prefix")
        .name("选中前缀")
        .description("用于标记被选中指令的符号。")
        .defaultValue("+")
        .visible(onlySelected::get)
        .build()
    );

    private int timer = 0;
    private int messageIndex = 0;

    public AutoMessage() {
        super(AddonTemplate.CATEGORY, "定时发送消息", "定时发送指令 (秒级延迟)。");
    }

    @Override
    public void onActivate() {
        timer = 0;
        messageIndex = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // 获取所有消息
        List<String> allMessages = messages.get();
        if (allMessages.isEmpty()) return;

        // 构建有效列表
        List<String> validList = new ArrayList<>();
        String prefix = selectPrefix.get();

        for (String line : allMessages) {
            if (onlySelected.get()) {
                if (line.startsWith(prefix)) {
                    validList.add(line);
                }
            } else {
                if (!line.trim().isEmpty()) {
                    validList.add(line);
                }
            }
        }

        if (validList.isEmpty()) return;

        // 计时器倒计时 (timer单位是tick，所以在tick事件中减1)
        if (timer > 0) {
            timer--;
            return;
        }

        // 发送消息
        sendMessage(validList);
    }

    private void sendMessage(List<String> validList) {
        // 索引保护
        if (messageIndex >= validList.size()) {
            if (loop.get()) {
                messageIndex = 0;
            } else {
                toggle();
                return;
            }
        }

        // 获取原始文本
        String rawLine;
        if (random.get()) {
            rawLine = validList.get((int) (Math.random() * validList.size()));
        } else {
            rawLine = validList.get(messageIndex);
        }

        // --- 解析文本 ---
        // 1. 去除选中前缀
        if (onlySelected.get() && rawLine.startsWith(selectPrefix.get())) {
            rawLine = rawLine.substring(selectPrefix.get().length()).trim();
        }

        String content = rawLine;
        int delaySeconds = defaultDelay.get(); // 获取秒数

        // 2. 解析延迟语法 ( #数字 )
        if (rawLine.contains("#")) {
            int hashIndex = rawLine.lastIndexOf("#");
            String potentialDelay = rawLine.substring(hashIndex + 1).trim();
            
            try {
                // 解析秒数
                int parsedDelay = Integer.parseInt(potentialDelay);
                delaySeconds = parsedDelay;
                content = rawLine.substring(0, hashIndex).trim();
            } catch (NumberFormatException ignored) {
                // 如果解析失败，则忽略，视为普通文本
            }
        }

        // 3. 发送
        if (!content.isEmpty()) {
            ChatUtils.sendPlayerMsg(content);
        }

        // 4. 设置计时器
        // 关键修改：将秒转换为Tick (1秒 = 20 ticks)
        timer = delaySeconds * 20;

        // 索引递增
        if (!random.get()) {
            messageIndex++;
        }
    }
}