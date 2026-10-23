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

/**
 * 注入 {@code Input.tick()} —— 父类版本。
 *
 * <h2>★★ 1.21.11 类结构陷阱（这是「按 A 却读到 F=false」的根因）</h2>
 * <table border="1">
 *   <tr><th>类</th><th>intermediary</th><th>mappings 里有无 {@code tick}</th></tr>
 *   <tr><td>{@code client.input.Input}</td><td>class_744</td><td>✅ {@code method_3129}</td></tr>
 *   <tr><td>{@code client.input.KeyboardInput}</td><td>class_743</td><td>❌ <b>无</b>（yarn 漏标，字节码里实际有）</td></tr>
 * </table>
 *
 * <p>若只写 {@code @Mixin(KeyboardInput.class) + method="tick"}，
 * refmap 会生成 {@code class_743;method_3129()} —— 但该方法属于 class_744，
 * <b>在 class_743 里不存在</b> ⇒ 注入被跳过且<b>不报错</b>。
 *
 * <p>因此这里注入父类 {@code Input}，同时 {@link MixinKeyboardInputChild}
 * 注入子类 {@code KeyboardInput}，两边都 {@code require = 0}，靠
 * {@code AlienRotationUtil.inputCompensated} 去重。
 *
 * <h2>AlienV4 原实现</h2>
 * <pre>
 * // MixinKeyboardInput.java:15-26
 * &#64;Inject(method = "tick", at = &#64;At(value = "FIELD",
 *         target = "Lnet/minecraft/client/input/KeyboardInput;sneaking:Z",
 *         shift = Shift.AFTER, opcode = Opcodes.PUTFIELD), cancellable = true)
 *
 * // RotationManager.java:253-270  onKeyInput
 * mc.player.input.movementSideways = Math.round(mS * cos - mF * sin);
 * mc.player.input.movementForward  = Math.round(mF * cos + mS * sin);
 * </pre>
 * 1.21.11 里那两个 float 已换成 {@code playerInput}（record）+ {@code movementVector}，
 * 两者都要重算 —— 见 {@link AlienSprint#onInputTick()}。
 */
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
