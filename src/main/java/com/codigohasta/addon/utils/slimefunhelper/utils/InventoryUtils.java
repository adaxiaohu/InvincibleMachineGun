package com.codigohasta.addon.utils.slimefunhelper.utils;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import com.codigohasta.addon.utils.slimefunhelper.accessors.access.ClientPlayerAccess;
import com.codigohasta.addon.utils.slimefunhelper.hacks.InvTasks;
import com.codigohasta.addon.utils.slimefunhelper.utils.collections.IndexEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Hand;
public final class InventoryUtils {
private static final net.minecraft.client.MinecraftClient mc=net.minecraft.client.MinecraftClient.getInstance();
    public static int getSelectedSlot() {
        return mc.player.getInventory().getSelectedSlot();
    }
    public static IndexEntry<Slot> findBestScreenSlot(
            List<Slot> slots, Function<Slot, Double> maxFunction, boolean acceptEmpty) {
        IndexEntry<Slot> result = null;
        Double maxVal = null;
        for (var i = 0; i < slots.size(); ++i) {
            var slot = slots.get(i);
            ItemStack stack = slot.getStack();
            if (!acceptEmpty && stack.isEmpty()) continue;
            Double val = maxFunction.apply(slot);
            if (val != null) {
                if (maxVal == null || maxVal < val) {
                    maxVal = val;
                    result = new IndexEntry<>(i, slot);
                }
            }
        }
        return result;
    }
    public static IndexEntry<ItemStack> findPlayerItem(
            Predicate<ItemStack> predicate, boolean doNotFSearchWhenOpenOtherScreen, boolean acceptEmpty) {
        return findPlayerItem(predicate, doNotFSearchWhenOpenOtherScreen, acceptEmpty, true);
    }
    public static IndexEntry<ItemStack> findPlayerItem(
            Predicate<ItemStack> predicate,
            boolean doNotFSearchWhenOpenOtherScreen,
            boolean acceptEmpty,
            boolean handPriority) {
        return findPlayerItem(predicate, doNotFSearchWhenOpenOtherScreen, acceptEmpty, handPriority, false);
    }
    public static IndexEntry<ItemStack> findPlayerItem(
            Predicate<ItemStack> predicate,
            boolean doNotFSearchWhenOpenOtherScreen,
            boolean acceptEmpty,
            boolean handPriority,
            boolean offHandPriority) {
        return findPlayerInventory(
                (val) -> predicate.test(val.val()),
                doNotFSearchWhenOpenOtherScreen,
                acceptEmpty,
                handPriority,
                offHandPriority);
    }
    public static IndexEntry<Slot> findPlayerBackpackItem(
            Predicate<ItemStack> predicate, boolean acceptEmpty, boolean includeCraft) {
        return findPlayerBackpackSlot((slot) -> predicate.test(slot.getStack()), acceptEmpty, includeCraft);
    }
    public static IndexEntry<Slot> findScreenSlot(List<Slot> slots, Predicate<Slot> predicate, boolean acceptEmpty) {
        for (var i = 0; i < slots.size(); ++i) {
            var slot = slots.get(i);
            ItemStack stack = slot.getStack();
            if (!acceptEmpty && stack.isEmpty()) continue;
            if (predicate.test(slot)) {
                return new IndexEntry<>(i, slot);
            }
        }
        return null;
    }
    public static int getPlayerInvSize() {
        // 傻逼mojang你给玩家放特么的saddle槽位干什么
        return 41;
    }
    public static IndexEntry<ItemStack> findPlayerHotBarItem(
            Predicate<ItemStack> predicate, boolean acceptEmpty, boolean acceptOffhand) {
        PlayerInventory pinv = mc.player.getInventory();
        ItemStack item = mc.player.getStackInHand(Hand.MAIN_HAND);
        // we assert player hold block while scaffold, or it will be really annoying
        // the holding block must be a full cube
        int selecedSlot = pinv.getSelectedSlot();
        if ((acceptEmpty || !item.isEmpty()) && predicate.test(item)) {
            return new IndexEntry<>(selecedSlot, item);
        }
        if (acceptOffhand) {
            item = mc.player.getStackInHand(Hand.OFF_HAND);
            if ((acceptEmpty || !item.isEmpty()) && predicate.test(item)) {
                return new IndexEntry<>(40, item);
            }
        }

        for (var i = 0; i < 9; ++i) {
            ItemStack stack = pinv.getStack(i);
            if (i == selecedSlot) continue;
            if ((acceptEmpty || !stack.isEmpty()) && predicate.test(stack)) {
                //                if(keepInHand.get()){
                //                    MovTasks.getMovExtra().sendPacketsForInventoryAction();
                //                    OptionalInt slotIndex = mc.player.currentScreenHandler.getSlotIndex(pinv, i);
                //                    if(slotIndex.isPresent()){
                //                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId,
                // slotIndex.getAsInt(), selecedSlot, SlotActionType.SWAP, mc.player);
                //                        return selecedSlot;
                //                    }
                //                }else
                return new IndexEntry<>(i, stack);
            }
        }
        return null;
    }
    public static IndexEntry<ItemStack> getSelectedItem() {
        return new IndexEntry<>(
                InventoryUtils.getSelectedSlot(), mc.player.getInventory().getSelectedStack());
    }
    public static IndexEntry<ItemStack> findBestPlayerItem(
            Function<ItemStack, Double> maxFunction, boolean doNotFSearchWhenOpenOtherScreen, boolean acceptEmpty) {
        return findBestPlayerInventory(
                s -> {
                    return maxFunction.apply(s.val());
                },
                doNotFSearchWhenOpenOtherScreen,
                acceptEmpty);
    }
    public static IndexEntry<ItemStack> findBestPlayerInventory(
            Function<IndexEntry<ItemStack>, Double> maxFunction,
            boolean doNotFSearchWhenOpenOtherScreen,
            boolean acceptEmpty) {
        // while player is open Screen
        PlayerInventory pinv = mc.player.getInventory();
        ItemStack item = mc.player.getStackInHand(Hand.MAIN_HAND);
        // we assert player hold block while scaffold, or it will be really annoying
        // the holding block must be a full cube
        int selecedSlot = pinv.getSelectedSlot();
        Double maxValue = null;
        IndexEntry<ItemStack> result = null;
        IndexEntry<ItemStack> test = null;
        if ((acceptEmpty || !item.isEmpty())) {
            test = new IndexEntry<>(selecedSlot, item);
            maxValue = maxFunction.apply(test);
            if (maxValue != null) {
                result = test;
            }
        }
        if (doNotFSearchWhenOpenOtherScreen
                && ClientPlayerAccess.of(mc.player).getServerScreenHandler().syncId
                        != mc.player.playerScreenHandler.syncId) {
            return result;
        }

        Double currentValue;
        for (var i = 0; i < getPlayerInvSize(); ++i) {
            ItemStack stack = pinv.getStack(i);
            test = new IndexEntry<>(i, stack);
            if ((acceptEmpty || !stack.isEmpty()) && (currentValue = maxFunction.apply(test)) != null) {
                if (maxValue == null || currentValue > maxValue) {
                    maxValue = currentValue;
                    result = test;
                }
            }
        }
        return result;
    }
    public static IndexEntry<Slot> findPlayerBackpackSlot(
            Predicate<Slot> predicate, boolean acceptEmpty, boolean includeCraft) {
        var handler = mc.player.playerScreenHandler;
        var serverHandler = ClientPlayerAccess.of(mc.player).getServerScreenHandler();
        if (serverHandler.syncId == handler.syncId) {
            if (includeCraft) {
                for (var i = 1; i < 5; ++i) {
                    var slot = handler.slots.get(i);
                    if (!acceptEmpty && slot.getStack().isEmpty()) continue;
                    if (predicate.test(slot)) {
                        return new IndexEntry<>(i, slot);
                    }
                }
            }
            var pinv = mc.player.getInventory();
            for (var i = 0; i < getPlayerInvSize(); ++i) {
                int slotIndex = InvTasks.getScreenSlotByInventoryIndex(i);
                var slot = handler.slots.get(slotIndex);
                if (!acceptEmpty && slot.getStack().isEmpty()) continue;
                if (predicate.test(slot)) {
                    return new IndexEntry<>(slotIndex, slot);
                }
            }
            return null;
        } else {
            return null;
        }
    }
    public static IndexEntry<ItemStack> findPlayerInventory(
            Predicate<IndexEntry<ItemStack>> predicate, boolean doNotFSearchWhenOpenOtherScreen, boolean acceptEmpty) {
        return findPlayerInventory(predicate, doNotFSearchWhenOpenOtherScreen, acceptEmpty, true, false);
    }
    public static IndexEntry<ItemStack> findPlayerInventory(
            Predicate<IndexEntry<ItemStack>> predicate,
            boolean doNotFSearchWhenOpenOtherScreen,
            boolean acceptEmpty,
            boolean handPriority,
            boolean offHandPriority) {
        PlayerInventory pinv = mc.player.getInventory();
        ItemStack item = mc.player.getStackInHand(Hand.MAIN_HAND);
        // we assert player hold block while scaffold, or it will be really annoying
        // the holding block must be a full cube
        int selecedSlot = pinv.getSelectedSlot();
        IndexEntry<ItemStack> result = null;
        IndexEntry<ItemStack> test;
        if ((acceptEmpty || !item.isEmpty()) && predicate.test((test = new IndexEntry<>(selecedSlot, item)))) {
            result = test;
        }
        if (handPriority && result != null) {
            return result;
        }
        if (result == null && offHandPriority) {
            item = mc.player.getStackInHand(Hand.OFF_HAND);
            if ((acceptEmpty || !item.isEmpty()) && predicate.test((test = new IndexEntry<>(40, item)))) {
                result = test;
            }
            if (result != null) {
                return result;
            }
        }
        // while player is open Screen
        if (doNotFSearchWhenOpenOtherScreen
                && ClientPlayerAccess.of(mc.player).getServerScreenHandler().syncId
                        != mc.player.playerScreenHandler.syncId) {
            return result;
        }
        for (var i = 0; i < getPlayerInvSize(); ++i) {
            ItemStack stack = pinv.getStack(i);
            test = new IndexEntry<>(i, stack);
            if ((acceptEmpty || !stack.isEmpty()) && predicate.test(test)) {
                //                if(keepInHand.get()){
                //                    MovTasks.getMovExtra().sendPacketsForInventoryAction();
                //                    OptionalInt slotIndex = mc.player.currentScreenHandler.getSlotIndex(pinv, i);
                //                    if(slotIndex.isPresent()){
                //                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId,
                // slotIndex.getAsInt(), selecedSlot, SlotActionType.SWAP, mc.player);
                //                        return selecedSlot;
                //                    }
                //                }else
                return new IndexEntry<>(i, stack);
            }
        }
        return null;
    }
    public static double computePlayerInventory(Function<ItemStack, Double> maxFunction, boolean acceptEmpty) {
        double sum = 0.0D;
        Double currentValue;
        PlayerInventory pinv = mc.player.getInventory();
        for (var i = 0; i < getPlayerInvSize(); ++i) {
            ItemStack stack = pinv.getStack(i);
            if ((acceptEmpty || !stack.isEmpty()) && (currentValue = maxFunction.apply(stack)) != null) {
                sum += currentValue;
            }
        }
        return sum;
    }
}