package com.codigohasta.addon.utils.slimefunhelper.events.impl;

import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;

/** Source event with the Lombok-generated accessors written explicitly. */
public class UseItemOnBlock {
    private BlockHitResult hitResult;
    private ActionResult actionResult;
    private final boolean placeBlock;
    private final Hand hand;

    public UseItemOnBlock(BlockHitResult hitResult, ActionResult actionResult, boolean placeBlock, Hand hand) {
        this.hitResult = hitResult;
        this.actionResult = actionResult;
        this.placeBlock = placeBlock;
        this.hand = hand;
    }
    public BlockHitResult hitResult() { return hitResult; }
    public UseItemOnBlock hitResult(BlockHitResult value) { hitResult = value; return this; }
    public ActionResult actionResult() { return actionResult; }
    public UseItemOnBlock actionResult(ActionResult value) { actionResult = value; return this; }
    public boolean placeBlock() { return placeBlock; }
    public Hand hand() { return hand; }
}
