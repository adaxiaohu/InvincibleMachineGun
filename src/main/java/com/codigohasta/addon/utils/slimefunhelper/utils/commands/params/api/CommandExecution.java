package com.codigohasta.addon.utils.slimefunhelper.utils.commands.params.api;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.joml.Vector2f;
import org.joml.Vector3d;

/** Execution context consumed by the source PlayerStateManager.ServerPlayerContext. */
public interface CommandExecution {
    PlayerEntity getExecutor();
    boolean hasPermission(String permission);
    void sendMessage(String message);
    void sendMessage(Text message);
    Vector2f getExecuteRot();
    Vector3d getExecutePos();
    World getExecuteWorld();
}
