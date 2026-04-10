package com.codigohasta.addon.utils;

import org.apache.commons.lang3.StringUtils;
import org.lwjgl.glfw.GLFW;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class KeyUtils {

    public static CharSequence getKey(int key) {
        switch (key) {
            case 2 -> {
                return "MMB";
            }
            case -1 -> {
                return "Unknown";
            }
            case 256 -> {
                return "Esc";
            }
            case 96 -> {
                return "Grave Accent";
            }
            case 161 -> {
                return "World 1";
            }
            case 162 -> {
                return "World 2";
            }
            case 283 -> {
                return "Print Screen";
            }
            case 284 -> {
                return "Pause";
            }
            case 260 -> {
                return "Insert";
            }
            case 261 -> {
                return "Delete";
            }
            case 268 -> {
                return "Home";
            }
            case 266 -> {
                return "Page Up";
            }
            case 267 -> {
                return "Page Down";
            }
            case 269 -> {
                return "End";
            }
            case 258 -> {
                return "Tab";
            }
            case 341 -> {
                return "Left Control";
            }
            case 345 -> {
                return "Right Control";
            }
            case 342 -> {
                return "Left Alt";
            }
            case 346 -> {
                return "Right Alt";
            }
            case 340 -> {
                return "Left Shift";
            }
            case 344 -> {
                return "Right Shift";
            }
            case 265 -> {
                return "Arrow Up";
            }
            case 264 -> {
                return "Arrow Down";
            }
            case 263 -> {
                return "Arrow Left";
            }
            case 262 -> {
                return "Arrow Right";
            }
            case 39 -> {
                return "Apostrophe";
            }
            case 259 -> {
                return "Backspace";
            }
            case 280 -> {
                return "Caps Lock";
            }
            case 348 -> {
                return "Menu";
            }
            case 343 -> {
                return "Left Super";
            }
            case 347 -> {
                return "Right Super";
            }
            case 257 -> {
                return "Enter";
            }
            case 335 -> {
                return "Numpad Enter";
            }
            case 282 -> {
                return "Num Lock";
            }
            case 32 -> {
                return "Space";
            }
            case 290 -> {
                return "F1";
            }
            case 291 -> {
                return "F2";
            }
            case 292 -> {
                return "F3";
            }
            case 293 -> {
                return "F4";
            }
            case 294 -> {
                return "F5";
            }
            case 295 -> {
                return "F6";
            }
            case 296 -> {
                return "F7";
            }
            case 297 -> {
                return "F8";
            }
            case 298 -> {
                return "F9";
            }
            case 299 -> {
                return "F10";
            }
            case 300 -> {
                return "F11";
            }
            case 301 -> {
                return "F12";
            }
            case 302 -> {
                return "F13";
            }
            case 303 -> {
                return "F14";
            }
            case 304 -> {
                return "F15";
            }
            case 305 -> {
                return "F16";
            }
            case 306 -> {
                return "F17";
            }
            case 307 -> {
                return "F18";
            }
            case 308 -> {
                return "F19";
            }
            case 309 -> {
                return "F20";
            }
            case 310 -> {
                return "F21";
            }
            case 311 -> {
                return "F22";
            }
            case 312 -> {
                return "F23";
            }
            case 313 -> {
                return "F24";
            }
            case 314 -> {
                return "F25";
            }
            case 281 -> {
                return "Scroll Lock";
            }
            case 91 -> {
                return "Left Bracket";
            }
            case 93 -> {
                return "Right Bracket";
            }
            case 59 -> {
                return "Semicolon";
            }
            case 61 -> {
                return "Equals";
            }
            case 92 -> {
                return "Backslash";
            }
            case 44 -> {
                return "Comma";
            }
            case 0 -> {
                return "LMB";
            }
            case 1 -> {
                return "RMB";
            }
        }
        String keyName = GLFW.glfwGetKeyName(key, 0);
        if (keyName == null) {
            return "None";
        }
        return StringUtils.capitalize(keyName);
    }

    public static boolean isKeyPressed(final int n) {
        if (n <= 8) {
            return GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), n) == 1;
        }
        return GLFW.glfwGetKey(mc.getWindow().getHandle(), n) == 1;
    }
}