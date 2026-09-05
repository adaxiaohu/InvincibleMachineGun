package com.codigohasta.addon.modules.villager;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

public enum VillagerType {
   盔甲匠(Items.BLAST_FURNACE, VillagerProfession.ARMORER),
   屠夫(Items.SMOKER, VillagerProfession.BUTCHER),
   制图师(Items.CARTOGRAPHY_TABLE, VillagerProfession.CARTOGRAPHER),
   牧师(Items.BREWING_STAND, VillagerProfession.CLERIC),
   农民(Items.COMPOSTER, VillagerProfession.FARMER),
   渔夫(Items.BARREL, VillagerProfession.FISHERMAN),
   制箭师(Items.FLETCHING_TABLE, VillagerProfession.FLETCHER),
   皮匠(Items.CAULDRON, VillagerProfession.LEATHERWORKER),
   图书管理员(Items.LECTERN, VillagerProfession.LIBRARIAN),
   石匠(Items.STONECUTTER, VillagerProfession.MASON),
   牧羊人(Items.LOOM, VillagerProfession.SHEPHERD),
   工具匠(Items.SMITHING_TABLE, VillagerProfession.TOOLSMITH),
   武器匠(Items.GRINDSTONE, VillagerProfession.WEAPONSMITH);

   private final Item item;
   private final ResourceKey<VillagerProfession> profession;

   private VillagerType(Item item, ResourceKey<VillagerProfession> profession) {
      this.item = item;
      this.profession = profession;
   }

   public Item getItem() {
      return this.item;
   }

   public ResourceKey<VillagerProfession> getProfession() {
      return this.profession;
   }

   public static VillagerType valueOf(net.minecraft.core.Holder<VillagerProfession> profession) {
      for (VillagerType villagerType : values()) {
         if (profession.is(villagerType.getProfession())) {
            return villagerType;
         }
      }
      return null;
   }
}