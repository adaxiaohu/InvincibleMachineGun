package com.codigohasta.addon.utils.slimefunhelper;

import com.codigohasta.addon.modules.slimefunhelper.MovExtra;
import com.codigohasta.addon.utils.slimefunhelper.accessors.access.ClientPlayerAccess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.slot.SlotActionType;

/** Completes SF ghost-hand swaps before returning to placement, combat or movement.
 * Grim 65815fc treats CLICK_WINDOW as opening the inventory even without a client GUI. */
public final class AutomaticInventoryActions {
    private AutomaticInventoryActions() {}

    public static void clickSlot(int syncId, int slotId, int button, SlotActionType type, PlayerEntity player) {
        var mc = MinecraftClient.getInstance();
        mc.interactionManager.clickSlot(syncId, slotId, button, type, player);

        if (type != SlotActionType.SWAP || !SlimefunRuntime.modifying()
            || player != mc.player || !MovExtra.INSTANCE.fuckGrimAC.get()
            || mc.currentScreen instanceof HandledScreen<?>) return;

        var access = ClientPlayerAccess.of(mc.player);
        var handler = access.getServerScreenHandler();
        if (access.getKeepedInvHandler() != null || handler != mc.player.playerScreenHandler
            || handler.syncId != syncId || !handler.getCursorStack().isEmpty()) return;

        // Closing after input has resumed would itself trigger MultiActionsD.
        MovExtra.INSTANCE.sendPacketsForInventoryAction();
        mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(syncId));
    }
}
