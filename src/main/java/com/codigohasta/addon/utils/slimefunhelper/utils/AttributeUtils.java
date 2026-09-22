package com.codigohasta.addon.utils.slimefunhelper.utils;
import static net.minecraft.entity.attribute.EntityAttributes.*;
import java.util.*;
import com.codigohasta.addon.utils.slimefunhelper.accessors.access.LivingEntityAccess;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.*;
public final class AttributeUtils {
private static final net.minecraft.client.MinecraftClient mc=net.minecraft.client.MinecraftClient.getInstance();
    public static void updateAttribute(LivingEntity living) {
        LivingEntityAccess.of(living).updateEquipmentAttributeChange();
    }
    public static AttributeContainer getAttributeWith(
            net.minecraft.entity.LivingEntity living, Map<net.minecraft.entity.EquipmentSlot, net.minecraft.item.ItemStack> equipmentOverrides) {
        Map<net.minecraft.entity.EquipmentSlot, net.minecraft.item.ItemStack> filterMap = new LinkedHashMap<>(equipmentOverrides);
        // The entity is already a LivingEntity; getType() erases that subtype bound.
        @SuppressWarnings("unchecked")
        var livingType = (net.minecraft.entity.EntityType<? extends LivingEntity>) living.getType();
        AttributeContainer attributeContainer = new AttributeContainer(
                DefaultAttributeRegistry.get(livingType));
        attributeContainer.setFrom(living.getAttributes());
        for (Map.Entry<net.minecraft.entity.EquipmentSlot, net.minecraft.item.ItemStack> entry : filterMap.entrySet()) {
            var slot = entry.getKey();
            var itemStack2 = entry.getValue();
            net.minecraft.item.ItemStack toBeRemoved = living.getEquippedStack(slot);
            if (!toBeRemoved.isEmpty()) {
                toBeRemoved.applyAttributeModifiers(slot, (attribute, modifier) -> {
                    EntityAttributeInstance entityAttributeInstance = attributeContainer.getCustomInstance(attribute);
                    if (entityAttributeInstance != null) {
                        entityAttributeInstance.removeModifier(modifier);
                    }
                });
            }
            if (!itemStack2.isEmpty() && !itemStack2.shouldBreak()) {
                itemStack2.applyAttributeModifiers(slot, (attribute, modifier) -> {
                    EntityAttributeInstance entityAttributeInstance = attributeContainer.getCustomInstance(attribute);
                    if (entityAttributeInstance != null) {
                        entityAttributeInstance.removeModifier(modifier.id());
                        entityAttributeInstance.addTemporaryModifier(modifier);
                    }
                });
            }
        }

        return attributeContainer;
    }

    public static double getPlayerBlockInteractionRange(net.minecraft.entity.player.PlayerEntity player) {
        return player.getAttributeValue(EntityAttributes.BLOCK_INTERACTION_RANGE);
    }
}