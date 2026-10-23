package com.codigohasta.addon.utils.standgui;

import net.minecraft.client.MinecraftClient;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 「关联状态」——条件驱动的自动开关（用户 D1(a) / D2 / E10）。
 *
 * <h2>★ 它不是「A 开就开 B」</h2>
 * <p>用户最初理解为「开启这个模块时一并开启关联的模块」，但查阅 Stand 的实际行为后确认
 * （见 {@code standdep} + [选项设置教程](https://www.kancloud.cn/pandrao/zxc12566/3040901)）：
 * <blockquote>
 *   关联状态：代表触发下方行为时，该选项将更改状态为「是」<br>
 *   无 / 当 Stand 打开时 / 步行时 / 瞄准时<br>
 *   反转关联状态：将上述的条件取反
 * </blockquote>
 * <p>用户已确认按官方行为做（D1(a)）。
 *
 * <h2>条件到 MC 的映射（D2，用户已确认）</h2>
 * <table>
 *   <tr><td>菜单打开时</td><td>{@link StandGuiState#visible()}</td></tr>
 *   <tr><td>步行时</td><td>在地面 且 不在载具 且 未滑翔 且 未游泳</td></tr>
 *   <tr><td>瞄准时</td><td>按住「使用/攻击」键（MC 没有真正的瞄准键）</td></tr>
 * </table>
 *
 * <h2>为什么每 tick 求值而不是每帧</h2>
 * <p>这个功能会<b>改写模块的开关状态</b>。若每帧（可达 144Hz）都调用
 * {@code Module.toggle()}，会疯狂触发开关通知、GL 事件与网络包。
 * tick（20Hz）已经足够灵敏，且与 Meteor 模块的常规节奏一致。
 */
public final class StandGuiLinks {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    /** 4 个关联条件（用户截图里的「关联状态: 无」）。 */
    public enum Link {
        无("无"),
        菜单打开时("菜单打开时"),
        步行时("步行时"),
        瞄准时("瞄准时");

        /** 菜单上显示的中文名。 */
        public final String label;

        Link(String label) { this.label = label; }

        /** 循环到下一个。 */
        public Link next() {
            Link[] all = values();
            return all[(ordinal() + 1) % all.length];
        }
    }

    /** 单个选项的关联配置。 */
    public static final class LinkState {
        public Link link = Link.无;
        public boolean inverted;
    }

    /** path -> 配置。 */
    private static final Map<String, LinkState> LINKS = new HashMap<>();
    /** 上次由关联逻辑写入的状态，用于「只在变化时写」避免反复 toggle。 */
    private static final Map<String, Boolean> LAST_WRITTEN = new HashMap<>();
    /** 由关联逻辑强制关闭的项（条件消失时要恢复）。 */
    private static final Set<String> TOUCHED = new HashSet<>();

    private StandGuiLinks() {}

    /** 取某项的配置（不存在则创建默认）。 */
    public static LinkState get(String path) {
        return LINKS.computeIfAbsent(path, k -> new LinkState());
    }

    /** 循环关联状态。 */
    public static void cycle(String path) {
        LinkState st = get(path);
        st.link = st.link.next();
        // 切回「无」时，如果之前被关联逻辑改过，清掉跟踪
        if (st.link == Link.无) {
            LAST_WRITTEN.remove(path);
        }
    }

    /** 反转开关。 */
    public static void toggleInvert(String path) {
        LinkState st = get(path);
        st.inverted = !st.inverted;
    }

    /**
     * 每 tick 求值并应用。
     *
     * <p>只在「期望状态」与「上次写入」不同时才真正 {@code toggle()}，
     * 这是避免每 tick 刷通知/刷网络的关键。
     *
     * @param state 菜单状态（判断「菜单打开时」）
     */
    public static void tick(StandGuiState state) {
        if (LINKS.isEmpty()) return;

        boolean menuOpen = state != null && state.visible();
        boolean onGround = isOnGround();
        boolean aiming = isAiming();

        for (Map.Entry<String, LinkState> e : LINKS.entrySet()) {
            String path = e.getKey();
            LinkState st = e.getValue();
            if (st.link == Link.无) continue;

            boolean cond = switch (st.link) {
                case 菜单打开时 -> menuOpen;
                case 步行时 -> onGround;
                case 瞄准时 -> aiming;
                default -> false;
            };
            if (st.inverted) cond = !cond;

            Boolean last = LAST_WRITTEN.get(path);
            if (last != null && last == cond) continue;   // 没变化，不写

            LAST_WRITTEN.put(path, cond);
            applyTo(path, cond);
        }
    }

    /** 把条件结果写进目标项的「是/否」。 */
    private static void applyTo(String path, boolean on) {
        StandGuiEntry target = StandGuiTree.findByPath(path);
        if (target == null) return;

        if (target.kind == StandGuiEntry.Kind.MODULE && target.module != null) {
            if (target.module.isActive() != on) {
                target.module.toggle();
                TOUCHED.add(path);
            }
            return;
        }
        if (target.kind == StandGuiEntry.Kind.SETTING
            && target.setting instanceof meteordevelopment.meteorclient.settings.BoolSetting bs) {
            Boolean cur = bs.get();
            if ((cur != null && cur) != on) {
                bs.set(on);
                TOUCHED.add(path);
            }
        }
    }

    // ═══════════ 条件求值 ═══════════

    /** 步行时：在地面 + 不在载具 + 未滑翔 + 未游泳。 */
    private static boolean isOnGround() {
        if (mc.player == null) return false;
        return mc.player.isOnGround()
            && !mc.player.hasVehicle()
            && !mc.player.isGliding()
            && !mc.player.isSwimming();
    }

    /** 瞄准时：按住右键（MC 的 useKey）。 */
    private static boolean isAiming() {
        if (mc.player == null || mc.options == null) return false;
        return mc.options.useKey.isPressed();
    }

    // ═══════════ 持久化（后续接 NBT）═══════════

    public static Map<String, LinkState> exportAll() {
        return new HashMap<>(LINKS);
    }

    public static void importAll(Map<String, LinkState> in) {
        LINKS.clear();
        if (in != null) LINKS.putAll(in);
    }

    /** 清空（模块关闭时）。 */
    public static void clear() {
        LINKS.clear();
        LAST_WRITTEN.clear();
        TOUCHED.clear();
    }
}
