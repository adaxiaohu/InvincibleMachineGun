package com.codigohasta.addon.mixin;

import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import meteordevelopment.meteorclient.systems.modules.Modules;
import com.codigohasta.addon.modules.SonarBypass; // 确保路径正确

@Mixin(ClientCommonNetworkHandler.class) // 修改目标为 CommonHandler
public class MixinClientCommonNetworkHandler {

    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
private void onSendPacket(Packet<?> packet, CallbackInfo ci) {
    // 同样添加判空保护
    if (Modules.get() == null) return;
        SonarBypass module = Modules.get().get(SonarBypass.class);
        
        if (module != null && module.isActive() && module.blockFabric.get()) {
            // 拦截自定义载荷包 (1.21.11 Record 规范)
            if (packet instanceof CustomPayloadC2SPacket customPayloadPacket) {
                try {
                    // 使用 1.21.11 Record 访问方式：payload()
                    String channelId = customPayloadPacket.payload().getId().id().toString().toLowerCase();

                    // 屏蔽包含 fabric 同步或 meteor 信息的通道
                    if (channelId.contains("fabric") || channelId.contains("meteor")) {
                        ci.cancel();
                    }
                } catch (Exception ignored) {
                    // 防止某些特殊 Packet 导致的空指针
                }
            }
        }
    }
}