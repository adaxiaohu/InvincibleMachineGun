package com.codigohasta.addon.modules;

import net.minecraft.util.math.Vec3d;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ChatScreen;

public class ChatHider extends Module {
    
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // 添加一个选项，允许用户自定义打开聊天时的透明度
    private final Setting<Double> openOpacity = sgGeneral.add(new DoubleSetting.Builder()
        .name("显示时透明度")
        .description("当你打开聊天框时，聊天文字的透明度 (0-1)。")
        .defaultValue(1.0)
        .min(0.1)
        .max(1.0)
        .sliderMax(1.0)
        .build()
    );

    // 用来记录玩家开启模块之前的原始设置，防止关闭模块后聊天框不见了
    private double originalOpacity;

    public ChatHider() {
        super(AddonTemplate.CATEGORY, "聊天隐藏者", "平时隐藏聊天框，只有在按T打字时才显示。");
    }

    @Override
    public void onActivate() {
        // 模块开启时，备份玩家当前的聊天透明度设置
        originalOpacity = mc.options.getChatOpacity().getValue();
    }

    @Override
    public void onDeactivate() {
        // 模块关闭时，恢复原来的透明度
        mc.options.getChatOpacity().setValue(originalOpacity);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        // 核心逻辑：
        // 检查当前屏幕是不是聊天屏幕 (ChatScreen)
        if (mc.currentScreen instanceof ChatScreen) {
            // 如果正在打字，设置为用户设定的透明度 (默认1.0)
            mc.options.getChatOpacity().setValue(openOpacity.get());
        } else {
            // 如果没在打字，直接把透明度设为 0 (隐藏)
            mc.options.getChatOpacity().setValue(0.0);
        }
    }
}