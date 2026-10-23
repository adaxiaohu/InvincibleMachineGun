package com.codigohasta.addon.mixin.slimefunhelper;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

import com.codigohasta.addon.utils.slimefunhelper.accessors.access.ClientPlayerAccess;
import com.codigohasta.addon.utils.slimefunhelper.events.Listener;
import com.codigohasta.addon.utils.slimefunhelper.SlimefunRuntime;
import com.codigohasta.addon.utils.slimefunhelper.utils.entity.PlayerInputUtils;
import net.minecraft.client.network.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.input.Input;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.PlayerInput;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Hand;
import com.codigohasta.addon.utils.slimefunhelper.hacks.InvTasks;
import net.minecraft.util.math.Vec2f;
import com.mojang.authlib.GameProfile;
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerMixin extends AbstractClientPlayerEntity implements ClientPlayerAccess {
    @Shadow private int ticksSinceLastPositionPacketSent;
    @Shadow private float lastYawClient,lastPitchClient;
    @Shadow private boolean lastSprinting;
    @Shadow private boolean lastOnGround;
    @Shadow private PlayerInput lastPlayerInput;
    @Shadow public Input input;
    @Unique private boolean sf$forceNoFall;
@Unique private boolean sf$cancelMovement;
    @Unique private boolean sf$resyncInput;
    protected ClientPlayerMixin(ClientWorld world,GameProfile profile){super(world,profile);}
    public void resyncPos(){ticksSinceLastPositionPacketSent=100;}
    public void setResyncMovementPacketTicks(int ticks){ticksSinceLastPositionPacketSent=ticks;}
    public void resyncRot(){lastYawClient=getYaw()+1;lastPitchClient=getPitch()+1;}
    public void resyncInput(){sf$resyncInput=true;}
    public void resyncOnGround(){lastOnGround=!isOnGround();}
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/util/PlayerInput;equals(Ljava/lang/Object;)Z"))
    private boolean sf$compareInput(PlayerInput previous,Object next,com.llamalad7.mixinextras.injector.wrapoperation.Operation<Boolean> original){
        if(sf$resyncInput){sf$resyncInput=false;if(SlimefunRuntime.modifying())return false;}
        return original.call(previous,next);
    }
    public void setLastSprintFlag(boolean value){lastSprinting=value;}
    public boolean isForceNoFall(){return sf$forceNoFall;}
    public void setForceNoFall(boolean value){sf$forceNoFall=value;}
    @Unique private net.minecraft.client.gui.screen.ingame.HandledScreen<?> sf$keptScreen;
    @Unique private ScreenHandler sf$keptHandler;
    @Unique private boolean sf$forceClose;
    public net.minecraft.client.gui.screen.ingame.HandledScreen<?> getKeepedInv(){return sf$keptScreen;}
    public ScreenHandler getKeepedInvHandler(){return sf$keptHandler;}
    public ScreenHandler getServerScreenHandler(){return sf$keptHandler != null ? sf$keptHandler : currentScreenHandler;}
    public void clearKeepedInventory(boolean closeInv){
        sf$keptScreen=null;
        sf$keptHandler=null;
        if(closeInv){
            sf$forceClose=true;
            try{((ClientPlayerEntity)(Object)this).closeHandledScreen();}
            finally{sf$forceClose=false;}
        }
    }
    @Inject(method="closeHandledScreen",at=@At("HEAD"),cancellable=true)
    private void sf$keepScreen(CallbackInfo ci){
        if(!sf$forceClose && SlimefunRuntime.modifying()
            && com.codigohasta.addon.modules.slimefunhelper.InvExtra.INSTANCE.isActive()
            && com.codigohasta.addon.modules.slimefunhelper.InvExtra.INSTANCE.enableKeepInv.get()){
            var screen=net.minecraft.client.MinecraftClient.getInstance().currentScreen;
            if(screen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?> handled
                && !(handled.getScreenHandler() instanceof net.minecraft.screen.PlayerScreenHandler)
                && !(screen instanceof net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen)){
                sf$keptScreen=handled;
                sf$keptHandler=currentScreenHandler;
                ((ClientPlayerEntity)(Object)this).closeScreen();
                ci.cancel();
            }
        }
    }
    @Inject(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/client/network/AbstractClientPlayerEntity;tick()V",shift=At.Shift.BEFORE))
    private void sf$pre(CallbackInfo ci){sf$cancelMovement=false;SlimefunRuntime.prePlayer((ClientPlayerEntity)(Object)this);}
    @Inject(method="tickMovement",at=@At(value="INVOKE",target="Lnet/minecraft/client/input/Input;tick()V",shift=At.Shift.AFTER))
    private void sf$input(CallbackInfo ci){SlimefunRuntime.afterInput((ClientPlayerEntity)(Object)this);}
    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(method="sendMovementPackets",at=@At(value="INVOKE",target="Lnet/minecraft/client/network/ClientPlayerEntity;isCamera()Z"))
    private boolean sf$packets(boolean original){if(sf$cancelMovement&&SlimefunRuntime.modifying()){sf$cancelMovement=false;return false;}return original;}
    @ModifyArg(method="sendMovementPackets",at=@At(value="INVOKE",target="Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V"))
    private net.minecraft.network.packet.Packet<?> sf$movementCause(net.minecraft.network.packet.Packet<?> packet){
        if(SlimefunRuntime.modifying()&&packet instanceof net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket movement)
            com.codigohasta.addon.utils.slimefunhelper.accessors.access.PlayerMoveC2SPacketAccess.setCause(movement,com.codigohasta.addon.utils.slimefunhelper.accessors.access.PlayerMoveC2SPacketAccess.Cause.PLAYER_MOVEMENT);
        return packet;
    }
    @Inject(method="tick",at=@At(value="INVOKE",target="Ljava/util/List;iterator()Ljava/util/Iterator;"))
    private void sf$post(CallbackInfo ci){SlimefunRuntime.postPlayer((ClientPlayerEntity)(Object)this);}
@Inject(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/client/network/AbstractClientPlayerEntity;tick()V",shift=At.Shift.AFTER),order=100)
private void sf$prePackets(CallbackInfo ci){sf$cancelMovement=!SlimefunRuntime.beforeMovement((ClientPlayerEntity)(Object)this);}
@com.llamalad7.mixinextras.injector.ModifyExpressionValue(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/client/network/ClientPlayerEntity;hasVehicle()Z"))
private boolean sf$vehicle(boolean original){return original&&!sf$cancelMovement;}
    @Override public boolean checkGliding(){
        if(!SlimefunRuntime.extraActive())return super.checkGliding();
        boolean flying=isGliding();boolean candidate=!flying&&canGlide()&&!isTouchingWater();
        var event=Listener.getPlayerSwitchFallFlying().fire(candidate,flying);
        if(event.isCancelled())return false;
        if(event.context()){if(flying)stopGliding();else startGliding();return true;}return false;
    }
public void setLastRot(float pitch,float yaw){lastPitchClient=pitch;lastYawClient=yaw;}

    @Override
    public ItemEntity dropItem(ItemStack stack, boolean throwRandomly, boolean retainOwnership) {
        if (!stack.isEmpty()
                && this.getEntityWorld().isClient()
                && InvTasks.SUPPRESS_DROPITEM_SPAWN.get()
                && !MinecraftClient.getInstance().isOnThread()) {
            this.swingHand(Hand.MAIN_HAND);
            return null;
        } else {
            return super.dropItem(stack, throwRandomly, retainOwnership);
        }
    }
}
