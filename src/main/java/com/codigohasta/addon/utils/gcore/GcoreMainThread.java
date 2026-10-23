package com.codigohasta.addon.utils.gcore;

import net.minecraft.client.MinecraftClient;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class GcoreMainThread {

    /**
     * 等待主线程执行的超时（毫秒）。
     *
     * <p>主线程每 tick 都会跑，正常情况下几十毫秒内就轮到了。
     * 设超时是为了**防止虚拟线程永久卡死**（例如玩家断线 / 主线程卡在加载界面时），
     * 宁可本次 TP 放弃，也不要泄漏一个永远等下去的线程。
     */
    private static final long TIMEOUT_MS = 2000L;

    private GcoreMainThread() {
    }

    /**
     * 若当前不在客户端主线程，则把 {@code task} 投递到主线程并**等待其完成**；
     * 若已经在主线程，则**就地执行**。
     *
     * <p>任务抛出的异常会被包装成 {@link RuntimeException} 重新抛出到调用方线程，
     * 这样虚拟线程的 {@code catch (Throwable)} 仍然能兜住并记录。
     */
    public static void runAndWait(Runnable task) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.isOnThread()) {
            task.run();
            return;
        }

        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        mc.execute(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });

        try {
            if (!done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException(
                    "GcoreMainThread: 主线程在 " + TIMEOUT_MS + "ms 内没有执行到本任务，已放弃");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GcoreMainThread: 等待主线程时被中断", e);
        }

        Throwable t = failure.get();
        if (t != null) {
            throw new RuntimeException("GcoreMainThread: 主线程任务抛异常", t);
        }
    }
}
