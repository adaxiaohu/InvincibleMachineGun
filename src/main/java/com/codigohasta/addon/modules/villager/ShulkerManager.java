package com.codigohasta.addon.modules.villager;

import com.codigohasta.addon.utils.heutil.HeBlockUtils;
import com.codigohasta.addon.utils.heutil.HeInvUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * 潜影盒满替换与仓储管理器 
 */
public class ShulkerManager {
    private static final Minecraft mc = Minecraft.getInstance();

    private BlockPos targetBoxPos;
    private Item targetBoxItem; 
    private int waitTimer = 0;
    private boolean isBreaking = false;
    private int searchAttempts = 0; 

    public void initReplacement(BlockPos pos, Item boxItem) {
        this.targetBoxPos = pos;
        this.targetBoxItem = boxItem;
        this.waitTimer = 0;
        this.isBreaking = false;
        this.searchAttempts = 0;
    }

    public boolean tickBreakFullBox() {
        if (waitTimer > 0) {
            waitTimer--;
            return false; 
        }

        BlockState state = mc.level.getBlockState(targetBoxPos);

        if (state.isAir()) {
            if (isBreaking) {
                isBreaking = false;
                waitTimer = 15; 
                return false;
            }
            return true; 
        }

        FindItemResult pickaxe = InvUtils.findInHotbar(itemStack -> 
            itemStack.getItem().toString().contains("pickaxe")
        );
        if (pickaxe.found()) {
            HeInvUtils.swapToSlot(pickaxe.slot());
        }

        Rotations.rotate(Rotations.getYaw(targetBoxPos), Rotations.getPitch(targetBoxPos));
        mc.gameMode.continueDestroyBlock(targetBoxPos, Direction.UP);
        mc.player.swing(InteractionHand.MAIN_HAND);
        isBreaking = true;

        return false;
    }

    public boolean tickDumpFullBox(BlockPos dumpChestPos) {
        if (waitTimer > 0) {
            waitTimer--;
            return false;
        }

        var handler = mc.player.containerMenu;

      
        if (handler instanceof InventoryMenu) {
            HeBlockUtils.open(dumpChestPos);
            waitTimer = 10; 
            return false;
        }

        FindItemResult fullBox = InvUtils.find(itemStack -> isNonEmptyShulker(itemStack));

        if (fullBox.found()) {
            InvUtils.shiftClick().slot(fullBox.slot());
            waitTimer = 5; 
            return false;
        } else {
            HeInvUtils.closeCurScreen();
            waitTimer = 10;
            return true;
        }
    }

    public boolean tickTakeEmptyBox(BlockPos emptyBoxChestPos) {
        if (waitTimer > 0) {
            waitTimer--;
            return false;
        }

        var handler = mc.player.containerMenu;

       
        if (handler instanceof InventoryMenu) {
            HeBlockUtils.open(emptyBoxChestPos);
            waitTimer = 10;
            searchAttempts = 0;
            return false;
        }

        
        if (InvUtils.find(itemStack -> isMatchColorEmptyShulker(itemStack, targetBoxItem)).found()) {
            HeInvUtils.closeCurScreen();
            waitTimer = 10;
            return true; 
        }

      
        boolean foundInContainer = false;
        for (int i = 0; i < handler.slots.size(); i++) {
            Slot slot = handler.getSlot(i);
          
            if (slot.container != mc.player.getInventory()) {
                ItemStack stack = slot.getItem();
                if (isMatchColorEmptyShulker(stack, targetBoxItem)) {
                    InvUtils.shiftClick().slotId(i);
                    waitTimer = 5;
                    foundInContainer = true;
                    return false; // 等待拿取结果
                }
            }
        }

        
        if (!foundInContainer) {
            searchAttempts++;
            if (searchAttempts > 3) {
                meteordevelopment.meteorclient.utils.player.ChatUtils.error("严重警告: 空盒补给箱(投掷器)里没有找到对应的空潜影盒！请人工补货。");
                HeInvUtils.closeCurScreen();
                waitTimer = 20;
                return true; 
            }
            waitTimer = 10; 
        }

        return false; 
    }

   public boolean tickPlaceNewBox() {
        if (waitTimer > 0) {
            waitTimer--;
            return false;
        }

        FindItemResult emptyBox = InvUtils.find(itemStack -> isMatchColorEmptyShulker(itemStack, targetBoxItem));

        if (!emptyBox.found()) {
            return false; 
        }

        if (!HeInvUtils.isHotbar(emptyBox.slot())) {
            int mainSlot = HeInvUtils.getMainSlot();
            InvUtils.move().from(emptyBox.slot()).toHotbar(mainSlot);
            waitTimer = 5;
            return false;
        }

      
        boolean placed = HeBlockUtils.place(
            targetBoxPos, 
            emptyBox.slot(), 
            true, 
            Direction.DOWN, 
            targetBoxPos.getCenter()
        );

        if (placed) {
            waitTimer = 10; 
            return true;
        }
        
        return false;
    }

    public static boolean isNonEmptyShulker(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!stack.getItem().toString().contains("shulker_box")) return false;

        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        if (container == null) return false;

        return container.nonEmptyItems().iterator().hasNext();
    }

   
    public static boolean isMatchColorEmptyShulker(ItemStack stack, Item targetBoxItem) {
        if (stack == null || stack.isEmpty()) return false;
        
       
        if (!stack.is(targetBoxItem)) return false;

        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        if (container == null) return true;
     
        return !container.nonEmptyItems().iterator().hasNext();
    }
}