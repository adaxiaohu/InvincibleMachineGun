package com.codigohasta.addon.utils.slimefunhelper.hooks.impl.baritone;

import java.util.ArrayList;
import java.util.List;

public class BaritoneFuture {
    final List<Runnable> onCompleteFutures = new ArrayList<>();
    final List<Runnable> onCancelFutures = new ArrayList<>();

    public List<Runnable> getOnCompleteFutures() { return onCompleteFutures; }
    public List<Runnable> getOnCancelFutures() { return onCancelFutures; }

    public void onComplete() {
        onCompleteFutures.forEach(Runnable::run);
    }

    public void onCancel() {
        onCancelFutures.forEach(Runnable::run);
    }
}
