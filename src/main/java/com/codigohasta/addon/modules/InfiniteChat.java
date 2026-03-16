package com.codigohasta.addon.modules;

import net.minecraft.util.math.Vec3d;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;

import java.lang.reflect.Field;

public class InfiniteChat extends Module {
    
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> infiniteChatBox = sgGeneral.add(new BoolSetting.Builder()
        .name("无限聊天框")
        .description("解除聊天栏256个字符限制 (暴力模式)。")
        .defaultValue(true)
        .build()
    );

    private boolean isHooked = false;

    public InfiniteChat() {
        super(AddonTemplate.CATEGORY, "无限聊天", "解除聊天栏输入限制，允许发送超长指令。");
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (!infiniteChatBox.get()) return;
        if (event.screen instanceof ChatScreen) {
            // 打开聊天框时尝试解锁
            unlockChatLength((ChatScreen) event.screen);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!infiniteChatBox.get()) return;

        if (mc.currentScreen instanceof ChatScreen) {
            // 每帧都尝试解锁，防止被游戏重置，或者第一次反射失败
            // 为了性能，我们只在 isHooked 为 false 时执行繁重的反射
            // 但为了确保万无一失，这里我们采用简单的“暴力”逻辑
            unlockChatLength((ChatScreen) mc.currentScreen);
        } else {
            isHooked = false;
        }
    }

    /**
     * 暴力反射解锁逻辑
     */
    private void unlockChatLength(ChatScreen screen) {
        if (screen == null) return;

        try {
            // 获取 ChatScreen 类的所有字段（不管它是 private 还是 protected）
            Field[] fields = ChatScreen.class.getDeclaredFields();

            for (Field field : fields) {
                try {
                    // 强制允许访问私有字段
                    field.setAccessible(true);

                    // 获取字段在这个 screen 实例中的具体值
                    Object value = field.get(screen);

                    // 核心逻辑：我们不通过字段名判断，而是通过“值的类型”判断
                    // 只要这个字段的值是一个 TextFieldWidget (输入框)，我们就修改它
                    if (value instanceof TextFieldWidget) {
                        TextFieldWidget inputField = (TextFieldWidget) value;
                        
                        // 只有当长度还被限制在 256 时才修改，避免重复操作 (虽然重复设置也没坏处)
                        // 这里直接强制设为 32767
                        inputField.setMaxLength(32767);
                        
                        // 标记已成功找到并修改
                        isHooked = true;
                        
                        // 找到后通常只有一个输入框，可以直接 break
                        // 但为了保险（万一以后有两个输入框），可以不 break
                        // break; 
                    }
                } catch (Exception ignored) {
                    // 忽略单个字段获取失败的错误，继续找下一个
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}