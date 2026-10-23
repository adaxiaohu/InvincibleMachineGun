package com.codigohasta.addon.utils.gcore;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.IntPredicate;

/**
 * 切物品工具 —— **真实背包版**（方案 2，取代影子背包）。
 *
 * <p>移植自 {@code dev.godden.core.Utils.NewItemUtil}（GCore），但**发包层已重写**：
 * 用户明确要求「参考 sfIMG 的鬼手切换（鞘翅追人/鞘翅增强那套）」，于是这里照
 * {@code modules/slimefunhelper/InvExtra} 的成熟做法重做。
 *
 * <h2>为什么不再用影子背包</h2>
 * 源实现维护一份 36 格副本（{@code ItemManager.SimulationPlayerInventory}），
 * 每次切换都靠 {@code operation()} **手工模拟**槽位互换。只要模拟与真实有一丝出入，
 * 二者就**永久分歧**。真实翻车点（用户实测「空手时 swap 失效」）：
 * <pre>
 * // 旧收尾：用「原手持**物品**」反查「原来哪只**手**」
 * findItem(range, i -&gt; simulationInv.getSlotByIndex(i) == originalItemStack);
 * </pre>
 * 空手时 {@code originalItemStack == ItemStack.EMPTY}，而 {@code EMPTY} 是**全局单例**
 * —— 每个空槽都指向它，于是命中**第一个空槽**而不是真正的手。随后
 * {@code swapTo} 走 {@code slot <= 8} 分支发 {@code UpdateSelectedSlotC2SPacket}
 * （服务端换了手持槽），而 {@code operation()} 对 {@code SwapInv} 的该分支是 no-op
 * —— 影子与真实从此分歧，后续所有 SWAP 的 button 都用错。
 *
 * <h2>新做法（与 InvExtra 同源）</h2>
 * <ol>
 *     <li><b>真相来源 = 真实客户端背包</b>，不再有副本，因此不存在分歧。</li>
 *     <li><b>切换用 {@code interactionManager.clickSlot(..., SWAP, ...)}</b> ——
 *         它会**就地改动本地背包**（{@code ClientPlayerInteractionManager} 里
 *         {@code screenHandler.onSlotClick(...)}），客户端与服务端自动一致，
 *         不需要任何手工模拟。</li>
 *     <li><b>还原靠「对称 SWAP」而不是「找回原物品」</b>：同一个
 *         {@code clickSlot} 再点一次即可复位（SWAP 是对合运算）。返回一个
 *         {@code Runnable} 让调用方在动作后执行 —— 于是旧代码那个易错的
 *         「切回原手持槽」步骤**整个消失**。</li>
 * </ol>
 *
 * <h2>怎么正确找到所需物品（用户特别问的点）</h2>
 * <ol>
 *     <li><b>按物品「类型」找，绝不按「实例」找</b>：
 *         {@code stack.getItem() == Items.RESPAWN_ANCHOR}。
 *         空手的 {@code getItem()} 是 {@code Items.AIR}，因此空手/满手**都不受影响**
 *         —— 这才是免疫 {@code ItemStack.EMPTY} 单例陷阱的根本。</li>
 *     <li><b>找到后记住「槽号」，还原时不再查找</b>。</li>
 *     <li><b>尊重 {@link GcoreEnumType.ItemMode} 给出的查找范围</b>
 *         （HotBar 0..8 / Inv、SwapInv 0..35 / None 仅选中槽）。</li>
 *     <li><b>处理三种边界</b>：命中槽 == 当前选中槽 → 无需交换；
 *         找不到 → 不动作（可按 {@code Print} 提示）；槽位解析失败 → 返回
 *         {@code null}，由调用方跳过。</li>
 * </ol>
 *
 * <p><b>★ 与源的差异（如实记录）</b>：
 * <ul>
 *     <li>{@code ItemMode.Inv} 原用 {@code PickFromInventoryC2SPacket}，该包在
 *         1.21.11 **已被移除**，故与 {@code SwapInv} 统一为 SWAP。</li>
 *     <li>{@code ItemMode.HotBar} 原语义是「直接切选中槽」（发
 *         {@code UpdateSelectedSlotC2SPacket}），现也统一为 SWAP —— 因为
 *         {@code UpdateSelectedSlot} 正是旧实现分歧的来源，而 SWAP 是
 *         sfIMG 线上**已验证可用**的那条路径。对用户可见的结果（物品到手并生效）
 *         完全一致；查找范围仍严格按 mode 走。</li>
 *     <li>{@code SwapInv} 与 {@code Inv} 的实现现在**完全收敛为同一条路径**。</li>
 * </ul>
 *
 * <p><b>★ 线程约束（重要）</b>：本类所有方法都会**触碰真实背包 / 发包**，
 * 因此**只能在客户端主线程调用**。TP 锚的规划跑在虚拟线程上，所以调用点必须
 * 把「执行段」交回主线程（见 {@code TpAnchorAura} 的 {@code mc.execute}）。
 */
public final class GcoreNewItemUtil {

    /**
     * 找不到物品时的提示前缀。
     *
     * <p>取值与源常量完全一致（从 class 文件常量池读出）：
     * <pre>
     * public static final String notFindItemText = "\u00a77can't find item \u00a72";
     * </pre>
     */
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

    /**
     * 在范围内找第一个满足条件的槽。
     *
     * <p>{@code GcoreIntRange} 是闭区间；源的 {@code getFirst()/getLast()} 对应
     * 本实现里 min/max 归一化后的两端（源里 HotBar/Inv/SwapInv 的区间本来就是
     * 升序，所以语义一致）。
     */
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
