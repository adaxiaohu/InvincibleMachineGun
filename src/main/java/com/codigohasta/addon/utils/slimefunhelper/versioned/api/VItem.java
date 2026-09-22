package com.codigohasta.addon.utils.slimefunhelper.versioned.api;

import net.minecraft.item.*;
import net.minecraft.component.DataComponentTypes;
public final class VItem {
    private static final VItem INSTANCE=new VItem();
    public static VItem getInstance(){return INSTANCE;}
    public boolean canGlide(ItemStack stack){return stack.contains(DataComponentTypes.GLIDER);}
    public boolean isSpear(ItemStack stack){return stack.contains(DataComponentTypes.KINETIC_WEAPON);}
    public boolean isWeapon(ItemStack stack) {
        if (stack.getItem() instanceof MaceItem) {
            return true;
        } else if (stack.contains(DataComponentTypes.TOOL)) {
            Item tool = stack.getItem();
            if (tool instanceof AxeItem) {
                return true;
            } else if (isMiningPurposeWeaponWTFTool(stack)) {
                return false;
            } else {
                return true;
            }
        } else {
            return false;
        }
    }
    private boolean isMiningPurposeWeaponWTFTool(ItemStack stack) {
        net.minecraft.component.type.WeaponComponent component = stack.get(DataComponentTypes.WEAPON);
        return component != null && component.itemDamagePerAttack() > 1;
    }
    public boolean isTool(ItemStack stack) {
        return stack.contains(DataComponentTypes.TOOL);
    }
    public boolean isNotAttackingTool(ItemStack stack) {
        if (isTool(stack)) {
            if (stack.contains(DataComponentTypes.WEAPON)) {
                var weapon = stack.get(DataComponentTypes.WEAPON);
                if (weapon.itemDamagePerAttack() > 1) {
                    // only axe
                    return !stack.isIn(net.minecraft.registry.tag.ItemTags.AXES);
                    // return !stack.getItem().toString().contains("_axe");
                }
                return false;
            }
            return true;
        }
        return true;
    }
    public boolean isShield(ItemStack stack) {
        return stack.contains(DataComponentTypes.BLOCKS_ATTACKS);
    }
    public boolean isAxe(ItemStack stack) {
        return stack.isIn(net.minecraft.registry.tag.ItemTags.AXES);
    }
    public boolean isEatable(ItemStack stack) {
        return stack.contains(DataComponentTypes.CONSUMABLE);
    }
    public Integer getAttackDurabilityCost(ItemStack stack) {
        net.minecraft.component.type.WeaponComponent weapon = stack.get(DataComponentTypes.WEAPON);
        return weapon != null ? weapon.itemDamagePerAttack() : null;
    }
}