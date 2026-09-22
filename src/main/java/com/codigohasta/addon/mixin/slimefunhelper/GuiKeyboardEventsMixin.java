package com.codigohasta.addon.mixin.slimefunhelper;

import com.codigohasta.addon.utils.slimefunhelper.SlimefunRuntime;
import com.codigohasta.addon.utils.slimefunhelper.events.Event;
import com.codigohasta.addon.utils.slimefunhelper.events.Listener;
import com.codigohasta.addon.utils.slimefunhelper.events.impl.KeyboardAction;
import com.codigohasta.addon.utils.slimefunhelper.managers.input.SimpleInputManager;
import net.minecraft.client.Keyboard;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class GuiKeyboardEventsMixin {
    @Inject(method="onKey",at=@At(value="FIELD",target="Lnet/minecraft/client/Keyboard;debugCrashStartTime:J",ordinal=0),cancellable=true)
    private void sf$keyboard(long window,int action,KeyInput input,CallbackInfo ci){
        SimpleInputManager.getInstance().onKeyInputPre(input.key(),action);
        if(!SlimefunRuntime.modifying())return;
        var event=new Event<>(new KeyboardAction((Keyboard)(Object)this,input.key(),input.scancode(),action,input.modifiers()),true,false);
        Listener.getKeyboardInput().dispatch(event);
        if(event.isCancelled())ci.cancel();
    }
}
