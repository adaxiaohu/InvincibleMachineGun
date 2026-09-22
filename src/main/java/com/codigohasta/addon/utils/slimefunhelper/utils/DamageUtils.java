package com.codigohasta.addon.utils.slimefunhelper.utils;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.predicate.entity.*;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EntityTypeTags;
public final class DamageUtils {
private static final net.minecraft.client.MinecraftClient mc=net.minecraft.client.MinecraftClient.getInstance();
    public static boolean isType(RegistryKey<DamageType> key, String type) {
        return key != null && Objects.equals(key.getValue().getPath(), type);
    }
    public static double getAttackDamage(PlayerEntity player, Entity livingEntity, ItemStack stack) {
        double att = player.getAttributeBaseValue(EntityAttributes.ATTACK_DAMAGE);
        AttributeModifiersComponent modifiers = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (modifiers != null && !modifiers.modifiers().isEmpty()) {
            att = applyOperations(modifiers.modifiers(), EntityAttributes.ATTACK_DAMAGE, att, EquipmentSlot.MAINHAND);
        }
        att += getEnchantmentBonus(player, livingEntity, stack);
        return att;
    }
    public static double getAttackDamage(
            PlayerEntity player, LivingEntity livingEntity, ItemStack stack, float cooldownProgress) {
        return getAttackDamage(player, livingEntity, stack);
    }
    public static double getAttackDamage(LivingEntity livingEntity, ItemStack stack) {
        return getAttackDamage(mc.player, livingEntity, stack);
    }
    public static double getAttackSpeed(PlayerEntity player, ItemStack stack) {
        double speed = player.getAttributeBaseValue(EntityAttributes.ATTACK_SPEED);
        AttributeModifiersComponent modifiers = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (modifiers != null && !modifiers.modifiers().isEmpty()) {
            speed = applyOperations(
                    modifiers.modifiers(), EntityAttributes.ATTACK_SPEED, speed, EquipmentSlot.MAINHAND);
        }
        return speed;
    }
    public static double applyOperations(
            List<AttributeModifiersComponent.Entry> modifiers,
            RegistryEntry<EntityAttribute> entityAttribute,
            double base,
            EquipmentSlot slot) {
        double d = base;
        Iterator var6 = modifiers.iterator();

        while (var6.hasNext()) {
            AttributeModifiersComponent.Entry entry = (AttributeModifiersComponent.Entry) var6.next();
            if (entry.slot().matches(slot) && Objects.equals(entityAttribute, entry.attribute())) {
                double e = entry.modifier().value();
                double var10001;
                switch (entry.modifier().operation()) {
                    case ADD_VALUE -> var10001 = e;
                    case ADD_MULTIPLIED_BASE -> var10001 = e * base;
                    case ADD_MULTIPLIED_TOTAL -> var10001 = e * d;
                    default -> throw new MatchException((String) null, (Throwable) null);
                }

                d += var10001;
            }
        }
        return d;
    }
    public static double getArmorToughnessValue(PlayerEntity player, ItemStack stack, EquipmentSlot slot) {
        return getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS, player, stack, slot);
    }
    public static double getArmorValue(PlayerEntity player, ItemStack stack, EquipmentSlot slot) {
        return getAttributeValue(EntityAttributes.ARMOR, player, stack, slot);
    }
    public static double getEnchantmentBonus(PlayerEntity player, Entity target, ItemStack stack) {
        ItemEnchantmentsComponent enchantments = stack.get(DataComponentTypes.ENCHANTMENTS);
        float bonus = 0.0F;
        if (enchantments != null && !enchantments.isEmpty()) {
            for (var entry : enchantments.getEnchantmentEntries()) {
                RegistryEntry<Enchantment> enchantment = entry.getKey();
                int level = entry.getIntValue();

                // 锋利 (Sharpness)
                if (enchantment.matchesKey(Enchantments.SHARPNESS)) {
                    bonus += 1.0f + (level - 1) * 0.5f;
                }
                // 亡灵杀手 (Smite)
                else if (enchantment.matchesKey(Enchantments.SMITE)
                        && target.getType().isIn(EntityTypeTags.SENSITIVE_TO_SMITE)) {
                    bonus += 2.5f * level;
                }
                // 节肢杀手 (Bane of Arthropods)
                else if (enchantment.matchesKey(Enchantments.BANE_OF_ARTHROPODS)
                        && target.getType().isIn(EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS)) {
                    bonus += 2.5f * level;
                }
                // 穿刺 (Impaling) —— 仅对三叉戟且目标为水生生物生效
                else if (enchantment.matchesKey(Enchantments.IMPALING)
                        && target.getType().isIn(EntityTypeTags.SENSITIVE_TO_IMPALING)) {
                    bonus += 2.5f * level;
                }
            }
        }
        return bonus;
    }
    public static double getAttributeValue(
            RegistryEntry<EntityAttribute> entry, PlayerEntity player, ItemStack stack, EquipmentSlot slot) {
        double att = player.getAttributeBaseValue(entry);
        AttributeModifiersComponent modifiers = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (modifiers != null && !modifiers.modifiers().isEmpty()) {
            att = applyOperations(modifiers.modifiers(), entry, att, slot);
        }
        return att;
    }
}