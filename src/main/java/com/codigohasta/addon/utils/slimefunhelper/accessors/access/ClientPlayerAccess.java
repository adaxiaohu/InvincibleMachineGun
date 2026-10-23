package com.codigohasta.addon.utils.slimefunhelper.accessors.access;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.screen.ScreenHandler;
public interface ClientPlayerAccess {
    static ClientPlayerAccess of(ClientPlayerEntity player){return (ClientPlayerAccess)player;}
    void resyncPos();void resyncRot();void resyncInput();
    default void resyncOnGround(){resyncPos();}
    void setLastSprintFlag(boolean value);
    boolean isForceNoFall();void setForceNoFall(boolean value);
    ScreenHandler getServerScreenHandler();
    net.minecraft.client.gui.screen.ingame.HandledScreen<?> getKeepedInv();
    ScreenHandler getKeepedInvHandler();
    void clearKeepedInventory(boolean closeInv);
    default net.minecraft.client.gui.screen.ingame.HandledScreen<?> getServerOpeningScreen() {
        if (getKeepedInv() != null) return getKeepedInv();
        return net.minecraft.client.MinecraftClient.getInstance().currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?> screen ? screen : null;
    }
default com.codigohasta.addon.utils.slimefunhelper.hacks.utils.entity.LegalMovementManager getLegalMovementManager(){return com.codigohasta.addon.utils.slimefunhelper.SlimefunRuntime.getMovement();}
default void resyncSneak(){resyncInput();}
void setLastRot(float pitch,float yaw);
/** Ported from upstream 1.1.7.2 ClientPlayerEntityAccess. */
void setResyncMovementPacketTicks(int ticks);
}
