package com.codigohasta.addon.utils.slimefunhelper.utils;

import net.minecraft.client.MinecraftClient;
import io.netty.buffer.Unpooled;
import com.codigohasta.addon.mixin.slimefunhelper.PendingUpdateManagerAccessor;
public final class NetworkUtils {
    public static io.netty.buffer.ByteBuf createBytebuf(){return Unpooled.buffer();}
    public static int generateNextSequence(){try(var manager=MinecraftClient.getInstance().world.getPendingUpdateManager().incrementSequence()){return manager.getSequence();}}
    public static void restoreSequence(int sequence){var manager=MinecraftClient.getInstance().world.getPendingUpdateManager();if(manager.getSequence()==sequence)((PendingUpdateManagerAccessor)manager).sf$setSequence(sequence-1);}
}
