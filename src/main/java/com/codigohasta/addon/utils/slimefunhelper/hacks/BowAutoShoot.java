package com.codigohasta.addon.utils.slimefunhelper.hacks;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.BowItem;

/** Auto-charge/release control adapted from ArrowDmg's ordinary rapid-fire loop. */
public final class BowAutoShoot {
    private boolean forcedPressed;

    public void tick(ClientPlayerEntity player, ClientPlayerInteractionManager interaction, KeyBinding use,
                     boolean enabled, boolean holdOnly, int chargeTicks, boolean paused, boolean physicallyHeld) {
        if (!enabled || paused || player == null || interaction == null) {
            reset(use, !paused && physicallyHeld);
            return;
        }
        boolean using = player.isUsingItem();
        boolean usingBow = using && player.getActiveItem().getItem() instanceof BowItem;
        boolean hasBow = player.getMainHandStack().getItem() instanceof BowItem
            || player.getOffHandStack().getItem() instanceof BowItem;
        if (!hasBow || (using && !usingBow)) {
            reset(use, physicallyHeld);
            return;
        }
        if (holdOnly) {
            reset(use, physicallyHeld);
            if (!physicallyHeld) return;
        } else if (!using) {
            use.setPressed(true);
            forcedPressed = true;
        }
        if (usingBow && player.getItemUseTime() >= Math.max(1, chargeTicks)) {
            // Keep the active item intact until vanilla sends RELEASE_USE_ITEM,
            // so BowEnhance's existing aim/velocity packet pipeline sees it normally.
            interaction.stopUsingItem(player);
        }
    }

    public void reset(KeyBinding use, boolean physicallyHeld) {
        if (forcedPressed && use != null) use.setPressed(physicallyHeld);
        forcedPressed = false;
    }
}
