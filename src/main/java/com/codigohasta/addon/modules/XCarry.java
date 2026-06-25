package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * XCarry - 合成栏储物
 *
 * 原理：拦截 CloseHandledScreenC2SPacket（syncId == 0 即玩家背包），
 * 让服务器认为背包未关闭，从而不会弹出合成栏中的物品，
 * 实现白嫖 4 格额外储存空间。
 */
public class XCarry extends Module {
    public XCarry() {
        super(AddonTemplate.CATEGORY, "合成栏携带", "在背包合成栏中存放物品，关闭背包时物品不会掉落（拦截syncId=0的关窗包）。");
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (event.packet instanceof CloseHandledScreenC2SPacket packet) {
            if (packet.getSyncId() == 0) {
                event.cancel();
            }
        }
    }
}
