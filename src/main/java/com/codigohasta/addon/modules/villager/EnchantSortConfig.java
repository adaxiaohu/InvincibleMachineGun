package com.codigohasta.addon.modules.villager;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import java.util.HashMap;
import java.util.Map;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

public class EnchantSortConfig {

    // 以下可以设定什么附魔书放进什么颜色的潜影盒盒子，应该还有更好的办法。以后弄
    private final Map<ResourceKey<Enchantment>, Block> sortingMap = new HashMap<>();

    private Block defaultFallbackBox = Blocks.SHULKER_BOX;

    public EnchantSortConfig() {
        sortingMap.put(Enchantments.MENDING, Blocks.LIGHT_BLUE_SHULKER_BOX);
        sortingMap.put(Enchantments.UNBREAKING, Blocks.BLACK_SHULKER_BOX);
        sortingMap.put(Enchantments.SHARPNESS, Blocks.RED_SHULKER_BOX);
        sortingMap.put(Enchantments.EFFICIENCY, Blocks.YELLOW_SHULKER_BOX);
        sortingMap.put(Enchantments.PROTECTION, Blocks.WHITE_SHULKER_BOX);
        sortingMap.put(Enchantments.FORTUNE, Blocks.GREEN_SHULKER_BOX);
        sortingMap.put(Enchantments.SILK_TOUCH, Blocks.GRAY_SHULKER_BOX);
    }

    public void addRule(ResourceKey<Enchantment> enchantment, Block shulkerColor) {
        sortingMap.put(enchantment, shulkerColor);
    }

    public void clearRules() {
        sortingMap.clear();
    }

    public void setDefaultFallbackBox(Block defaultFallbackBox) {
        this.defaultFallbackBox = defaultFallbackBox;
    }

    public Block getTargetBoxType(ItemStack bookStack) {
        if (bookStack == null || bookStack.isEmpty() || !bookStack.getItem().toString().contains("enchanted_book")) {
            return defaultFallbackBox;
        }

        ItemEnchantments enchants = bookStack.get(DataComponents.STORED_ENCHANTMENTS);
        if (enchants == null || enchants.isEmpty()) {
            return defaultFallbackBox; 
        }

        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchants.entrySet()) {
            ResourceKey<Enchantment> key = entry.getKey().unwrapKey().orElse(null);
            
            if (key != null && sortingMap.containsKey(key)) {
                return sortingMap.get(key);
            }
        }

        return defaultFallbackBox;
    }

    public ResourceKey<Enchantment> getMainEnchantment(ItemStack bookStack) {
        if (bookStack == null || bookStack.isEmpty() || !bookStack.getItem().toString().contains("enchanted_book")) {
            return null;
        }

        ItemEnchantments enchants = bookStack.get(DataComponents.STORED_ENCHANTMENTS);
        if (enchants == null || enchants.isEmpty()) {
            return null;
        }

        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchants.entrySet()) {
            return entry.getKey().unwrapKey().orElse(null);
        }
        return null;
    }

    public int getEnchantmentLevel(ItemStack bookStack, ResourceKey<Enchantment> targetKey) {
        if (bookStack == null || bookStack.isEmpty() || targetKey == null) return 0;

        ItemEnchantments enchants = bookStack.get(DataComponents.STORED_ENCHANTMENTS);
        if (enchants == null) return 0;

        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchants.entrySet()) {
            ResourceKey<Enchantment> key = entry.getKey().unwrapKey().orElse(null);
            if (targetKey.equals(key)) {
                return entry.getIntValue(); 
            }
        }
        return 0;
    }
}