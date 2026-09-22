package com.codigohasta.addon.utils.slimefunhelper.events.impl;

import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

public class UseItem {
    ActionResult actionResult;

    final Hand hand;
    public UseItem(ActionResult actionResult, Hand hand) { this.actionResult=actionResult; this.hand=hand; }
    public ActionResult actionResult() { return actionResult; }
    public UseItem actionResult(ActionResult value) { actionResult=value; return this; }
    public Hand hand() { return hand; }
}
