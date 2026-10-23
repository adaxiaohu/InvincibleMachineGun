package com.codigohasta.addon.utils.standgui;

import org.lwjgl.glfw.GLFW;

public final class StandGuiKeys {

    private StandGuiKeys() {}

    // ═══════════ 小键盘方案 ═══════════

    public static final int NP_TOGGLE = GLFW.GLFW_KEY_KP_ADD;          // 334
    public static final int NP_UP     = GLFW.GLFW_KEY_KP_8;            // 328
    public static final int NP_DOWN   = GLFW.GLFW_KEY_KP_2;            // 322
    public static final int NP_LEFT   = GLFW.GLFW_KEY_KP_4;            // 324
    public static final int NP_RIGHT  = GLFW.GLFW_KEY_KP_6;            // 326
    public static final int NP_OK     = GLFW.GLFW_KEY_KP_5;            // 325
    public static final int NP_SETTINGS = GLFW.GLFW_KEY_KP_9;          // 329
    public static final int NP_CONTEXT  = GLFW.GLFW_KEY_KP_3;          // 323
    public static final int NP_PREV_CAT = GLFW.GLFW_KEY_KP_7;          // 327
    public static final int NP_NEXT_CAT = GLFW.GLFW_KEY_KP_1;          // 321
    public static final int NP_BACK   = GLFW.GLFW_KEY_KP_0;            // 320

    // ═══════════ 无小键盘方案（B1）═══════════

    public static final int ALT_TOGGLE   = GLFW.GLFW_KEY_F4;           // 293
    public static final int ALT_UP       = GLFW.GLFW_KEY_UP;           // 265
    public static final int ALT_DOWN     = GLFW.GLFW_KEY_DOWN;         // 264
    public static final int ALT_LEFT     = GLFW.GLFW_KEY_LEFT;         // 263
    public static final int ALT_RIGHT    = GLFW.GLFW_KEY_RIGHT;        // 262
    public static final int ALT_OK       = GLFW.GLFW_KEY_ENTER;        // 257（原版无绑定）
    public static final int ALT_SETTINGS = GLFW.GLFW_KEY_RIGHT_ALT;    // 346
    public static final int ALT_CONTEXT  = GLFW.GLFW_KEY_F6;           // 295
    public static final int ALT_PREV_CAT = GLFW.GLFW_KEY_RIGHT_SHIFT;  // 344
    public static final int ALT_NEXT_CAT = GLFW.GLFW_KEY_RIGHT_CONTROL;// 345
    public static final int ALT_BACK     = GLFW.GLFW_KEY_BACKSPACE;    // 259

    /** 命令框（E4(b)：独立可配，默认波浪键 `` ` ``）。 */
    public static final int COMMAND_BOX = GLFW.GLFW_KEY_GRAVE_ACCENT;  // 96

    /** 动作枚举。 */
    public enum Action {
        TOGGLE, UP, DOWN, LEFT, RIGHT, OK, SETTINGS, CONTEXT,
        PREV_CAT, NEXT_CAT, BACK, COMMAND_BOX, NONE
    }

    /**
     * 码 → 动作。
     *
     * @param key      GLFW 键码
     * @param numpad   true = 小键盘方案，false = 无小键盘方案
     */
    public static Action actionOf(int key, boolean numpad) {
        if (key == COMMAND_BOX) return Action.COMMAND_BOX;

        if (numpad) {
            return switch (key) {
                case NP_TOGGLE -> Action.TOGGLE;
                case NP_UP -> Action.UP;
                case NP_DOWN -> Action.DOWN;
                case NP_LEFT -> Action.LEFT;
                case NP_RIGHT -> Action.RIGHT;
                case NP_OK -> Action.OK;
                case NP_SETTINGS -> Action.SETTINGS;
                case NP_CONTEXT -> Action.CONTEXT;
                case NP_PREV_CAT -> Action.PREV_CAT;
                case NP_NEXT_CAT -> Action.NEXT_CAT;
                case NP_BACK -> Action.BACK;
                default -> Action.NONE;
            };
        }

        return switch (key) {
            case ALT_TOGGLE -> Action.TOGGLE;
            case ALT_UP -> Action.UP;
            case ALT_DOWN -> Action.DOWN;
            case ALT_LEFT -> Action.LEFT;
            case ALT_RIGHT -> Action.RIGHT;
            case ALT_OK -> Action.OK;
            case ALT_SETTINGS -> Action.SETTINGS;
            case ALT_CONTEXT -> Action.CONTEXT;
            case ALT_PREV_CAT -> Action.PREV_CAT;
            case ALT_NEXT_CAT -> Action.NEXT_CAT;
            case ALT_BACK -> Action.BACK;
            default -> Action.NONE;
        };
    }

    /** 键名（用于设置界面显示）。 */
    public static String nameOf(Action a) {
        return switch (a) {
            case TOGGLE -> "呼出/隐藏";
            case UP -> "上移";
            case DOWN -> "下移";
            case LEFT -> "数值-";
            case RIGHT -> "数值+";
            case OK -> "确定";
            case SETTINGS -> "进模块设置";
            case CONTEXT -> "操作界面";
            case PREV_CAT -> "上翻分类";
            case NEXT_CAT -> "下翻分类";
            case BACK -> "返回";
            case COMMAND_BOX -> "命令框";
            case NONE -> "无";
        };
    }
}
