package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.AlienSprint;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 输入补偿的<b>唯一有效注入点</b>：{@code KeyboardInput.tick()}。
 *
 * <h2>★★ 修正：之前「yarn 漏标」的结论是错的（⛔ 已撤回）</h2>
 * 用 intermediary jar 反编译实测（{@code minecraft-clientonly-intermediary-1.21.11}）：
 * <pre>
 * class net.minecraft.class_743 extends net.minecraft.class_744 {   // KeyboardInput extends Input
 *   private final class_315 field_3902;                            // GameOptions
 *   public class_743(class_315);
 *   private static float method_40218(boolean, boolean);
 *   public void method_3129();                                     // ★ tick —— 真的存在！
 * }
 * class net.minecraft.class_744 {                                  // Input
 *   public class_10185 field_54155;                                // playerInput
 *   protected class_241 field_55868;                               // movementVector
 *   public void method_3129();                                     // tick（被子类覆写）
 *   public class_241 method_3128();
 *   public boolean method_20622();
 *   public void method_64054();
 * }
 * </pre>
 *
 * <p>所以：
 * <ul>
 *   <li>❌ <b>不是</b> yarn 漏标 —— {@code class_743} 自己声明了 {@code method_3129}</li>
 *   <li>✅ refmap 生成 {@code class_743;method_3129()} <b>是对的</b></li>
 *   <li>✅ 签名是 <b>{@code ()V}</b>（无参），不是 {@code (Z)V}</li>
 *   <li>✅ 对 {@code Input.tick} 注入<b>不能</b>兜底 {@code KeyboardInput} 实例 ——
 *       子类覆写后除非显式 {@code super.tick()}，否则父类注入不触发</li>
 * </ul>
 *
 * <h2>★ 所以去掉了 require = 0</h2>
 * {@code @Inject} 的 {@code require} 默认是 1，注入失败会<b>抛
 * InvalidInjectionException</b> 而不是静默跳过。之前加 {@code require = 0}
 * 是在把错误藏起来 —— 现在让它暴露，配合
 * {@code -Dmixin.debug.verbose=true} 可看到真实原因。
 *
 * <h2>AlienV4 原实现</h2>
 * <pre>
 * // MixinKeyboardInput.java:15-26
 * &#64;Inject(method = "tick", at = &#64;At(value = "FIELD",
 *         target = "Lnet/minecraft/client/input/KeyboardInput;sneaking:Z",
 *         shift = Shift.AFTER, opcode = Opcodes.PUTFIELD), cancellable = true)
 * private void keyInput(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
 *    KeyboardInputEvent event = KeyboardInputEvent.get();
 *    Alien.EVENT_BUS.post(event);
 *    if (event.isCancelled()) ci.cancel();
 * }
 * </pre>
 *
 * <p>Alien 用的是 {@code @At(FIELD ... PUTFIELD sneaking, AFTER)} ——
 * 但 1.21.11 的 {@code KeyboardInput.tick} 里已经没有 {@code sneaking} 字段了
 * （改用 {@code PlayerInput} record），所以这里用 {@code @At("TAIL")}：
 * vanilla 全部算完之后再改，语义等价。
 */
@Mixin(KeyboardInput.class)
public abstract class MixinKeyboardInputChild {

    /**
     * vanilla {@code KeyboardInput.tick()} 跑完后改写输入。
     *
     * <p>不用 {@code require = 0}：注入失败就该报错，不要藏。
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void onKeyboardTickTail(CallbackInfo ci) {
        if (AlienSprint.INSTANCE == null || !AlienSprint.INSTANCE.isActive()) return;
        if (AlienSprint.INSTANCE.mode.get() != AlienSprint.Mode.Rotation) return;

        AlienSprint.INSTANCE.onInputTick();
    }
}
