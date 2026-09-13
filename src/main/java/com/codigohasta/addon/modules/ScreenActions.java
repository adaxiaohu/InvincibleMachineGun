package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.mixin.InventoryAccessor;
import com.codigohasta.addon.utils.leaveshack.InventoryUtil;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

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
        if (mc.player == null || mc.world == null) return;

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
        if (!(mc.crosshairTarget instanceof BlockHitResult hit)
            || hit.getType() != HitResult.Type.BLOCK) return;
        if (mc.player.squaredDistanceTo(hit.getBlockPos().toCenterPos()) > range.get() * range.get()) return;

        int slot = getBlockSlot();
        if (slot == -1) return;

        int oldSlot = ((InventoryAccessor) mc.player.getInventory()).getSelectedSlot();
        if (slot != oldSlot) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }

        BlockHitResult result = new BlockHitResult(hit.getPos(), hit.getSide(), hit.getBlockPos(), false);
        mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, result, 0));
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

        if (slot != oldSlot) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(oldSlot));
        }
    }

    // ========= 破坏方块 =========
    private void breakBlock() {
        if (!(mc.crosshairTarget instanceof BlockHitResult hit)
            || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = hit.getBlockPos();
        if (mc.player.squaredDistanceTo(pos.toCenterPos()) > range.get() * range.get()) return;
        if (mc.world.getBlockState(pos).isAir()) return;

        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, hit.getSide()));
        if (instantBreak.get()) {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, hit.getSide()));
        }
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
    }

    // ========= 交互方块 =========
    private void interactBlock() {
        if (!(mc.crosshairTarget instanceof BlockHitResult hit)
            || hit.getType() != HitResult.Type.BLOCK) return;
        if (mc.player.squaredDistanceTo(hit.getBlockPos().toCenterPos()) > range.get() * range.get()) return;

        BlockHitResult result = new BlockHitResult(hit.getPos(), hit.getSide(), hit.getBlockPos(), false);
        mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, result, 0));
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
    }

    // ========= 使用物品 =========
    private void useItem() {
        mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(
            Hand.MAIN_HAND, 0, mc.player.getYaw(), mc.player.getPitch()));
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
    }

    // ========= 攻击实体 =========
    private void attackEntity() {
        if (!(mc.crosshairTarget instanceof EntityHitResult hit)) return;
        if (mc.player.squaredDistanceTo(hit.getEntity().getX(), hit.getEntity().getY(), hit.getEntity().getZ())
            > range.get() * range.get()) return;

        mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(hit.getEntity(), mc.player.isSneaking()));
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
    }

    // ========= 辅助方法 =========

    /** 获取放置方块用的槽位 */
    private int getBlockSlot() {
        int cur = ((InventoryAccessor) mc.player.getInventory()).getSelectedSlot();

        // 1) 当前槽位是方块
        if (mc.player.getInventory().getStack(cur).getItem() instanceof BlockItem) return cur;
        if (!autoSearch.get()) return -1;

        // 2) 鼠标拿起物品
        if (mc.player.currentScreenHandler != null) {
            ItemStack cursor = mc.player.currentScreenHandler.getCursorStack();
            if (cursor.getItem() instanceof BlockItem) {
                if (mc.player.getInventory().getStack(cur).isEmpty()) {
                    placeCursorToSlot(cur);
                    return cur;
                }
                for (int i = 0; i < 9; i++) {
                    if (mc.player.getInventory().getStack(i).isEmpty()) {
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
        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId,
            hotbarSlot + 36,
            0, SlotActionType.PICKUP, mc.player
        );
    }
}
