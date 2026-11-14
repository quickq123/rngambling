package rngambling.outcome;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.server.MinecraftServer;

/**
 * Runs effects' later steps: TNT raining over ten seconds, a countdown, a curse that wears off.
 * Server-side, in ticks; tasks don't survive a restart (an effect cut short by one just stops).
 */
public final class Scheduler {
    private record Task(long due, Runnable run) {
    }

    private static final List<Task> TASKS = new ArrayList<>();
    private static long now;

    private Scheduler() {
    }

    /** Runs {@code task} after {@code ticks}. */
    public static void later(int ticks, Runnable task) {
        TASKS.add(new Task(now + Math.max(1, ticks), task));
    }

    /** Runs {@code step} {@code times} times, every {@code interval} ticks, starting after one interval; the step gets its count from 0. */
    public static void repeat(int interval, int times, IntConsumer step) {
        for (int i = 0; i < times; i++) {
            int index = i;
            later(interval * (i + 1), () -> step.accept(index));
        }
    }

    public static void tick(MinecraftServer server) {
        now++;
        if (TASKS.isEmpty()) {
            return;
        }
        List<Task> due = new ArrayList<>();
        TASKS.removeIf(task -> {
            if (task.due() <= now) {
                due.add(task);
                return true;
            }
            return false;
        });
        for (Task task : due) {
            try {
                task.run().run();
            } catch (RuntimeException e) {
                com.mojang.logging.LogUtils.getLogger().error("A lucky block effect's later step failed", e);
            }
        }
    }

    /** For tests: runs every pending task now, including any they schedule (up to {@code limit} rounds). Failures are thrown. */
    public static void runAllNow(int limit) {
        for (int round = 0; round < limit && !TASKS.isEmpty(); round++) {
            List<Task> due = new ArrayList<>(TASKS);
            TASKS.clear();
            for (Task task : due) {
                task.run().run();
            }
        }
    }

    /** Drops every pending task (server stopping, or tests). */
    public static void clear() {
        TASKS.clear();
    }

    public static int pending() {
        return TASKS.size();
    }
}
