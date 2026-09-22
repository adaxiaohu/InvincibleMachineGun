package com.codigohasta.addon.modules.slimefunhelper;

import java.util.List;
import com.codigohasta.addon.utils.slimefunhelper.events.Event;
import com.codigohasta.addon.utils.slimefunhelper.events.Listener;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.BaseModule;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.ModulePath;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.config.StringFormat;
import com.codigohasta.addon.utils.slimefunhelper.managers.Configs;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.FlagRef;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.NBTRef;
import com.codigohasta.addon.utils.slimefunhelper.utils.ChatUtils;
import com.codigohasta.addon.utils.slimefunhelper.utils.Debug;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;

public class SetBackLog extends BaseModule {
    public final ModulePath moveSafety = makePath(Configs.MOV_CONFIG, "move-safety");

    public SetBackLog() {
        super("SetBackLog");
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(moveSafety.add("setback-log-enable")).build();

    public final FlagRef logResync =
            flagBuilder(moveSafety.add("log-resync-packets")).build();

    public final NBTRef<StringFormat> logResyncFormat = builder(moveSafety.add("log-resync-format"), StringFormat.class)
            .defaultValue(new StringFormat(List.of("position"), "&fPos Resync {position}", true))
            .build();

    public final FlagRef logAc =
            flagBuilder(moveSafety.add("check-setback-packets")).build();

    public final NBTRef<StringFormat> logAcFormat = builder(moveSafety.add("log-ac-format"), StringFormat.class)
            .defaultValue(new StringFormat(
                    List.of("teleportId", "position"), "&c[AC] 反作弊回弹! tp号:{teleportId}, 位置: {position}", true))
            .build();


    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPacketPoint().getChannel(PlayerPositionLookS2CPacket.class), this::onSetBack);
    }

    public int maxTpId = -1;
    public Vec3d lastDesyncPos = Vec3d.ZERO;

    public void resetRuntimeState() { maxTpId = -1; lastDesyncPos = Vec3d.ZERO; }

    @Override public void onDisableModule() { resetRuntimeState(); }

    public void onSetBack(Event<PlayerPositionLookS2CPacket> event) {
        PlayerPositionLookS2CPacket packet0 = event.context;
        int tpId = packet0.teleportId();
        maxTpId = Math.max(maxTpId, tpId);
        if (mc.player != null) {
            lastDesyncPos = mc.player.getEntityPos();
        }
        var packet = packet0.change().position();

        if (logResync.get()) {
            StringFormat logFormat = logResyncFormat.get();
            Debug.chat(logFormat.formatText(
                    ChatUtils.getDisplayedLocationDouble(packet)));
        }
        if (logAc.get()) {

            if (tpId < 0) {
                if (mc.player != null) {
                    StringFormat logFormat = logAcFormat.get();
                    try {
                        Debug.chat(logFormat.formatText(
                                tpId,
                                ChatUtils.getDisplayedLocationDouble(packet)));
                    } catch (Throwable e) {
                        Debug.chat(ChatUtils.stringToText("&cInvalid format string: " + e.getMessage()));
                    }
                }
            }
        }
    }
}
