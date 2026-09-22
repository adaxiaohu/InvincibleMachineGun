package com.codigohasta.addon.utils.slimefunhelper.hacks.api;

import com.codigohasta.addon.utils.slimefunhelper.SfTranslations;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.settings.IntSetting;

/** Commit valid SF integer edits before a screen rebuild can discard the focused editor. */
public final class SfIntegerEditor {
    private SfIntegerEditor() {}

    public static void bind(IntSetting setting, WIntEdit editor) {
        if (!SfTranslations.isSourceSetting(setting)) return;
        for (var cell : editor.cells) {
            if (!(cell.widget() instanceof WTextBox text)) continue;
            Runnable previous = text.action;
            text.action = () -> {
                if (previous != null) previous.run();
                try {
                    int value = Integer.parseInt(text.get().trim());
                    if (value != setting.get()) setting.set(value);
                } catch (NumberFormatException ignored) {
                    // Empty text or a partial sign is an edit in progress, not a reset.
                }
            };
        }
    }
}
