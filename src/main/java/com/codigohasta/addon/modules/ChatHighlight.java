package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class ChatHighlight extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSelf = settings.createGroup("自己 (Self)");
    private final SettingGroup sgManager = settings.createGroup("名单管理 (Manager)");
    private final SettingGroup sgOthers = settings.createGroup("路人 (Others)");

    // ================== 数据存储 ==================
    // 存储特定玩家的名单，Key是名字，Value是颜色
    private final Map<String, SettingColor> specificPlayers = new HashMap<>();

    // ================== 通用设置 ==================
    private final Setting<Boolean> strictMode = sgGeneral.add(new BoolSetting.Builder()
        .name("严格匹配模式")
        .description("开启后，只匹配完整的名字。\n例如：名字叫 'God' 时，不会匹配 'Godzilla'。")
        .defaultValue(true)
        .build());

    // ================== 1. 自己 (Self) ==================
    private final Setting<Boolean> highlightSelf = sgSelf.add(new BoolSetting.Builder()
        .name("高亮自己")
        .description("是否高亮显示自己的聊天信息。")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> onlySender = sgSelf.add(new BoolSetting.Builder()
        .name("仅作为发送者时")
        .description("推荐开启！\n只有当你的名字出现在消息开头(可能是你发的)才变色。\n防止别人提到你(@你)时也变成你的颜色。")
        .defaultValue(true)
        .build());

    private final Setting<SettingColor> selfColor = sgSelf.add(new ColorSetting.Builder()
        .name("颜色")
        .defaultValue(new SettingColor(0, 255, 255))
        .visible(highlightSelf::get)
        .build());

    private final Setting<Boolean> selfBold = sgSelf.add(new BoolSetting.Builder()
        .name("粗体 (Bold)")
        .defaultValue(true)
        .visible(highlightSelf::get)
        .build());

    private final Setting<Boolean> selfItalic = sgSelf.add(new BoolSetting.Builder()
        .name("斜体 (Italic)")
        .defaultValue(false)
        .visible(highlightSelf::get)
        .build());

    private final Setting<Boolean> selfUnderline = sgSelf.add(new BoolSetting.Builder()
        .name("下划线 (Underline)")
        .defaultValue(false)
        .visible(highlightSelf::get)
        .build());

    private final Setting<Boolean> selfStrikethrough = sgSelf.add(new BoolSetting.Builder()
        .name("删除线 (Strikethrough)")
        .defaultValue(false)
        .visible(highlightSelf::get)
        .build());

    // ================== 2. 名单管理 (Manager) ==================
    private final Setting<String> inputName = sgManager.add(new StringSetting.Builder()
        .name("目标玩家ID")
        .description("要添加、修改或删除的玩家名字。")
        .defaultValue("")
        .build()
    );

    private final Setting<SettingColor> inputColor = sgManager.add(new ColorSetting.Builder()
        .name("设定颜色")
        .description("为上方输入的玩家选择一个专属颜色。")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );

    private final Setting<Boolean> btnAdd = sgManager.add(new BoolSetting.Builder()
        .name("添加 / 更新名单")
        .description("点击此按钮将 [名字+颜色] 保存进高亮名单。")
        .defaultValue(false)
        .onChanged(v -> {
            if (v) {
                addEntry();
            }
        })
        .build()
    );

    private final Setting<Boolean> btnRemove = sgManager.add(new BoolSetting.Builder()
        .name("从名单移除")
        .description("点击此按钮将 [名字] 从名单中删除。")
        .defaultValue(false)
        .onChanged(v -> {
            if (v) {
                removeEntry();
            }
        })
        .build()
    );
    
    private final Setting<Boolean> btnPrint = sgManager.add(new BoolSetting.Builder()
        .name("打印当前名单")
        .description("在聊天栏显示所有已配置的特定玩家。")
        .defaultValue(false)
        .onChanged(v -> {
            if (v) {
                printList();
            }
        })
        .build()
    );

    // --- 名单通用特效 ---
    private final Setting<Boolean> specificBold = sgManager.add(new BoolSetting.Builder()
        .name("名单-粗体").defaultValue(true).build());
    private final Setting<Boolean> specificItalic = sgManager.add(new BoolSetting.Builder()
        .name("名单-斜体").defaultValue(false).build());
    private final Setting<Boolean> specificUnderline = sgManager.add(new BoolSetting.Builder()
        .name("名单-下划线").defaultValue(false).build());
    private final Setting<Boolean> specificStrikethrough = sgManager.add(new BoolSetting.Builder()
        .name("名单-删除线").defaultValue(false).build());


    // ================== 3. 路人 (Others) ==================
    private final Setting<Boolean> highlightOthers = sgOthers.add(new BoolSetting.Builder()
        .name("高亮路人")
        .description("是否改变其他非特定玩家的聊天颜色。")
        .defaultValue(true)
        .build());

    // --- 路人前缀设置 ---
    private final Setting<String> othersPrefix = sgOthers.add(new StringSetting.Builder()
        .name("前缀内容")
        .description("给路人消息添加前缀，例如 '[路人] '。留空则不显示。")
        .defaultValue("") 
        .visible(highlightOthers::get)
        .build());

    private final Setting<SettingColor> othersPrefixColor = sgOthers.add(new ColorSetting.Builder()
        .name("前缀颜色")
        .defaultValue(new SettingColor(150, 150, 150))
        .visible(highlightOthers::get)
        .build());
    
    private final Setting<Boolean> prefixBold = sgOthers.add(new BoolSetting.Builder()
        .name("前缀-粗体").defaultValue(false).visible(highlightOthers::get).build());

    // --- 路人正文设置 ---
    private final Setting<SettingColor> othersColor = sgOthers.add(new ColorSetting.Builder()
        .name("正文颜色")
        .defaultValue(new SettingColor(255, 170, 0))
        .visible(highlightOthers::get)
        .build());
    
    private final Setting<Boolean> othersBold = sgOthers.add(new BoolSetting.Builder()
        .name("正文-粗体").defaultValue(false).visible(highlightOthers::get).build());
    private final Setting<Boolean> othersItalic = sgOthers.add(new BoolSetting.Builder()
        .name("正文-斜体").defaultValue(false).visible(highlightOthers::get).build());
    private final Setting<Boolean> othersUnderline = sgOthers.add(new BoolSetting.Builder()
        .name("正文-下划线").defaultValue(false).visible(highlightOthers::get).build());
    private final Setting<Boolean> othersStrikethrough = sgOthers.add(new BoolSetting.Builder()
        .name("正文-删除线").defaultValue(false).visible(highlightOthers::get).build());


    // =============================================================

    public ChatHighlight() {
        super(AddonTemplate.CATEGORY, "chat-highlight", "自定义高亮显示玩家的聊天信息，支持无限名单和路人前缀。");
    }

    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        if (mc.player == null || mc.world == null) return;

        String textContent = event.getMessage().getString();
        String myName = mc.player.getName().getString();

        // ====================================================
        // 1. 判断自己 (修复版逻辑)
        // ====================================================
        if (highlightSelf.get()) {
            boolean isSelfMessage = false;

            // 首先，字符串里必须得有我的名字
            if (isWordPresent(textContent, myName)) {
                if (onlySender.get()) {
                    // --- 判定是否为发送者 ---
                    // 1. 找到名字出现的位置
                    int index = textContent.indexOf(myName);
                    
                    // 2. 只有当名字出现在前25个字符内（允许有 [Admin] 这种前缀）
                    //    并且名字前面没有 '@' 符号
                    if (index >= 0 && index < 25) {
                         String prefixStr = textContent.substring(0, index);
                         if (!prefixStr.contains("@")) {
                             isSelfMessage = true;
                         }
                    }
                } else {
                    // 宽松模式：只要包含名字就算
                    isSelfMessage = true;
                }
            }

            if (isSelfMessage) {
                modifyMessage(event, selfColor.get(), 
                    selfBold.get(), selfItalic.get(), selfUnderline.get(), selfStrikethrough.get());
                return;
            }
        }

        // ====================================================
        // 2. 判断特定玩家 (严格匹配)
        // ====================================================
        for (Map.Entry<String, SettingColor> entry : specificPlayers.entrySet()) {
            String targetName = entry.getKey();
            if (isWordPresent(textContent, targetName)) {
                modifyMessage(event, entry.getValue(), 
                    specificBold.get(), specificItalic.get(), specificUnderline.get(), specificStrikethrough.get());
                return;
            }
        }

        // ====================================================
        // 3. 判断路人 (严格匹配)
        // ====================================================
        if (highlightOthers.get() && isFromPlayer(textContent)) {
            addPrefixAndModify(event);
        }
    }

    // === 核心逻辑：检测单词是否存在 (支持严格模式) ===
    private boolean isWordPresent(String text, String name) {
        if (name == null || name.isEmpty()) return false;
        
        if (strictMode.get()) {
            // 使用正则 \b 来匹配完整的单词边界
            // (?i) 表示忽略大小写
            // Pattern.quote 确保名字里的特殊字符不会破坏正则
            String regex = "(?i).*\\b" + Pattern.quote(name) + "\\b.*";
            return text.matches(regex);
        } else {
            // 旧模式：简单的包含
            return text.contains(name);
        }
    }

    // 判断消息来源是否为其他玩家
    private boolean isFromPlayer(String message) {
        Collection<PlayerListEntry> playerList = mc.getNetworkHandler().getPlayerList();
        for (PlayerListEntry entry : playerList) {
            String pName = entry.getProfile().getName();
            // 排除自己
            if (pName.equals(mc.player.getName().getString())) continue;
            
            // 使用严格匹配，防止匹配到名字相似的人
            if (isWordPresent(message, pName)) {
                return true;
            }
        }
        return false;
    }

    // 修改消息样式 (用于自己和名单玩家)
    private void modifyMessage(ReceiveMessageEvent event, SettingColor color, boolean bold, boolean italic, boolean underline, boolean strikethrough) {
        MutableText newMessage = event.getMessage().copy();
        Style style = newMessage.getStyle()
                .withColor(TextColor.fromRgb(color.getPacked()))
                .withBold(bold)
                .withItalic(italic)
                .withUnderline(underline)
                .withStrikethrough(strikethrough);
        
        newMessage.setStyle(style);
        event.setMessage(newMessage);
    }

    // 添加前缀并修改样式 (用于路人)
    private void addPrefixAndModify(ReceiveMessageEvent event) {
        MutableText body = event.getMessage().copy();
        Style bodyStyle = body.getStyle()
                .withColor(TextColor.fromRgb(othersColor.get().getPacked()))
                .withBold(othersBold.get())
                .withItalic(othersItalic.get())
                .withUnderline(othersUnderline.get())
                .withStrikethrough(othersStrikethrough.get());
        body.setStyle(bodyStyle);

        String pText = othersPrefix.get();
        if (pText != null && !pText.isEmpty()) {
            MutableText prefix = Text.literal(pText);
            // 前缀的样式
            Style prefixStyle = Style.EMPTY
                    .withColor(TextColor.fromRgb(othersPrefixColor.get().getPacked()))
                    .withBold(prefixBold.get());
            prefix.setStyle(prefixStyle);

            prefix.append(body);
            event.setMessage(prefix);
        } else {
            event.setMessage(body);
        }
    }

    // ================== 管理器操作逻辑 ==================

    private void addEntry() {
        String name = inputName.get().trim();
        if (name.isEmpty()) {
            ChatUtils.error("名字不能为空！");
            btnAdd.set(false); 
            return;
        }

        // 复制一份颜色，防止引用问题
        SettingColor color = new SettingColor(inputColor.get());
        specificPlayers.put(name, color);

        ChatUtils.info("已添加/更新特定玩家: " + name);
        btnAdd.set(false); // 按钮回弹
    }

    private void removeEntry() {
        String name = inputName.get().trim();
        if (specificPlayers.containsKey(name)) {
            specificPlayers.remove(name);
            ChatUtils.info("已移除玩家: " + name);
        } else {
            ChatUtils.error("列表中找不到玩家: " + name);
        }
        btnRemove.set(false); // 按钮回弹
    }

    private void printList() {
        if (specificPlayers.isEmpty()) {
            ChatUtils.info("特定玩家列表为空。");
        } else {
            ChatUtils.info("=== 特定高亮名单 (" + specificPlayers.size() + ") ===");
            for (Map.Entry<String, SettingColor> entry : specificPlayers.entrySet()) {
                // 使用该颜色的文字打印名字
                MutableText nameText = Text.literal(" - " + entry.getKey());
                nameText.setStyle(Style.EMPTY.withColor(TextColor.fromRgb(entry.getValue().getPacked())));
                mc.inGameHud.getChatHud().addMessage(nameText);
            }
        }
        btnPrint.set(false); // 按钮回弹
    }

    // ================== NBT 保存与读取 (防止重启失效) ==================

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = super.toTag();
        NbtList list = new NbtList();

        for (Map.Entry<String, SettingColor> entry : specificPlayers.entrySet()) {
            NbtCompound entryTag = new NbtCompound();
            entryTag.putString("name", entry.getKey());
            
            // 保存颜色 (R, G, B, A)
            SettingColor c = entry.getValue();
            entryTag.putInt("r", c.r);
            entryTag.putInt("g", c.g);
            entryTag.putInt("b", c.b);
            entryTag.putInt("a", c.a);
            
            list.add(entryTag);
        }

        tag.put("specificPlayers", list);
        return tag;
    }

    @Override
    public Module fromTag(NbtCompound tag) {
        super.fromTag(tag); // 先读取常规设置

        if (tag.contains("specificPlayers")) {
            specificPlayers.clear(); // 清空旧数据
            NbtList list = tag.getList("specificPlayers", NbtElement.COMPOUND_TYPE);
            
            for (NbtElement element : list) {
                NbtCompound entryTag = (NbtCompound) element;
                String name = entryTag.getString("name");
                int r = entryTag.getInt("r");
                int g = entryTag.getInt("g");
                int b = entryTag.getInt("b");
                int a = entryTag.getInt("a");
                
                specificPlayers.put(name, new SettingColor(r, g, b, a));
            }
        }
        return this;
    }
}