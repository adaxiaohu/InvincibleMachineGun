package com.codigohasta.addon.utils.slimefunhelper.events.impl;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

/**
 * Ported from upstream 1.1.7.2 {@code me.matl114.events.impl.UseItem}
 * (Lombok accessors written explicitly; {@code handItem} added by upstream).
 */
public class UseItem implements SequencedAction {
    ActionResult actionResult;

    final Hand hand;

    ItemStack handItem;

    public UseItem(ActionResult actionResult, Hand hand) { this(actionResult, hand, ItemStack.EMPTY); }

    public UseItem(ActionResult actionResult, Hand hand, ItemStack handItem) {
        this.actionResult = actionResult;
        this.hand = hand;
        this.handItem = handItem;
    }

    public ActionResult actionResult() { return actionResult; }
    public UseItem actionResult(ActionResult value) { actionResult = value; return this; }
    public Hand hand() { return hand; }
    public ItemStack handItem() { return handItem; }
    public UseItem handItem(ItemStack value) { handItem = value; return this; }
}
