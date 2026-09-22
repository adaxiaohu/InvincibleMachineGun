package com.codigohasta.addon.modules.slimefunhelper;

import java.util.Locale;
import java.util.OptionalInt;
import com.codigohasta.addon.utils.slimefunhelper.AutomaticInventoryActions;
import com.codigohasta.addon.utils.slimefunhelper.accessors.access.ClientPlayerAccess;
import com.codigohasta.addon.utils.slimefunhelper.accessors.access.HandledScreenAccess;
import com.codigohasta.addon.utils.slimefunhelper.accessors.hacks.PlayerInteractionAccess;
import com.codigohasta.addon.utils.slimefunhelper.events.Event;
import com.codigohasta.addon.utils.slimefunhelper.events.Listener;
import com.codigohasta.addon.utils.slimefunhelper.events.impl.SlotClickAction;
import com.codigohasta.addon.utils.slimefunhelper.hacks.InvTasks;
import com.codigohasta.addon.utils.slimefunhelper.hacks.MovTasks;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.BaseModule;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.ModulePath;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.HotKeyUtils;
import com.codigohasta.addon.utils.slimefunhelper.hooks.ViaFabricPlusHooks;
import com.codigohasta.addon.utils.slimefunhelper.hooks.ViaProtocols;
import com.codigohasta.addon.utils.slimefunhelper.managers.Configs;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.FlagRef;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.IntRef;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.KeyBindRef;
import com.codigohasta.addon.utils.slimefunhelper.managers.input.MultiKeyBind;
import com.codigohasta.addon.utils.slimefunhelper.utils.AttributeUtils;
import com.codigohasta.addon.utils.slimefunhelper.utils.Debug;
import com.codigohasta.addon.utils.slimefunhelper.utils.InventoryUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

public class InvExtra extends BaseModule {
    public static InvExtra INSTANCE;
    public final ModulePath inventory = makePath(Configs.INV_CONFIG, "inventory");

    public InvExtra() {
        super("InvExtra");
        INSTANCE = this;
    }

    public final IntRef inventoryClickLimit = intBuilder(inventory.add("packet-limit"))
            .defaultValue(40)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final FlagRef invGrimFix =
            flagBuilder(inventory.add("move-click-grim-fix")).build();

    public final FlagRef invSprintGrimFix =
            flagBuilder(inventory.add("sprint-click-grim-fix")).build();

    public final FlagRef expandInventory =
            flagBuilder(inventory.add("expand-backpack-inventory")).build();

    public final FlagRef ghostHandAttribute =
            flagBuilder(inventory.add("ghost-hand-attribute-sync")).build();

    public final KeyBindRef pickItemHotkey = hotkey(inventory.add("pick-item"))
            .defaultValue(MultiKeyBind.mouse(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL,org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_MIDDLE))
            .registerHotkey(()->{if(isActive())onPickItem();})
            .build();

    public final ModulePath keepInv = inventory.add("keep-inv");
    public final FlagRef enableKeepInv = flagBuilder(keepInv).build();

    public static final String CLEAR_KEEP = "clear-keep";
    @Override
    public meteordevelopment.meteorclient.gui.widgets.WWidget getWidget(meteordevelopment.meteorclient.gui.GuiTheme theme) {
        var button=theme.button(com.codigohasta.addon.utils.slimefunhelper.SfTranslations.text("inventory.clear-keep", "清除保留界面"));
        button.action=this::clearKeep;
        return button;
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreClickSlot(), this::onClickSlot);
        registerListener(Listener.getPacketPoint().getChannel(CloseHandledScreenC2SPacket.class), this::onCloseScreen);
    }

    public void onClickSlot(Event<SlotClickAction> event) {
        onInvClick(event.context.syncId());
    }

    public void onInvClick(int syncId) {
        // check if it is manually clicked
        if (mc.currentScreen instanceof HandledScreen<?> handled && handled.getScreenHandler().syncId == syncId) {
            // do not fix all of them
            // some module may use MultiAction to gain advantage
            if (invSprintGrimFix.get()) {
                // fix GuiMove situation
                MovTasks.getMovExtra().sendSprintPacketsForInventoryAction();
            }
        }
        if (invGrimFix.get()) {
            // do not support viafabric, I guess
            // just send input packets, do not change sprint status
            // do not send the fucking sprint packets, shit
            MovTasks.getMovExtra().sendInputPacketsForInventoryAction();
        }
    }

    public void onCloseScreen(Event<CloseHandledScreenC2SPacket> closeS2C) {
        if (expandInventory.get() && closeS2C.context.getSyncId() == mc.player.playerScreenHandler.syncId) {
            closeS2C.cancel();
        }
    }

    public void syncAttr() {
        if (ghostHandAttribute.get()) {
            AttributeUtils.updateAttribute(mc.player);
        }
    }

    public Runnable switchOrSwapInventoryIndexToHand(int hand) {
        int selected = InventoryUtils.getSelectedSlot();
        if (selected != hand) {
            if (hand < 9) {
                PlayerInteractionAccess.of(mc.interactionManager).syncSelectedHotbar(hand);
                syncAttr();
                return () -> {
                    PlayerInteractionAccess.of(mc.interactionManager).syncSelectedHotbar(selected);
                    syncAttr();
                };
            } else {
                OptionalInt slotIndex = mc.player.currentScreenHandler.getSlotIndex(mc.player.getInventory(), hand);
                if (slotIndex.isPresent()) {
                    int swapped = slotIndex.getAsInt();
                    if (swapped >= 0) {
                        MovTasks.getMovExtra().sendPacketsForInventoryAction();
                        AutomaticInventoryActions.clickSlot(
                                mc.player.currentScreenHandler.syncId,
                                swapped,
                                selected,
                                SlotActionType.SWAP,
                                mc.player);
                        syncAttr();
                        return () -> {
                            MovTasks.getMovExtra().sendPacketsForInventoryAction();
                            AutomaticInventoryActions.clickSlot(
                                    mc.player.currentScreenHandler.syncId,
                                    swapped,
                                    selected,
                                    SlotActionType.SWAP,
                                    mc.player);
                            syncAttr();
                        };
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
            }
        }
        return () -> {};
    }

    public Runnable swapInventoryIndexToHand(int hand) {
        int selected = InventoryUtils.getSelectedSlot();
        if (selected != hand) {
            //            if (hand < 9) {
            //                PlayerInteractionAccess.of(mc.interactionManager).syncSelectedHotbar(hand);
            //                return () -> {
            //                    PlayerInteractionAccess.of(mc.interactionManager).syncSelectedHotbar(selected);
            //                };
            //            } else {
            OptionalInt slotIndex = mc.player.currentScreenHandler.getSlotIndex(mc.player.getInventory(), hand);
            if (slotIndex.isPresent()) {
                int swapped = slotIndex.getAsInt();
                if (swapped >= 0) {
                    MovTasks.getMovExtra().sendPacketsForInventoryAction();
                    AutomaticInventoryActions.clickSlot(
                            mc.player.currentScreenHandler.syncId, swapped, selected, SlotActionType.SWAP, mc.player);
                    syncAttr();
                    return () -> {
                        MovTasks.getMovExtra().sendPacketsForInventoryAction();
                        AutomaticInventoryActions.clickSlot(
                                mc.player.currentScreenHandler.syncId,
                                swapped,
                                selected,
                                SlotActionType.SWAP,
                                mc.player);
                        syncAttr();
                    };
                } else {
                    return null;
                }
            } else {
                return null;
            }
            // }
        }
        return () -> {};
    }

    public Runnable swapInventoryIndexToOffhand(int hand) {
        if (hand == 40) return () -> {};
        OptionalInt slotIndex = mc.player.currentScreenHandler.getSlotIndex(mc.player.getInventory(), hand);
        if (slotIndex.isPresent()) {
            int swapped = slotIndex.getAsInt();
            if (swapped >= 0) {
                MovTasks.getMovExtra().sendPacketsForInventoryAction();

                AutomaticInventoryActions.clickSlot(
                        mc.player.currentScreenHandler.syncId, swapped, 40, SlotActionType.SWAP, mc.player);
                syncAttr();
                return () -> {
                    MovTasks.getMovExtra().sendPacketsForInventoryAction();
                    AutomaticInventoryActions.clickSlot(
                            mc.player.currentScreenHandler.syncId, swapped, 40, SlotActionType.SWAP, mc.player);
                    syncAttr();
                };
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    //    public Runnable swapInventoryIndex(int a, int b){
    //
    //    }

    public Runnable swapInventorySlotToHand(int slot) {
        int selected = InventoryUtils.getSelectedSlot();
        OptionalInt slotIndex = mc.player.currentScreenHandler.getSlotIndex(mc.player.getInventory(), selected);
        if (slotIndex.isPresent()) {
            return swapScreenSlots(slot, slotIndex.getAsInt());
        } else {
            return null;
        }
    }

    public Runnable swapInventorySlotToOffhand(int slot) {
        int selected = 40;
        OptionalInt slotIndex = mc.player.currentScreenHandler.getSlotIndex(mc.player.getInventory(), selected);
        if (slotIndex.isPresent()) {
            return swapScreenSlots(slot, slotIndex.getAsInt());
        } else {
            return null;
        }
    }

    public Runnable swapInventoryIndexes(int slot1, int slot2) {
        ;
        OptionalInt slotIndex = mc.player.currentScreenHandler.getSlotIndex(mc.player.getInventory(), slot1);
        OptionalInt slotIndex2 = mc.player.currentScreenHandler.getSlotIndex(mc.player.getInventory(), slot2);
        if (slotIndex.isPresent() && slotIndex2.isPresent()) {
            return swapScreenSlots(slotIndex.getAsInt(), slotIndex2.getAsInt());
        } else {
            return null;
        }
    }

    public Runnable swapScreenSlots(int armorSlot, int targetSlot) {
        if (armorSlot == targetSlot) return () -> {};
        var handler = ClientPlayerAccess.of(mc.player).getServerScreenHandler();
        var slots = handler.slots;
        if (slots.size() <= armorSlot || slots.size() <= targetSlot) {
            return null;
        }
        MovTasks.getMovExtra().sendPacketsForInventoryAction();
        var targetSlotInstance = handler.slots.get(targetSlot);
        if (targetSlotInstance.inventory instanceof PlayerInventory
                && (targetSlotInstance.getIndex() < 9 || targetSlotInstance.getIndex() == 40)) {
            // use number operation
            int target = targetSlotInstance.getIndex();
            AutomaticInventoryActions.clickSlot(handler.syncId, armorSlot, target, SlotActionType.SWAP, mc.player);
            syncAttr();
            return () -> {
                MovTasks.getMovExtra().sendPacketsForInventoryAction();
                AutomaticInventoryActions.clickSlot(handler.syncId, armorSlot, target, SlotActionType.SWAP, mc.player);
                syncAttr();
            };
        } else {
            var armorSlotInstance = handler.slots.get(armorSlot);
            if (armorSlotInstance.inventory instanceof PlayerInventory
                    && (armorSlotInstance.getIndex() < 9 || armorSlotInstance.getIndex() == 40)) {
                int target = armorSlotInstance.getIndex();
                AutomaticInventoryActions.clickSlot(handler.syncId, targetSlot, target, SlotActionType.SWAP, mc.player);
                syncAttr();
                return () -> {
                    MovTasks.getMovExtra().sendPacketsForInventoryAction();
                    AutomaticInventoryActions.clickSlot(handler.syncId, targetSlot, target, SlotActionType.SWAP, mc.player);
                    syncAttr();
                };
            } else {
                // fuck, do not kick me.
                swapTwoIdiotSlot(handler, targetSlot, armorSlot);
                syncAttr();
                return () -> {
                    MovTasks.getMovExtra().sendPacketsForInventoryAction();
                    swapTwoIdiotSlot(handler, targetSlot, armorSlot);
                    syncAttr();
                };
            }
        }
    }

    public void mergeScreenSlotTo(int from, int to) {
        if (from == to) return;
        var handler = ClientPlayerAccess.of(mc.player).getServerScreenHandler();
        var slots = handler.slots;
        if (slots.size() <= from || slots.size() <= to) {
            return;
        }
        MovTasks.getMovExtra().sendPacketsForInventoryAction();
        var fromSlotInstance = handler.slots.get(from);
        var toSlotInstance = handler.slots.get(to);
        ItemStack fromStack = fromSlotInstance.getStack();
        if (fromStack.isEmpty()) {
            return;
        }
        if (!toSlotInstance.canInsert(fromStack)) {
            return;
        }

        if (toSlotInstance.getStack().isEmpty()
                || !ItemStack.areItemsAndComponentsEqual(fromStack, toSlotInstance.getStack())) {
            swapScreenSlots(from, to);
        } else {
            ItemStack toStack = toSlotInstance.getStack();
            int maxSize = toStack.getMaxCount();
            boolean overStack = fromStack.getCount() + toStack.getCount() > maxSize;
            mc.interactionManager.clickSlot(handler.syncId, from, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(handler.syncId, to, 0, SlotActionType.PICKUP, mc.player);
            if (overStack) {
                mc.interactionManager.clickSlot(handler.syncId, from, 0, SlotActionType.PICKUP, mc.player);
            }
        }
    }

    private void swapTwoIdiotSlot(ScreenHandler handler, int targetSlot, int armorSlot) {
        int fuckingHotbar114514 = InventoryUtils.getSelectedSlot() == 8 ? 7 : 8;
        // swap target to hotbar, hotbar to target
        AutomaticInventoryActions.clickSlot(
                handler.syncId, targetSlot, fuckingHotbar114514, SlotActionType.SWAP, mc.player);
        // swap hotbar to armor, armor to hotbar
        AutomaticInventoryActions.clickSlot(handler.syncId, armorSlot, fuckingHotbar114514, SlotActionType.SWAP, mc.player);
        // swap the rest
        AutomaticInventoryActions.clickSlot(
                handler.syncId, targetSlot, fuckingHotbar114514, SlotActionType.SWAP, mc.player);
    }

    public boolean onPickItem() {
        PlayerEntity player = mc.player;
        if (player == null) return false;
        Screen nowScreen = InvTasks.getCurrentServerScreen(player);
        if (!player.isCreative() && nowScreen instanceof HandledScreen<?> handled) {
            double mouseX = mc.mouse.getScaledX(mc.getWindow());
            double mouseY = mc.mouse.getScaledY(mc.getWindow());
            Slot slot = HandledScreenAccess.of(handled).reallyGetSlotAt(mouseX, mouseY);
            if (slot != null) {
                if (slot.inventory instanceof PlayerInventory) {
                    if (slot.getIndex() >= 36) {
                        Debug.chat("Invalid slot for player Inventory", slot.getIndex());
                    } else {
                        if (ViaFabricPlusHooks.getInstance().isViaEnabled()
                                && ViaFabricPlusHooks.getInstance()
                                        .getCurrentVersion()
                                        .isLowerOrEqualTo(21, 3)) {
                            // use via shit to send pickup packet
                            var wrapper = ViaFabricPlusHooks.getInstance().createViaPacket();
                            wrapper.writePacketType(
                                    ViaProtocols.V1_21_2_TO_1_21_4, "pick_item".toUpperCase(Locale.ROOT));
                            wrapper.write("VAR_INT", slot.getIndex());
                            wrapper.scheduleSendToServer(ViaProtocols.V1_21_2_TO_1_21_4, true);
                            Debug.chat("run pickup");
                        } else {
                            Debug.chat("No Longer support this feat in version "
                                    + ViaFabricPlusHooks.getInstance().getCurrentVersion());
                        }
                        // mc.interactionManager.pickFromInventory(slot.getIndex());
                    }
                    return true;
                } else {
                    Debug.chat("Invalid slot outside player Inventory");
                }
            }
        }
        return false;
    }

    @Override
    public void onDisableModule() {
        if (mc.player != null && ClientPlayerAccess.of(mc.player).getKeepedInvHandler() != null)
            ClientPlayerAccess.of(mc.player).clearKeepedInventory(true);
    }

    public void clearKeep() {

        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            ClientPlayerAccess access = ClientPlayerAccess.of(player);
            access.clearKeepedInventory(true);
            Debug.chat(Text.literal("已清除界面历史记录"));
        }
    }
}
