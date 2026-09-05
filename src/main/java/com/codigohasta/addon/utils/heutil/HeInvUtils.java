package com.codigohasta.addon.utils.heutil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.codigohasta.addon.mixin.InventoryAccessor;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;

public class HeInvUtils {
    public static final Minecraft mc = Minecraft.getInstance();
    public static final int MAX_SLOT = 36;

    /**
     * 1.21.11 专用：判断物品是否为潜影盒
     */
    public static boolean isShulkerBox(Item item) {
        if (item == null) return false;
        return item.toString().contains("shulker_box");
    }

    /**
     * 1.21.11 专用：获取物品附魔 Key 集合
     */
    public static Set<ResourceKey<Enchantment>> getEnchantment(ItemStack stack) {
        Set<ResourceKey<Enchantment>> set = new java.util.HashSet<>();
        if (stack == null || stack.isEmpty()) return set;
        
        // 尝试获取存储附魔（附魔书）或普通附魔
        net.minecraft.world.item.enchantment.ItemEnchantments enchants = stack.get(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS);
        if (enchants == null) enchants = stack.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
        
        if (enchants != null) {
            for (net.minecraft.core.Holder<Enchantment> entry : enchants.keySet()) {
                entry.unwrapKey().ifPresent(set::add);
            }
        }
        return set;
    }

    public static void closeCurScreen() {
        if (mc.player == null) return;
        if (!(mc.player.containerMenu instanceof InventoryMenu)) {
            mc.player.connection.send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
            mc.player.closeContainer();
        }
    }

    public static FindItemResult findAndMoveHotbar(Item item) {
        FindItemResult hotbarResult = InvUtils.findInHotbar(item);
        if (hotbarResult.slot() != -1) {
            return hotbarResult;
        } else {
            FindItemResult invResult = InvUtils.find(item);
            if (invResult.slot() == -1) return null;
            
            int mainSlot = getMainSlot();
            InvUtils.move().from(invResult.slot()).toHotbar(mainSlot);
            return InvUtils.findInHotbar(item);
        }
    }

    public static FindItemResult findShulkerBoxInHotBar(Item item) {
        return InvUtils.find(itemStack -> hasItem(item, itemStack), 0, 9);
    }

    public static FindItemResult findShulkerBoxNotEmpty() {
        return InvUtils.find(itemStack -> {
            if (isShulkerBox(itemStack.getItem())) {
                net.minecraft.world.item.component.ItemContainerContents container = itemStack.get(net.minecraft.core.component.DataComponents.CONTAINER);
                if (container == null) return false;
                return container.nonEmptyItems().iterator().hasNext();
            }
            return false;
        }, 0, 36);
    }

    public static FindItemResult findShulkerBox(Item item) {
        return InvUtils.find(itemStack -> hasItem(item, itemStack), 0, 36);
    }

    public static List<ItemStack> findAndMargeShulkerBox() {
        List<ItemStack> kitItemStackList = mc.player.containerMenu.slots.stream()
            .map(Slot::getItem)
            .filter(Objects::nonNull)
            .filter(stack -> isShulkerBox(stack.getItem()))
            .toList();
            
        Map<String, ItemStack> map = new LinkedHashMap<>();

        for (ItemStack kitItemStack : kitItemStackList) {
            String key = kitItemStack.getComponents().toString();
            if (map.containsKey(key)) {
                ItemStack itemStack = map.get(key);
                itemStack.setCount(itemStack.getCount() + 1);
            } else {
                map.put(key, kitItemStack.copy());
            }
        }

        List<ItemStack> itemStacks = new ArrayList<>(map.values());
        itemStacks.sort((o1, o2) -> Integer.compare(o2.getCount(), o1.getCount()));
        return itemStacks;
    }

    private static boolean hasItem(Item item, ItemStack itemStack) {
        if (isShulkerBox(itemStack.getItem())) {
            net.minecraft.world.item.component.ItemContainerContents container = itemStack.get(net.minecraft.core.component.DataComponents.CONTAINER);
            if (container == null) return item == Items.AIR;
            if (item == Items.AIR) {
                return !container.nonEmptyItems().iterator().hasNext();
            }
            for (net.minecraft.world.item.ItemStackTemplate stack : container.nonEmptyItems()) {
                if (stack.item().value() == item) return true;
            }
        }
        return false;
    }

    public static boolean isHotbar(int slot) {
        return slot >= 0 && slot <= 8;
    }

    public static int findBookSlot(ResourceKey<Enchantment> enchantment) {
        Container playerInventory = mc.player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack bookItemStack = playerInventory.getItem(i);
            if (bookItemStack != null && bookItemStack.getItem() == Items.ENCHANTED_BOOK) {
                Set<ResourceKey<Enchantment>> bookEnchantMentSet = getEnchantment(bookItemStack);
                if (bookEnchantMentSet.contains(enchantment)) return i;
            }
        }
        return -1;
    }

    @SafeVarargs
    public static int findBookSlot(ResourceKey<Enchantment>... enchantmentArr) {
        Container playerInventory = mc.player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack bookItemStack = playerInventory.getItem(i);
            if (bookItemStack != null && bookItemStack.getItem() == Items.ENCHANTED_BOOK) {
                Set<ResourceKey<Enchantment>> bookEnchantMentSet = getEnchantment(bookItemStack);
                boolean exist = true;
                for (ResourceKey<Enchantment> enchantment : enchantmentArr) {
                    if (!bookEnchantMentSet.contains(enchantment)) {
                        exist = false;
                        break;
                    }
                }
                if (exist) return i;
            }
        }
        return -1;
    }

    public static int findBookSlotInChest(ChestMenu screenHandler, ResourceKey<Enchantment> enchantment) {
        Container inventory = screenHandler.getContainer();
        for (int slotId = 0; slotId < inventory.getContainerSize(); slotId++) {
            ItemStack bookItemStack = screenHandler.getSlot(slotId).getItem();
            if (bookItemStack != null && bookItemStack.getItem() == Items.ENCHANTED_BOOK) {
                Set<ResourceKey<Enchantment>> bookEnchantMentSet = getEnchantment(bookItemStack);
                if (bookEnchantMentSet.contains(enchantment)) return slotId;
            }
        }
        return -1;
    }

    public static int findEquipSlotInChest(ChestMenu screenHandler, Item item) {
        Container inventory = screenHandler.getContainer();
        for (int slotId = 0; slotId < inventory.getContainerSize(); slotId++) {
            ItemStack itemStack = screenHandler.getSlot(slotId).getItem();
            if (itemStack != null && itemStack.getItem() == item) {
                Set<ResourceKey<Enchantment>> enchantments = getEnchantment(itemStack);
                if (enchantments.isEmpty()) return slotId;
            }
        }
        return -1;
    }

    public static int findItemSlot(Item item) {
        Container inventory = mc.player.getInventory();
        for (int i = 0; i < 36; i++) {
            if (inventory.getItem(i).getItem() == item) return i;
        }
        return -1;
    }

    public static int findFullItemSlot(Item item) {
        Container inventory = mc.player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack itemStack = inventory.getItem(i);
            if (itemStack.getItem() == item && itemStack.getCount() == itemStack.getMaxStackSize()) return i;
        }
        return -1;
    }

    public static boolean findItemAndSwitch(Item item) {
        int slot = findItemSlot(item);
        if (slot < 0) return false;
        return swapToSlot(slot);
    }

    /**
     * 使用 Mixin Accessor 获取当前选中的快捷栏索引
     */
    public static int getMainSlot() {
        return ((InventoryAccessor) mc.player.getInventory()).getSelectedSlot();
    }

    /**
     * 使用 Mixin Accessor 修改当前快捷栏索引
     */
    public static boolean swapToSlot(int slot) {
        if (!isHotbar(slot)) return false;
        InventoryAccessor accessor = (InventoryAccessor) mc.player.getInventory();
        if (accessor.getSelectedSlot() != slot) {
            accessor.setSelectedSlot(slot);
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
        }
        return true;
    }

    public static boolean swap(Item item) {
        FindItemResult hotbarResult = InvUtils.findInHotbar(item);
        if (hotbarResult.slot() != -1) {
            return swapToSlot(hotbarResult.slot());
        } else {
            FindItemResult inventoryResult = InvUtils.find(item);
            if (inventoryResult.slot() == -1) return false;
            InvUtils.move().from(inventoryResult.slot()).toHotbar(getMainSlot());
            return true;
        }
    }

    public static void swapMainHand(int fromSlot) {
        if (isHotbar(fromSlot)) {
            swapToSlot(fromSlot);
        } else {
            boolean needSwap = !InvUtils.testInMainHand(Items.AIR);
            InvUtils.move().from(fromSlot).toHotbar(getMainSlot());
            if (needSwap) InvUtils.click().to(fromSlot);
        }
    }

    public static void swap(int fromSlot, int toSlot) {
        if (isHotbar(fromSlot)) {
            swapToSlot(fromSlot);
        } else {
            boolean needSwap = !mc.player.getInventory().getItem(fromSlot).isEmpty();
            InvUtils.move().from(fromSlot).to(toSlot);
            if (needSwap) InvUtils.click().to(fromSlot);
        }
    }

    public static boolean isKitInMainHand() {
        return isShulkerBox(mc.player.getMainHandItem().getItem());
    }

    /**
     * 1.21.11 专用：获取潜影盒内容物 (使用 NonNullList 修复编译错误)
     */
    public static List<ItemStack> getShulkerContents(ItemStack shulkerStack) {
        NonNullList<ItemStack> contents = NonNullList.withSize(27, ItemStack.EMPTY);
        if (!isShulkerBox(shulkerStack.getItem())) return contents;

        net.minecraft.world.item.component.ItemContainerContents container = shulkerStack.get(net.minecraft.core.component.DataComponents.CONTAINER);
        if (container != null) {
            container.copyInto(contents);
        }
        return contents;
    }
}