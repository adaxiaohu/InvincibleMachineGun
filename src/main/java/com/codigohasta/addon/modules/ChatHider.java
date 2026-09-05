package com.codigohasta.addon.modules;

import net.minecraft.world.phys.Vec3;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.ChatScreen;

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
        originalOpacity = mc.options.chatOpacity().get();
    }

    @Override
    public void onDeactivate() {
        // 模块关闭时，恢复原来的透明度
        // 修复：如果 originalOpacity 为 0.0，说明是上一次运行时模块将 options 文件写成了 0.0，
        // 此时应使用 openOpacity 的值（默认 1.0）来恢复，否则聊天框仍然不可见。
        mc.options.chatOpacity().set(originalOpacity > 0.0 ? originalOpacity : openOpacity.get());
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;
        // 防止在关闭模块后，该 handler 仍在本 tick 或下个 tick 再次执行，覆盖 onDeactivate 恢复的原始透明度
        if (!isActive()) return;

        // 核心逻辑：
        // 检查当前屏幕是不是聊天屏幕 (ChatScreen)
        if (mc.screen instanceof ChatScreen) {
            // 如果正在打字，设置为用户设定的透明度 (默认1.0)
            mc.options.chatOpacity().set(openOpacity.get());
        } else {
            // 如果没在打字，直接把透明度设为 0 (隐藏)
            mc.options.chatOpacity().set(0.0);
        }
    }
}