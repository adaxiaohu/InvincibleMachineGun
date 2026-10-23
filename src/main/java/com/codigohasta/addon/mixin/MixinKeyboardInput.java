package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.AlienSprint;
import com.codigohasta.addon.modules.GlobalSetting;
import com.codigohasta.addon.utils.leaveshack.Rotation;
import com.codigohasta.addon.utils.leaveshack.events.KeyboardInputEvent;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.input.Input;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Input.class)
public abstract class MixinKeyboardInput {

    /**
     * vanilla {@code tick()} 跑完后改写输入。
     *
     * <p>{@code require = 0}：允许在某个类上找不到 tick 而不报错。
     *
     * <p>⚠️ 不能 {@code extends Input}：目标是 {@code Input} 本身，
     * Mixin 不允许「父类 = 目标类」。字段访问用 {@code @Shadow}。
     */
    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void onTickTail(CallbackInfo ci) {
        // ═══ 分支 A：AlienSprint Rotation ═══
        if (AlienSprint.INSTANCE != null && AlienSprint.INSTANCE.isActive()
            && AlienSprint.INSTANCE.mode.get() == AlienSprint.Mode.Rotation) {
            AlienSprint.INSTANCE.onInputTick();
            return;
        }

        // ═══ 分支 B：leaveshack moveFix（现状保留，未改）═══
        if (!GlobalSetting.on() || !GlobalSetting.INSTANCE.moveFix.get() || !Rotation.rotation) return;

        PlayerInput raw = this.playerInput;

        KeyboardInputEvent event = new KeyboardInputEvent(
            raw.forward(), raw.backward(), raw.left(), raw.right(), raw.jump(), raw.sneak());

        MeteorClient.EVENT_BUS.post(event);

        this.playerInput = new PlayerInput(
            event.getForward() > 0,
            event.getForward() < 0,
            event.getStrafe() < 0,
            event.getStrafe() > 0,
            event.jump,
            event.sneak,
            raw.sprint()
        );

        float f = this.playerInput.forward() == this.playerInput.backward() ? 0.0F
            : (this.playerInput.forward() ? 1.0F : -1.0F);
        float g = this.playerInput.left() == this.playerInput.right() ? 0.0F
            : (this.playerInput.left() ? 1.0F : -1.0F);
        this.movementVector = new Vec2f(g, f).normalize();
    }

    /** 1.21.11 yarn: {@code Input.playerInput}。 */
    @Shadow public PlayerInput playerInput;
    /** 1.21.11 yarn: {@code Input.movementVector}。 */
    @Shadow protected Vec2f movementVector;
}
