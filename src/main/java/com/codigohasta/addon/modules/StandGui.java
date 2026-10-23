package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.standgui.StandGuiInput;
import com.codigohasta.addon.utils.standgui.StandGuiKeys;
import com.codigohasta.addon.utils.standgui.StandGuiRenderer;
import com.codigohasta.addon.utils.standgui.StandGuiState;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.orbit.EventHandler;

public class StandGui extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgLayout = settings.createGroup("布局");

    /** 键位方案（C10：二选一）。 */
    public enum KeyScheme {
        /** 有小键盘：小键盘区操作。 */
        Numpad("小键盘"),
        /** 无小键盘：F4 / 方向键 / 回车 / 退格 / 右Ctrl / 右Shift。 */
        NoNumpad("无小键盘");

        private final String label;

        KeyScheme(String label) { this.label = label; }

        @Override
        public String toString() { return label; }
    }

    private final Setting<KeyScheme> keyScheme = sgGeneral.add(new EnumSetting.Builder<KeyScheme>()
        .name("键位方案")
        .description("小键盘 = 小键盘区操作；无小键盘 = F4/方向键/回车/退格。")
        .defaultValue(KeyScheme.Numpad)
        .build());

    private final Setting<Boolean> showInScreens = sgGeneral.add(new BoolSetting.Builder()
        .name("界面打开时也显示")
        .description("背包/聊天等界面打开时，菜单仍然绘制（用户 B5/E11）。")
        .defaultValue(true)
        .build());

    /** 布局：X / Y / 缩放（B3）。 */
    private final Setting<Integer> menuX = sgLayout.add(new IntSetting.Builder()
        .name("X坐标")
        .description("菜单左上角 X（逻辑坐标）。")
        .defaultValue(20)
        .sliderRange(0, 2000)
        .build());

    private final Setting<Integer> menuY = sgLayout.add(new IntSetting.Builder()
        .name("Y坐标")
        .description("菜单左上角 Y（逻辑坐标）。")
        .defaultValue(40)
        .sliderRange(0, 2000)
        .build());

    private final Setting<Double> menuScale = sgLayout.add(new DoubleSetting.Builder()
        .name("缩放")
        .description("整体缩放。")
        .defaultValue(1.0)
        .min(0.5)
        .max(3.0)
        .sliderRange(0.5, 3.0)
        .build());

    /** 左栏最多显示多少行（用户：左栏太长了，要滚动）。 */
    private final Setting<Integer> leftRows = sgLayout.add(new IntSetting.Builder()
        .name("左栏可见行数")
        .description("左栏最多显示多少个分类，超出后用滚动条。")
        .defaultValue(9)
        .range(3, 30)
        .sliderRange(3, 30)
        .build());

    /** 右栏最多显示多少行（用户：右栏也太长了）。 */
    private final Setting<Integer> rightRows = sgLayout.add(new IntSetting.Builder()
        .name("右栏可见行数")
        .description("右栏最多显示多少项，超出后用滚动条。")
        .defaultValue(12)
        .range(3, 40)
        .sliderRange(3, 40)
        .build());

    /** 是否随窗口自适应缩小可见行数（B3）。 */
    private final Setting<Boolean> fitToWindow = sgLayout.add(new BoolSetting.Builder()
        .name("随窗口自适应")
        .description("窗口变矮时自动减少可见行数，保证菜单不超出屏幕。")
        .defaultValue(true)
        .build());

    // ═══════════ 内部状态 ═══════════

    /** 按住连发速度（用户：「stand的滑块是可以按住滑动」，速度可调）。 */
    private final Setting<Integer> holdStepMs = sgGeneral.add(new IntSetting.Builder()
        .name("滑动速度")
        .description("按住 4/6 或 ←/→ 时每多少毫秒调一步，越小越快。")
        .defaultValue(60)
        .range(10, 500)
        .sliderRange(10, 300)
        .build());

    /** 主题色（用户 B2：默认 Stand 品红，可自定义）。 */
    private final Setting<meteordevelopment.meteorclient.utils.render.color.SettingColor> accentColor =
        sgLayout.add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
            .name("主题色")
            .description("标题条 / 焦点行 / 描边 / 滚动条的颜色（Stand 默认是品红）。")
            .defaultValue(new meteordevelopment.meteorclient.utils.render.color.SettingColor(
                0xFF, 0x00, 0xFF, 255))
            .build());

   
    private final Setting<String> hiddenCategories = sgLayout.add(
        new meteordevelopment.meteorclient.settings.StringSetting.Builder()
            .name("隐藏的分类")
            .description("逗号分隔的分类原名，如：Combat, Movement, sfIMG。留空显示全部。")
            .defaultValue("")
            .build());

    private final StandGuiState state = new StandGuiState();
    private final StandGuiInput input = new StandGuiInput(state);
    private final StandGuiRenderer renderer = new StandGuiRenderer(state, input);

    public StandGui() {
        super(AddonTemplate.CATEGORY, "stand-gui",
            "仿 Stand 菜单的 HUD 菜单（三栏布局，小键盘/备用键两套操作）。");
    }

    private void syncHiddenCategories() {
        try {
            String raw = hiddenCategories.get();
            java.util.List<String> list = new java.util.ArrayList<>();
            if (raw != null && !raw.isBlank()) {
                for (String part : raw.split("[,，]")) {
                    if (!part.isBlank()) list.add(part.trim());
                }
            }

            if (!new java.util.HashSet<>(list).equals(com.codigohasta.addon.utils.standgui.StandGuiTree.hiddenCategories())) {
                com.codigohasta.addon.utils.standgui.StandGuiTree.setHiddenCategories(list);
            }
        } catch (Throwable ignored) { }
    }

    @Override
    public void onActivate() {
        // 模块刚开时菜单保持关闭，等用户按呼出键
        state.setVisible(false);
        AddonTemplate.LOG.info("[StandGui] activated; categories={}", StandGuiTreeDescribe());
    }

    @Override
    public void onDeactivate() {
        state.setVisible(false);
    }

  
    @Override
    public net.minecraft.nbt.NbtCompound toTag() {
        net.minecraft.nbt.NbtCompound tag = super.toTag();
        if (tag == null) return null;
        try {
            tag.put("standgui", com.codigohasta.addon.utils.standgui.StandGuiStorage
                .toTag(state.exportFocus()));
        } catch (Throwable t) {
            AddonTemplate.LOG.warn("[StandGui] toTag failed", t);
        }
        return tag;
    }

    @Override
    public meteordevelopment.meteorclient.systems.modules.Module fromTag(
        net.minecraft.nbt.NbtCompound tag) {
        super.fromTag(tag);
        try {
            java.util.Map<String, Integer> focus =
                com.codigohasta.addon.utils.standgui.StandGuiStorage
                    .fromTag(tag.getCompoundOrEmpty("standgui"));
            state.importFocus(focus);
        } catch (Throwable t) {
            AddonTemplate.LOG.warn("[StandGui] fromTag failed", t);
        }
        return this;
    }

    private static String StandGuiTreeDescribe() {
        return com.codigohasta.addon.utils.standgui.StandGuiTree.describe();
    }

    // ═══════════ 输入 ═══════════

    @EventHandler
    private void onKey(KeyEvent event) {
        if (mc.player == null) return;

        boolean numpad = keyScheme.get() == KeyScheme.Numpad;
        StandGuiKeys.Action action = StandGuiKeys.actionOf(event.key(), numpad);

     
        if (action == StandGuiKeys.Action.TOGGLE) {
            if (event.action != KeyAction.Release) {
                state.toggle();
                // 关菜单时清掉按住状态，避免下次打开"卡住一直滚"
                if (!state.visible()) input.releaseHold();
            }
            event.cancel();
            return;
        }

        // ── 其余键只在菜单可见时接管 ──
        if (!state.visible()) return;

    
        if (event.action == KeyAction.Press && state.isAwaitingSettingBind()) {
            boolean cancelKey = action == StandGuiKeys.Action.BACK
                || action == StandGuiKeys.Action.CONTEXT
                || event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
            if (cancelKey) {
                state.setAwaitingSettingBind(false);
                event.cancel();
                return;
            }
            input.bindSettingKey(event.key(), event.modifiers());
            event.cancel();
            return;
        }

        // ── Color 编辑子页：8/2 选分量、4/6 调值、0 退出 ──────────
        if (state.isColorSubpage()) {
            switch (action) {
                case UP -> { input.colorUp(); event.cancel(); }
                case DOWN -> { input.colorDown(); event.cancel(); }
                case LEFT -> { input.colorStep(-1); event.cancel(); }
                case RIGHT -> { input.colorStep(1); event.cancel(); }
                case BACK -> { state.setColorSubpage(false); event.cancel(); }
                case OK -> event.cancel();
                default -> { }
            }
            return;
        }


        Runnable repeatAction = repeatActionOf(action);
        if (repeatAction != null) {
            if (event.action == KeyAction.Press) {
                input.trackHold(repeatAction, true);
                repeatAction.run();
            } else if (event.action == KeyAction.Release) {
                input.trackHold(repeatAction, false);
            }
            event.cancel();
            return;
        }

        if (event.action == KeyAction.Repeat) { event.cancel(); return; }
        if (event.action == KeyAction.Release) return;   // 松开不重复执行

        if (state.inContext()) {
            switch (action) {
                case OK -> { input.contextConfirm(); event.cancel(); }
                case BACK -> { input.contextBack(); event.cancel(); }
                case CONTEXT -> { input.contextBack(); event.cancel(); }
                case COMMAND_BOX -> {
                    input.releaseHold();
                    com.codigohasta.addon.utils.standgui.StandGuiCommandScreen.open("");
                    event.cancel();
                }
                default -> { /* 其它键在操作界面里不响应，也不透传 */ }
            }
            return;
        }

        boolean handled = switch (action) {
            case OK -> input.confirm();
            case SETTINGS -> input.enter();     // 9 / RightAlt = 强制进入子节点
            case BACK -> input.back();
            case CONTEXT -> input.openContext(); // 3 = 打开操作界面（仅模块）
            case COMMAND_BOX -> {
                // 命令框：独立窗口，不预填（预填走「数值项按 5」那条路）
                input.releaseHold();
                com.codigohasta.addon.utils.standgui.StandGuiCommandScreen.open("");
                yield true;
            }
            default -> false;
        };

        if (handled) event.cancel();
    }

    private Runnable repeatActionOf(StandGuiKeys.Action action) {
        return switch (action) {
            case UP -> input::up;
            case DOWN -> input::down;
            case LEFT -> () -> input.step(-1);
            case RIGHT -> () -> input.step(1);
            case PREV_CAT -> input::prevCategory;
            case NEXT_CAT -> input::nextCategory;
            default -> null;
        };
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
  
        syncHiddenCategories();

        if (!state.visible()) return;
        input.setHoldStepMs(holdStepMs.get());
        input.tickHold(0L);

        com.codigohasta.addon.utils.standgui.StandGuiLinks.tick(state);
    }


    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!state.visible()) return;
        if (mc.player == null || mc.world == null) return;
        // 有界面时是否仍绘制，取决于用户的「界面打开时也显示」开关
        if (mc.currentScreen != null && !showInScreens.get()) return;

        drawMenu();
    }

 
    public void renderOnScreen(float scale) {
        if (!showInScreens.get()) return;
        if (!state.visible()) return;
        if (mc.currentScreen == null) return;
        drawMenu();
    }


    private void drawMenu() {
    
        try {
            var sc = accentColor.get();
            com.codigohasta.addon.utils.standgui.StandGuiRenderer.setAccent(
                new meteordevelopment.meteorclient.utils.render.color.Color(
                    sc.r, sc.g, sc.b, sc.a));
        } catch (Throwable ignored) {
 
        }

        int left = leftRows.get();
        int right = rightRows.get();

        if (fitToWindow.get()) {
            float scale = menuScale.get().floatValue();
            float rowPx = 15f * scale;
            float screenH = mc.getWindow().getScaledHeight();
            float reserve = 14f * scale + 2f + 12f * 2f * scale + 2f + 20f;
            int maxByScreen = (int) Math.max(3, (screenH - menuY.get() - reserve) / rowPx);
            left = Math.min(left, maxByScreen);
            right = Math.min(right, maxByScreen);
        }

        renderer.render(menuX.get(), menuY.get(), menuScale.get().floatValue(), left, right);
    }
}
