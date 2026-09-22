package com.codigohasta.addon.utils.slimefunhelper.managers;

import com.codigohasta.addon.utils.slimefunhelper.managers.task.*;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.BooleanSupplier;

/** Source pre/post queues and task timing, with the addon shutdown boundary. */
public final class Tasks {
    private static volatile int tick;
    private static long generation;
    private static final Deque<Task> preTasks = new ConcurrentLinkedDeque<>();
    private static final Deque<Task> postTasks = new ConcurrentLinkedDeque<>();

    public static int getTick() { return tick; }
    public static void tick() { if (++tick < 0) tick = 0; run(preTasks); }
    public static void postTick() { run(postTasks); }

    private static void run(Deque<Task> tasks) {
        long epoch = generation;
        var iterator = tasks.iterator();
        while (epoch == generation && iterator.hasNext()) {
            var task = iterator.next();
            try {
                if (task.execute()) iterator.remove();
            } catch (net.minecraft.util.crash.CrashException | StackOverflowError error) {
                iterator.remove();
                throw error;
            } catch (Throwable error) {
                iterator.remove();
                com.codigohasta.addon.utils.slimefunhelper.utils.Debug.info("unexpected error while executing TimedTask:",error);
            }
        }
    }

    public static void scheduleDelayed(Runnable task, int delay) { postTasks.addLast(new TimedTask.Impl(task, delay)); }
    public static void scheduleDelayedPre(Runnable task, int delay) { preTasks.addLast(new TimedTask.Impl(task, delay)); }
    public static void scheduleRepeated(BooleanSupplier task, int delay, int period) { postTasks.addLast(new RepeatTask.Impl(task, delay, period)); }
    public static void scheduleRepeatedPre(BooleanSupplier task, int delay, int period) { preTasks.addLast(new RepeatTask.Impl(task, delay, period)); }
    public static void scheduleRepeatedPre(BooleanSupplier task, int delay, int period, int count) { preTasks.addLast(new RepeatTask.CountDown(task, delay, period, count)); }
    public static void reset() { generation++; preTasks.clear(); postTasks.clear(); }
}
