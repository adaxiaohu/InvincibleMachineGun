package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.mixin.InventoryAccessor; // 必须确保这个存在
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;

public class MaceBreakerPro extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // --- 触发设置 ---
    private final Setting<Boolean> swordTrigger = sgGeneral.add(new BoolSetting.Builder().name("剑触发").description("手持剑攻击时触发").defaultValue(true).build());
    private final Setting<Boolean> axeTrigger = sgGeneral.add(new BoolSetting.Builder().name("斧触发").description("手持斧攻击时触发").defaultValue(true).build());
    private final Setting<Boolean> maceTrigger = sgGeneral.add(new BoolSetting.Builder().name("锤触发").description("手持重锤攻击时触发").defaultValue(true).build());

    // --- 核心逻辑 ---
    private final Setting<Boolean> onlyOnShield = sgGeneral.add(new BoolSetting.Builder().name("仅举盾时触发").description("敌人没有举盾时不执行，防止浪费耐久或卡顿。").defaultValue(true).build());
    private final Setting<Boolean> legitMode = sgGeneral.add(new BoolSetting.Builder().name("Legit模式 (Grim)").description("强制同步客户端本地槽位，绕过 Grim 的库存检测。").defaultValue(true).build());
    private final Setting<Boolean> autoReturn = sgGeneral.add(new BoolSetting.Builder().name("攻击后切回").description("完成后切回最初的武器。").defaultValue(true).build());

    public MaceBreakerPro() {
        super(AddonTemplate.CATEGORY, "没敌打断", "飞机大炮式。就是一个秒切模块，不能绕过反作弊");
    }

    @EventHandler
    private void onAttackEntity(AttackEntityEvent event) {
        if (event.entity == null || mc.player == null || mc.level == null) return;

        // 1. 目标举盾检测
        if (onlyOnShield.get()) {
            if (event.entity instanceof LivingEntity target) {
                // isBlocking 是 1.21.11 通用方法
                if (!target.isBlocking()) return; 
            } else {
                return; // 不是活物，肯定没盾
            }
        }

        // 2. 检查当前手持物品 (字符串判定)
        ItemStack handStack = mc.player.getMainHandItem();
        String handItem = handStack.getItem().toString().toLowerCase();
        
        boolean holdingSword = handItem.contains("sword");
        boolean holdingAxe = handItem.contains("_axe");
        boolean holdingMace = handItem.contains("mace");

        if (holdingSword && !swordTrigger.get()) return;
        if (holdingAxe && !axeTrigger.get()) return;
        if (holdingMace && !maceTrigger.get()) return;
        if (!holdingSword && !holdingAxe && !holdingMace) return;

        // 3. 寻找背包里的斧头和重锤
        FindItemResult axeRes = InvUtils.findInHotbar(s -> s.getItem().toString().toLowerCase().contains("_axe"));
        FindItemResult maceRes = InvUtils.findInHotbar(s -> s.getItem().toString().toLowerCase().contains("mace"));

        // 如果缺武器，直接停止
        if (!axeRes.found() || !maceRes.found()) return;

        int axeSlot = axeRes.slot();
        int maceSlot = maceRes.slot();
        int originalSlot = ((InventoryAccessor) mc.player.getInventory()).getSelectedSlot();

        // --- 核心执行阶段 ---
        event.cancel(); // 拦截原版那次平平无奇的攻击

        // 步骤 A: 切换到斧头并破盾
        performAttackStep(axeSlot, event.entity);

        // 步骤 B: 切换到重锤并猛击
        performAttackStep(maceSlot, event.entity);

        // 步骤 C: 收尾
        if (autoReturn.get()) {
            // 切回原位
            performSlotSwitch(originalSlot);
        } else {
            // 停留在重锤
            performSlotSwitch(maceSlot);
        }
        
        // 视觉效果：挥手一次，不然看起来像挂
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    /**
     * 执行单次攻击步骤：切槽 -> 攻击
     */
    private void performAttackStep(int slot, net.minecraft.world.entity.Entity target) {
        // 1. 切换槽位
        performSlotSwitch(slot);

        // 2. 发送攻击包
        // 这里不需要再 swingHand，因为我们在最后统一挥手
        mc.getConnection().send(new ServerboundAttackPacket(target.getId()));
    }

    /**
     * 核心切换逻辑：同时处理数据包和客户端本地状态 (Legit 核心)
     */
    private void performSlotSwitch(int slot) {
        if (slot == -1) return;
        
        // 如果当前已经在该槽位，无需操作
        if (((InventoryAccessor) mc.player.getInventory()).getSelectedSlot() == slot) return;

        // 1. 发送网络包 (告诉服务器我换了)
        mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));

        // 2. Legit 模式：强制修改客户端本地内存 (告诉 GrimAC 的模拟器我换了)
        if (legitMode.get()) {
            ((InventoryAccessor) mc.player.getInventory()).setSelectedSlot(slot);
        }
    }
}