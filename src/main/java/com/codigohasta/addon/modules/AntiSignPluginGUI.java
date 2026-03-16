package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

public class AntiSignPluginGUI extends Module {
    public AntiSignPluginGUI() {
        super(AddonTemplate.CATEGORY, "anti-sign-plugin-gui", "拦截插件告示牌界面并打开原版编辑器,这在很多时候是不能保存编辑");
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (event.screen == null || mc.player == null) return;

        // 获取标题并转为小写进行匹配
        String title = event.screen.getTitle().getString().toLowerCase();

        // 如果已经是原版界面，不做处理
        if (event.screen instanceof SignEditScreen) return;

        // 匹配插件界面标题（包含 sign 和 editor）
        if (title.contains("sign") && title.contains("editor")) {
            // 拦截插件 GUI
            event.cancel();

            // 切换到原版界面
            mc.execute(() -> {
                HitResult hit = mc.crosshairTarget;
                if (hit instanceof BlockHitResult blockHit) {
                    BlockEntity be = mc.world.getBlockEntity(blockHit.getBlockPos());
                    
                    if (be instanceof SignBlockEntity sign) {
                        // 本地设置编辑权
                        sign.setEditor(mc.player.getUuid());
                        
                        // 打开原版界面 (1.21.4 参数: 告示牌, 是否正面, 是否过滤)
                        mc.setScreen(new SignEditScreen(sign, true, mc.player.shouldFilterText()));
                    }
                }
            });
        }
    }
}