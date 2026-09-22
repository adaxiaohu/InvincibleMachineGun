package com.codigohasta.addon.modules.slimefunhelper;

import java.util.Objects;
import com.codigohasta.addon.utils.slimefunhelper.events.Event;
import com.codigohasta.addon.utils.slimefunhelper.events.Listener;
import com.codigohasta.addon.utils.slimefunhelper.events.PacketManager;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.BaseModule;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.ModulePath;
import com.codigohasta.addon.utils.slimefunhelper.managers.Configs;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.*;
import com.codigohasta.addon.utils.slimefunhelper.hooks.ViaFabricPlusHooks;
import com.codigohasta.addon.utils.slimefunhelper.utils.InventoryUtils;
import com.codigohasta.addon.utils.slimefunhelper.utils.NetworkUtils;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

/** Independent UI for the source DisablerManager placement checks and queue flushes. */
public class MultiPlaceDisabler extends BaseModule {
    public static MultiPlaceDisabler INSTANCE;
    public final ModulePath root = makePath(Configs.EXTRA_CONFIG, "multi-place-disabler");
    public final FlagRef enable = flagBuilder(root.addEnable()).build();
    public final EnumRef<DisablerManager.SupportAC> currentAC = builder(root.add("current-ac"), DisablerManager.SupportAC.class)
            .defaultValue(DisablerManager.SupportAC.NONE).build();
    public final FlagRef grimMultiplace = flagBuilder(root.add("grim-multi-place")).defaultValue(true).build();
    public final FlagRef autoFlushPlaceQueue = flagBuilder(root.add("auto-flush-multi-place-queue")).defaultValue(true).build();
    public final FlagRef autoFlushPlaceBreakQueue = flagBuilder(root.add("auto-flush-place-break-queue")).defaultValue(true).build();
    private Direction lastDirection;
    private Vec3d lastCursor;
    private BlockPos lastPos;
    private boolean hasPlaceThisTick;
    private boolean hasAnyPlaceActionGrimQueue;
    private long generation;

    public MultiPlaceDisabler() {
        super("MultiPlaceDisabler");
        INSTANCE = this;
        bindFlag(enable);
    }
    public static boolean active() { return INSTANCE != null && INSTANCE.isActive(); }
    @Override public void registerAll() {
        registerListener(Listener.getPacketPoint().getChannel(PlayerInteractBlockC2SPacket.class), this::onPlace, Integer.MAX_VALUE - 1);
        registerListener(Listener.getPacketPoint().getChannel(PlayerActionC2SPacket.class), this::onBreakAction, Integer.MAX_VALUE - 1);
        registerListener(Listener.getPacketPoint().getChannel(PlayerMoveC2SPacket.class), this::onFlying);
        registerListener(Listener.getPacketPoint().getChannel(CommonPongC2SPacket.class), this::onPingPong);
        registerListener(Listener.getPostTick(), this::onTick);
        Listener.getWorldSwitchPoint().add(event -> onDisableModule(), 0);
    }
    @Override public void onEnableModule() { onDisableModule(); }
    @Override public void onDisableModule() {
        generation++;
        hasPlaceThisTick = false;
        hasAnyPlaceActionGrimQueue = false;
        lastDirection = null;
        lastCursor = null;
        lastPos = null;
        if (DisablerManager.INSTANCE != null) DisablerManager.INSTANCE.resetPlacementState();
    }
    public boolean isGrimSelfCheckDisabled() {
        return DisablerManager.INSTANCE != null && DisablerManager.INSTANCE.isActive()
                && DisablerManager.INSTANCE.isGrimSelfCheckDisabled();
    }

    public boolean isMultiPlaceCheckDisabled() {
        if (enable.get()) {
            return switch (currentAC.get()) {
                case GRIM -> isGrimMultiPlaceDisabled();
                case MATRIX -> false;
                default -> true;
            };
        }
        return false;
    }

    public boolean isMultiRotPlaceCheckDisabled(boolean methodCanMultiRot) {
        return isMultiPlaceCheckDisabled() && (methodCanMultiRot || isRotationPlaceCheckDisabled());
    }

    public boolean isRotationPlaceCheckDisabled() {
        if (enable.get()) {
            return switch (currentAC.get()) {
                case GRIM -> isGrimSelfCheckDisabled();
                case MATRIX -> false;
                default -> true;
            };
        }
        return false;
    }

    public boolean isGrimMultiPlaceDisabled() {
        return enable.get() && currentAC.get() == DisablerManager.SupportAC.GRIM && (grimMultiplace.get());
    }

    public boolean flushACPlaceQueue() {
        if (autoFlushPlaceQueue.get()) {
            return flushACPlaceQueue0();
        }
        return false;
    }

    private boolean flushACPlaceQueue0() {
        switch (currentAC.get()) {
            case GRIM -> {
                // flush ghost blocks
                // see GrimAC handleQueuedPlaces()
                if (hasAnyPlaceActionGrimQueue) {
                    if (ViaFabricPlusHooks.isSupportDupRot()) {
                        LegacySnapRotManager.INSTANCE.snapAt(mc.player.getPitch(), mc.player.getYaw(), true);
                    } else {
                        int selected = InventoryUtils.getSelectedSlot();
                        int next = selected == 8 ? 7 : 8;
                        Listener.sendPacketNoEvents(new UpdateSelectedSlotC2SPacket(next));
                        Listener.sendPacketNoEvents(new UpdateSelectedSlotC2SPacket(selected));
                    }
                }
                hasAnyPlaceActionGrimQueue = false;
                return true;
            }
        }
        return false;
    }

    public boolean flushACPlaceBreakQueue() {
        if (autoFlushPlaceBreakQueue.get()) {
            return flushACPlaceQueue0();
        }
        return false;
    }

    public void onPlace(Event<PlayerInteractBlockC2SPacket> blockPlace) {
        if (blockPlace.isCancelled()) return;
        boolean grimSelfCheckDisabler = isGrimSelfCheckDisabled();
        long queuedGeneration = generation;
        BlockHitResult hitResult = blockPlace.context.getBlockHitResult();
        Direction direction = hitResult.getSide();
        Vec3d cursor = hitResult.getPos();
        BlockPos blockPos = hitResult.getBlockPos();
        if (enable.get() && hasAnyPlaceActionGrimQueue && autoFlushPlaceQueue.get()) {
            flushACPlaceQueue0();
        }
        hasAnyPlaceActionGrimQueue = true;

        if (grimSelfCheckDisabler) {
            hasPlaceThisTick = false;
        }
        if (hasPlaceThisTick && enable.get() && currentAC.get() == DisablerManager.SupportAC.GRIM && grimMultiplace.get()) {
            if (direction != lastDirection
                    || !Objects.equals(cursor, lastCursor)
                    || !Objects.equals(blockPos, lastPos)) {
                PlayerInteractBlockC2SPacket pkt = blockPlace.context;
                PacketManager.schedulePostCallback(pkt, () -> {
                    if (!isActive() || queuedGeneration != generation) return;
                    Listener.sendPacketNoEvents(new PlayerInteractBlockC2SPacket(
                            pkt.getHand(), pkt.getBlockHitResult(), NetworkUtils.generateNextSequence()));
                });
            }
        }
        lastDirection = direction;
        lastCursor = cursor;
        lastPos = blockPos;
    }

    public void onBreakAction(Event<PlayerActionC2SPacket> eventBreak) {
        if (eventBreak.isCancelled()) return;
        switch (eventBreak.context.getAction()) {
            case START_DESTROY_BLOCK, STOP_DESTROY_BLOCK -> {}
            default -> {
                return;
            }
        }
        if (enable.get() && hasAnyPlaceActionGrimQueue && autoFlushPlaceBreakQueue.get()) {
            flushACPlaceQueue0();
        }
        hasAnyPlaceActionGrimQueue = false;
    }

    public void onFlying(Event<PlayerMoveC2SPacket> playerMoveC2SPacket) {
        hasAnyPlaceActionGrimQueue = false;
    }

    public void onPingPong(Event<CommonPongC2SPacket> eventTransaction) {
        int id = eventTransaction.context.getParameter();
        if (id == (short) id) {
            // grimTransaction
            hasAnyPlaceActionGrimQueue = false;
        }
    }

    public void onTick(Event<Void> event) {
        hasPlaceThisTick = false;
    }
}
