package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ChatFilter extends Module {
    
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // 脏话列表：我为你增加了很多常见的词汇
    private final Setting<List<String>> badWords = sgGeneral.add(new StringListSetting.Builder()
        .name("屏蔽词列表")
        .description("检测到这些词时，将其替换。")
        .defaultValue(Arrays.asList(
            // 常见单字/短词
            "操", "滚", "爬", "艹", "日", 
            // 常见攻击性词汇
            "傻逼", "沙比", "煞笔", "sb", "SB", "脑残", "弱智", "智障", "废物", "脑瘫", "孤儿", "畜生", "狗叫","吃屎",
            // 涉及亲属
            "妈死了", "你妈", "尼玛", "死妈", "nmsl", "NMSL", "cnm", "CNM", "tmd", "TMD", "nm", "NM", "wcnm", "wdnmd","你妈死了",
            // 其他
            "杂种", "婊子", "司马", "死全家", "贱人", "垃圾","你妈炸了"
        ))
        .build()
    );

    // 基础替换字符
    private final Setting<String> replacementChar = sgGeneral.add(new StringSetting.Builder()
        .name("替换字符")
        .description("用来屏蔽的符号 (例如 *)。")
        .defaultValue("*")
        .build()
    );

    public ChatFilter() {
        super(AddonTemplate.CATEGORY, "文明聊天", "自动将聊天中的脏话替换成对应数量的 * 号 (例如: 傻逼 -> **)。");
    }

    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        if (mc.world == null || mc.player == null) return;

        Text originalMessage = event.getMessage();
        String rawText = originalMessage.getString();

        if (!containsBadWord(rawText)) {
            return;
        }

        MutableText cleanMessage = Text.empty();

        // 获取并排序屏蔽词列表
        // 关键逻辑：我们创建一个副本，并按长度从长到短排序
        // 这样可以保证 "操你妈" (3字) 会优先被处理，而不是先被 "操" (1字) 替换掉
        List<String> sortedBadWords = new ArrayList<>(badWords.get());
        sortedBadWords.sort((s1, s2) -> s2.length() - s1.length());

        originalMessage.visit((style, asString) -> {
            String cleanPart = filterText(asString, sortedBadWords);
            cleanMessage.append(Text.literal(cleanPart).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);

        event.setMessage(cleanMessage);
    }

    private boolean containsBadWord(String text) {
        for (String word : badWords.get()) {
            if (text.contains(word)) return true;
        }
        return false;
    }

    private String filterText(String text, List<String> sortedWords) {
        String result = text;
        String baseChar = replacementChar.get();
        
        // 如果用户没填字符，默认用 *
        if (baseChar.isEmpty()) baseChar = "*";
        // 只取第一个字符，防止用户填了 "abc" 导致替换变成 "abcabcabc"
        String singleChar = baseChar.substring(0, 1);

        for (String word : sortedWords) {
            if (result.contains(word)) {
                // 动态生成替换字符串：字数是多少，就重复多少个星号
                // "傻逼" (2) -> "**"
                // "操你妈" (3) -> "***"
                String stars = singleChar.repeat(word.length());
                
                result = result.replace(word, stars);
            }
        }
        return result;
    }
}