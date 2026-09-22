package com.codigohasta.addon.utils.slimefunhelper.utils;
import static net.minecraft.component.DataComponentTypes.*;
import com.google.gson.*;
import java.util.*;
import org.jetbrains.annotations.NotNull;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientDynamicRegistryType;
import net.minecraft.component.ComponentType;
import net.minecraft.component.type.*;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
public final class ItemStackUtils {
private static final net.minecraft.client.MinecraftClient mc=net.minecraft.client.MinecraftClient.getInstance();
    public static String getSfId(NbtCompound nbt) {
        NbtCompound bukkitValues = getBukkitValue(nbt);
        if (bukkitValues == null) return null;
        return getSfIdFromBukkitValues(bukkitValues);
    }
    public static String getSfId(ItemStack stack) {
        NbtCompound bukkitValues = getBukkitValueReadOnly(stack);
        if (bukkitValues == null) return null;
        return getSfIdFromBukkitValues(bukkitValues);
    }
    public static boolean matchItemMiningAbility(ItemStack stack1, ItemStack stack2) {
        return Objects.equals(stack1.get(TOOL), stack2.get(TOOL)) && matchEfficiency(stack1, stack2);
    }
    public static String getSfIdFromBukkitValues(NbtCompound ntb) {
        return ntb == null
                ? null
                : (ntb.get(SLIMEFUN_ID_PATH) instanceof NbtString nbtString ? nbtString.value() : null);
    }
    private static boolean matchEfficiency(ItemStack stack1, ItemStack stack2) {
        ItemEnchantmentsComponent ench1 = stack1.get(ENCHANTMENTS);
        ItemEnchantmentsComponent ench2 = stack2.get(ENCHANTMENTS);
        if (ench1 == null || ench2 == null) {
            return ench1 == ench2;
        } else {
            RegistryEntry<Enchantment> efficient = ItemStackUtils.registry().getEntryOrThrow(Enchantments.EFFICIENCY);
            return ench1.getLevel(efficient) == ench2.getLevel(efficient);
        }
    }
    public static NbtCompound getBukkitValue(@NotNull NbtCompound nbt) {
        return nbt.get(BUKKIT_NAMESPACE) instanceof NbtCompound cpd ? cpd : null;
    }
    public static NbtCompound getBukkitValueReadOnly(ItemStack stack) {
        NbtCompound compound = getCustomDataReadOnly(stack);
        return getBukkitValue(compound);
    }
    public static DynamicRegistryManager registry() {
        if (mc.getNetworkHandler() != null) {
            return cachedRegistry = mc.getNetworkHandler().getRegistryManager();
        } else {
            // when asking registry() offline, just return the cache value
            if (cachedRegistry != null) {
                return cachedRegistry;
            }
            if (staticRegistry == null) {
                staticRegistry = ClientDynamicRegistryType.createCombinedDynamicRegistries()
                        .getCombinedRegistryManager();
            }
            return staticRegistry;
        }
    }


private static final String BUKKIT_NAMESPACE="PublicBukkitValues",SLIMEFUN_ID_PATH="slimefun:slimefun_item";
private static DynamicRegistryManager cachedRegistry,staticRegistry;
public static NbtCompound getCustomDataReadOnly(ItemStack stack){var data=stack.get(CUSTOM_DATA);return data==null?new NbtCompound():data.copyNbt();}public static boolean hasInPatch(ItemStack stack, ComponentType<?> type) {
        if (stack != null && !stack.isEmpty()) {
            var map = stack.components.changedComponents;
            if (map == null) return false;
            return map.containsKey(type) && !Objects.equals(Optional.empty(), map.get(type));
        }
        return false;
    }
public static int getEnchantmentLevel(ItemEnchantmentsComponent component, RegistryKey<Enchantment> key) {
        return component.getLevel(ItemStackUtils.registry().getEntryOrThrow(key));
    }
public static ItemStack getCleanedItem(
            ItemStack stack, int setAmount, boolean keepNBT, boolean keepDur, boolean keepEnchant) {
        ItemStack cleaned = stack.getItem().getDefaultStack();

        if (!keepNBT) {
            if (setAmount != -999) {
                cleaned.setCount(setAmount);
            }
            return cleaned;
        }
        ItemStack stackCopy = stack.copy();
        if (setAmount != -999) {
            stackCopy.setCount(setAmount);
        }
        if (!keepDur) {
            setOrRemoveChange(stackCopy, DAMAGE, null);
        }
        if (!keepEnchant) {
            setOrRemoveChange(stackCopy, ENCHANTMENTS, null);
            setOrRemoveChange(stackCopy, STORED_ENCHANTMENTS, null);
        }

        return stackCopy;
    }
public static <T> void setOrRemoveChange(ItemStack stack, ComponentType<T> type, T val) {
        if (stack != null && !stack.isEmpty()) {
            var cpmap = stack.components;
            var map = cpmap.changedComponents;
            if (map == null) return;
            boolean shouldChange;
            if (val == null) {
                shouldChange = map.containsKey(type);
            } else {
                var op = map.get(type);
                if (op != null && Objects.equals(val, op.orElse(null))) {
                    shouldChange = false;
                } else shouldChange = true;
            }
            if (shouldChange) {
                // copy before write
                cpmap.onWrite();
                // update the map after copy
                map = cpmap.changedComponents;
                if (val == null) map.remove(type);
                else map.put(type, Optional.of(val));
            }
        }
    }
}
