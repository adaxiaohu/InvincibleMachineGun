package com.codigohasta.addon.utils.gcore;

import net.minecraft.client.MinecraftClient;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 「把一段逻辑送回客户端主线程执行并等它跑完」的小桥。
 *
 * <h2>为什么需要它（方案 2 的直接后果）</h2>
 * TP 锚的规划跑在**虚拟线程**上（见 {@code TpAnchorAura.doAura}），这是源的设计
 * ——一次 TP 要跑成千上万次碰撞采样，放主线程会掉帧。
 *
 * <p>旧实现之所以能在虚拟线程里安全地推进整个流程，是因为它**只碰影子背包**
 * （{@code GcoreItemManager.SimulationPlayerInventory}）——一份自己的副本。
 * 代价是副本会与真实背包漂移（真实翻车点：空手时 {@code ItemStack.EMPTY}
 * 单例导致切回错误的槽）。
 *
 * <p>方案 2 改成**直接操作真实背包**（{@code interactionManager.clickSlot}），
 * 于是「虚拟线程」与「真实背包」第一次相遇。真实背包 / {@code ScreenHandler} /
 * 发包都**只能主线程碰**，否则与渲染线程竞争。所以：
 * <blockquote>
 * 规划留在虚拟线程，<b>执行段交回主线程</b>。
 * </blockquote>
 *
 * <h2>为什么可以直接阻塞等待</h2>
 * 调用方是**虚拟线程**。虚拟线程阻塞时会从载体线程（carrier）上卸载，
 * 不会占住 ForkJoinPool 的工作线程，所以 {@link CountDownLatch#await()} 是廉价且安全的。
 * 反过来，如果调用方是平台线程（或在主线程上），则会变成真阻塞 —— 因此本类在
 * 主线程调用时**直接就地执行**，绝不排队等待（否则必然死锁）。
 */
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
