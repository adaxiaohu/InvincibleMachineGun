package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.Text;

import java.util.Arrays;
import java.util.List;

public class FeedbackBlocker extends Module {
    
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // 添加一个忽略大小写的选项，通常更有用
    private final Setting<Boolean> ignoreCase = sgGeneral.add(new BoolSetting.Builder()
        .name("忽略大小写")
        .description("开启后，'placed' 也可以屏蔽 'Placed'。")
        .defaultValue(true) 
        .build()
    );

    // 可以在这里填入你的中文关键词
    private final Setting<List<String>> keywords = sgGeneral.add(new StringListSetting.Builder()
        .name("屏蔽关键词")
        .description("当消息中完整包含这些短语时进行拦截。")
        .defaultValue(Arrays.asList(
            "全球拍卖开始",
            "更改了位于", 
            "已成功填充", 
            "召唤了新的", 
            "Placed", 
            "Undo", 
            "Affected", 
            "Paste", 
            "Schematic"
        ))
        .build()
    );

    public FeedbackBlocker() {
        super(AddonTemplate.CATEGORY, "刷屏屏蔽器", "智能拦截包含特定关键词的聊天信息。");
    }

    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        if (mc.world == null || mc.player == null) return;

        Text message = event.getMessage();
        // .getString() 获取的是去除了颜色代码的纯文本，非常适合用来匹配
        String content = message.getString(); 
        
        // 这一步是关键：如果开启忽略大小写，则全部转为小写进行比对
        String contentToCheck = ignoreCase.get() ? content.toLowerCase() : content;

        for (String keyword : keywords.get()) {
            // 如果关键词是空的，跳过
            if (keyword.isEmpty()) continue;

            String keywordToCheck = ignoreCase.get() ? keyword.toLowerCase() : keyword;

            // 核心逻辑：contains 只要包含了这一整段文字就屏蔽
            // 例子：
            // 关键词="全球拍卖开始"
            // 内容="全球拍卖" -> false (不屏蔽)
            // 内容="快看，全球拍卖开始了" -> true (屏蔽)
            if (contentToCheck.contains(keywordToCheck)) {
                event.cancel();
                // 可以在控制台打印日志方便调试，不需要的话删掉下面这行
                // info("已拦截消息: " + content);
                return; // 只要匹配到一个关键词就拦截并结束循环
            }
        }
    }
}