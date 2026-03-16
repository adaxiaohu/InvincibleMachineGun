package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent; // 修改了这里
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.MaceItem;
import net.minecraft.item.SwordItem;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;

public class MaceBreakerPro extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // --- 触发开关 ---
    private final Setting<Boolean> swordTrigger = sgGeneral.add(new BoolSetting.Builder()
        .name("剑切斧锤")
        .description("当你手持剑左键时允许触发")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> axeTrigger = sgGeneral.add(new BoolSetting.Builder()
        .name("斧切锤")
        .description("当你手持斧头左键时允许触发")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> maceTrigger = sgGeneral.add(new BoolSetting.Builder()
        .name("飞机大炮式没敌版")
        .description("当你手持重锤左键时允许触发")
        .defaultValue(true)
        .build());

    // --- 智能检测 ---
    private final Setting<Boolean> onlyOnShield = sgGeneral.add(new BoolSetting.Builder()
        .name("只有举盾才触发")
        .description("仅当敌人正在举盾时才触发破盾宏")
        .defaultValue(true)
        .build());

    // --- 结束后处理 ---
    private final Setting<Boolean> autoReturn = sgGeneral.add(new BoolSetting.Builder()
        .name("切回")
        .description("攻击完成后自动切回原始武器")
        .defaultValue(true)
        .build());

    public MaceBreakerPro() {
        super(AddonTemplate.CATEGORY, "没敌打断", "飞机大炮式，当你要打断盾牌的时候，打断他。");
    }

    @EventHandler
    private void onAttackEntity(AttackEntityEvent event) { // 修改了事件类名
        if (event.entity == null || mc.player == null) return;

        // 1. 目标检测
        if (onlyOnShield.get()) {
            if (event.entity instanceof LivingEntity target) {
                if (!target.isBlocking()) return;
            } else {
                return;
            }
        }

        // 2. 记录当前原始槽位
        int originalSlot = mc.player.getInventory().selectedSlot;
        boolean holdingSword = mc.player.getMainHandStack().getItem() instanceof SwordItem;
        boolean holdingAxe = mc.player.getMainHandStack().getItem() instanceof AxeItem;
        boolean holdingMace = mc.player.getMainHandStack().getItem() instanceof MaceItem;

        // 3. 触发检查
        if (holdingSword && !swordTrigger.get()) return;
        if (holdingAxe && !axeTrigger.get()) return;
        if (holdingMace && !maceTrigger.get()) return;
        if (!holdingSword && !holdingAxe && !holdingMace) return;

        // 4. 寻找武器
        int axeSlot = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof AxeItem).slot();
        int maceSlot = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof MaceItem).slot();

        if (axeSlot == -1 || maceSlot == -1) return;

        // --- 核心包注入 ---
        event.cancel(); // 拦截原始攻击

        // A. 瞬切斧头
        if (originalSlot != axeSlot) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(axeSlot));
        }
        
        // B. 斧头攻击
        mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(event.entity, mc.player.isSneaking()));

        // C. 瞬切重锤
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(maceSlot));

        // D. 重锤攻击
        mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(event.entity, mc.player.isSneaking()));

        // E. 还原或停留
        if (autoReturn.get()) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(originalSlot));
            mc.player.getInventory().selectedSlot = originalSlot;
        } else {
            mc.player.getInventory().selectedSlot = maceSlot;
        }

        // 视觉效果
        mc.player.swingHand(Hand.MAIN_HAND);
    }
}