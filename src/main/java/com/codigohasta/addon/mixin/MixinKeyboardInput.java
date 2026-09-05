package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.FireworkElytraFly;
import com.codigohasta.addon.modules.GlobalSetting;
import com.codigohasta.addon.utils.leaveshack.Rotation;
import com.codigohasta.addon.utils.leaveshack.events.KeyboardInputEvent;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(KeyboardInput.class)
public abstract class MixinKeyboardInput extends ClientInput {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onTickHead(CallbackInfo ci) {
        if (FireworkElytraFly.INSTANCE.isActive() && FireworkElytraFly.INSTANCE.clearInputTicks > 0) {
            boolean jump = FireworkElytraFly.INSTANCE.forceJumpInput;
            this.keyPresses = new Input(false, false, false, false, jump, false, false);
            this.moveVector = new Vec2(0, 0);
            ci.cancel();
            return;
        }

        if (!GlobalSetting.INSTANCE.moveFix.get() || !Rotation.rotation) return;

        this.keyPresses = new Input(
            mc.options.keyUp.isDown(),
            mc.options.keyDown.isDown(),
            mc.options.keyLeft.isDown(),
            mc.options.keyRight.isDown(),
            mc.options.keyJump.isDown(),
            mc.options.keyShift.isDown(),
            mc.options.keySprint.isDown()
        );

        KeyboardInputEvent event = new KeyboardInputEvent(
            this.keyPresses.forward(),
            this.keyPresses.backward(),
            this.keyPresses.left(),
            this.keyPresses.right(),
            this.keyPresses.jump(),
            this.keyPresses.shift()
        );

        MeteorClient.EVENT_BUS.post(event);

        this.keyPresses = new Input(
            event.getForward() > 0,
            event.getForward() < 0,
            event.getStrafe() < 0,
            event.getStrafe() > 0,
            event.jump,
            event.sneak,
            this.keyPresses.sprint()
        );

        float f = this.keyPresses.forward() == this.keyPresses.backward() ? 0.0F : (this.keyPresses.forward() ? 1.0F : -1.0F);
        float g = this.keyPresses.left() == this.keyPresses.right() ? 0.0F : (this.keyPresses.left() ? 1.0F : -1.0F);
        this.moveVector = new Vec2(g, f).normalized();

        ci.cancel();
    }
}
