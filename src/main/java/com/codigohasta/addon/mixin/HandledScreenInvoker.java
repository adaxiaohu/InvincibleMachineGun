package com.codigohasta.addon.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 允许模块调用容器界面的原生槽位点击入口。
 *
 * TweakerMore 的自动清空容器最终通过 Item Scroller 调用容器 GUI 的 slotClicked，
 * 而不是自己先拿到鼠标光标再做二次操作。这个 Invoker 用于复现同一路径。
 *
 * 26.1.2 中该入口位于 AbstractContainerScreen#slotClicked，
 * 动作类型由 SlotActionType 改名为 ContainerInput；类名与 Invoker 方法名
 * 沿用上游命名，以便与上游后续改动对比。
 */
@Mixin(AbstractContainerScreen.class)
public interface HandledScreenInvoker {
    @Invoker("slotClicked")
    void codigohasta$invokeOnMouseClick(Slot slot, int slotId, int button, ContainerInput actionType);
}
