package com.codigohasta.addon.mixins; // 注意这里变成了 mixins

import com.codigohasta.addon.modules.CustomArmor;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmorFeatureRenderer.class)
public abstract class MixinArmorFeatureRenderer<S extends BipedEntityRenderState, M extends BipedEntityModel<S>, A extends BipedEntityModel<S>> extends FeatureRenderer<S, M> {

    public MixinArmorFeatureRenderer(FeatureRendererContext<S, M> context) {
        super(context);
    }

    // 适配 1.21.4 的 render 方法签名
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i, S state, float f, float g, CallbackInfo ci) {
        // 获取模块实例
        CustomArmor module = Modules.get().get(CustomArmor.class);
        
        // 检查模块是否开启且启用了隐藏铠甲
        if (module != null && module.isActive() && module.hideArmor.get()) {
            ci.cancel(); // 取消渲染，实现隐藏效果
        }
    }
}