package com.codigohasta.addon.utils.slimefunhelper.chatcraft;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import com.codigohasta.addon.utils.slimefunhelper.hacks.InvTasks;
public final class CreativeGive {
    private static final MinecraftClient mc=MinecraftClient.getInstance();
    public static void give(ItemStack itemStack, int count) {
        if (mc.player != null
                && mc.interactionManager != null
                && mc.interactionManager.getCurrentGameMode().isCreative()) {
            PlayerScreenHandler inventoryView = mc.player.playerScreenHandler;
            int stackMax = itemStack.getMaxCount();
            for (int inventoryIndex = 0; inventoryIndex < 46; inventoryIndex++) {
                int i = InvTasks.getScreenSlotByInventoryIndex(inventoryIndex);
                Slot slot0 = inventoryView.getSlot(i);
                int transfer = 0;
                ItemStack stackToSet = null;
                if (slot0.getStack().isEmpty()) {
                    transfer = Math.min(stackMax, count);
                    stackToSet = itemStack.copyWithCount(transfer);
                } else if (slot0.getStack().getCount() < stackMax
                        && ItemStack.areItemsAndComponentsEqual(slot0.getStack(), itemStack)) {
                    transfer = Math.min(stackMax - slot0.getStack().getCount(), count);
                    stackToSet = itemStack.copyWithCount(slot0.getStack().getCount() + transfer);
                }
                count -= transfer;
                if (stackToSet != null) {
                    mc.interactionManager.clickCreativeStack(stackToSet, i);
                    slot0.setStack(stackToSet);
                }
                if (count <= 0) return;
            }
            ItemStack sample = itemStack.copy();
            while (count > 0) {
                int transfer = Math.min(stackMax, count);
                count -= transfer;
                sample.setCount(transfer);
                mc.interactionManager.dropCreativeStack(sample);
            }
        }
    }

}
