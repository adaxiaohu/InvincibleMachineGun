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
