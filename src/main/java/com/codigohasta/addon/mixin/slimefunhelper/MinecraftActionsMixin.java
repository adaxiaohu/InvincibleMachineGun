package com.codigohasta.addon.mixin.slimefunhelper;
import com.codigohasta.addon.utils.slimefunhelper.events.Listener;
import com.codigohasta.addon.utils.slimefunhelper.SlimefunRuntime;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
@Mixin(MinecraftClient.class) public abstract class MinecraftActionsMixin {
@Inject(method="setScreen",at=@At("RETURN"))
private void sf$screenChanged(net.minecraft.client.gui.screen.Screen screen,CallbackInfo ci){
    if(SlimefunRuntime.modifying())Listener.getPostSetScreen().broadcast(MinecraftClient.getInstance().currentScreen);
}

    @ModifyArg(method="handleInputEvents",at=@At(value="INVOKE",target="Lnet/minecraft/client/MinecraftClient;setScreen(Lnet/minecraft/client/gui/screen/Screen;)V",ordinal=1))
    private net.minecraft.client.gui.screen.Screen sf$reopenInventory(net.minecraft.client.gui.screen.Screen screen){
        if(SlimefunRuntime.modifying()
            && com.codigohasta.addon.modules.slimefunhelper.InvExtra.INSTANCE.isActive()
            && com.codigohasta.addon.modules.slimefunhelper.InvExtra.INSTANCE.enableKeepInv.get()){
            var player=MinecraftClient.getInstance().player;
            var access=com.codigohasta.addon.utils.slimefunhelper.accessors.access.ClientPlayerAccess.of(player);
            if(access.getKeepedInvHandler()!=null&&access.getKeepedInv()!=null){
                var kept=access.getKeepedInv();
                player.currentScreenHandler=access.getKeepedInvHandler();
                access.clearKeepedInventory(false);
                return kept;
            }
        }
        return screen;
    }
@Shadow public HitResult crosshairTarget;
@Shadow private int itemUseCooldown;
@Unique private HitResult sf$cachedTarget;
// Preserve MinecraftClientEvents' source phases; Meteor TickEvent has different boundaries.
@Inject(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/client/tutorial/TutorialManager;tick(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/util/hit/HitResult;)V",shift=At.Shift.AFTER))
private void sf$preTick(CallbackInfo ci){SlimefunRuntime.preTick();}
@Inject(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/client/MinecraftClient;getNetworkHandler()Lnet/minecraft/client/network/ClientPlayNetworkHandler;",shift=At.Shift.BEFORE))
private void sf$postGameTick(CallbackInfo ci){SlimefunRuntime.postGameTick();}
@Inject(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/util/profiler/Profiler;pop()V",ordinal=1,shift=At.Shift.BEFORE))
private void sf$postTick(CallbackInfo ci){SlimefunRuntime.postTick();}
@Inject(method="doAttack",at=@At(value="INVOKE",target="Lnet/minecraft/util/hit/HitResult;getType()Lnet/minecraft/util/hit/HitResult$Type;"),cancellable=true)
private void sf$attack(CallbackInfoReturnable<Boolean> cir){if(!SlimefunRuntime.ready()||crosshairTarget==null)return;var event=Listener.getAttackAction().fire(crosshairTarget);if(event.isCancelled()){cir.setReturnValue(false);return;}if(event.context() instanceof BlockHitResult){var mine=Listener.getMineBlockAction().fire(event.context(),true);if(mine.isCancelled()){cir.setReturnValue(false);return;}event.context(mine.context());}sf$cachedTarget=crosshairTarget;crosshairTarget=event.context();}
@Inject(method="doAttack",at=@At("RETURN"))private void sf$restoreAttack(CallbackInfoReturnable<Boolean> cir){sf$restore();}
@Inject(method="handleBlockBreaking",at=@At(value="FIELD",target="Lnet/minecraft/client/MinecraftClient;crosshairTarget:Lnet/minecraft/util/hit/HitResult;",ordinal=0))
private void sf$mine(boolean breaking,CallbackInfo ci){if(!SlimefunRuntime.ready()||!breaking||crosshairTarget==null)return;var e=Listener.getMineBlockAction().fire(crosshairTarget,false);sf$cachedTarget=crosshairTarget;crosshairTarget=e.isCancelled()?null:e.context();}
@Inject(method="handleBlockBreaking",at=@At("RETURN"))private void sf$restoreMine(boolean breaking,CallbackInfo ci){sf$restore();}
@Unique private void sf$restore(){if(sf$cachedTarget!=null){crosshairTarget=sf$cachedTarget;sf$cachedTarget=null;}}
@Inject(method="doItemUse",at=@At(value="INVOKE",target="Lnet/minecraft/client/network/ClientPlayerEntity;isRiding()Z"))private void sf$cooldown(CallbackInfo ci){if(!SlimefunRuntime.ready())return;var e=Listener.getUseItemCooldownReset().fire(null);if(e.isCancelled())itemUseCooldown=0;else if(e.context()!=null)itemUseCooldown=e.context();}
@WrapOperation(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/client/MinecraftClient;handleInputEvents()V"))private void sf$input(MinecraftClient client,Operation<Void> original){if(!SlimefunRuntime.ready()){original.call(client);return;}var e=Listener.getPreHandleInputEvents().fire(null);if(!e.isCancelled())original.call(client);Listener.getPostHandleInputEvents().fire(null);}
}
