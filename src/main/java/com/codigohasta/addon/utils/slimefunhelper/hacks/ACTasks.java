package com.codigohasta.addon.utils.slimefunhelper.hacks;
import com.codigohasta.addon.modules.slimefunhelper.PostManager;
import java.util.function.Consumer;
import net.minecraft.client.network.ClientPlayNetworkHandler;
public final class ACTasks {
    public static void addPostTickAction(Consumer<ClientPlayNetworkHandler> action){PostManager.INSTANCE.addPostTickAction(action);}
    public static void addPostTransactionAction(Consumer<ClientPlayNetworkHandler> action){PostManager.INSTANCE.addNextPreTickAction(action);}
    public static void clear(){PostManager.INSTANCE.clearQueues();}
}
