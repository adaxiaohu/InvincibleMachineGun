package com.codigohasta.addon.mixin.slimefunhelper;
import com.google.common.collect.Maps;import java.util.*;import net.minecraft.entity.*;import net.minecraft.item.ItemStack;import net.minecraft.entity.attribute.*;import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.codigohasta.addon.utils.slimefunhelper.accessors.access.LivingEntityAccess;
@Mixin(LivingEntity.class) public abstract class EquipmentAttributeMixin implements LivingEntityAccess {
@Unique private Map<EquipmentSlot,ItemStack> clientLastEquipmentSnapshot;
@Shadow public abstract ItemStack getEquippedStack(EquipmentSlot slot);
@Shadow public abstract boolean areItemsDifferent(ItemStack a,ItemStack b);
@Shadow public abstract AttributeContainer getAttributes();
@Shadow protected abstract void onEquipmentRemoved(ItemStack stack,EquipmentSlot slot,AttributeContainer container);
@Inject(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/entity/LivingEntity;isRemoved()Z"))private void sf$snapshot(CallbackInfo ci){if((Object)this instanceof net.minecraft.entity.player.PlayerEntity)tickEquipment();}
    public Map<EquipmentSlot, ItemStack> getClientLastEquipmentSnapshot() {
        if (clientLastEquipmentSnapshot == null) {
            clientLastEquipmentSnapshot = Util.mapEnum(EquipmentSlot.class, (slot) -> {
                return ItemStack.EMPTY;
            });
        }
        return clientLastEquipmentSnapshot;
    }
    public void tickEquipment() {
        var map = getClientLastEquipmentSnapshot();
        for (var re : EquipmentSlot.values()) {
            map.put(re, getEquippedStack(re));
        }
    }
    public final void updateEquipmentAttributeChange() {
        getClientLastEquipmentSnapshot();
        Map<EquipmentSlot, ItemStack> map = null;
        Iterator<EquipmentSlot> var2 = EquipmentSlot.VALUES.iterator();
        ItemStack itemStack2;
        while (var2.hasNext()) {
            EquipmentSlot equipmentSlot = (EquipmentSlot) var2.next();
            ItemStack itemStack = (ItemStack) clientLastEquipmentSnapshot.get(equipmentSlot);
            itemStack2 = this.getEquippedStack(equipmentSlot);
            if (this.areItemsDifferent(itemStack, itemStack2)) {
                if (map == null) {
                    map = Maps.newEnumMap(EquipmentSlot.class);
                }

                map.put(equipmentSlot, itemStack2);
                AttributeContainer attributeContainer = this.getAttributes();
                if (!itemStack.isEmpty()) {
                    this.onEquipmentRemoved(itemStack, equipmentSlot, attributeContainer);
                }
            }
        }

        if (map != null) {
            var entries = map.entrySet().iterator();

            while (entries.hasNext()) {
                Map.Entry<EquipmentSlot, ItemStack> entry = entries.next();
                EquipmentSlot equipmentSlot2 = (EquipmentSlot) entry.getKey();
                itemStack2 = (ItemStack) entry.getValue();
                if (!itemStack2.isEmpty() && !itemStack2.shouldBreak()) {
                    itemStack2.applyAttributeModifiers(equipmentSlot2, (attribute, modifier) -> {
                        EntityAttributeInstance entityAttributeInstance =
                                this.getAttributes().getCustomInstance(attribute);
                        if (entityAttributeInstance != null) {
                            entityAttributeInstance.removeModifier(modifier.id());
                            entityAttributeInstance.addTemporaryModifier(modifier);
                        }
                    });
                }
            }
            tickEquipment();


        }
    }
}
