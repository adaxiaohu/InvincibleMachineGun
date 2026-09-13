package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.FireworkElytraFly;
import com.codigohasta.addon.modules.GlobalSetting;
import com.codigohasta.addon.utils.leaveshack.Rotation;
import com.codigohasta.addon.utils.leaveshack.events.KeyboardInputEvent;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(KeyboardInput.class)
public abstract class MixinKeyboardInput extends Input {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onTickHead(CallbackInfo ci) {
        if (FireworkElytraFly.INSTANCE.isActive() && FireworkElytraFly.INSTANCE.clearInputTicks > 0) {
            boolean jump = FireworkElytraFly.INSTANCE.forceJumpInput;
            this.playerInput = new PlayerInput(false, false, false, false, jump, false, false);
            this.movementVector = new Vec2f(0, 0);
            ci.cancel();
            return;
        }

        if (!GlobalSetting.INSTANCE.moveFix.get() || !Rotation.rotation) return;

        this.playerInput = new PlayerInput(
            mc.options.forwardKey.isPressed(),
            mc.options.backKey.isPressed(),
            mc.options.leftKey.isPressed(),
            mc.options.rightKey.isPressed(),
            mc.options.jumpKey.isPressed(),
            mc.options.sneakKey.isPressed(),
            mc.options.sprintKey.isPressed()
        );

        KeyboardInputEvent event = new KeyboardInputEvent(
            this.playerInput.forward(),
            this.playerInput.backward(),
            this.playerInput.left(),
            this.playerInput.right(),
            this.playerInput.jump(),
            this.playerInput.sneak()
        );

        MeteorClient.EVENT_BUS.post(event);

        this.playerInput = new PlayerInput(
            event.getForward() > 0,
            event.getForward() < 0,
            event.getStrafe() < 0,
            event.getStrafe() > 0,
            event.jump,
            event.sneak,
            this.playerInput.sprint()
        );

        float f = this.playerInput.forward() == this.playerInput.backward() ? 0.0F : (this.playerInput.forward() ? 1.0F : -1.0F);
        float g = this.playerInput.left() == this.playerInput.right() ? 0.0F : (this.playerInput.left() ? 1.0F : -1.0F);
        this.movementVector = new Vec2f(g, f).normalize();

        ci.cancel();
    }
}
