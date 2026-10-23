package com.codigohasta.addon.utils.leaveshack;

import com.codigohasta.addon.modules.GlobalSetting;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class EntityUtil {
    public static void attackSwingHand() {
        var swing = GlobalSetting.on() ? GlobalSetting.INSTANCE.attackSwing.get() : GlobalSetting.SwingMode.Both;
        if (swing == GlobalSetting.SwingMode.None) return;
        Hand hand = !GlobalSetting.on() || GlobalSetting.INSTANCE.handMode.get() == GlobalSetting.HandMode.MainHand
                ? Hand.MAIN_HAND : Hand.OFF_HAND;
        if (swing != GlobalSetting.SwingMode.Packet) mc.player.swingHand(hand);
        if (swing != GlobalSetting.SwingMode.Client) mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
    }
    public static void placeSwingHand() {
        var swing = GlobalSetting.on() ? GlobalSetting.INSTANCE.placeSwing.get() : GlobalSetting.SwingMode.Both;
        if (swing == GlobalSetting.SwingMode.None) return;
        Hand hand = !GlobalSetting.on() || GlobalSetting.INSTANCE.handMode.get() == GlobalSetting.HandMode.MainHand
                ? Hand.MAIN_HAND : Hand.OFF_HAND;
        if (swing != GlobalSetting.SwingMode.Packet) mc.player.swingHand(hand);
        if (swing != GlobalSetting.SwingMode.Client) mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
    }
}
