package com.codigohasta.addon.utils.slimefunhelper.events.impl;

import java.util.Optional;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

/**
 * Ported from upstream 1.1.7.2 {@code me.matl114.events.impl.UseItemOnBlock}
 * (Lombok accessors written explicitly; {@code placingBlockPos} + {@code handItem} added by
 * upstream in place of IMG's {@code boolean placeBlock}).
 */
public class UseItemOnBlock implements SequencedAction {
    private BlockHitResult hitResult;
    private ActionResult actionResult;
    private final Optional<BlockPos> placingBlockPos;
    private final Hand hand;
    private ItemStack handItem;

    public UseItemOnBlock(BlockHitResult hitResult, ActionResult actionResult, Optional<BlockPos> placingBlockPos,
            Hand hand, ItemStack handItem) {
        this.hitResult = hitResult;
        this.actionResult = actionResult;
        this.placingBlockPos = placingBlockPos;
        this.hand = hand;
        this.handItem = handItem;
    }

    public BlockHitResult hitResult() { return hitResult; }
    public UseItemOnBlock hitResult(BlockHitResult value) { hitResult = value; return this; }
    public ActionResult actionResult() { return actionResult; }
    public UseItemOnBlock actionResult(ActionResult value) { actionResult = value; return this; }
    public Optional<BlockPos> placingBlockPos() { return placingBlockPos; }
    public Hand hand() { return hand; }
    public ItemStack handItem() { return handItem; }
    public UseItemOnBlock handItem(ItemStack value) { handItem = value; return this; }

    public boolean blockPlace() {
        return placingBlockPos.isPresent();
    }
}
