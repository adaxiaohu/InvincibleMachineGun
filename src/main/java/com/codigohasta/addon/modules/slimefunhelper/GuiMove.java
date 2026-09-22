package com.codigohasta.addon.modules.slimefunhelper;

import com.codigohasta.addon.utils.slimefunhelper.events.Event;
import com.codigohasta.addon.utils.slimefunhelper.events.Listener;
import com.codigohasta.addon.utils.slimefunhelper.events.impl.KeyboardAction;

import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import com.codigohasta.addon.mixin.slimefunhelper.SfWidgetScreenAccessor;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.BaseModule;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.ModulePath;
import com.codigohasta.addon.utils.slimefunhelper.managers.Configs;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.FlagRef;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.KeyBindRef;
import com.codigohasta.addon.utils.slimefunhelper.managers.input.MultiKeyBind;
import com.codigohasta.addon.utils.slimefunhelper.managers.input.SimpleInputManager;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.*;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

public class GuiMove extends BaseModule {
    public GuiMove() {
        super("GuiMove");
        bindFlag(enable);
    }

    public ModulePath path = makePath(Configs.INV_CONFIG, "inventory.gui-move");

    public final FlagRef enable = flagBuilder(path.addEnable()).build();

    public final KeyBindRef hotkey =
            moduleEntry(path.addHotkey(), new MultiKeyBind(), path.addEnable()).build();

    public final FlagRef allGui = flagBuilder(path.add("all-gui-move")).build();

    public final FlagRef noShiftInChest = builder(path.add("no-shift-in-chest"), FlagRef.TYPE)
            .defaultValue(true)
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getKeyboardInput(), this::onKeyInput);
        registerListener(Listener.getPostSetScreen(), this::onPostSetScreen);
    }

    public KeyBinding[] inputBindings;
    public KeyBinding[] inputBindingsNoSneak;

    private void initBinding() {
        if (inputBindings == null || inputBindingsNoSneak == null) {
            inputBindings = new KeyBinding[] {
                mc.options.forwardKey,
                mc.options.backKey,
                mc.options.leftKey,
                mc.options.rightKey,
                mc.options.jumpKey,
                mc.options.sneakKey,
                mc.options.sprintKey
            };
            inputBindingsNoSneak = new KeyBinding[] {
                mc.options.forwardKey,
                mc.options.backKey,
                mc.options.leftKey,
                mc.options.rightKey,
                mc.options.jumpKey,
                mc.options.sprintKey
            };
        }
    }

    public KeyBinding[] getBindings() {
        initBinding();
        return noShiftInChest.get() && mc.currentScreen instanceof HandledScreen<?>
                ? inputBindingsNoSneak
                : inputBindings;
    }

    public void onKeyInput(Event<KeyboardAction> eventInput) {
        if (checkNull()) return;
        if (enable.get()) {
            if (skip()) return;
            int keyCode = eventInput.context.keyCode();
            int action = eventInput.context.action();
            for (var re : getBindings()) {
                if (handle(re, keyCode, action)) {}
            }
        }
    }

    public boolean handle(KeyBinding keyBinding, int keyCode, int action) {
        if (keyBinding.boundKey.getCode() != keyCode) {
            return false;
        }
        if (action == GLFW.GLFW_PRESS) {
            keyBinding.setPressed(true);
            return true;
        } else if (action == GLFW.GLFW_RELEASE) {
            keyBinding.setPressed(false);
            return true;
        }
        return false;
    }

    public void onPostSetScreen(Event<Screen> event) {
        if (checkNull()) return;
        if (enable.get() && event.context != null) {
            initBinding();
            for (var re : getBindings()) {
                re.setPressed(SimpleInputManager.getInstance().isKeyPressed(re.boundKey.getCode()));
            }
        }
    }

    public boolean skip() {
        if (mc.currentScreen == null
                || mc.currentScreen instanceof CreativeInventoryScreen
                || mc.currentScreen instanceof ChatScreen
                || mc.currentScreen instanceof SignEditScreen
                || mc.currentScreen instanceof AnvilScreen
                || mc.currentScreen instanceof CommandBlockScreen
                || mc.currentScreen instanceof StructureBlockScreen
                || mc.currentScreen.getFocused() instanceof TextFieldWidget
                || (mc.currentScreen instanceof WidgetScreen screen && checkCustomWidget(((SfWidgetScreenAccessor)screen).sf$getRoot())))
            return true;
        if (allGui.get()) return false;
        return !(mc.currentScreen instanceof HandledScreen<?>);
    }

    public boolean checkCustomWidget(WWidget widget) {
        if(widget instanceof WTextBox box && box.isFocused())return true;
        if(widget instanceof WContainer container)for(var cell:container.cells)if(checkCustomWidget(cell.widget()))return true;
        return false;
    }

    @Override public void onDisableModule() {
        if (mc.options == null) return;
        initBinding();
        for (var key : inputBindings) key.setPressed(mc.currentScreen == null && SimpleInputManager.getInstance().isKeyPressed(key.boundKey.getCode()));
    }
}
