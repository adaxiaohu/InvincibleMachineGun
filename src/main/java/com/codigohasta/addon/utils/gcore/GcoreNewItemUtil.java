package com.codigohasta.addon.utils.gcore;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.IntPredicate;

public final class GcoreNewItemUtil {

    private static final String NOT_FIND_ITEM_TEXT = "\u00a77can't find item \u00a72";

    /** 主背包总格数（0..8 快捷栏，9..35 主区）。与 {@code getMainStacks()} 长度一致。 */
    public static final int MAIN_SIZE = 36;

    private GcoreNewItemUtil() {
    }

    // ─────────────────────── 范围 ───────────────────────

    /** 快捷栏范围 0..8。 */
    public static GcoreIntRange getHotBarRange() {
        return new GcoreIntRange(0, 8);
    }

    /** 背包范围 9..35。 */
    public static GcoreIntRange getInvRange() {
        return new GcoreIntRange(9, 35);
    }

    /** 全部槽位 0..35。 */
    public static GcoreIntRange getAll() {
        return new GcoreIntRange(0, 35);
    }

    // ─────────────────────── 读真实背包 ───────────────────────

    /**
     * 读真实背包第 {@code invIndex} 格（0..35）。
     *
     * <p>越界或没有玩家时返回 {@link ItemStack#EMPTY}。
     */
    public static ItemStack realStack(int invIndex) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || invIndex < 0 || invIndex >= MAIN_SIZE) {
            return ItemStack.EMPTY;
        }
        return player.getInventory().getMainStacks().get(invIndex);
    }

    /** 当前选中的快捷栏槽位（0..8）。 */
    public static int selectedSlot() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return player == null ? 0 : player.getInventory().getSelectedSlot();
    }

    /** 真实背包里是否有该物品（查找范围按 {@code opt} 的 mode）。 */
    public static boolean hasItem(GcoreSwapItemOption opt, Item item) {
        return findItem(opt, item) != -1;
    }

    // ─────────────────────── 找物品 ───────────────────────

    /**
     * 在 {@code opt} 的查找范围内找第一个该物品的槽。
     *
     * <p>按 {@code getItem()} 比较**类型**（不是实例）—— 见类注释「怎么正确找到所需物品」。
     */
    public static int findItem(GcoreSwapItemOption opt, Item item) {
        int slot = findItem(opt.getSlotMode().getFindIntRange(selectedSlot()),
            i -> Objects.equals(realStack(i).getItem(), item));
        if (slot == -1 && opt.getPrint()) {
            GcoreChatUtils.chat(NOT_FIND_ITEM_TEXT + item.getName().getString());
        }
        return slot;
    }

    /** 见 {@link #findItem(GcoreSwapItemOption, Item)}，按自定义条件匹配（不带聊天提示）。 */
    public static int findItem(GcoreSwapItemOption opt, IntPredicate invoke) {
        return findItem(opt.getSlotMode().getFindIntRange(selectedSlot()), invoke);
    }

    /** 见 {@link #findItem}，按 raw id 匹配。 */
    public static int findItemById(GcoreSwapItemOption opt, int id) {
        return findItemById(opt.getSlotMode().getFindIntRange(selectedSlot()),
            id, opt.getPrint());
    }

    /** 见 {@link #findItemById}。 */
    public static int findItemById(GcoreIntRange range, int id, boolean print) {
        int slot = findItem(range, i -> Item.getRawId(realStack(i).getItem()) == id);
        if (slot == -1 && print) {
            GcoreChatUtils.chat(NOT_FIND_ITEM_TEXT + Item.byRawId(id).getName().getString());
        }
        return slot;
    }

    public static int findItem(GcoreIntRange range, IntPredicate invoke) {
        int first = Math.min(range.getStart(), range.getStep());
        int last = Math.max(range.getStart(), range.getStep());
        for (int i = first; i <= last; i++) {
            if (invoke.test(i)) {
                return i;
            }
        }
        return -1;
    }

    // ─────────────────────── 切换 / 还原 ───────────────────────

    /**
     * 把真实背包第 {@code slot} 格换到**当前选中的快捷栏槽**，并返回一个「换回去」的
     * {@link Runnable}。
     *
     * <p>用的是 {@code interactionManager.clickSlot(syncId, 屏幕槽, 选中槽, SWAP, player)}
     * —— 与 sfIMG {@code InvExtra.swapInventoryIndexToHand} 同一手法。
     * {@code clickSlot} 会就地改动本地背包，所以本地 / 服务端自动一致。
     *
     * <p>还原就是**再来一次完全相同的 SWAP**（SWAP 是对合运算），因此不需要记住
     * 「原来手里是什么」—— 这正是旧实现栽跟头的地方。
     *
     * @return 还原用的 {@code Runnable}；{@code slot} 已在手上时返回**空操作**；
     *         无法解析屏幕槽位（无玩家 / 索引越界）时返回 {@code null}（调用方应跳过）。
     */
    public static Runnable swapTo(int slot) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null) {
            return null;
        }

        int selected = player.getInventory().getSelectedSlot();
        if (slot < 0 || slot >= MAIN_SIZE || slot == selected) {
            return () -> {
            }; // 已经在手上，无需切换
        }

        OptionalInt slotIndex = player.currentScreenHandler
            .getSlotIndex(player.getInventory(), slot);
        if (slotIndex.isEmpty()) {
            return null;
        }
        int screenSlot = slotIndex.getAsInt();
        if (screenSlot < 0) {
            return null;
        }

        int syncId = player.currentScreenHandler.syncId;
        clickSwap(syncId, screenSlot, selected);

        // ★ 对称还原：同一个 SWAP 再点一次
        return () -> clickSwap(syncId, screenSlot, selected);
    }

    /**
     * 发一个 {@code SWAP} 点击。
     *
     * <p>用 {@code interactionManager.clickSlot} 而不是自己拼
     * {@code ClickSlotC2SPacket} —— 前者会**同时**更新本地背包并发送包
     * （{@code ClientPlayerInteractionManager.clickSlot} 内部先
     * {@code screenHandler.onSlotClick(...)} 再发包）。旧实现只发包、再手工模拟，
     * 那正是分歧的温床。
     */
    private static void clickSwap(int syncId, int screenSlot, int button) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null) {
            return;
        }
        mc.interactionManager.clickSlot(syncId, screenSlot, button, SlotActionType.SWAP, mc.player);
    }

    // ─────────────────────── swapUse 系列 ───────────────────────

    /**
     * 切到指定物品 → 执行动作 → 切回。
     *
     * <p>找不到物品时**不执行动作**；动作抛异常时也会走还原（{@code finally}）。
     */
    public static void swapUse(GcoreSwapItemOption swapItemOption, Item item, Runnable action) {
        swapUse(swapItemOption,
            i -> Objects.equals(realStack(i).getItem(), item),
            action);
    }

    /** 见 {@link #swapUse}，按 raw item id 匹配。 */
    public static void swapUseById(GcoreSwapItemOption swapItemOption, int id, Runnable action) {
        swapUse(swapItemOption,
            i -> Item.getRawId(realStack(i).getItem()) == id,
            action);
    }

    /** 见 {@link #swapUse}，按槽位直接操作。 */
    public static void swapUseBySlot(int slot, Runnable action) {
        Runnable undo = swapTo(slot);
        if (undo == null) {
            return;
        }
        try {
            action.run();
        } finally {
            undo.run();
        }
    }

    /** 切到第一个满足条件的物品 → 执行动作 → 切回。 */
    public static void swapUse(GcoreSwapItemOption swapItemOption, IntPredicate findInvoke,
                               Runnable action) {
        int findSlot = findItem(
            swapItemOption.getSlotMode().getFindIntRange(selectedSlot()), findInvoke);
        if (findSlot == -1) {
            return;
        }
        Runnable undo = swapTo(findSlot);
        if (undo == null) {
            return;
        }
        try {
            action.run();
        } finally {
            undo.run();
        }
    }
}
