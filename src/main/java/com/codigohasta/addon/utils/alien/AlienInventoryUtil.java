package com.codigohasta.addon.utils.alien;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.client.Minecraft;
import com.codigohasta.addon.mixin.InventoryAccessor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.inventory.ContainerInput;

public class AlienInventoryUtil {
   private static final Minecraft mc = Minecraft.getInstance();

   public static void switchToSlot(int slot) {
      if (slot < 0 || slot > 8) return;
      ((InventoryAccessor) mc.player.getInventory()).setSelectedSlot(slot);
      mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
   }

   public static void inventorySwap(int slot, int selectedSlot) {
      if (slot - 36 != selectedSlot) {
         if (AlienEntityUtil.inInventory()) {
            mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, slot, selectedSlot, ContainerInput.SWAP, mc.player);
         }
      }
   }

   public static int findItem(Item input) {
      for (int i = 0; i < 9; i++) {
         Item item = mc.player.getInventory().getItem(i).getItem();
         if (Item.getId(item) == Item.getId(input)) {
            return i;
         }
      }
      return -1;
   }

   public static int findBlock(Block blockIn) {
      for (int i = 0; i < 9; i++) {
         ItemStack stack = mc.player.getInventory().getItem(i);
         if (stack != ItemStack.EMPTY && stack.getItem() instanceof BlockItem && ((BlockItem) stack.getItem()).getBlock() == blockIn) {
            return i;
         }
      }
      return -1;
   }

   public static int findBlockInventorySlot(Block block) {
      return findItemInventorySlot(block.asItem());
   }

   public static int findItemInventorySlot(Item item) {
      for (int i = 35; i >= 0; i--) {
         ItemStack stack = mc.player.getInventory().getItem(i);
         if (stack.getItem() == item) {
            return i < 9 ? i + 36 : i;
         }
      }
      return -1;
   }

   public static Map<Integer, ItemStack> getInventoryAndHotbarSlots() {
      HashMap<Integer, ItemStack> fullInventorySlots = new HashMap<>();
      for (int current = 0; current <= 35; current++) {
         fullInventorySlots.put(current, mc.player.getInventory().getItem(current));
      }
      return fullInventorySlots;
   }

   public static int getPotionCount(MobEffect targetEffect) {
      int count = 0;
      for (int i = 35; i >= 0; i--) {
         ItemStack itemStack = mc.player.getInventory().getItem(i);
         if (Item.getId(itemStack.getItem()) == Item.getId(Items.SPLASH_POTION)) {
            PotionContents potionContentsComponent = itemStack.getOrDefault(
               DataComponents.POTION_CONTENTS, PotionContents.EMPTY
            );
            for (MobEffectInstance effect : potionContentsComponent.getAllEffects()) {
               if (effect.getEffect().value() == targetEffect) {
                  count += itemStack.getCount();
               }
            }
         }
      }
      return count;
   }
}
