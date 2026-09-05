package com.codigohasta.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;

import java.util.ArrayList;
import java.util.List;

import com.codigohasta.addon.AddonTemplate;


public class PortalGodMode extends Module {
    // 缓存被拦截的 TeleportConfirm 包
    private final List<ServerboundAcceptTeleportationPacket> packets = new ArrayList<>();

    public PortalGodMode() {
        super(AddonTemplate.CATEGORY, "没敌", "穿越地狱门后进入没敌状态，不会受到任何物理伤害，但是无法靠走的移动，可以用没影珍珠，紫颂果进行移动。");
    }

    @Override
    public void onActivate() {
        packets.clear(); // 激活时清空缓存
    }

    @Override
    public void onDeactivate() {
        // 安全发送最后一个被拦截的包，完成传送确认流程
        if (mc.getConnection() != null && !packets.isEmpty()) {
            mc.getConnection().send(packets.get(packets.size() - 1));
        }
        packets.clear();
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        // 只拦截 ServerboundAcceptTeleportationPacket
        if (event.packet instanceof ServerboundAcceptTeleportationPacket packet) {
            packets.add(packet);
            event.cancel(); // 阻止该包发送到服务器
        }
    }
}