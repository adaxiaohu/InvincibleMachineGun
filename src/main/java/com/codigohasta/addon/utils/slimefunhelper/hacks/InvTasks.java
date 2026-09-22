package com.codigohasta.addon.utils.slimefunhelper.hacks;
import com.codigohasta.addon.utils.slimefunhelper.SlimefunRuntime;
import com.codigohasta.addon.utils.slimefunhelper.accessors.access.ClientPlayerAccess;
import com.codigohasta.addon.utils.slimefunhelper.events.Event;
import com.codigohasta.addon.utils.slimefunhelper.events.Listener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.screen.ScreenHandler;
public final class InvTasks {
    public static final ThreadLocal<Boolean> SUPPRESS_DROPITEM_SPAWN=ThreadLocal.withInitial(()->false);
    public static net.minecraft.client.gui.screen.Screen getCurrentServerScreen(net.minecraft.entity.player.PlayerEntity player) {
        if (player == null) return null;
        var screen = player instanceof ClientPlayerAccess access ? access.getServerOpeningScreen() : mc.currentScreen;
        return screen instanceof net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen ? null : screen;
    }
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static com.codigohasta.addon.utils.slimefunhelper.utils.tasks.LimitedSpeedExecutor clickExecutor;
    public static com.codigohasta.addon.utils.slimefunhelper.utils.tasks.LimitedSpeedExecutor getClickExecutor() {
        if (clickExecutor == null) clickExecutor = new com.codigohasta.addon.utils.slimefunhelper.utils.tasks.LimitedSpeedExecutor(
            com.codigohasta.addon.modules.slimefunhelper.InvExtra.INSTANCE.inventoryClickLimit);
        return clickExecutor;
    }
    public static void clearClickQueue() { if (clickExecutor != null) clickExecutor.clear(); }
    public static void initialize() {
        Listener.getPreGameTick().add(event -> {
            if (SlimefunRuntime.modifying()) getClickExecutor().reset();
            else clearClickQueue();
        }, 0);
        Listener.getWorldSwitchPoint().add(event -> clearClickQueue(), 0);
        Listener.getServerLeavePoint().add(event -> clearClickQueue(), 0);
        Listener.getPacketPoint().getChannel(ScreenHandlerSlotUpdateS2CPacket.class).add(event -> {
            if (SlimefunRuntime.modifying()) fastAsyncUpdateRevision(event);
        }, 0);
        Listener.getPacketPoint().getChannel(InventoryS2CPacket.class).add(event -> {
            if (SlimefunRuntime.modifying()) fastAsyncUpdateRevision2(event);
        }, 0);
    }
    // Copied from source InvTasks: update revisions at receipt, before main-thread inventory application.
    public static int playerInventoryRevisionManage = 0;

    public static void syncPlayerInventoryRevision(int revision) {
        if (playerInventoryRevisionManage < 0) {
            playerInventoryRevisionManage = revision;
        } else {
            int abs = Math.abs(playerInventoryRevisionManage - revision);
            if (abs > 20) {
                playerInventoryRevisionManage = revision;
            } else {
                if (playerInventoryRevisionManage < revision) {
                    playerInventoryRevisionManage = revision;
                }
            }
        }
    }

    public static void fastAsyncUpdateRevision(Event<ScreenHandlerSlotUpdateS2CPacket> eventUpdate) {
        if (mc.player == null || mc.world == null) return;
        int syncId = eventUpdate.context.getSyncId();
        if (mc.interactionManager.getCurrentGameMode().isSurvivalLike()) {
            if (syncId == 0) {
                syncPlayerInventoryRevision(eventUpdate.context.getRevision());
            } else {
                ScreenHandler handler = ClientPlayerAccess.of(mc.player).getServerScreenHandler();
                if (handler.syncId == syncId) {
                    handler.revision = eventUpdate.context.getRevision();
                }
            }
        }
    }

    public static void fastAsyncUpdateRevision2(Event<InventoryS2CPacket> eventUpdate) {
        if (mc.player == null) return;
        int syncId = eventUpdate.context.syncId();
        if (mc.interactionManager.getCurrentGameMode().isSurvivalLike()) {
            if (syncId == 0) {
                syncPlayerInventoryRevision(eventUpdate.context.revision());
            } else {
                ScreenHandler handler = ClientPlayerAccess.of(mc.player).getServerScreenHandler();
                if (handler.syncId == syncId) {
                    handler.revision = eventUpdate.context.revision();
                }
            }
        }
    }
        private static final int[] INVENTORY_INDEX_TO_SCREEN_SLOT = new int[] {
        36, 37, 38, 39, 40, 41, 42, 43, 44, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27,
        28, 29, 30, 31, 32, 33, 34, 35, 8, 7, 6, 5, 45, 0, 1, 2, 3, 4
    };
    private static final int[] SCREEN_SLOT_TO_INVENTORY_INDEX = new int[46];

    static {
        for (var i = 0; i < INVENTORY_INDEX_TO_SCREEN_SLOT.length; ++i) {
            SCREEN_SLOT_TO_INVENTORY_INDEX[INVENTORY_INDEX_TO_SCREEN_SLOT[i]] = i;
        }
    }

public static int getScreenSlotByInventoryIndex(int v) {
        return INVENTORY_INDEX_TO_SCREEN_SLOT[v];
    }
public static int getInventoryIndexByScreenSlot(int v) {
        return SCREEN_SLOT_TO_INVENTORY_INDEX[v];
    }
}
