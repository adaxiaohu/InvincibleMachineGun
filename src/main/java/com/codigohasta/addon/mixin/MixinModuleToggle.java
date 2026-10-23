package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.NotificationsWidget;
import com.codigohasta.addon.utils.notify.ImgNotifications;
import com.codigohasta.addon.utils.notify.ImgNotificationType;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 模块开关时发通知。
 *
 * <p>✅ 照用户 2026-10-07 的截图（CatLean 的通知组件）：
 * <pre>
 *   Gui Move was enabled
 *   Gui Move was disabled
 * </pre>
 * —— 左侧是<b>开关图标</b>（on / off），右侧是「模块名 + 状态」。
 *
 * <p>用户同时要求「文案改中文 <b>已运作/停机</b>（可选中英文）」：
 * 由 {@code NotificationsWidget} 的「中文文案」设置决定 ——
 * <pre>
 *   中文（默认）：{模块名} 已运作 / {模块名} 已停机
 *   英文        ：{模块名} was enabled / {模块名} was disabled
 * </pre>
 *
 * <p>做法：在 Meteor {@code Module.toggle()} 的<b>返回处</b>注入，
 * 此时 {@code active} 已是新状态，直接据此选 ENABLED / DISABLED。
 *
 * <p><b>为什么不在各模块里改 {@code onActivate()}：</b>那要动上百个模块；
 * 在 {@code Module.toggle()} 注入一次即可覆盖全部模块（含其它 addon 的）。
 *
 * <p><b>⚠️ remap = false</b>：{@code Module} 是 <b>Meteor 的类</b>（不是 MC 的），
 * 没有 intermediary 映射 —— 不加这个参数会报
 * {@code Unable to locate obfuscation mapping for @Inject target toggle}。
 */
@Mixin(value = Module.class, remap = false)
public class MixinModuleToggle {

    @Shadow
    private boolean active;

    @Inject(method = "toggle", at = @At("RETURN"))
    private void img$notifyToggle(CallbackInfo ci) {
        try {
            Module self = (Module) (Object) this;
            String name = self.name;
            if (name == null || name.isEmpty()) return;

            // 通知组件本身不通知，否则「关通知」这个动作也会弹一条
            if (self instanceof NotificationsWidget) return;

            // 中文 / 英文由通知模块的设置决定；模块不存在时默认中文
            NotificationsWidget cfg = Modules.get().get(NotificationsWidget.class);
            boolean zh = cfg == null || cfg.useChineseText();

            ImgNotificationType type = active
                ? ImgNotificationType.ENABLED
                : ImgNotificationType.DISABLED;

            String text;
            if (zh) {
                text = name + (active ? " 已运作" : " 已停机");
            } else {
                text = name + (active ? " was enabled" : " was disabled");
            }

            // 标题留空 -> 渲染器画单行（照截图的单行样式）
            // 驻留时长走通知模块的「驻留时长」设置（用户：现在显示太久，要可调）
            int secs = ImgNotifications.INSTANCE.defaultSeconds;
            ImgNotifications.INSTANCE.post("", text, type, Math.max(1, secs));
        } catch (Throwable ignored) {
            // 通知失败绝不能影响模块开关本身
        }
    }
}
