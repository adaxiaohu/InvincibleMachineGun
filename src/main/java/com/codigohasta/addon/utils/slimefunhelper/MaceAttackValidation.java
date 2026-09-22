package com.codigohasta.addon.utils.slimefunhelper;

import com.codigohasta.addon.modules.slimefunhelper.*;
import com.codigohasta.addon.utils.slimefunhelper.managers.Configs;
import com.codigohasta.addon.utils.slimefunhelper.utils.EntityUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Revalidates a delayed mace Bot attack against the last movement sent to the server. */
public final class MaceAttackValidation {
    private MaceAttackValidation() {}

    public static boolean allow(Entity target) {
        var bot = ElytraBot.INSTANCE;
        if (bot == null || !bot.isActive() || bot.mode.get() != ElytraBot.Mode.MACE_ARUA
            || Attack.INSTANCE.legalTargetingMode.get() != Configs.LegalTargetingMode.DELAY_MOVEMENT) return true;

        var player = MinecraftClient.getInstance().player;
        if (target == null || target.isRemoved()) return false;
        var state = PlayerStateManager.INSTANCE;
        Vec3d feet = state.getLastPosition();
        Vec3d direction = EntityUtils.pitchYawToRotation(state.lastPitch, state.lastYaw);
        double range = Math.min(player.getEntityInteractionRange(), CombatExtra.INSTANCE.getAttackAtTargetRange(target));
        Box box = target.getBoundingBox();
        boolean valid = Double.isFinite(range) && range > 0 && TargetSelector.INSTANCE.getPotentialEyeHeights()
            .anyMatch(height -> intersects(feet.add(0, height, 0), direction, box, range));
        FlightPacketDiagnostics.attackCheck(target, feet, direction, range, valid);
        return valid;
    }

    private static boolean intersects(Vec3d eye, Vec3d direction, Box box, double range) {
        return box.contains(eye) || box.raycast(eye, eye.add(direction.multiply(range))).isPresent();
    }
}
