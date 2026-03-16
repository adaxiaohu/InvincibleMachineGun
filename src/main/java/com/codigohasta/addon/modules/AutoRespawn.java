package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;

public class AutoRespawn extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> showMessage = sgGeneral.add(new BoolSetting.Builder()
        .name("show-message")
        .description("在复活时发送反馈。这个消息只有你能看到。")
        .defaultValue(true)
        .build()
    );

    public AutoRespawn() {
        super(AddonTemplate.CATEGORY, "c自动复活", "当你死亡时自动复活。");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // 确保玩家实例存在
        if (mc.player == null) {
            return;
        }

        // 检查玩家是否死亡 (getHealth() <= 0 也可以)
        if (mc.player.isDead()) {
            // 向服务器发送重生请求
            mc.player.networkHandler.sendPacket(new ClientStatusC2SPacket(ClientStatusC2SPacket.Mode.PERFORM_RESPAWN));

            // 如果设置了，则在聊天框中显示一条消息
            if (showMessage.get()) {
                info("已复活。");
            }
        }
    }
}