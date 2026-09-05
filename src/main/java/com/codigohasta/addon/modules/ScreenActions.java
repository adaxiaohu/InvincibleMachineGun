package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.mixin.InventoryAccessor;
import com.codigohasta.addon.utils.leaveshack.InventoryUtil;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;

/**
 * ScreenActions - 界面操作快捷键
 * 在 GUI 界面(背包/箱子/潜影盒等)打开时，按下自定义快捷键执行放置/破坏/交互/使用/攻击操作。
 * 全部使用直接发包。
 *
 * 按键检测：TickEvent.Pre + Keybind.isPressed() + 边缘检测
 * 此模式已在 AdaPacketMine.handleKeyToggles() 中验证有效
 */
public class ScreenActions extends Module {

    public enum ActionType {
        PLACE_BLOCK,
        BREAK_BLOCK,
        INTERACT_BLOCK,
        USE_ITEM,
        ATTACK_ENTITY
    }

    // ========= 设置 =========
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSlot1   = settings.createGroup("快捷键1");
    private final SettingGroup sgSlot2   = settings.createGroup("快捷键2");
    private final SettingGroup sgSlot3   = settings.createGroup("快捷键3");
    private final SettingGroup sgSlot4   = settings.createGroup("快捷键4");

    private final Setting<Integer> range;
    private final Setting<Boolean> autoSearch;
    private final Setting<Boolean> instantBreak;

    private final Setting<Keybind>    key1;
    private final Setting<ActionType> action1;
    private final Setting<Keybind>    key2;
    private final Setting<ActionType> action2;
    private final Setting<Keybind>    key3;
    private final Setting<ActionType> action3;
    private final Setting<Keybind>    key4;
    private final Setting<ActionType> action4;

    // 边缘检测状态（和 AdaPacketMine 同样的模式）
    private boolean wasPressed1, wasPressed2, wasPressed3, wasPressed4;

    public ScreenActions() {
        super(AddonTemplate.CATEGORY, "界面操作快捷键",
            "在GUI界面(背包/箱子等)打开时，按下自定义快捷键执行放置/破坏/交互/使用/攻击操作。这个功能有助于在一些用cmi插件的右键打开潜影盒的服务器上可以刷物品");

        range = sgGeneral.add(new IntSetting.Builder()
            .name("操作距离")
            .description("准星目标的最大操作距离")
            .defaultValue(4).min(1).sliderRange(1, 6).build()
        );
        autoSearch = sgGeneral.add(new BoolSetting.Builder()
            .name("自动搜索方块")
            .description("放置时自动从热键栏搜索方块，也支持鼠标拿起物品")
            .defaultValue(true).build()
        );
        instantBreak = sgGeneral.add(new BoolSetting.Builder()
            .name("瞬间破坏")
            .description("破坏时发送 STOP 包完成瞬间破坏")
            .defaultValue(true).build()
        );

        // 注意：EnumSetting 在 KeybindSetting 之前添加，保持 GUI 中操作选择在上方
        action1 = sgSlot1.add(new EnumSetting.Builder<ActionType>()
            .name("操作").defaultValue(ActionType.PLACE_BLOCK).build()
        );
        key1 = sgSlot1.add(new KeybindSetting.Builder()
            .name("键位").defaultValue(Keybind.none()).build()
        );

        action2 = sgSlot2.add(new EnumSetting.Builder<ActionType>()
            .name("操作").defaultValue(ActionType.BREAK_BLOCK).build()
        );
        key2 = sgSlot2.add(new KeybindSetting.Builder()
            .name("键位").defaultValue(Keybind.none()).build()
        );

        action3 = sgSlot3.add(new EnumSetting.Builder<ActionType>()
            .name("操作").defaultValue(ActionType.INTERACT_BLOCK).build()
        );
        key3 = sgSlot3.add(new KeybindSetting.Builder()
            .name("键位").defaultValue(Keybind.none()).build()
        );

        action4 = sgSlot4.add(new EnumSetting.Builder<ActionType>()
            .name("操作").defaultValue(ActionType.USE_ITEM).build()
        );
        key4 = sgSlot4.add(new KeybindSetting.Builder()
            .name("键位").defaultValue(Keybind.none()).build()
        );
    }

    // ========= 每 Tick 检测按键（和 AdaPacketMine.handleKeyToggles 相同模式） =========
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        checkSlot(key1, action1, wasPressed1);
        checkSlot(key2, action2, wasPressed2);
        checkSlot(key3, action3, wasPressed3);
        checkSlot(key4, action4, wasPressed4);
    }

    private void checkSlot(Setting<Keybind> key, Setting<ActionType> action, boolean wasPressed) {
        boolean isPressed = key.get().isPressed();
        if (isPressed && !wasPressed) {
            executeAction(action.get());
        }
        // 无法直接修改 wasPressed，改用字段
        setWasPressed(key, isPressed);
    }

    // 辅助：设置对应的 wasPressed 状态
    private void setWasPressed(Setting<Keybind> key, boolean pressed) {
        if (key == key1) wasPressed1 = pressed;
        else if (key == key2) wasPressed2 = pressed;
        else if (key == key3) wasPressed3 = pressed;
        else if (key == key4) wasPressed4 = pressed;
    }

    // ========= 执行操作 =========
    private void executeAction(ActionType action) {
        try {
            switch (action) {
                case PLACE_BLOCK    -> placeBlock();
                case BREAK_BLOCK    -> breakBlock();
                case INTERACT_BLOCK -> interactBlock();
                case USE_ITEM       -> useItem();
                case ATTACK_ENTITY  -> attackEntity();
            }
        } catch (Exception e) {
            error("执行操作异常: " + e.getMessage());
        }
    }

    // ========= 放置方块 =========
    private void placeBlock() {
        if (!(mc.hitResult instanceof BlockHitResult hit)
            || hit.getType() != HitResult.Type.BLOCK) return;
        if (mc.player.distanceToSqr(hit.getBlockPos().getCenter()) > range.get() * range.get()) return;

        int slot = getBlockSlot();
        if (slot == -1) return;

        int oldSlot = ((InventoryAccessor) mc.player.getInventory()).getSelectedSlot();
        if (slot != oldSlot) {
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
        }

        BlockHitResult result = new BlockHitResult(hit.getLocation(), hit.getDirection(), hit.getBlockPos(), false);
        mc.getConnection().send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, result, 0));
        mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));

        if (slot != oldSlot) {
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(oldSlot));
        }
    }

    // ========= 破坏方块 =========
    private void breakBlock() {
        if (!(mc.hitResult instanceof BlockHitResult hit)
            || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = hit.getBlockPos();
        if (mc.player.distanceToSqr(pos.getCenter()) > range.get() * range.get()) return;
        if (mc.level.getBlockState(pos).isAir()) return;

        mc.getConnection().send(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, hit.getDirection()));
        if (instantBreak.get()) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, hit.getDirection()));
        }
        mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
    }

    // ========= 交互方块 =========
    private void interactBlock() {
        if (!(mc.hitResult instanceof BlockHitResult hit)
            || hit.getType() != HitResult.Type.BLOCK) return;
        if (mc.player.distanceToSqr(hit.getBlockPos().getCenter()) > range.get() * range.get()) return;

        BlockHitResult result = new BlockHitResult(hit.getLocation(), hit.getDirection(), hit.getBlockPos(), false);
        mc.getConnection().send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, result, 0));
        mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
    }

    // ========= 使用物品 =========
    private void useItem() {
        mc.getConnection().send(new ServerboundUseItemPacket(
            InteractionHand.MAIN_HAND, 0, mc.player.getYRot(), mc.player.getXRot()));
        mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
    }

    // ========= 攻击实体 =========
    private void attackEntity() {
        if (!(mc.hitResult instanceof EntityHitResult hit)) return;
        if (mc.player.distanceToSqr(hit.getEntity().getX(), hit.getEntity().getY(), hit.getEntity().getZ())
            > range.get() * range.get()) return;

        mc.getConnection().send(new ServerboundAttackPacket(hit.getEntity().getId()));
        mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
    }

    // ========= 辅助方法 =========

    /** 获取放置方块用的槽位 */
    private int getBlockSlot() {
        int cur = ((InventoryAccessor) mc.player.getInventory()).getSelectedSlot();

        // 1) 当前槽位是方块
        if (mc.player.getInventory().getItem(cur).getItem() instanceof BlockItem) return cur;
        if (!autoSearch.get()) return -1;

        // 2) 鼠标拿起物品
        if (mc.player.containerMenu != null) {
            ItemStack cursor = mc.player.containerMenu.getCarried();
            if (cursor.getItem() instanceof BlockItem) {
                if (mc.player.getInventory().getItem(cur).isEmpty()) {
                    placeCursorToSlot(cur);
                    return cur;
                }
                for (int i = 0; i < 9; i++) {
                    if (mc.player.getInventory().getItem(i).isEmpty()) {
                        placeCursorToSlot(i);
                        return i;
                    }
                }
                placeCursorToSlot(cur);
                return cur;
            }
        }

        // 3) 热键栏搜索
        return InventoryUtil.findBlock();
    }

    /** 把鼠标拿起物品放进指定热键栏槽位 */
    private void placeCursorToSlot(int hotbarSlot) {
        mc.gameMode.handleContainerInput(
            mc.player.containerMenu.containerId,
            hotbarSlot + 36,
            0, ContainerInput.PICKUP, mc.player
        );
    }
}
